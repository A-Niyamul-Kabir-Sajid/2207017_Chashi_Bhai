package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * CropDetailController - Detailed crop view with photo carousel and farmer profile
 */
public class CropDetailController {

    @FXML private ImageView imgMainPhoto;
    @FXML private HBox hboxThumbnails;
    @FXML private Label lblCropName;
    @FXML private Label lblCropPrice;
    @FXML private Label lblProductCode;
    @FXML private Button btnCopyCode;
    @FXML private Label lblCategory;
    @FXML private Label lblQuantity;
    @FXML private Label lblLocation;
    @FXML private Label lblTransport;
    @FXML private Label lblDescription;
    @FXML private ImageView imgFarmerPhoto;
    @FXML private Label lblFarmerName;
    @FXML private Label lblFarmerRating;
    @FXML private Label lblTotalReviews;
    @FXML private Label lblFarmerYears;
    @FXML private Label lblFarmerSales;
    @FXML private Label lblFarmerDistrict;
    @FXML private Button btnCall;
    @FXML private Button btnWhatsApp;
    @FXML private Button btnChat;
    @FXML private Button btnOrder;
    @FXML private Button btnFavorite;

    private User currentUser;
    private int cropId;
    private String productCode;
    private int farmerId;
    private String farmerPhone;
    private double cropPrice;
    private String cropUnit;
    private String[] photoPaths;
    private int currentPhotoIndex = 0;
    private Double orderedQuantity = null; // Set when viewing from order context

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        cropId = App.getCurrentCropId();
        System.out.println("Loading crop details for cropId: " + cropId);
        
        // Check if viewing from order context
        int contextOrderId = App.getCurrentOrderId();
        if (contextOrderId > 0) {
            loadOrderQuantity(contextOrderId);
        }
        
        if (currentUser == null) {
            showError("অ্যাক্সেস অস্বীকার", "দয়া করে লগইন করুন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        if (cropId <= 0) {
            showError("ত্রুটি", "ফসলের তথ্য খুঁজে পাওয়া যায়নি।");
            onBack();
            return;
        }

        loadCropDetails();
        loadCropPhotos();
    }

