package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.io.File;

/**
 * FarmerProfileController - Display farmer profile with stats and farm photos
 */
public class FarmerProfileController {

    @FXML private ImageView imgProfilePhoto;
    @FXML private Label lblFarmerName;
    @FXML private Label lblVerifiedBadge;
    @FXML private Label lblPhone;
    @FXML private Label lblDistrict;
    @FXML private Label lblUpazila;
    @FXML private Label lblFarmType;
    @FXML private Label lblYearsFarming;
    @FXML private Label lblTotalSales;
    @FXML private Label lblRating;
    @FXML private HBox hboxFarmPhotos;
    @FXML private Button btnEditProfile;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        
        if (currentUser == null || !"farmer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র কৃষকরা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        loadProfileData();
    }

    /**
     * Load farmer profile data from database
     */
    private void loadProfileData() {
        DatabaseService.executeQueryAsync(
            "SELECT u.*, " +
            "COALESCE(CAST((julianday('now') - julianday(u.created_at)) / 365 AS INTEGER), 0) as years_farming, " +
            "(SELECT COUNT(*) FROM orders o JOIN crops c ON o.crop_id = c.id WHERE c.farmer_id = u.id AND o.status = 'delivered') as total_sales, " +
            "(SELECT COALESCE(AVG(r.rating), 0.0) FROM ratings r WHERE r.farmer_id = u.id) as avg_rating " +
            "FROM users u WHERE u.id = ?",
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            String name = resultSet.getString("name");
                            String phone = resultSet.getString("phone");
                            String district = resultSet.getString("district");
                            String upazila = resultSet.getString("upazila");
                            String farmType = resultSet.getString("farm_type");
                            boolean isVerified = resultSet.getBoolean("is_verified");
                            int yearsFarming = resultSet.getInt("years_farming");
                            int totalSales = resultSet.getInt("total_sales");
                            double avgRating = resultSet.getDouble("avg_rating");
                            String photoPath = resultSet.getString("profile_photo");

                            // Set profile data
                            lblFarmerName.setText(name);
                            lblPhone.setText(phone != null ? phone : "N/A");
                            lblDistrict.setText(district != null ? district : "N/A");
                            lblUpazila.setText(upazila != null ? upazila : "N/A");
                            lblFarmType.setText(farmType != null ? farmType : "N/A");
                            lblYearsFarming.setText(String.valueOf(yearsFarming));
                            lblTotalSales.setText(String.valueOf(totalSales));
                            lblRating.setText(String.format("%.1f ★", avgRating));

                            // Verified badge
                            if (isVerified) {
                                lblVerifiedBadge.setVisible(true);
                                lblVerifiedBadge.setText("✓ যাচাইকৃত কৃষক");
                            } else {
                                lblVerifiedBadge.setVisible(false);
                            }

                            // Load profile photo
                            if (photoPath != null && !photoPath.isEmpty()) {
                                File photoFile = new File(photoPath);
                                if (photoFile.exists()) {
                                    imgProfilePhoto.setImage(new Image(photoFile.toURI().toString()));
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("ত্রুটি", "প্রোফাইল লোড করতে ব্যর্থ হয়েছে।");
                    }
                });
            },
            error -> {
                Platform.runLater(() -> {
                    showError("ডাটাবেস ত্রুটি", "প্রোফাইল ডেটা লোড করতে সমস্যা হয়েছে।");
                    error.printStackTrace();
                });
            }
        );

        // Load farm photos
        loadFarmPhotos();
    }

    /**
     * Load farm photos into horizontal gallery
     */
    private void loadFarmPhotos() {
        DatabaseService.executeQueryAsync(
            "SELECT photo_path FROM farm_photos WHERE farmer_id = ? ORDER BY id LIMIT 10",
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        hboxFarmPhotos.getChildren().clear();
                        while (resultSet.next()) {
                            String photoPath = resultSet.getString("photo_path");
                            File photoFile = new File(photoPath);
                            if (photoFile.exists()) {
                                ImageView imageView = new ImageView(new Image(photoFile.toURI().toString()));
                                imageView.setFitWidth(150);
                                imageView.setFitHeight(150);
                                imageView.setPreserveRatio(true);
                                imageView.getStyleClass().add("farm-photo");
                                hboxFarmPhotos.getChildren().add(imageView);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> {
                error.printStackTrace();
            }
        );
    }

    @FXML
    private void onEditProfile() {
        // Navigate to edit profile view
        App.loadScene("signup-view.fxml", "প্রোফাইল সম্পাদনা"); // Can pass edit mode flag
    }

    @FXML
    private void onBack() {
        App.loadScene("farmer-dashboard-view.fxml", "Dashboard");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
