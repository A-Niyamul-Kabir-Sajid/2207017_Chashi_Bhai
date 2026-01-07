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

import java.io.File;

/**
 * BuyerProfileController - Display buyer profile with purchase history stats
 */
public class BuyerProfileController {

    @FXML private ImageView imgProfilePhoto;
    @FXML private Label lblBuyerName;
    @FXML private Label lblUserId;
    @FXML private Label lblPhone;
    @FXML private Label lblDistrict;
    @FXML private Label lblUpazila;
    @FXML private Label lblTotalPurchases;
    @FXML private Label lblTotalSpent;
    @FXML private Label lblMemberSince;
    @FXML private Button btnEditProfile;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        
        if (currentUser == null || !"buyer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র ক্রেতারা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        loadProfileData();
    }

    /**
     * Load buyer profile data from database
     */
    private void loadProfileData() {
        DatabaseService.executeQueryAsync(
            "SELECT u.*, " +
            "(SELECT COUNT(*) FROM orders WHERE buyer_id = u.id AND status = 'delivered') as total_purchases, " +
            "(SELECT COALESCE(SUM(o.quantity * c.price), 0) FROM orders o JOIN crops c ON o.crop_id = c.id WHERE o.buyer_id = u.id AND o.status = 'delivered') as total_spent " +
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
                            int totalPurchases = resultSet.getInt("total_purchases");
                            double totalSpent = resultSet.getDouble("total_spent");
                            String createdAt = resultSet.getString("created_at");
                            String photoPath = resultSet.getString("profile_photo");

                            // Set profile data
                            lblBuyerName.setText(name);
                            lblUserId.setText("ID: " + currentUser.getId());
                            lblPhone.setText(phone != null ? phone : "N/A");
                            lblDistrict.setText(district != null ? district : "N/A");
                            lblUpazila.setText(upazila != null ? upazila : "N/A");
                            lblTotalPurchases.setText(String.valueOf(totalPurchases));
                            lblTotalSpent.setText(String.format("৳%.2f", totalSpent));
                            
                            // Member since
                            if (createdAt != null) {
                                lblMemberSince.setText(createdAt.substring(0, 10));
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
    }

    @FXML
    private void onEditProfile() {
        // Navigate to edit profile view
        App.loadScene("edit-profile-view.fxml", "প্রোফাইল সম্পাদনা");
    }

    @FXML
    private void onBack() {
        App.loadScene("buyer-dashboard-view.fxml", "Dashboard");
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

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
