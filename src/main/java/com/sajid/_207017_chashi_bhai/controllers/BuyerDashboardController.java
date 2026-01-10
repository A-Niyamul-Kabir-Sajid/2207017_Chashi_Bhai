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

/**
 * BuyerDashboardController - Main dashboard for buyers
 */
public class BuyerDashboardController {

    @FXML private Label lblWelcome;
    @FXML private TextField txtSearch;
    @FXML private Button btnSearch;
    @FXML private Button btnBrowseAll;
    @FXML private Button btnMyOrders;
    @FXML private Button btnHistory;
    @FXML private Button btnProfile;
    @FXML private HBox hboxPriceTicker;
    @FXML private HBox hboxFeaturedCrops;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        
        if (currentUser == null || !"buyer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র ক্রেতারা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        lblWelcome.setText("স্বাগতম, " + currentUser.getName() + "!");
        
        loadPriceTicker();
        loadFeaturedCrops();
    }

    private void loadPriceTicker() {
        DatabaseService.executeQueryAsync(
            "SELECT crop_name, price FROM market_prices ORDER BY updated_at DESC LIMIT 10",
            new Object[]{},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        hboxPriceTicker.getChildren().clear();
                        while (resultSet.next()) {
                            String cropName = resultSet.getString("crop_name");
                            double price = resultSet.getDouble("price");
                            
                            VBox priceItem = new VBox(5);
                            priceItem.getStyleClass().add("price-item");
                            priceItem.setPadding(new Insets(10));
                            
                            Label lblCrop = new Label(cropName);
                            lblCrop.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
                            
                            Label lblPrice = new Label(String.format("৳%.2f/কেজি", price));
                            lblPrice.setStyle("-fx-font-size: 12px; -fx-text-fill: #4CAF50;");
                            
                            priceItem.getChildren().addAll(lblCrop, lblPrice);
                            hboxPriceTicker.getChildren().add(priceItem);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> error.printStackTrace()
        );
    }

    private void loadFeaturedCrops() {
        if (progressIndicator != null) {
            progressIndicator.setVisible(true);
        }

        DatabaseService.executeQueryAsync(
            "SELECT c.*, u.name as farmer_name, u.phone as farmer_phone, u.is_verified, " +
            "(SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as photo " +
            "FROM crops c " +
            "JOIN users u ON c.farmer_id = u.id " +
            "WHERE c.status = 'active' " +
            "ORDER BY c.created_at DESC LIMIT 4",
            new Object[]{},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        hboxFeaturedCrops.getChildren().clear();
                        while (resultSet.next()) {
                            VBox cropCard = createFeaturedCropCard(resultSet);
                            hboxFeaturedCrops.getChildren().add(cropCard);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
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
                    error.printStackTrace();
                });
            }
        );
    }

    private VBox createFeaturedCropCard(ResultSet rs) throws Exception {
        int cropId = rs.getInt("id");
        String name = rs.getString("name");
        String farmerName = rs.getString("farmer_name");
        boolean isVerified = rs.getBoolean("is_verified");
        double price = rs.getDouble("price_per_kg");
        String unit = "কেজি";
        String district = rs.getString("district");
        String photoPath = rs.getString("photo");

        VBox card = new VBox(10);
        card.getStyleClass().add("featured-crop-card");
        card.setPrefWidth(200);
        card.setPadding(new Insets(10));
        card.setOnMouseClicked(e -> onViewCrop(cropId));

        // Image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(180);
        imageView.setFitHeight(150);
        imageView.setPreserveRatio(true);
        if (photoPath != null && !photoPath.isEmpty()) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                imageView.setImage(new Image(photoFile.toURI().toString()));
            }
        }

        Label lblName = new Label(name);
        lblName.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        HBox farmerBox = new HBox(5);
        Label lblFarmer = new Label(farmerName);
        lblFarmer.setStyle("-fx-font-size: 12px;");
        if (isVerified) {
            Label lblVerified = new Label("✓");
            lblVerified.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            farmerBox.getChildren().addAll(lblFarmer, lblVerified);
        } else {
            farmerBox.getChildren().add(lblFarmer);
        }
        
        Label lblPrice = new Label(String.format("৳%.2f/%s", price, unit));
        lblPrice.setStyle("-fx-font-size: 14px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        
        Label lblLocation = new Label("📍 " + district);
        lblLocation.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        card.getChildren().addAll(imageView, lblName, farmerBox, lblPrice, lblLocation);
        return card;
    }

    @FXML
    private void onSearch() {
        System.out.println("[DEBUG] Search button clicked!");
        String searchQuery = txtSearch.getText().trim();
        if (!searchQuery.isEmpty()) {
            App.setSearchQuery(searchQuery);
            App.loadScene("crop-feed-view.fxml", "ফসল খুঁজুন");
        }
    }

    @FXML
    private void onBrowseAll() {
        System.out.println("[DEBUG] Browse All button clicked!");
        App.loadScene("crop-feed-view.fxml", "সকল ফসল");
    }

    @FXML
    private void onMyOrders() {
        System.out.println("[DEBUG] My Orders button clicked!");
        App.loadScene("buyer-orders-view.fxml", "আমার অর্ডারসমূহ");
    }

    @FXML
    private void onHistory() {
        System.out.println("[DEBUG] History button clicked!");
        try {
            App.loadScene("buyer-history-view.fxml", "ক্রয় ইতিহাস");
            System.out.println("[DEBUG] History scene load initiated successfully");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load buyer history view:");
            e.printStackTrace();
            showError("ত্রুটি", "ইতিহাস পেজ লোড করতে ব্যর্থ: " + e.getMessage());
        }
    }

    @FXML
    private void onProfile() {
        System.out.println("[DEBUG] Profile button clicked!");
        App.loadScene("buyer-profile-view.fxml", "আমার প্রোফাইল");
    }

    private void onViewCrop(int cropId) {
        App.setCurrentCropId(cropId);
        App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
    }

    @FXML
    private void onCropClick() {
        // Navigate to crop feed to browse all crops
        App.loadScene("crop-feed-view.fxml", "সকল ফসল");
    }

    @FXML
    private void onBack() {
        App.loadScene("welcome-view.fxml", "Chashi Bhai");
    }

    @FXML
    private void onSignOut() {
        App.logout();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
