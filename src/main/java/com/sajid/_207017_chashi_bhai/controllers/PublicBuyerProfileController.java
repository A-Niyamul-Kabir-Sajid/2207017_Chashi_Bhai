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

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

/**
 * PublicBuyerProfileController - Shows buyer profile to farmers/other users
 * Displays purchase history, favorite farmers, and reviews given
 */
public class PublicBuyerProfileController {

    @FXML private ImageView imgProfilePhoto;
    @FXML private Label lblBuyerName;
    @FXML private Label lblVerifiedBadge;
    @FXML private Label lblVerifiedText;
    @FXML private Label lblUserId;
    @FXML private Label lblPhone;
    @FXML private Label lblDistrict;
    @FXML private Label lblMemberSince;
    @FXML private Label lblTotalPurchases;
    @FXML private Label lblTotalSpent;
    @FXML private Label lblRating;
    @FXML private VBox vboxPurchaseHistory;
    @FXML private Label lblNoPurchases;
    @FXML private HBox hboxFavoriteFarmers;
    @FXML private Label lblNoFarmers;
    @FXML private VBox vboxReviews;
    @FXML private Label lblNoReviews;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private VBox profileContent;
    @FXML private HBox hboxContactActions;
    @FXML private Button btnChat;
    @FXML private Button btnWhatsApp;

    private User currentUser;
    private int buyerId;
    private String buyerPhone;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        buyerId = App.getCurrentViewedUserId();
        
