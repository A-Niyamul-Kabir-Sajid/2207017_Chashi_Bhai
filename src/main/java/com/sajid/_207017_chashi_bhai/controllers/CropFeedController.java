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
import javafx.scene.layout.*;

import java.io.File;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * CropFeedController - Shared crop feed for both buyers and farmers
 * Role-aware behavior and actions, with filters and search
 */
public class CropFeedController {

    @FXML private Label lblTitle;
    @FXML private TextField txtSearch;
    @FXML private Button btnToggleFilter;
    @FXML private VBox vboxCrops;
    @FXML private GridPane myCropsGrid;
    @FXML private HBox hboxMyCropsHeader;
    @FXML private VBox filterPane;
    @FXML private ComboBox<String> cmbCategory;
    @FXML private ComboBox<String> cmbDistrict;
    @FXML private TextField tfPriceMin;
    @FXML private TextField tfPriceMax;
    @FXML private CheckBox chkVerifiedOnly;
    @FXML private Label lblTotalCount;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;
    private String role; // "farmer" or "buyer"

    // Keep a simple in-memory representation to support quick filtering
    private static class CropItem {
        int id;
        int farmerId;
        String name;
        String farmerName;
        boolean farmerVerified;
        String farmerPhone; // Add this field
        double price;
        String unit;
        double quantity;
        String district;
        String availableDate; // created_at or date string
        String photoPath;
    }

