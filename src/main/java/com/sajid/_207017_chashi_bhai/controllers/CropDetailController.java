package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.utils.ImageBase64Util;
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
    @FXML private Button btnPrevPhoto;
    @FXML private Button btnNextPhoto;
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
    private int cropIdNumber; // The numeric crop ID to copy
    private int farmerId;
    private String farmerPhone;
    private double cropPrice;
    private String cropUnit;
    private List<CropPhoto> cropPhotos = new ArrayList<>(); // Store photo data
    private int currentPhotoIndex = 0;
    private Double orderedQuantity = null; // Set when viewing from order context
    
    // Inner class to hold photo data
    private static class CropPhoto {
        String path;
        String base64;
        
        CropPhoto(String path, String base64) {
            this.path = path;
            this.base64 = base64;
        }
        
        Image toImage() {
            // Try Base64 first
            if (base64 != null && !base64.isEmpty()) {
                Image img = ImageBase64Util.base64ToImage(base64);
                if (img != null) return img;
            }
            // Fall back to file path
            if (path != null && !path.isEmpty()) {
                File file = new File(path);
                if (file.exists()) {
                    return new Image(file.toURI().toString());
                }
            }
            return null;
        }
    }

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        cropId = App.getCurrentCropId();
        System.out.println("Loading crop details for cropId: " + cropId);

        // Only buyers can place orders
        if (btnOrder != null) {
            boolean isBuyer = currentUser != null && "buyer".equals(currentUser.getRole());
            btnOrder.setVisible(isBuyer);
            btnOrder.setManaged(isBuyer);
        }
        
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
                    "(SELECT COUNT(*) FROM orders o JOIN crops cr ON o.crop_id = cr.id WHERE cr.farmer_id = u.id AND o.status IN ('delivered','completed')) as total_sales, " +
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
                        int pCode = rs.getInt("id");
                        String category = rs.getString("category");
                        double unitPrice = rs.getDouble("unit_price");
                        String unit = "কেজি"; // All crops measured in kg
                        double quantity = rs.getDouble("available_qty");
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
                                cropIdNumber = pCode; // Store numeric crop ID for copying
                                cropPrice = unitPrice;
                                cropUnit = unit;
                                farmerId = fId;
                                farmerPhone = fPhone;

                                lblCropName.setText(name != null ? name : "N/A");
                                lblCropPrice.setText(String.format("৳%.2f/%s", cropPrice, cropUnit));
                                lblProductCode.setText(String.valueOf(cropIdNumber)); // Show numeric crop ID
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
        // Updated query to include image_base64 and order by photo_order
        String sql = "SELECT id, crop_id, photo_path, image_base64, photo_order FROM crop_photos WHERE crop_id = ? ORDER BY photo_order ASC";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{cropId},
            rs -> {
                // CRITICAL: Read ResultSet data BEFORE Platform.runLater to avoid closed ResultSet
                List<CropPhoto> photos = new ArrayList<>();
                try {
                    while (rs.next()) {
                        String path = rs.getString("photo_path");
                        String base64 = rs.getString("image_base64");
                        int photoOrder = rs.getInt("photo_order");
                        
                        // Create photo object with both path and base64
                        CropPhoto photo = new CropPhoto(path, base64);
                        photos.add(photo);
                        
                        System.out.println("✓ Loaded photo " + photoOrder + " for crop " + cropId + 
                                         " (Base64: " + (base64 != null && !base64.isEmpty() ? "yes" : "no") + 
                                         ", Path: " + (path != null ? path : "none") + ")");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // Now update UI on JavaFX thread with pre-loaded data
                Platform.runLater(() -> {
                    try {
                        cropPhotos = photos;
                        
                        if (!cropPhotos.isEmpty()) {
                            loadPhoto(0);
                            loadThumbnails();
                            updateNavigationButtons();
                        } else {
                            System.out.println("⚠️ No photos found for crop " + cropId);
                            if (btnPrevPhoto != null) btnPrevPhoto.setVisible(false);
                            if (btnNextPhoto != null) btnNextPhoto.setVisible(false);
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
        if (cropPhotos != null && index >= 0 && index < cropPhotos.size()) {
            currentPhotoIndex = index;
            CropPhoto photo = cropPhotos.get(index);
            Image image = photo.toImage();
            if (image != null) {
                imgMainPhoto.setImage(image);
            }
            updateNavigationButtons();
        }
    }

    @FXML
    private void onPreviousPhoto() {
        if (currentPhotoIndex > 0) {
            loadPhoto(currentPhotoIndex - 1);
            loadThumbnails();
        }
    }

    @FXML
    private void onNextPhoto() {
        if (currentPhotoIndex < cropPhotos.size() - 1) {
            loadPhoto(currentPhotoIndex + 1);
            loadThumbnails();
        }
    }

    private void updateNavigationButtons() {
        if (btnPrevPhoto != null) {
            btnPrevPhoto.setDisable(currentPhotoIndex == 0);
            btnPrevPhoto.setVisible(cropPhotos.size() > 1);
        }
        if (btnNextPhoto != null) {
            btnNextPhoto.setDisable(currentPhotoIndex >= cropPhotos.size() - 1);
            btnNextPhoto.setVisible(cropPhotos.size() > 1);
        }
    }

    private void loadThumbnails() {
        hboxThumbnails.getChildren().clear();
        for (int i = 0; i < cropPhotos.size(); i++) {
            final int photoIndex = i;
            CropPhoto photo = cropPhotos.get(i);
            Image image = photo.toImage();
            if (image != null) {
                ImageView thumbnail = new ImageView(image);
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
            if (App.getCurrentUser() != null && farmerId == App.getCurrentUser().getId()) {
                showInfo("Not Allowed", "You cannot chat with yourself.");
                return;
            }

            if (farmerId <= 0) {
                showError("ত্রুটি", "কৃষকের তথ্য পাওয়া যায়নি।");
                return;
            }

            App.setPreviousScene("crop-detail-view.fxml");
            App.showView("chat-conversation-view.fxml", controller -> {
                if (controller instanceof ChatConversationController) {
                    ChatConversationController chatController = (ChatConversationController) controller;
                    // Get or create conversation with farmer
                    chatController.loadConversation(0, farmerId, lblFarmerName.getText(), (cropId > 0 ? cropId : null));
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
        if (cropIdNumber <= 0) {
            showInfo("কোড নেই", "এই ফসলের ID পাওয়া যায়নি।");
            return;
        }
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(String.valueOf(cropIdNumber)); // Copy only the numeric ID
            clipboard.setContent(content);
            showInfo("কপি সফল", "ফসল ID কপি হয়েছে: " + cropIdNumber);
        } catch (Exception e) {
            showError("ত্রুটি", "ID কপি করতে ব্যর্থ হয়েছে।");
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
        // Navigate back to previous scene
        String previousScene = App.getPreviousScene();
        if (previousScene != null && !previousScene.isEmpty()) {
            // Only clear order context if NOT going back to order detail view
            if (!"order-detail-view.fxml".equals(previousScene)) {
                App.setCurrentOrderId(-1);
            }
            
            String title = getSceneTitle(previousScene);
            App.loadScene(previousScene, title);
        } else {
            // Clear order context when going to default fallback
            App.setCurrentOrderId(-1);
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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