        if (currentUser == null) {
            showError("অ্যাক্সেস অস্বীকার", "দয়া করে লগইন করুন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        if (buyerId <= 0) {
            showError("ত্রুটি", "ক্রেতার প্রোফাইল খুঁজে পাওয়া যায়নি।");
            App.loadScene("crop-feed-view.fxml", "ফসলের তালিকা");
            return;
        }

        // Hide contact buttons if user is viewing their own public profile.
        if (currentUser != null && buyerId == currentUser.getId()) {
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

        loadBuyerProfile();
        loadPurchaseHistory();
        loadFavoriteFarmers();
        loadReviews();
    }

    @FXML
    private void onRefresh() {
        loadBuyerProfile();
        loadPurchaseHistory();
        loadFavoriteFarmers();
        loadReviews();
    }

    private void loadBuyerProfile() {
        if (progressIndicator != null) {
            progressIndicator.setVisible(true);
        }

        String sql = "SELECT u.*, " +
                    "u.total_buyer_orders, " +
                    "u.total_expense, " +
                    "(SELECT COALESCE(AVG(r.rating), 0.0) FROM reviews r WHERE r.reviewer_id = u.id) as avg_rating " +
                    "FROM users u WHERE u.id = ?";

        DatabaseService.executeQueryAsync(sql, new Object[]{buyerId},
            rs -> {
                try {
                    if (rs.next()) {
                        // Read all data BEFORE Platform.runLater
                        String name = rs.getString("name");
                        String phone = rs.getString("phone");
                        String district = rs.getString("district");
                        boolean isVerified = rs.getBoolean("is_verified");
                        String createdAt = rs.getString("created_at");
                        int totalPurchases = rs.getInt("total_buyer_orders");
                        double totalSpent = rs.getDouble("total_expense");
                        double avgRating = rs.getDouble("avg_rating");
                        String photoPath = rs.getString("profile_photo");

                        Platform.runLater(() -> {
                            lblBuyerName.setText(name);
                            lblUserId.setText("ID: " + buyerId);
                            lblPhone.setText(phone != null ? phone : "N/A");
                            buyerPhone = phone;
                            lblDistrict.setText(district != null ? district : "N/A");
                            lblTotalPurchases.setText(String.valueOf(totalPurchases));
                            lblTotalSpent.setText(String.format("৳%.0f", totalSpent));
                            lblRating.setText(avgRating > 0 ? String.format("%.1f", avgRating) : "N/A");

                            // Member since
                            if (createdAt != null && createdAt.length() >= 4) {
                                lblMemberSince.setText(createdAt.substring(0, 4));
                            }

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
                            
                            if (progressIndicator != null) {
                                progressIndicator.setVisible(false);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        showError("ত্রুটি", "প্রোফাইল লোড করতে ব্যর্থ হয়েছে।");
                        if (progressIndicator != null) {
                            progressIndicator.setVisible(false);
                        }
                    });
                }
            },
            error -> {
                Platform.runLater(() -> {
                    if (progressIndicator != null) {
                        progressIndicator.setVisible(false);
                    }
                    showError("ডাটাবেস ত্রুটি", "প্রোফাইল ডেটা লোড করতে সমস্যা হয়েছে।");
                });
                error.printStackTrace();
            }
        );
    }

    private void loadPurchaseHistory() {
        String sql = "SELECT o.id, o.crop_id, o.quantity_kg, o.price_per_kg, o.total_amount, " +
                    "COALESCE(o.completed_at, o.delivered_at, o.updated_at, o.created_at) as effective_date, " +
                    "c.name as crop_name, " +
                    "u.name as farmer_name, u.is_verified " +
                    "FROM orders o " +
                    "JOIN crops c ON o.crop_id = c.id " +
                    "JOIN users u ON o.farmer_id = u.id " +
                    "WHERE o.buyer_id = ? " +
                    "ORDER BY effective_date DESC LIMIT 5";

        DatabaseService.executeQueryAsync(sql, new Object[]{buyerId},
            rs -> {
                try {
                    java.util.List<PurchaseRow> rows = new java.util.ArrayList<>();
                    while (rs.next()) {
                        int cropId = rs.getInt("crop_id");
                        String cropName = rs.getString("crop_name");
                        String farmerName = rs.getString("farmer_name");
                        boolean isVerified = rs.getBoolean("is_verified");
                        double quantity = rs.getDouble("quantity_kg");
                        double price = rs.getDouble("price_per_kg");
                        String date = rs.getString("effective_date");
                        rows.add(new PurchaseRow(cropId, cropName, farmerName, isVerified, quantity, price, date));
                    }

                    Platform.runLater(() -> {
                        try {
                            vboxPurchaseHistory.getChildren().clear();
                            boolean hasPurchases = !rows.isEmpty();
                            for (PurchaseRow row : rows) {
                                HBox purchaseCard = createPurchaseCard(
                                    row.cropId,
                                    row.cropName,
                                    row.farmerName,
                                    row.isVerified,
                                    row.quantityKg,
                                    "কেজি",
                                    row.pricePerKg,
                                    row.date
                                );
                                vboxPurchaseHistory.getChildren().add(purchaseCard);
                            }
                            lblNoPurchases.setVisible(!hasPurchases);
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

    private static final class PurchaseRow {
        final int cropId;
        final String cropName;
        final String farmerName;
        final boolean isVerified;
        final double quantityKg;
        final double pricePerKg;
        final String date;

        PurchaseRow(int cropId, String cropName, String farmerName, boolean isVerified, double quantityKg, double pricePerKg, String date) {
            this.cropId = cropId;
            this.cropName = cropName;
            this.farmerName = farmerName;
            this.isVerified = isVerified;
            this.quantityKg = quantityKg;
            this.pricePerKg = pricePerKg;
            this.date = date;
        }
    }

    private HBox createPurchaseCard(int cropId, String cropName, String farmerName, boolean isVerified,
                                    double quantity, String unit, double price, String date) {
        HBox card = new HBox(20);
        card.getStyleClass().add("history-card");
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #f8f8f8; -fx-background-radius: 8;");
        card.setStyle(card.getStyle() + " -fx-cursor: hand;");
        card.setOnMouseClicked(e -> {
            if (cropId > 0) {
                App.setCurrentCropId(cropId);
                App.setPreviousScene("public-buyer-profile-view.fxml");
                App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
            }
        });

        // Date
        VBox dateBox = new VBox(3);
        dateBox.setPrefWidth(100);
        Label lblDate = new Label(date != null ? date.substring(0, 10) : "N/A");
        lblDate.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        dateBox.getChildren().add(lblDate);

        // Crop
        VBox cropBox = new VBox(3);
        cropBox.setPrefWidth(150);
        Label lblCrop = new Label(cropName);
        lblCrop.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label lblQty = new Label(String.format("%.1f %s", quantity, unit));
        lblQty.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        cropBox.getChildren().addAll(lblCrop, lblQty);

        // Farmer
        VBox farmerBox = new VBox(3);
        farmerBox.setPrefWidth(150);
        Label lblFarmerTitle = new Label("কৃষক:");
        lblFarmerTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
        HBox farmerNameBox = new HBox(5);
        Label lblFarmer = new Label(farmerName);
        lblFarmer.setStyle("-fx-font-size: 13px;");
        if (isVerified) {
            Label verified = new Label("✓");
            verified.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            farmerNameBox.getChildren().addAll(lblFarmer, verified);
        } else {
            farmerNameBox.getChildren().add(lblFarmer);
        }
        farmerBox.getChildren().addAll(lblFarmerTitle, farmerNameBox);

        // Total
        VBox totalBox = new VBox(3);
        totalBox.setPrefWidth(100);
        Label lblTotal = new Label(String.format("৳%.0f", quantity * price));
        lblTotal.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        totalBox.getChildren().add(lblTotal);

        card.getChildren().addAll(dateBox, cropBox, farmerBox, totalBox);
        return card;
    }

    private void loadFavoriteFarmers() {
        String sql = "SELECT u.id, u.name, u.is_verified, u.profile_photo, COUNT(*) as order_count " +
                    "FROM orders o " +
                    "JOIN crops c ON o.crop_id = c.id " +
                    "JOIN users u ON c.farmer_id = u.id " +
                    "WHERE o.buyer_id = ? AND o.status IN ('delivered','completed') " +
                    "GROUP BY c.farmer_id " +
                    "ORDER BY order_count DESC LIMIT 5";

        DatabaseService.executeQueryAsync(sql, new Object[]{buyerId},
            rs -> {
                try {
                    java.util.List<java.util.Map<String, Object>> farmers = new java.util.ArrayList<>();
                    while (rs.next()) {
                        java.util.Map<String, Object> row = new java.util.HashMap<>();
                        row.put("id", rs.getInt("id"));
                        row.put("name", rs.getString("name"));
                        row.put("isVerified", rs.getBoolean("is_verified"));
                        row.put("profilePhoto", rs.getString("profile_photo"));
                        row.put("orderCount", rs.getInt("order_count"));
                        farmers.add(row);
                    }

                    Platform.runLater(() -> {
                        try {
                            hboxFavoriteFarmers.getChildren().clear();
                            boolean hasFarmers = !farmers.isEmpty();
                            for (java.util.Map<String, Object> f : farmers) {
                                VBox farmerCard = createFarmerCard(
                                    (int) f.get("id"),
                                    (String) f.get("name"),
                                    (boolean) f.get("isVerified"),
                                    (String) f.get("profilePhoto"),
                                    (int) f.get("orderCount")
                                );
                                hboxFavoriteFarmers.getChildren().add(farmerCard);
                            }
                            lblNoFarmers.setVisible(!hasFarmers);
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

    private VBox createFarmerCard(int farmerId, String name, boolean isVerified, String photoPath, int orderCount) {
        VBox card = new VBox(8);
        card.getStyleClass().add("farmer-card");
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: #f8f8f8; -fx-background-radius: 12; -fx-cursor: hand;");
        card.setPrefWidth(140);
        card.setOnMouseClicked(e -> {
            App.setCurrentViewedUserId(farmerId);
            App.loadScene("public-farmer-profile-view.fxml", "কৃষকের প্রোফাইল");
        });

        // Photo
        ImageView photo = new ImageView();
        photo.setFitWidth(60);
        photo.setFitHeight(60);
        photo.setPreserveRatio(true);
        if (photoPath != null && !photoPath.isEmpty()) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                photo.setImage(new Image(photoFile.toURI().toString()));
            }
        }

        // Name
        HBox nameBox = new HBox(5);
        Label lblName = new Label(name);
        lblName.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        lblName.setWrapText(true);
        if (isVerified) {
            Label verified = new Label("✓");
            verified.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            nameBox.getChildren().addAll(lblName, verified);
        } else {
            nameBox.getChildren().add(lblName);
        }

        // Order count
        Label lblOrders = new Label(orderCount + " অর্ডার");
        lblOrders.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        card.getChildren().addAll(photo, nameBox, lblOrders);
        return card;
    }

    private void loadReviews() {
        // Reviews given by this buyer to farmers
        String sql = "SELECT r.rating, r.comment, r.created_at, u.name as farmer_name " +
                    "FROM reviews r " +
                    "JOIN users u ON r.reviewee_id = u.id " +
                    "WHERE r.reviewer_id = ? " +
                    "ORDER BY r.created_at DESC LIMIT 10";

        DatabaseService.executeQueryAsync(sql, new Object[]{buyerId},
            rs -> {
                try {
                    java.util.List<java.util.Map<String, Object>> reviews = new java.util.ArrayList<>();
                    while (rs.next()) {
                        java.util.Map<String, Object> row = new java.util.HashMap<>();
                        row.put("farmerName", rs.getString("farmer_name"));
                        row.put("rating", rs.getInt("rating"));
                        row.put("comment", rs.getString("comment"));
                        row.put("createdAt", rs.getString("created_at"));
                        reviews.add(row);
                    }

                    Platform.runLater(() -> {
                        try {
                            vboxReviews.getChildren().clear();
                            boolean hasReviews = !reviews.isEmpty();
                            for (java.util.Map<String, Object> r : reviews) {
                                HBox reviewCard = createReviewCard(
                                    (String) r.get("farmerName"),
                                    (int) r.get("rating"),
                                    (String) r.get("comment"),
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

    private HBox createReviewCard(String farmerName, int rating, String comment, String date) {
        HBox card = new HBox(15);
        card.getStyleClass().add("review-card");
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #f8f8f8; -fx-background-radius: 8;");

        VBox content = new VBox(5);
        content.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(content, javafx.scene.layout.Priority.ALWAYS);

        // Header: farmer name + rating
        HBox header = new HBox(10);
        Label lblFarmer = new Label("কৃষক: " + farmerName);
        lblFarmer.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        
        // Stars
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < rating; i++) stars.append("★");
        for (int i = rating; i < 5; i++) stars.append("☆");
        Label lblStars = new Label(stars.toString());
        lblStars.setStyle("-fx-font-size: 14px; -fx-text-fill: #FFA726;");
        
        header.getChildren().addAll(lblFarmer, lblStars);

        // Comment
        Label lblComment = new Label(comment != null && !comment.isEmpty() ? comment : "(কোনো মন্তব্য নেই)");
        lblComment.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        lblComment.setWrapText(true);

        // Date
        Label lblDate = new Label(date != null ? date.substring(0, 10) : "");
        lblDate.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaa;");

        content.getChildren().addAll(header, lblComment, lblDate);
        card.getChildren().add(content);
        return card;
    }

    @FXML
    private void onChat() {
        if (buyerId > 0) {
            try {
                if (currentUser != null && buyerId == currentUser.getId()) {
                    showInfo("Not Allowed", "You cannot chat with yourself.");
                    return;
                }

                App.setPreviousScene("public-buyer-profile-view.fxml");
                App.showView("chat-conversation-view.fxml", controller -> {
                    if (controller instanceof ChatConversationController) {
                        ChatConversationController chatController = (ChatConversationController) controller;
                        chatController.loadConversation(0, buyerId, lblBuyerName.getText(), null);
                    }
                });
            } catch (Exception e) {
                showError("ত্রুটি", "চ্যাট খুলতে ব্যর্থ হয়েছে।");
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void onWhatsApp() {
        if (buyerPhone != null && !buyerPhone.isEmpty()) {
            try {
                String cleanPhone = buyerPhone.replaceAll("[^0-9]", "");
                if (cleanPhone.startsWith("0")) {
                    cleanPhone = "88" + cleanPhone;
                } else if (!cleanPhone.startsWith("88")) {
                    cleanPhone = "88" + cleanPhone;
                }
                String url = "https://wa.me/" + cleanPhone;
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                } else {
                    showInfo("WhatsApp", "WhatsApp নম্বর: " + buyerPhone);
                }
            } catch (Exception e) {
                e.printStackTrace();
                showInfo("WhatsApp", "WhatsApp নম্বর: " + buyerPhone);
            }
        } else {
            showError("ত্রুটি", "ফোন নম্বর পাওয়া যায়নি।");
        }
    }

    @FXML
    private void onBack() {
        App.loadScene("crop-feed-view.fxml", "ফসলের তালিকা");
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
