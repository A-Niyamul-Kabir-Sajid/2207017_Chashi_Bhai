package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.utils.DataSyncManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * FarmerProfileController - Display farmer profile with stats and farm photos
 * Features real-time sync with database polling
 */
public class FarmerProfileController {

    @FXML private ImageView imgProfilePhoto;
    @FXML private Label lblFarmerName;
    @FXML private Label lblVerifiedBadge;
    @FXML private Label lblUserId;
    @FXML private Label lblPhone;
    @FXML private Label lblDistrict;
    @FXML private Label lblYearsFarming;
    @FXML private Label lblTotalSales;
    @FXML private Label lblRating;
    @FXML private HBox hboxFarmPhotos;
    @FXML private Button btnEditProfile;
    @FXML private Button btnUploadFarmPhoto;
    @FXML private Label lblNoPhotos;
    @FXML private Label lblLastUpdated;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;
    private DataSyncManager syncManager;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        syncManager = DataSyncManager.getInstance();
        
        if (currentUser == null || !"farmer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র কৃষকরা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        loadProfileData();
        
        // Start real-time sync polling
        syncManager.startProfileSync(currentUser.getId(), this::loadProfileData);
    }

    /**
     * Refresh button handler
     */
    @FXML
    private void onRefresh() {
        loadProfileData();
        loadFarmPhotos();
    }

