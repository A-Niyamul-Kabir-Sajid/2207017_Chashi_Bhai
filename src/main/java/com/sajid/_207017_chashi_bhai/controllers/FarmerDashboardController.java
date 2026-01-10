package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

/**
 * FarmerDashboardController - Main dashboard for farmers
 * Displays welcome message, verification status, quick stats, and navigation buttons
 */
public class FarmerDashboardController {

    @FXML private Label lblWelcome;
    @FXML private Label lblVerifiedBadge;
    @FXML private Label lblTotalEarnings;
    @FXML private Label lblActiveListings;
    @FXML private Label lblPendingOrders;
    
    @FXML private Button btnPostCrop;
    @FXML private Button btnMyCrops;
    @FXML private Button btnMyOrders;
    @FXML private Button btnHistory;
    @FXML private Button btnProfile;
    @FXML private Button btnBack;
    @FXML private Button btnFeed;
    
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        
        if (currentUser == null || !"farmer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র কৃষকরা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        // Set welcome message
        lblWelcome.setText("স্বাগতম, " + currentUser.getName() + "!");
        
        // Show/hide verified badge
        if (currentUser.isVerified()) {
            lblVerifiedBadge.setVisible(true);
            lblVerifiedBadge.setText("✓ যাচাইকৃত কৃষক");
        } else {
            lblVerifiedBadge.setVisible(false);
        }

        // Load dashboard statistics
        loadDashboardStats();
    }

    /**
     * Load statistics: total earnings, active crops, pending orders
     */
    private void loadDashboardStats() {
        if (progressIndicator != null) {
            progressIndicator.setVisible(true);
        }

        DatabaseService.executeQueryAsync(
            "SELECT " +
            "(SELECT COALESCE(SUM(o.quantity_kg * o.price_per_kg), 0) FROM orders o " +
            " WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed')) as total_earnings, " +
            "(SELECT COUNT(*) FROM crops WHERE farmer_id = ? AND status = 'active') as active_crops, " +
            "(SELECT COUNT(*) FROM orders o " +
            " WHERE o.farmer_id = ? AND o.status IN ('new', 'pending', 'accepted')) as pending_orders",
            new Object[]{currentUser.getId(), currentUser.getId(), currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            double earnings = resultSet.getDouble("total_earnings");
                            int activeCrops = resultSet.getInt("active_crops");
                            int pendingOrders = resultSet.getInt("pending_orders");

                            lblTotalEarnings.setText(String.format("৳%.2f", earnings));
                            lblActiveListings.setText(String.valueOf(activeCrops));
                            lblPendingOrders.setText(String.valueOf(pendingOrders));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("ত্রুটি", "পরিসংখ্যান লোড করতে ব্যর্থ হয়েছে।");
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
                    showError("ডাটাবেস ত্রুটি", "পরিসংখ্যান লোড করতে সমস্যা হয়েছে।");
                    error.printStackTrace();
                });
            }
        );
    }

    @FXML
    private void onPostCrop() {
        System.out.println("[DEBUG] Post Crop button clicked!");
        App.setPreviousScene("farmer-dashboard-view.fxml");
        App.loadScene("post-crop-view.fxml", "নতুন ফসল যোগ করুন");
    }

    @FXML
    private void onMyCrops() {
        System.out.println("[DEBUG] My Crops button clicked!");
        App.loadScene("my-crops-view.fxml", "আমার ফসলসমূহ");
    }

    @FXML
    private void onMyOrders() {
        System.out.println("[DEBUG] My Orders button clicked!");
        try {
            App.loadScene("farmer-orders-view.fxml", "আমার অর্ডারসমূহ");
            System.out.println("[DEBUG] Scene load initiated successfully");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load farmer orders view:");
            e.printStackTrace();
            showError("ত্রুটি", "অর্ডার পেজ লোড করতে ব্যর্থ: " + e.getMessage());
        }
    }

    @FXML
    private void onHistory() {
        System.out.println("[DEBUG] History button clicked!");
        try {
            App.loadScene("farmer-history-view.fxml", "বিক্রয় ইতিহাস");
            System.out.println("[DEBUG] History scene load initiated successfully");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load farmer history view:");
            e.printStackTrace();
            showError("ত্রুটি", "ইতিহাস পেজ লোড করতে ব্যর্থ: " + e.getMessage());
        }
    }

    @FXML
    private void onProfile() {
        System.out.println("[DEBUG] Profile button clicked!");
        App.loadScene("farmer-profile-view.fxml", "আমার প্রোফাইল");
    }

    @FXML
    private void onBack() {
        // Navigate to main feed
        App.loadScene("crop-feed-view.fxml", "ফসলের তালিকা");
    }

    @FXML
    private void onGoToFeed() {
        // Navigate to main feed
        App.loadScene("crop-feed-view.fxml", "ফসলের তালিকা");
    }

    @FXML
    private void onRefresh() {
        loadDashboardStats();
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