    private void loadCropDetails() {
        String sql = "SELECT c.*, " +
                    "c.price_per_kg as unit_price, " +
                    "c.available_quantity_kg as available_qty, " +
                    "u.id as farmer_id, u.name as farmer_name, u.phone as farmer_phone, " +
                    "u.district as farmer_district, u.is_verified as farmer_verified, " +
                    "u.profile_photo as farmer_photo, " +
                    "COALESCE(CAST((julianday('now') - julianday(u.created_at)) / 365 AS INTEGER), 0) as years_farming, " +
                    "(SELECT COUNT(*) FROM orders o JOIN crops cr ON o.crop_id = cr.id WHERE cr.farmer_id = u.id AND o.status = 'delivered') as total_sales, " +
                    "(SELECT COALESCE(AVG(r.rating), 0.0) FROM reviews r WHERE r.reviewee_id = u.id) as avg_rating, " +
                    "(SELECT COUNT(*) FROM reviews r WHERE r.reviewee_id = u.id) as total_reviews " +
                    "FROM crops c " +
                    "JOIN users u ON c.farmer_id = u.id " +
                    "WHERE c.id = ?";

        DatabaseService.executeQueryAsync(sql, new Object[]{cropId},
            rs -> {
                // CRITICAL: Read ResultSet data BEFORE Platform.runLater to avoid closed ResultSet
                try {
                    if (rs.next()) {
                        // Read all crop details from ResultSet
                        String name = rs.getString("name");
                        String pCode = rs.getString("product_code");
                        String category = rs.getString("category");
                        double unitPrice = rs.getDouble("unit_price");
                        String unit = "কেজি"; // All crops measured in kg
                        double quantity = rs.getDouble("available_qty");
                        String harvestDate = rs.getString("harvest_date");
                        String district = rs.getString("district");
                        String transport = rs.getString("transport_info");
                        String description = rs.getString("description");

                        // Read all farmer details from ResultSet
                        int fId = rs.getInt("farmer_id");
                        String farmerName = rs.getString("farmer_name");
                        String fPhone = rs.getString("farmer_phone");
                        String farmerDistrict = rs.getString("farmer_district");
                        boolean isVerified = rs.getBoolean("farmer_verified");
                        int yearsFarming = rs.getInt("years_farming");
                        int totalSales = rs.getInt("total_sales");
                        double avgRating = rs.getDouble("avg_rating");
                        int totalReviews = rs.getInt("total_reviews");
                        String farmerPhotoPath = rs.getString("farmer_photo");

                        // Now update UI on JavaFX thread with pre-loaded data
                        Platform.runLater(() -> {
                            try {
                                // Set crop details
                                productCode = pCode;
                                cropPrice = unitPrice;
                                cropUnit = unit;
                                farmerId = fId;
                                farmerPhone = fPhone;

                                lblCropName.setText(name != null ? name : "N/A");
                                lblCropPrice.setText(String.format("৳%.2f/%s", cropPrice, cropUnit));
                                lblProductCode.setText(productCode != null ? productCode : "N/A");
                                lblCategory.setText(category != null ? category : "N/A");
                                
                                // Show ordered quantity if viewing from order, otherwise show available quantity
                                if (orderedQuantity != null) {
                                    lblQuantity.setText(String.format("অর্ডার: %.1f %s (মোট: %.1f %s)", orderedQuantity, cropUnit, quantity, cropUnit));
                                } else {
                                    lblQuantity.setText(String.format("%.1f %s", quantity, cropUnit));
                                }

                                lblLocation.setText(district != null ? district : "N/A");
                                lblTransport.setText(transport != null ? transport : "N/A");
                                lblDescription.setText(description != null ? description : "কোনো বিবরণ নেই");

                                lblFarmerName.setText(farmerName + (isVerified ? " ✓" : ""));
                                lblFarmerRating.setText(String.format("★ %.1f", avgRating));
                                lblTotalReviews.setText("(" + totalReviews + " রিভিউ)");
                                lblFarmerYears.setText(yearsFarming + " বছর");
                                lblFarmerSales.setText(totalSales + " বিক্রয়");
                                lblFarmerDistrict.setText(farmerDistrict != null ? farmerDistrict : "N/A");

                                // Load farmer photo
                                if (farmerPhotoPath != null && !farmerPhotoPath.isEmpty()) {
                                    File photoFile = new File(farmerPhotoPath);
                                    if (photoFile.exists() && imgFarmerPhoto != null) {
                                        imgFarmerPhoto.setImage(new Image(photoFile.toURI().toString()));
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                showError("ত্রুটি", "ফসলের তথ্য প্রদর্শন করতে ব্যর্থ হয়েছে।");
                            }
                        });
                    } else {
                        Platform.runLater(() -> showError("ত্রুটি", "ফসলের তথ্য পাওয়া যায়নি।"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> showError("ত্রুটি", "ফসলের তথ্য লোড করতে ব্যর্থ হয়েছে।"));
                }
            },
            error -> {
                Platform.runLater(() -> showError("ডাটাবেস ত্রুটি", "ফসলের তথ্য লোড করতে সমস্যা হয়েছে।"));
                error.printStackTrace();
            }
        );
    }

    private void loadCropPhotos() {
        String sql = "SELECT photo_path FROM crop_photos WHERE crop_id = ? ORDER BY photo_order";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{cropId},
            rs -> {
                // CRITICAL: Read ResultSet data BEFORE Platform.runLater to avoid closed ResultSet
                List<String> paths = new ArrayList<>();
                try {
                    while (rs.next()) {
                        String path = rs.getString("photo_path");
                        if (path != null && new File(path).exists()) {
                            paths.add(path);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // Now update UI on JavaFX thread with pre-loaded data
                Platform.runLater(() -> {
                    try {
                        photoPaths = paths.toArray(new String[0]);
                        
                        if (photoPaths.length > 0) {
                            loadPhoto(0);
                            loadThumbnails();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> error.printStackTrace()
        );
    }

    private void loadPhoto(int index) {
        if (photoPaths != null && index >= 0 && index < photoPaths.length) {
            currentPhotoIndex = index;
            File photoFile = new File(photoPaths[index]);
            if (photoFile.exists()) {
                imgMainPhoto.setImage(new Image(photoFile.toURI().toString()));
            }
        }
    }

    private void loadThumbnails() {
        hboxThumbnails.getChildren().clear();
        for (int i = 0; i < photoPaths.length; i++) {
            final int photoIndex = i;
            File photoFile = new File(photoPaths[i]);
            if (photoFile.exists()) {
                ImageView thumbnail = new ImageView(new Image(photoFile.toURI().toString()));
                thumbnail.setFitWidth(80);
                thumbnail.setFitHeight(80);
                thumbnail.setPreserveRatio(true);
                thumbnail.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 5, 0, 0, 0);");
                thumbnail.setOnMouseClicked(e -> loadPhoto(photoIndex));
                hboxThumbnails.getChildren().add(thumbnail);
            }
        }
    }

    @FXML
    private void onPrevPhoto() {
        if (currentPhotoIndex > 0) {
            loadPhoto(currentPhotoIndex - 1);
        }
    }

    @FXML
    private void onNextPhoto() {
        if (photoPaths != null && currentPhotoIndex < photoPaths.length - 1) {
            loadPhoto(currentPhotoIndex + 1);
        }
    }

    @FXML
    private void onCall() {
        try {
            Desktop.getDesktop().browse(new URI("tel:" + farmerPhone));
        } catch (Exception e) {
            showInfo("কল করুন", "ফোন নম্বর: " + farmerPhone);
        }
    }

    @FXML
    private void onWhatsApp() {
        try {
            String cleanPhone = farmerPhone.replaceAll("[^0-9]", "");
            // Add Bangladesh country code if not present
            if (!cleanPhone.startsWith("880")) {
                if (cleanPhone.startsWith("0")) {
                    cleanPhone = "880" + cleanPhone.substring(1);
                } else {
                    cleanPhone = "880" + cleanPhone;
                }
            }
            String url = "https://wa.me/" + cleanPhone;
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            showInfo("WhatsApp", "WhatsApp: " + farmerPhone);
            e.printStackTrace();
        }
    }

    @FXML
    private void onChat() {
        // Navigate to chat conversation with farmer
        try {
            App.showView("chat-conversation-view.fxml", controller -> {
                if (controller instanceof ChatConversationController) {
                    ChatConversationController chatController = (ChatConversationController) controller;
                    // Get or create conversation with farmer
                    chatController.loadConversation(0, farmerId, lblFarmerName.getText(), cropId);
                }
            });
        } catch (Exception e) {
            showError("ত্রুটি", "চ্যাট খুলতে ব্যর্থ হয়েছে।");
            e.printStackTrace();
        }
    }

    @FXML
    private void onOrder() {
        try {
            // Load order dialog
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/sajid/_207017_chashi_bhai/place-order-dialog.fxml")
            );
            javafx.scene.Parent root = loader.load();
            
            // Get controller and set crop details
            PlaceOrderDialogController dialogController = loader.getController();
            dialogController.setCropDetails(cropId, farmerId, lblCropName.getText(), cropPrice, Double.parseDouble(lblQuantity.getText().replaceAll("[^0-9.]", "")));
            
            // Create and show dialog
            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle("অর্ডার করুন");
            dialogStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dialogStage.initOwner(App.getPrimaryStage());
            dialogStage.setScene(new javafx.scene.Scene(root));
            dialogController.setDialogStage(dialogStage);
            
            dialogStage.showAndWait();
            
            // If order was placed successfully, navigate to buyer orders
            if (dialogController.isOrderPlaced()) {
                App.loadScene("buyer-orders-view.fxml", "আমার অর্ডারসমূহ");
            }
        } catch (Exception e) {
            showError("ত্রুটি", "অর্ডার ডায়ালগ খুলতে ব্যর্থ হয়েছে।");
            e.printStackTrace();
        }
    }

    @FXML
    private void onToggleFavorite() {
        showInfo("প্রিয়", "প্রিয় বৈশিষ্ট্য শীঘ্রই আসছে...");
    }

    @FXML
    private void onCopyProductCode() {
        if (productCode == null || productCode.isEmpty()) {
            showInfo("কোড নেই", "এই পণ্যের কোড পাওয়া যায়নি।");
            return;
        }
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(productCode);
            clipboard.setContent(content);
            showInfo("কপি সফল", "পণ্য কোড কপি হয়েছে: " + productCode);
        } catch (Exception e) {
            showError("ত্রুটি", "কোড কপি করতে ব্যর্থ হয়েছে।");
            e.printStackTrace();
        }
    }

    private void loadOrderQuantity(int orderId) {
        String sql = "SELECT quantity_kg FROM orders WHERE id = ?";
        DatabaseService.executeQueryAsync(sql, new Object[]{orderId},
            rs -> {
                try {
                    // Read ResultSet data BEFORE Platform.runLater
                    if (rs.next()) {
                        double qty = rs.getDouble("quantity_kg");
                        
                        // Now update UI on JavaFX thread with pre-loaded data
                        Platform.runLater(() -> {
                            orderedQuantity = qty;
                            System.out.println("Ordered quantity: " + orderedQuantity);
                            // Refresh quantity display if already loaded
                            if (lblQuantity != null && cropUnit != null) {
                                // This will be properly shown when loadCropDetails completes
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            err -> err.printStackTrace()
        );
    }

    @FXML
    private void onViewFarmerProfile() {
        // Store farmer ID and navigate to public farmer profile view
        App.setCurrentViewedUserId(farmerId);
        App.loadScene("public-farmer-profile-view.fxml", "কৃষকের প্রোফাইল");
    }

    @FXML
    private void onBack() {
        // Clear order context when going back
        App.setCurrentOrderId(-1);
        
        // Navigate back to previous scene
        String previousScene = App.getPreviousScene();
        if (previousScene != null && !previousScene.isEmpty()) {
            String title = getSceneTitle(previousScene);
            App.loadScene(previousScene, title);
        } else {
            // Default fallback to crop feed
            App.loadScene("crop-feed-view.fxml", "সকল ফসল");
        }
    }
    
    private String getSceneTitle(String sceneName) {
        switch (sceneName) {
            case "crop-feed-view.fxml": return "সকল ফসল";
            case "my-crops-view.fxml": return "আমার ফসল";
            case "buyer-dashboard-view.fxml": return "Dashboard";
            case "buyer-history-view.fxml": return "ক্রয়ের ইতিহাস";
            case "order-detail-view.fxml": return "অর্ডার বিস্তারিত";
            case "public-farmer-profile-view.fxml": return "কৃষকের প্রোফাইল";
            default: return "ফসলের তালিকা";
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
