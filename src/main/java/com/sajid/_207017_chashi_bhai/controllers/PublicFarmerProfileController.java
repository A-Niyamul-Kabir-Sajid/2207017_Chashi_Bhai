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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

/**
 * PublicFarmerProfileController - Shows farmer profile to buyers with products and sales history
 */
public class PublicFarmerProfileController {

    @FXML private ImageView imgProfilePhoto;
    @FXML private Label lblFarmerName;
    @FXML private Label lblVerifiedBadge;
    @FXML private Label lblVerifiedText;
    @FXML private Label lblUserId;
    @FXML private Label lblPhone;
    @FXML private Label lblDistrict;
    @FXML private Label lblYearsFarming;
    @FXML private Label lblTotalProducts;
    @FXML private Label lblTotalSales;
    @FXML private Label lblRating;
    @FXML private GridPane gridProducts;
    @FXML private Label lblNoProducts;
    @FXML private TableView<?> tblSalesHistory;
    @FXML private VBox vboxReviews;
    @FXML private Label lblNoReviews;
    @FXML private HBox hboxContactActions;
    @FXML private Button btnChat;
    @FXML private Button btnWhatsApp;
    @FXML private GridPane gridFarmPhotos;
    @FXML private Label lblNoFarmPhotos;

    private User currentUser;
    private int farmerId;
    private String farmerPhone;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        farmerId = App.getCurrentViewedUserId();
        
        if (currentUser == null) {
            showError("অ্যাক্সেস অস্বীকার", "দয়া করে লগইন করুন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        if (farmerId <= 0) {
            showError("ত্রুটি", "কৃষকের প্রোফাইল খুঁজে পাওয়া যায়নি।");
            App.loadScene("crop-feed-view.fxml", "ফসলের তালিকা");
            return;
        }

        // Hide contact buttons if user is viewing their own public profile.
        if (currentUser != null && farmerId == currentUser.getId()) {
            if (hboxContactActions != null) {
                hboxContactActions.setVisible(false);
                hboxContactActions.setManaged(false);
            }
            if (btnChat != null) {
                btnChat.setVisible(false);
                btnChat.setManaged(false);
            }
            if (btnWhatsApp != null) {
                btnWhatsApp.setVisible(false);
                btnWhatsApp.setManaged(false);
            }
        }

        loadFarmerProfile();
        loadFarmerProducts();
        loadSalesHistory();
        loadReviews();
        loadFarmPhotos();
    }