    private final List<CropItem> loadedCrops = new ArrayList<>();

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        if (currentUser == null) {
            showError("অ্যাক্সেস অস্বীকার", "দয়া করে লগইন করুন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }
        role = currentUser.getRole();

        // Set dynamic title by role
        if ("farmer".equals(role)) {
            lblTitle.setText("বাজারের ফসল এবং আমার তালিকা");
        } else {
            lblTitle.setText("আজকের উপলব্ধ ফসলসমূহ");
        }

        // Pre-select district for farmer
        if ("farmer".equals(role) && currentUser.getDistrict() != null) {
            cmbDistrict.getSelectionModel().select(currentUser.getDistrict());
        }

        // Live search
        txtSearch.textProperty().addListener((obs, oldV, newV) -> filterLocally(newV));

        // Initial load
        loadCrops(false);
    }

    @FXML
    private void onBack() {
        App.loadScene("welcome-view.fxml", "Chashi Bhai");
    }

    @FXML
    private void onToggleFilter() {
        boolean visible = filterPane.isVisible();
        filterPane.setVisible(!visible);
    }

    @FXML
    private void onSearchKeyUp() {
        filterLocally(txtSearch.getText().trim());
    }

    @FXML
    private void onApplyFilter() {
        loadCrops(true);
    }

    @FXML
    private void onClearFilter() {
        cmbCategory.getSelectionModel().clearSelection();
        cmbDistrict.getSelectionModel().clearSelection();
        tfPriceMin.clear();
        tfPriceMax.clear();
        chkVerifiedOnly.setSelected(false);
        txtSearch.clear();
        loadCrops(false);
    }

    /**
     * Load crops from DB with optional filters.
     */
    private void loadCrops(boolean useFilters) {
        if (progressIndicator != null) progressIndicator.setVisible(true);
        vboxCrops.getChildren().clear();
        myCropsGrid.setVisible(false);
        hboxMyCropsHeader.setVisible(false);
        loadedCrops.clear();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.*, u.name as farmer_name, u.phone as farmer_phone, u.is_verified, ")
           .append(" (SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as photo")
           .append(" FROM crops c JOIN users u ON c.farmer_id = u.id WHERE c.status = 'active'");

        List<Object> params = new ArrayList<>();

        if (useFilters) {
            String category = cmbCategory.getSelectionModel().getSelectedItem();
            String district = cmbDistrict.getSelectionModel().getSelectedItem();
            String pMin = tfPriceMin.getText().trim();
            String pMax = tfPriceMax.getText().trim();
            boolean verifiedOnly = chkVerifiedOnly.isSelected();

            if (category != null && !category.isEmpty() && !"সব".equals(category)) {
                sql.append(" AND c.category = ?");
                params.add(category);
            }
            if (district != null && !district.isEmpty() && !"সব".equals(district)) {
                sql.append(" AND c.district = ?");
                params.add(district);
            }
            if (!pMin.isEmpty()) {
                sql.append(" AND c.price >= ?");
                try { params.add(Double.parseDouble(pMin)); } catch (Exception ignored) {}
            }
            if (!pMax.isEmpty()) {
                sql.append(" AND c.price <= ?");
                try { params.add(Double.parseDouble(pMax)); } catch (Exception ignored) {}
            }
            if (verifiedOnly) {
                sql.append(" AND u.is_verified = 1");
            }
        }

        // Ordering by role
        if ("farmer".equals(role)) {
            sql.append(" ORDER BY CASE WHEN c.farmer_id = ? THEN 0 ELSE 1 END, c.created_at DESC");
            params.add(currentUser.getId());
        } else { // buyer
            String district = currentUser.getDistrict();
            if (district != null && !district.isEmpty()) {
                sql.append(" ORDER BY CASE WHEN c.district = ? THEN 0 ELSE 1 END, c.created_at DESC");
                params.add(district);
            } else {
                sql.append(" ORDER BY c.created_at DESC");
            }
        }

        DatabaseService.executeQueryAsync(sql.toString(), params.toArray(), rs -> {
            Platform.runLater(() -> {
                try {
                    int myCropPreviewCount = 0;
                    while (rs.next()) {
                        CropItem item = mapItem(rs);
                        loadedCrops.add(item);

                        // For farmer: populate top highlights with own crops (first 3)
                        if ("farmer".equals(role) && item.farmerId == currentUser.getId() && myCropPreviewCount < 3) {
                            addMyCropPreview(item, myCropPreviewCount++);
                        }

                        vboxCrops.getChildren().add(buildCropCard(item));
                    }

                    // Show farmer highlights section if any
                    if ("farmer".equals(role) && myCropPreviewCount > 0) {
                        hboxMyCropsHeader.setVisible(true);
                        myCropsGrid.setVisible(true);
                    }

                    lblTotalCount.setText("মোট ফসল: " + loadedCrops.size() + "টি");
                } catch (Exception e) {
                    e.printStackTrace();
                    showError("ত্রুটি", "ফসলের তালিকা লোড করতে ব্যর্থ হয়েছে।");
                } finally {
                    if (progressIndicator != null) progressIndicator.setVisible(false);
                }
            });
        }, err -> {
            Platform.runLater(() -> {
                if (progressIndicator != null) progressIndicator.setVisible(false);
                showError("ডাটাবেস ত্রুটি", "ফসল লোডে সমস্যা হয়েছে।");
                err.printStackTrace();
            });
        });
    }

    private CropItem mapItem(ResultSet rs) throws Exception {
        CropItem item = new CropItem();
        item.id = rs.getInt("id");
        item.farmerId = rs.getInt("farmer_id");
        item.name = rs.getString("name");
        item.farmerName = rs.getString("farmer_name");
        item.farmerPhone = safeString(rs, "farmer_phone");
        item.farmerVerified = rs.getBoolean("is_verified");
        item.price = rs.getDouble("price");
        item.unit = rs.getString("unit");
        try { item.quantity = rs.getDouble("quantity"); } catch (Exception ignored) { item.quantity = 0.0; }
        item.district = safeString(rs, "district");
        item.availableDate = safeString(rs, "created_at");
        item.photoPath = safeString(rs, "photo");
        return item;
    }

    private String safeString(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (Exception e) { return ""; }
    }

    private void addMyCropPreview(CropItem item, int index) {
        VBox preview = new VBox(6);
        preview.getStyleClass().add("crop-card");
        preview.setPadding(new Insets(8));

        Label name = new Label(item.name);
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label price = new Label(String.format("৳%.2f/%s", item.price, item.unit));
        price.setStyle("-fx-font-size: 12px; -fx-text-fill: #4CAF50;");
        Label district = new Label("📍 " + item.district);
        district.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        preview.getChildren().addAll(name, price, district);
        preview.setOnMouseClicked(e -> openDetails(item.id));

        myCropsGrid.add(preview, index % 3, index / 3);
    }

    private Pane buildCropCard(CropItem item) {
        HBox card = new HBox(12);
        card.getStyleClass().add("crop-card");
        card.setPadding(new Insets(12));
        card.setOnMouseClicked(e -> openDetails(item.id));

        // Photo
        ImageView imageView = new ImageView();
        imageView.setFitWidth(120);
        imageView.setFitHeight(100);
        imageView.setPreserveRatio(true);
        if (item.photoPath != null && !item.photoPath.isEmpty()) {
            File photoFile = new File(item.photoPath);
            if (photoFile.exists()) {
                imageView.setImage(new Image(photoFile.toURI().toString()));
            }
        }

        // Details
        VBox details = new VBox(6);
        details.setPrefWidth(480);

        HBox titleRow = new HBox(6);
        Label name = new Label(item.name);
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label myBadge = new Label("আমার ফসল");
        myBadge.getStyleClass().add("my-crop-badge");
        myBadge.setVisible(item.farmerId == currentUser.getId());
        titleRow.getChildren().addAll(name, myBadge);

        HBox farmerRow = new HBox(6);
        Label farmerName = new Label(item.farmerName);
        farmerName.setStyle("-fx-font-size: 12px;");
        Label verified = new Label("✓");
        verified.getStyleClass().add("verified-badge");
        verified.setVisible(item.farmerVerified);
        farmerRow.getChildren().addAll(farmerName, verified);

        Label price = new Label(String.format("৳%.2f/%s", item.price, item.unit));
        price.setStyle("-fx-font-size: 16px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        Label qty = new Label(item.quantity > 0 ? String.format("পরিমাণ: %.1f", item.quantity) : "পরিমাণ: N/A");
        qty.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label district = new Label("📍 " + item.district);
        district.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label date = new Label("তারিখ: " + (item.availableDate != null ? item.availableDate : "N/A"));
        date.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        details.getChildren().addAll(titleRow, farmerRow, price, qty, district, date);

        // Actions by role
        VBox actionsBox = new VBox(8);
        actionsBox.setPrefWidth(200);
        actionsBox.setId("actionsHBox");

        if (item.farmerId == currentUser.getId()) {
            // Farmer own crop
            Button edit = new Button("সম্পাদনা");
            edit.getStyleClass().add("button-secondary");
            edit.setMaxWidth(Double.MAX_VALUE);
            edit.setOnAction(e -> editCrop(item.id));

            Button del = new Button("মুছুন");
            del.getStyleClass().add("button-danger");
            del.setMaxWidth(Double.MAX_VALUE);
            del.setOnAction(e -> deleteCrop(item.id));

            actionsBox.getChildren().addAll(edit, del);
        } else if ("buyer".equals(role)) {
            // Buyer actions
            Button contact = new Button("যোগাযোগ করুন");
            contact.getStyleClass().add("button-secondary");
            contact.setMaxWidth(Double.MAX_VALUE);
            contact.setOnAction(e -> contactFarmer(item));

            Button order = new Button("অর্ডার করুন");
            order.getStyleClass().add("button-primary");
            order.setMaxWidth(Double.MAX_VALUE);
            order.setOnAction(e -> orderCrop(item.id));

            Button whatsapp = new Button("WhatsApp");
            whatsapp.getStyleClass().add("button-transparent");
            whatsapp.setMaxWidth(Double.MAX_VALUE);
            whatsapp.setOnAction(e -> openWhatsApp(item.farmerPhone));

            Button call = new Button("Call");
            call.getStyleClass().add("button-transparent");
            call.setMaxWidth(Double.MAX_VALUE);
            call.setOnAction(e -> openPhone(item.farmerPhone));

            actionsBox.getChildren().addAll(contact, order, whatsapp, call);
        } else {
            // Farmer viewing others
            Button view = new Button("বিস্তারিত দেখুন");
            view.getStyleClass().add("button-secondary");
            view.setMaxWidth(Double.MAX_VALUE);
            view.setOnAction(e -> openDetails(item.id));
            actionsBox.getChildren().add(view);
        }

        card.getChildren().addAll(imageView, details, actionsBox);
        return card;
    }

    private void filterLocally(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        vboxCrops.getChildren().clear();
        int count = 0;
        for (CropItem item : loadedCrops) {
            if (q.isEmpty() || (item.name != null && item.name.toLowerCase().contains(q)) ||
                (item.district != null && item.district.toLowerCase().contains(q)) ||
                (item.farmerName != null && item.farmerName.toLowerCase().contains(q))) {
                vboxCrops.getChildren().add(buildCropCard(item));
                count++;
            }
        }
        lblTotalCount.setText("মোট ফসল: " + count + "টি");
    }

    private void openDetails(int cropId) {
        App.setCurrentCropId(cropId);
        App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
    }

    private void editCrop(int cropId) {
        App.setCurrentCropId(cropId);
        App.loadScene("edit-crop-view.fxml", "ফসল সম্পাদনা");
    }

    private void deleteCrop(int cropId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("আপনি কি এই ফসলটি মুছতে চান?");
        confirm.setContentText("এই কাজটি পূর্বাবস্থায় ফেরত নেওয়া যাবে না।");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                DatabaseService.executeUpdateAsync(
                    "UPDATE crops SET status = 'deleted' WHERE id = ?",
                    new Object[]{cropId},
                    rowsAffected -> {
                        Platform.runLater(() -> {
                            showSuccess("সফল", "ফসল মুছে ফেলা হয়েছে।");
                            loadCrops(false);
                        });
                    },
                    err -> {
                        Platform.runLater(() -> showError("ত্রুটি", "ফসল মুছতে ব্যর্থ হয়েছে।"));
                        err.printStackTrace();
                    }
                );
            }
        });
    }

    private void orderCrop(int cropId) {
        App.setCurrentCropId(cropId);
        App.loadScene("crop-detail-view.fxml", "অর্ডার করুন");
    }

    private void contactFarmer(CropItem item) {
        // Get or create conversation with farmer
        String sql = "SELECT id FROM conversations WHERE " +
                    "(user1_id = ? AND user2_id = ? AND (crop_id = ? OR crop_id IS NULL)) OR " +
                    "(user1_id = ? AND user2_id = ? AND (crop_id = ? OR crop_id IS NULL))";
        Object[] params = {currentUser.getId(), item.farmerId, item.id, 
                          item.farmerId, currentUser.getId(), item.id};
        
        DatabaseService.executeQueryAsync(sql, params,
            rs -> {
                try {
                    if (rs.next()) {
                        // Conversation exists
                        int convId = rs.getInt("id");
                        Platform.runLater(() -> openConversation(convId, item.farmerId, item.farmerName, item.id));
                    } else {
                        // Create new conversation
                        createAndOpenConversation(item);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> showError("Error", "Failed to open chat"));
                }
            },
            err -> {
                Platform.runLater(() -> showError("Error", "Database error: " + err.getMessage()));
            }
        );
    }
    
    private void createAndOpenConversation(CropItem item) {
        String insertSql = "INSERT INTO conversations (user1_id, user2_id, crop_id) VALUES (?, ?, ?)";
        Object[] params = {currentUser.getId(), item.farmerId, item.id};
        
        DatabaseService.executeUpdateAsync(insertSql, params,
            rows -> {
                // Get the newly created conversation ID
                String selectSql = "SELECT id FROM conversations WHERE user1_id = ? AND user2_id = ? AND crop_id = ?";
                DatabaseService.executeQueryAsync(selectSql, params,
                    rs -> {
                        try {
                            if (rs.next()) {
                                int convId = rs.getInt("id");
                                Platform.runLater(() -> openConversation(convId, item.farmerId, item.farmerName, item.id));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    },
                    err -> err.printStackTrace()
                );
            },
            err -> {
                Platform.runLater(() -> showError("Error", "Failed to create conversation"));
            }
        );
    }
    
    private void openConversation(int convId, int userId, String userName, int cropId) {
        try {
            App.showView("chat-conversation-view.fxml", controller -> {
                if (controller instanceof ChatConversationController) {
                    ChatConversationController chatController = (ChatConversationController) controller;
                    chatController.loadConversation(convId, userId, userName, cropId);
                }
            });
        } catch (Exception e) {
            showError("Error", "Failed to open chat");
            e.printStackTrace();
        }
    }

    private void openPhone(String phone) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("tel:" + (phone == null ? "" : phone)));
        } catch (Exception e) {
            showInfo("Phone", "ফোন নম্বর: " + (phone == null ? "N/A" : phone));
        }
    }

    private void openWhatsApp(String phone) {
        try {
            String cleanPhone = phone == null ? "" : phone.replaceAll("[^0-9]", "");
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://wa.me/" + cleanPhone));
        } catch (Exception e) {
            showInfo("WhatsApp", "WhatsApp: " + (phone == null ? "N/A" : phone));
        }
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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
