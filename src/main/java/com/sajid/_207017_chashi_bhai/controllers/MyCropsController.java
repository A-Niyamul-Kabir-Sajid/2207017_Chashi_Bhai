package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.sql.ResultSet;
import java.util.Optional;

/**
 * MyCropsController - Display farmer's crops with filter and management options
 */
public class MyCropsController {

    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterActive;
    @FXML private Button btnFilterSold;
    @FXML private Button btnFilterExpired;
    @FXML private ComboBox<String> cbSortBy;
    @FXML private VBox vboxCropsList;
    @FXML private VBox vboxEmptyState;
    @FXML private Button btnAddNew;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;
    private String currentFilter = "all";

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        
        if (currentUser == null || !"farmer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র কৃষকরা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }
        
        // Initialize sort dropdown with default selection
        if (cbSortBy != null) {
            cbSortBy.getSelectionModel().select(0); // Default: Newest First
            cbSortBy.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    loadCrops(currentFilter);
                }
            });
        }

        loadCrops(currentFilter);
    }

    @FXML
    private void onFilterAll() {
        setActiveFilter(btnFilterAll);
        currentFilter = "all";
        loadCrops(currentFilter);
    }

    @FXML
    private void onFilterActive() {
        setActiveFilter(btnFilterActive);
        currentFilter = "active";
        loadCrops(currentFilter);
    }

    @FXML
    private void onFilterSold() {
        setActiveFilter(btnFilterSold);
        currentFilter = "sold";
        loadCrops(currentFilter);
    }

    @FXML
    private void onFilterExpired() {
        setActiveFilter(btnFilterExpired);
        currentFilter = "expired";
        loadCrops(currentFilter);
    }

    private void setActiveFilter(Button activeButton) {
        btnFilterAll.getStyleClass().remove("filter-active");
        btnFilterActive.getStyleClass().remove("filter-active");
        btnFilterSold.getStyleClass().remove("filter-active");
        btnFilterExpired.getStyleClass().remove("filter-active");
        
        activeButton.getStyleClass().add("filter-active");
    }

    private void loadCrops(String filter) {
        if (progressIndicator != null) {
            progressIndicator.setVisible(true);
        }
        vboxCropsList.getChildren().clear();

        Runnable loadQuery = () -> {
            String query = "SELECT c.*, " +
                          "c.price_per_kg as price, " +
                          "c.available_quantity_kg as quantity, " +
                          "'কেজি' as unit, " +
                          "(SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as first_photo " +
                          "FROM crops c WHERE c.farmer_id = ?";
            
            if (!"all".equals(filter)) {
                query += " AND c.status = ?";
            }
            
            // Apply sorting based on user selection
            String sortOption = cbSortBy != null ? cbSortBy.getSelectionModel().getSelectedItem() : null;
            if (sortOption != null) {
                if (sortOption.contains("High to Low") || sortOption.contains("বেশি থেকে কম")) {
                    query += " ORDER BY c.price_per_kg DESC";
                } else if (sortOption.contains("Low to High") || sortOption.contains("কম থেকে বেশি")) {
                    query += " ORDER BY c.price_per_kg ASC";
                } else {
                    // Default: Newest First
                    query += " ORDER BY c.created_at DESC";
                }
            } else {
                query += " ORDER BY c.created_at DESC";
            }

            Object[] params = "all".equals(filter) ? 
                new Object[]{currentUser.getId()} : 
                new Object[]{currentUser.getId(), filter};

            System.out.println("[MyCrops] Loading crops for farmer_id=" + currentUser.getId() + ", filter=" + filter);
            System.out.println("[MyCrops] Query: " + query);

            DatabaseService.executeQueryAsync(
                query,
                params,
                resultSet -> {
                    try {
                        // Read ALL data from ResultSet FIRST (before Platform.runLater)
                        java.util.List<java.util.Map<String, Object>> cropDataList = new java.util.ArrayList<>();
                        while (resultSet.next()) {
                            java.util.Map<String, Object> cropData = new java.util.HashMap<>();
                            cropData.put("id", resultSet.getInt("id"));
                            cropData.put("name", resultSet.getString("name"));
                            cropData.put("category", resultSet.getString("category"));
                            cropData.put("price", resultSet.getDouble("price"));
                            cropData.put("unit", resultSet.getString("unit"));
                            cropData.put("quantity", resultSet.getDouble("quantity"));
                            cropData.put("harvest_date", resultSet.getString("harvest_date"));
                            cropData.put("district", resultSet.getString("district"));
                            cropData.put("status", resultSet.getString("status"));
                            cropData.put("first_photo", resultSet.getString("first_photo"));
                            cropDataList.add(cropData);
                        }
                        
                        System.out.println("[MyCrops] Read " + cropDataList.size() + " crops from database");
                        
                        // NOW use Platform.runLater with the data we've already read
                        Platform.runLater(() -> {
                            try {
                                for (java.util.Map<String, Object> cropData : cropDataList) {
                                    HBox cropCard = loadCropCard(cropData);
                                    if (cropCard != null) {
                                        vboxCropsList.getChildren().add(cropCard);
                                    }
                                }

                                if (cropDataList.isEmpty()) {
                                    vboxEmptyState.setVisible(true);
                                    vboxCropsList.setVisible(false);
                                } else {
                                    vboxEmptyState.setVisible(false);
                                    vboxCropsList.setVisible(true);
                                }
                            } catch (Exception e) {
                                System.err.println("[MyCrops] Error creating crop cards: " + e.getMessage());
                                e.printStackTrace();
                                showError("ত্রুটি", "ফসল লোড করতে ব্যর্থ হয়েছে।");
                            } finally {
                                if (progressIndicator != null) {
                                    progressIndicator.setVisible(false);
                                }
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("[MyCrops] Error reading ResultSet: " + e.getMessage());
                        e.printStackTrace();
                        Platform.runLater(() -> {
                            if (progressIndicator != null) {
                                progressIndicator.setVisible(false);
                            }
                            showError("ত্রুটি", "ফসল লোড করতে ব্যর্থ হয়েছে।");
                        });
                    }
                },
                error -> {
                    Platform.runLater(() -> {
                        if (progressIndicator != null) {
                            progressIndicator.setVisible(false);
                        }
                        showError("ডাটাবেস ত্রুটি", "ফসল লোড করতে সমস্যা হয়েছে।");
                        error.printStackTrace();
                    });
                }
            );
        };

        // First, ensure sold-out crops are marked as 'sold' so they show in the correct tab
        DatabaseService.executeUpdateAsync(
            "UPDATE crops SET status = 'sold', updated_at = datetime('now') " +
            "WHERE farmer_id = ? AND status != 'sold' AND initial_quantity_kg > 0 AND available_quantity_kg <= 0",
            new Object[]{currentUser.getId()},
            rows -> loadQuery.run(),
            error -> Platform.runLater(() -> {
                if (progressIndicator != null) {
                    progressIndicator.setVisible(false);
                }
                showError("ত্রুটি", "ফসল লোড করতে ব্যর্থ হয়েছে।");
                error.printStackTrace();
            })
        );
    }

    private HBox loadCropCard(java.util.Map<String, Object> data) {
        try {
            int cropId = (int) data.get("id");
            String name = (String) data.get("name");
            String category = (String) data.get("category");
            double price = (double) data.get("price");
            String unit = (String) data.get("unit");
            double quantity = (double) data.get("quantity");
            String harvestDate = (String) data.get("harvest_date");
            String district = (String) data.get("district");
            String status = (String) data.get("status");
            String photoPath = (String) data.get("first_photo");

            // Load FXML template
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/sajid/_207017_chashi_bhai/item-my-crop-farmer.fxml"));
            HBox card = loader.load();
            
            // Get controller and set data
            ItemMyCropFarmerController controller = loader.getController();
            controller.setCropData(
                cropId,
                name,
                category,
                price,
                unit,
                quantity,
                harvestDate,
                district,
                status,
                photoPath,
                () -> onDeleteCrop(cropId, name)  // Delete callback
            );
            
            return card;
        } catch (Exception e) {
            System.err.println("[MyCrops] Error loading crop card: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void onEditCrop(int cropId) {
        App.setCurrentCropId(cropId);
        App.loadScene("edit-crop-view.fxml", "ফসল সম্পাদনা করুন");
    }

    private void onDeleteCrop(int cropId, String cropName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("ফসল মুছে ফেলতে চান?");
        confirm.setContentText("আপনি কি নিশ্চিত যে \"" + cropName + "\" মুছে ফেলতে চান? এই কাজ পূর্বাবস্থায় ফেরানো যাবে না।");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            DatabaseService.executeUpdateAsync(
                "DELETE FROM crops WHERE id = ? AND farmer_id = ?",
                new Object[]{cropId, currentUser.getId()},
                rowsAffected -> {
                    if (rowsAffected > 0) {
                        // Also delete photos
                        DatabaseService.executeUpdateAsync(
                            "DELETE FROM crop_photos WHERE crop_id = ?",
                            new Object[]{cropId},
                            rows -> {},
                            error -> error.printStackTrace()
                        );
                        
                        Platform.runLater(() -> {
                            showSuccess("সফল", "ফসল মুছে ফেলা হয়েছে।");
                            loadCrops(currentFilter);
                        });
                    }
                },
                error -> {
                    Platform.runLater(() -> {
                        showError("ত্রুটি", "ফসল মুছতে সমস্যা হয়েছে।");
                        error.printStackTrace();
                    });
                }
            );
        }
    }

    private void showCropStats(int cropId) {
        // Load crop statistics (views, orders, etc.)
        DatabaseService.executeQueryAsync(
            "SELECT " +
            "(SELECT COUNT(*) FROM orders WHERE crop_id = ?) as total_orders, " +
            "(SELECT COALESCE(SUM(quantity_kg), 0) FROM orders WHERE crop_id = ? AND status IN ('accepted', 'in_transit', 'completed', 'delivered')) as total_sold " +
            "FROM crops WHERE id = ?",
            new Object[]{cropId, cropId, cropId},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            int totalOrders = resultSet.getInt("total_orders");
                            double totalSold = resultSet.getDouble("total_sold");
                            
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("ফসলের পরিসংখ্যান");
                            alert.setHeaderText("এই ফসলের তথ্য");
                            alert.setContentText(
                                "মোট অর্ডার: " + totalOrders + "\n" +
                                "মোট বিক্রিত: " + totalSold + " একক"
                            );
                            alert.showAndWait();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> error.printStackTrace()
        );
    }

    @FXML
    private void onAddNew() {
        App.setPreviousScene("my-crops-view.fxml");
        App.loadScene("post-crop-view.fxml", "নতুন ফসল যোগ করুন");
    }

    @FXML
    private void onBack() {
        App.loadScene("farmer-dashboard-view.fxml", "Dashboard");
    }

    @FXML
    private void onRefresh() {
        loadCrops(currentFilter);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