    private void loadFarmerProfile() {
        String sql = "SELECT u.*, " +
                    "COALESCE(CAST((julianday('now') - julianday(u.created_at)) / 365 AS INTEGER), 0) as years_farming, " +
                    "(SELECT COUNT(*) FROM crops WHERE farmer_id = u.id AND status = 'active') as total_products " +
                    "FROM users u WHERE u.id = ?";

        DatabaseService.executeQueryAsync(sql, new Object[]{farmerId},
            rs -> {
                // CRITICAL: Read ResultSet data BEFORE Platform.runLater to avoid closed ResultSet
                try {
                    if (rs.next()) {
                        String name = rs.getString("name");
                        String phone = rs.getString("phone");
                        String district = rs.getString("district");
                        boolean isVerified = rs.getBoolean("is_verified");
                        int yearsFarming = rs.getInt("years_farming");
                        int totalProducts = rs.getInt("total_products");
                        int totalAcceptedOrders = rs.getInt("total_accepted_orders");
                        double rating = rs.getDouble("rating");
                        String photoPath = rs.getString("profile_photo");

                        // Now update UI on JavaFX thread with pre-loaded data
                        Platform.runLater(() -> {
                            try {
                                lblFarmerName.setText(name);
                                lblUserId.setText("ID: " + farmerId);
                                lblPhone.setText(phone != null ? phone : "N/A");
                                farmerPhone = phone;
                                lblDistrict.setText(district != null ? district : "N/A");
                                lblYearsFarming.setText(String.valueOf(yearsFarming));
                                lblTotalProducts.setText(String.valueOf(totalProducts));
                                lblTotalSales.setText(String.valueOf(totalAcceptedOrders));
                                lblRating.setText(String.format("%.1f", rating));

                                if (isVerified) {
                                    lblVerifiedBadge.setVisible(true);
                                    lblVerifiedText.setVisible(true);
                                }

                                if (photoPath != null && !photoPath.isEmpty()) {
                                    File photoFile = new File(photoPath);
                                    if (photoFile.exists()) {
                                        imgProfilePhoto.setImage(new Image(photoFile.toURI().toString()));
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                showError("ত্রুটি", "প্রোফাইল প্রদর্শন করতে ব্যর্থ হয়েছে।");
                            }
                        });
                    } else {
                        Platform.runLater(() -> showError("ত্রুটি", "প্রোফাইল ডেটা পাওয়া যায়নি।"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> showError("ত্রুটি", "প্রোফাইল লোড করতে ব্যর্থ হয়েছে।"));
                }
            },
            error -> {
                Platform.runLater(() -> showError("ডাটাবেস ত্রুটি", "প্রোফাইল ডেটা লোড করতে সমস্যা হয়েছে।"));
                error.printStackTrace();
            }
        );
    }

    private void loadFarmerProducts() {
        String sql = "SELECT c.id, c.name, c.product_code, c.price_per_kg, c.available_quantity_kg, c.district " +
                    "FROM crops c WHERE c.farmer_id = ? AND c.status = 'active' ORDER BY c.created_at DESC LIMIT 6";

        DatabaseService.executeQueryAsync(sql, new Object[]{farmerId},
            rs -> {
                // CRITICAL: Read ResultSet data BEFORE Platform.runLater to avoid closed ResultSet
                java.util.List<java.util.Map<String, Object>> products = new java.util.ArrayList<>();
                try {
                    while (rs.next()) {
                        java.util.Map<String, Object> product = new java.util.HashMap<>();
                        product.put("id", rs.getInt("id"));
                        product.put("name", rs.getString("name"));
                        product.put("productCode", rs.getString("product_code"));
                        product.put("price", rs.getDouble("price_per_kg"));
                        product.put("quantity", rs.getDouble("available_quantity_kg"));
                        product.put("district", rs.getString("district"));
                        products.add(product);
                        if (products.size() >= 6) break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // Now update UI on JavaFX thread with pre-loaded data
                Platform.runLater(() -> {
                    try {
                        gridProducts.getChildren().clear();
                        int index = 0;
                        boolean hasProducts = !products.isEmpty();

                        for (java.util.Map<String, Object> product : products) {
                            VBox productCard = createProductCard(
                                (int) product.get("id"),
                                (String) product.get("name"),
                                (String) product.get("productCode"),
                                (double) product.get("price"),
                                (double) product.get("quantity"),
                                (String) product.get("district")
                            );
                            gridProducts.add(productCard, index % 3, index / 3);
                            index++;
                        }

                        lblNoProducts.setVisible(!hasProducts);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> error.printStackTrace()
        );
    }

    private VBox createProductCard(int cropId, String name, String productCode, double price, double quantity, String district) {
        VBox card = new VBox(8);
        card.getStyleClass().add("crop-card");
        card.setPadding(new Insets(12));
        card.setOnMouseClicked(e -> {
            App.setCurrentCropId(cropId);
            App.setPreviousScene("public-farmer-profile-view.fxml");
            App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
        });
        card.setStyle("-fx-cursor: hand;");

        Label lblName = new Label(name);
        lblName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        Label lblCode = new Label("কোড: " + productCode);
        lblCode.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
        
        Label lblPrice = new Label(String.format("৳%.2f/কেজি", price));
        lblPrice.setStyle("-fx-font-size: 13px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        
        Label lblQty = new Label(String.format("পরিমাণ: %.1f কেজি", quantity));
        lblQty.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        
        Label lblDist = new Label("📍 " + district);
        lblDist.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        card.getChildren().addAll(lblName, lblCode, lblPrice, lblQty, lblDist);
        return card;
    }

    private void loadSalesHistory() {
        // TODO: Implement sales history table
        // For now, just hide if empty
    }

    private void loadReviews() {
        String sql = "SELECT r.rating, r.comment, r.created_at, u.name as reviewer_name " +
                    "FROM reviews r JOIN users u ON r.reviewer_id = u.id " +
                    "WHERE r.reviewee_id = ? ORDER BY r.created_at DESC LIMIT 10";

        DatabaseService.executeQueryAsync(sql, new Object[]{farmerId},
            rs -> {
                try {
                    java.util.List<java.util.Map<String, Object>> reviews = new java.util.ArrayList<>();
                    while (rs.next()) {
                        java.util.Map<String, Object> row = new java.util.HashMap<>();
                        row.put("rating", rs.getInt("rating"));
                        row.put("comment", rs.getString("comment"));
                        row.put("reviewerName", rs.getString("reviewer_name"));
                        row.put("createdAt", rs.getString("created_at"));
                        reviews.add(row);
                    }

                    Platform.runLater(() -> {
                        try {
                            vboxReviews.getChildren().clear();
                            boolean hasReviews = !reviews.isEmpty();
                            for (java.util.Map<String, Object> r : reviews) {
                                VBox reviewCard = createReviewCard(
                                    (int) r.get("rating"),
                                    (String) r.get("comment"),
                                    (String) r.get("reviewerName"),
                                    (String) r.get("createdAt")
                                );
                                vboxReviews.getChildren().add(reviewCard);
                            }
                            lblNoReviews.setVisible(!hasReviews);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
    }

    private VBox createReviewCard(int rating, String comment, String reviewerName, String date) {
        VBox card = new VBox(6);
        card.getStyleClass().add("review-card");
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 8;");

        HBox header = new HBox(10);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        Label lblName = new Label(reviewerName);
        lblName.setStyle("-fx-font-weight: bold;");
        
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < rating; i++) stars.append("⭐");
        Label lblStars = new Label(stars.toString());
        
        Label lblDate = new Label(date != null ? date.substring(0, 10) : "");
        lblDate.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        
        header.getChildren().addAll(lblName, lblStars, lblDate);

        Label lblComment = new Label(comment != null ? comment : "");
        lblComment.setWrapText(true);
        lblComment.setStyle("-fx-font-size: 13px;");

        card.getChildren().addAll(header, lblComment);
        return card;
    }

    @FXML
    private void onChat() {
        try {
            if (currentUser != null && farmerId == currentUser.getId()) {
                showInfo("Not Allowed", "You cannot chat with yourself.");
                return;
            }

            App.setPreviousScene("public-farmer-profile-view.fxml");
            App.showView("chat-conversation-view.fxml", controller -> {
                if (controller instanceof ChatConversationController) {
                    ChatConversationController chatController = (ChatConversationController) controller;
                    chatController.loadConversation(0, farmerId, lblFarmerName.getText(), null);
                }
            });
        } catch (Exception e) {
            showError("ত্রুটি", "চ্যাট খুলতে ব্যর্থ হয়েছে।");
            e.printStackTrace();
        }
    }

    @FXML
    private void onWhatsApp() {
        try {
            String cleanPhone = farmerPhone.replaceAll("[^0-9]", "");
            if (!cleanPhone.startsWith("880")) {
                if (cleanPhone.startsWith("0")) {
                    cleanPhone = "880" + cleanPhone.substring(1);
                } else {
                    cleanPhone = "880" + cleanPhone;
                }
            }
            Desktop.getDesktop().browse(new URI("https://wa.me/" + cleanPhone));
        } catch (Exception e) {
            showInfo("WhatsApp", "WhatsApp: " + farmerPhone);
            e.printStackTrace();
        }
    }

    private void loadFarmPhotos() {
        String sql = "SELECT id, photo_path, image_base64 FROM farm_photos WHERE farmer_id = ? ORDER BY id LIMIT 12";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{farmerId},
            rs -> {
                java.util.List<java.util.Map<String, Object>> photos = new java.util.ArrayList<>();
                try {
                    while (rs.next()) {
                        java.util.Map<String, Object> photo = new java.util.HashMap<>();
                        photo.put("id", rs.getInt("id"));
                        photo.put("photoPath", rs.getString("photo_path"));
                        photo.put("imageBase64", rs.getString("image_base64"));
                        photos.add(photo);
                        if (photos.size() >= 12) break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                Platform.runLater(() -> {
                    try {
                        if (gridFarmPhotos != null) {
                            gridFarmPhotos.getChildren().clear();
                            boolean hasPhotos = !photos.isEmpty();
                            
                            for (int i = 0; i < photos.size(); i++) {
                                java.util.Map<String, Object> photo = photos.get(i);
                                ImageView imageView = new ImageView();
                                imageView.setFitWidth(200);
                                imageView.setFitHeight(150);
                                imageView.setPreserveRatio(false);
                                imageView.setStyle("-fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.4), 6, 0, 0, 2);");
                                
                                // Try loading from Base64 first, then file path
                                String base64 = (String) photo.get("imageBase64");
                                String photoPath = (String) photo.get("photoPath");
                                
                                Image image = null;
                                if (base64 != null && !base64.isEmpty()) {
                                    image = com.sajid._207017_chashi_bhai.utils.ImageBase64Util.base64ToImage(base64);
                                }
                                if (image == null && photoPath != null && !photoPath.isEmpty()) {
                                    File photoFile = new File(photoPath);
                                    if (photoFile.exists()) {
                                        image = new Image(photoFile.toURI().toString());
                                    }
                                }
                                
                                if (image != null) {
                                    imageView.setImage(image);
                                    gridFarmPhotos.add(imageView, i % 4, i / 4);
                                }
                            }
                            
                            if (lblNoFarmPhotos != null) {
                                lblNoFarmPhotos.setVisible(!hasPhotos);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> {
                Platform.runLater(() -> {
                    if (lblNoFarmPhotos != null) {
                        lblNoFarmPhotos.setVisible(true);
                    }
                });
                error.printStackTrace();
            }
        );
    }

    @FXML
    private void onBack() {
        App.loadScene("crop-feed-view.fxml", "সকল ফসল");
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