    /**
     * Load farmer profile data from database
     */
    private void loadProfileData() {
        if (progressIndicator != null) {
            progressIndicator.setVisible(true);
        }
        
        DatabaseService.executeQueryAsync(
            "SELECT u.*, " +
            "COALESCE(CAST((julianday('now') - julianday(u.created_at)) / 365 AS INTEGER), 0) as years_farming, " +
            "(SELECT COALESCE(SUM(o.quantity_kg * o.price_per_kg), 0) FROM orders o WHERE o.farmer_id = u.id AND o.status IN ('delivered', 'completed')) as total_sales, " +
            "(SELECT COALESCE(AVG(r.rating), 0.0) FROM reviews r WHERE r.reviewee_id = u.id) as avg_rating " +
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
                            double totalSales = resultSet.getDouble("total_sales");
                            double avgRating = resultSet.getDouble("avg_rating");
                            String photoPath = resultSet.getString("profile_photo");

                            // Set profile data
                            lblFarmerName.setText(name);
                            lblUserId.setText("ID: " + currentUser.getId());
                            lblPhone.setText(phone != null ? phone : "N/A");
                            lblDistrict.setText(district != null ? district : "N/A");
                            lblYearsFarming.setText(String.valueOf(yearsFarming));
                            lblTotalSales.setText(String.format("৳%.2f", totalSales));
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
     * Load farm photos into horizontal gallery with delete buttons
     */
    private void loadFarmPhotos() {
        DatabaseService.executeQueryAsync(
            "SELECT id, photo_path FROM farm_photos WHERE farmer_id = ? ORDER BY id LIMIT 10",
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        hboxFarmPhotos.getChildren().clear();
                        boolean hasPhotos = false;
                        
                        while (resultSet.next()) {
                            hasPhotos = true;
                            int photoId = resultSet.getInt("id");
                            String photoPath = resultSet.getString("photo_path");
                            File photoFile = new File(photoPath);
                            
                            if (photoFile.exists()) {
                                // Create a StackPane to overlay delete button on image
                                StackPane photoContainer = new StackPane();
                                photoContainer.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 8;");
                                
                                ImageView imageView = new ImageView(new Image(photoFile.toURI().toString()));
                                imageView.setFitWidth(180);
                                imageView.setFitHeight(150);
                                imageView.setPreserveRatio(true);
                                imageView.setStyle("-fx-cursor: hand;");
                                imageView.getStyleClass().add("farm-photo");
                                
                                // Delete button overlay
                                Button btnDelete = new Button("✕");
                                btnDelete.setStyle("-fx-background-color: rgba(220, 53, 69, 0.9); -fx-text-fill: white; " +
                                                  "-fx-font-size: 12px; -fx-padding: 2 6; -fx-background-radius: 50; " +
                                                  "-fx-cursor: hand;");
                                btnDelete.setOnAction(e -> deleteFarmPhoto(photoId, photoPath));
                                StackPane.setAlignment(btnDelete, Pos.TOP_RIGHT);
                                
                                photoContainer.getChildren().addAll(imageView, btnDelete);
                                hboxFarmPhotos.getChildren().add(photoContainer);
                            }
                        }
                        
                        // Show/hide no photos label
                        if (lblNoPhotos != null) {
                            lblNoPhotos.setVisible(!hasPhotos);
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
    
    /**
     * Upload a new farm photo
     */
    @FXML
    private void onUploadFarmPhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("খামারের ছবি নির্বাচন করুন");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        
        File file = fileChooser.showOpenDialog(hboxFarmPhotos.getScene().getWindow());
        if (file != null) {
            try {
                // Create directory for farm photos
                Path photosDir = Paths.get("data/farm_photos/" + currentUser.getId());
                Files.createDirectories(photosDir);
                
                // Copy file to app directory
                String fileName = "farm_" + System.currentTimeMillis() + getFileExtension(file);
                Path destination = photosDir.resolve(fileName);
                Files.copy(file.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
                
                // Save to database
                DatabaseService.executeUpdateAsync(
                    "INSERT INTO farm_photos (farmer_id, photo_path) VALUES (?, ?)",
                    new Object[]{currentUser.getId(), destination.toString()},
                    rows -> {
                        Platform.runLater(() -> {
                            if (rows > 0) {
                                showSuccess("সফল!", "খামারের ছবি আপলোড হয়েছে।");
                                loadFarmPhotos(); // Refresh the photos
                            }
                        });
                    },
                    error -> {
                        Platform.runLater(() -> showError("ত্রুটি", "ছবি সংরক্ষণে সমস্যা হয়েছে।"));
                        error.printStackTrace();
                    }
                );
            } catch (Exception e) {
                showError("ত্রুটি", "ছবি আপলোড করতে ব্যর্থ হয়েছে।");
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Delete a farm photo
     */
    private void deleteFarmPhoto(int photoId, String photoPath) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("আপনি কি এই ছবিটি মুছতে চান?");
        confirm.setContentText("এই কাজটি পূর্বাবস্থায় ফেরত নেওয়া যাবে না।");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                // Delete from database
                DatabaseService.executeUpdateAsync(
                    "DELETE FROM farm_photos WHERE id = ?",
                    new Object[]{photoId},
                    rows -> {
                        Platform.runLater(() -> {
                            if (rows > 0) {
                                // Also try to delete the file
                                try {
                                    Files.deleteIfExists(Paths.get(photoPath));
                                } catch (Exception e) {
                                    // Ignore file deletion errors
                                }
                                showSuccess("সফল!", "ছবি মুছে ফেলা হয়েছে।");
                                loadFarmPhotos(); // Refresh the photos
                            }
                        });
                    },
                    error -> {
                        Platform.runLater(() -> showError("ত্রুটি", "ছবি মুছতে সমস্যা হয়েছে।"));
                        error.printStackTrace();
                    }
                );
            }
        });
    }
    
    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot) : "";
    }

    @FXML
    private void onEditProfile() {
        // Navigate to edit profile view
        App.loadScene("edit-profile-view.fxml", "প্রোফাইল সম্পাদনা");
    }

    @FXML
    private void onBack() {
        // Stop polling when leaving the view
        if (syncManager != null && currentUser != null) {
            syncManager.stopPolling("profile_" + currentUser.getId());
        }
        App.loadScene("farmer-dashboard-view.fxml", "Dashboard");
    }

    @FXML
    private void onLogout() {
        // Show confirmation dialog
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("আপনি কি লগআউট করতে চান?");
        confirm.setContentText("আপনাকে পুনরায় লগইন করতে হবে।");
        confirm.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                // Clear current user
                App.setCurrentUser(null);
                // Navigate to login screen
                App.loadScene("login-view.fxml", "Login");
            }
        });
    }

    @FXML
    private void onCopyUserId() {
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(String.valueOf(currentUser.getId()));
            clipboard.setContent(content);
            showSuccess("কপি সফল", "User ID কপি হয়েছে: " + currentUser.getId());
        } catch (Exception e) {
            showError("ত্রুটি", "ID কপি করতে ব্যর্থ হয়েছে।");
            e.printStackTrace();
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
}
