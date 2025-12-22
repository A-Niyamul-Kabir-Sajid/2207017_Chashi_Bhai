package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
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

        String query = "SELECT c.*, " +
                      "(SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as first_photo " +
                      "FROM crops c WHERE c.farmer_id = ?";
        
        if (!"all".equals(filter)) {
            query += " AND c.status = ?";
        }
        query += " ORDER BY c.created_at DESC";

        Object[] params = "all".equals(filter) ? 
            new Object[]{currentUser.getId()} : 
            new Object[]{currentUser.getId(), filter};

        DatabaseService.executeQueryAsync(
            query,
            params,
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        boolean hasResults = false;
                        while (resultSet.next()) {
                            hasResults = true;
                            HBox cropCard = createCropCard(resultSet);
                            vboxCropsList.getChildren().add(cropCard);
                        }

                        if (!hasResults) {
                            vboxEmptyState.setVisible(true);
                            vboxCropsList.setVisible(false);
                        } else {
                            vboxEmptyState.setVisible(false);
                            vboxCropsList.setVisible(true);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("ত্রুটি", "ফসল লোড করতে ব্যর্থ হয়েছে।");
                    } finally {
                        if (progressIndicator != null) {
                            progressIndicator.setVisible(false);
                        }
                    }
                });
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
    }

    private HBox createCropCard(ResultSet rs) throws Exception {
        int cropId = rs.getInt("id");
        String name = rs.getString("name");
        String category = rs.getString("category");
        double price = rs.getDouble("price");
        String unit = rs.getString("unit");
        double quantity = rs.getDouble("quantity");
        String harvestDate = rs.getString("harvest_date");
        String status = rs.getString("status");
        String photoPath = rs.getString("first_photo");

        HBox card = new HBox(15);
        card.getStyleClass().add("crop-card");
        card.setPadding(new Insets(15));

        // Image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(100);
        imageView.setFitHeight(100);
        imageView.setPreserveRatio(true);
        if (photoPath != null && !photoPath.isEmpty()) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                imageView.setImage(new Image(photoFile.toURI().toString()));
            }
        }

        // Details
        VBox detailsBox = new VBox(5);
        detailsBox.setPrefWidth(400);
        Label lblName = new Label(name);
        lblName.getStyleClass().add("crop-name");
        lblName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        Label lblCategory = new Label(category);
        lblCategory.setStyle("-fx-font-size: 14px; -fx-text-fill: #888;");
        
        Label lblPrice = new Label(String.format("৳%.2f/%s", price, unit));
        lblPrice.setStyle("-fx-font-size: 16px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        
        Label lblQuantity = new Label(String.format("পরিমাণ: %.1f %s", quantity, unit));
        Label lblDate = new Label("তারিখ: " + harvestDate);
        
        Label lblStatus = new Label(getStatusText(status));
        lblStatus.getStyleClass().add("status-" + status);
        lblStatus.setStyle("-fx-padding: 4px 8px; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        detailsBox.getChildren().addAll(lblName, lblCategory, lblPrice, lblQuantity, lblDate, lblStatus);

        // Action buttons
        VBox actionsBox = new VBox(10);
        actionsBox.setPrefWidth(150);
        
        Button btnEdit = new Button("সম্পাদনা করুন");
        btnEdit.getStyleClass().add("button-info");
        btnEdit.setMaxWidth(Double.MAX_VALUE);
        btnEdit.setOnAction(e -> onEditCrop(cropId));
        
        Button btnDelete = new Button("মুছে ফেলুন");
        btnDelete.getStyleClass().add("button-danger");
        btnDelete.setMaxWidth(Double.MAX_VALUE);
        btnDelete.setOnAction(e -> onDeleteCrop(cropId, name));
        
        Button btnStats = new Button("পরিসংখ্যান");
        btnStats.getStyleClass().add("button-secondary");
        btnStats.setMaxWidth(Double.MAX_VALUE);
        btnStats.setOnAction(e -> showCropStats(cropId));

        actionsBox.getChildren().addAll(btnEdit, btnDelete, btnStats);

        card.getChildren().addAll(imageView, detailsBox, actionsBox);
        return card;
    }

    private String getStatusText(String status) {
        switch (status) {
            case "active": return "✓ সক্রিয়";
            case "sold": return "✓ বিক্রীত";
            case "expired": return "⏰ মেয়াদোত্তীর্ণ";
            default: return status;
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
            "(SELECT COALESCE(SUM(quantity), 0) FROM orders WHERE crop_id = ? AND status IN ('accepted', 'delivered')) as total_sold " +
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
