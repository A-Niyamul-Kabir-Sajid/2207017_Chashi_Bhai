package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.NotificationService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;

/**
 * FarmerDashboardController - Main dashboard for farmers
 * Displays welcome message, verification status, quick stats, and navigation buttons
 */
public class FarmerDashboardController {

    @FXML private Label lblWelcome;
    @FXML private Label lblVerifiedBadge;
    @FXML private Label lblVerifiedText;
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
    @FXML private Button btnSearchOrder;
    @FXML private Button btnAllChat;
    @FXML private Button btnNotifications;
    @FXML private Label lblNotificationBadge;
    
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;
    private NotificationService notificationService;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        notificationService = NotificationService.getInstance();
        
        if (currentUser == null || !"farmer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র কৃষকরা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        // Set welcome message
        lblWelcome.setText("স্বাগতম, " + currentUser.getName() + "!");
        
        // Show/hide verified badge
        if (currentUser.isVerified()) {
            if (lblVerifiedBadge != null) {
                lblVerifiedBadge.setVisible(true);
                lblVerifiedBadge.setText("✓");
            }
            if (lblVerifiedText != null) {
                lblVerifiedText.setVisible(true);
                lblVerifiedText.setText("যাচাইকৃত কৃষক");
            }
        } else {
            if (lblVerifiedBadge != null) {
                lblVerifiedBadge.setVisible(false);
            }
            if (lblVerifiedText != null) {
                lblVerifiedText.setVisible(false);
            }
        }

        // Load dashboard statistics
        loadDashboardStats();
        
        // Load notification count
        loadNotificationCount();
    }
    
    /**
     * Load unread notification count and update badge
     */
    private void loadNotificationCount() {
        notificationService.getUnreadCount(currentUser.getId(), count -> {
            if (lblNotificationBadge != null) {
                if (count > 0) {
                    lblNotificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                    lblNotificationBadge.setVisible(true);
                } else {
                    lblNotificationBadge.setVisible(false);
                }
            }
        });
    }
    
    @FXML
    private void onNotifications() {
        App.setPreviousScene("farmer-dashboard-view.fxml");
        App.loadScene("notifications-view.fxml", "নোটিফিকেশন");
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
            "(SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o " +
            " WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed')) as total_earnings, " +
            "(SELECT COUNT(*) FROM crops WHERE farmer_id = ? AND status = 'active') as active_crops, " +
            "(SELECT COUNT(*) FROM orders o " +
            " WHERE o.farmer_id = ? AND o.status IN ('new', 'processing', 'accepted', 'shipped', 'in_transit')) as pending_orders",
            new Object[]{currentUser.getId(), currentUser.getId(), currentUser.getId()},
            resultSet -> {
                double earnings = 0.0;
                int activeCrops = 0;
                int pendingOrders = 0;
                boolean ok = false;
                try {
                    if (resultSet.next()) {
                        earnings = resultSet.getDouble("total_earnings");
                        activeCrops = resultSet.getInt("active_crops");
                        pendingOrders = resultSet.getInt("pending_orders");
                        ok = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                final boolean finalOk = ok;
                final double finalEarnings = earnings;
                final int finalActiveCrops = activeCrops;
                final int finalPendingOrders = pendingOrders;
                Platform.runLater(() -> {
                    try {
                        if (finalOk) {
                            if (lblTotalEarnings != null) {
                                lblTotalEarnings.setText(String.format("৳%.2f", finalEarnings));
                            }
                            if (lblActiveListings != null) {
                                lblActiveListings.setText(String.valueOf(finalActiveCrops));
                            }
                            if (lblPendingOrders != null) {
                                lblPendingOrders.setText(String.valueOf(finalPendingOrders));
                            }
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
    private void onSearchOrder() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("অর্ডার ID দিয়ে খুঁজুন");
        dialog.setHeaderText("অর্ডার খুঁজুন / Search Order by ID");
        dialog.setContentText("অর্ডার ID লিখুন (সংখ্যা):");

        dialog.showAndWait().ifPresent(input -> {
            try {
                int orderId = Integer.parseInt(input.trim());
                searchOrderById(orderId);
            } catch (NumberFormatException e) {
                showError("ত্রুটি", "সঠিক Order ID লিখুন (শুধুমাত্র সংখ্যা)");
            }
        });
    }

    private void searchOrderById(int orderId) {
        String sql = "SELECT o.id, o.order_number, o.status, o.total_amount, o.created_at, " +
                    "c.name as crop_name, b.name as buyer_name " +
                    "FROM orders o " +
                    "JOIN crops c ON o.crop_id = c.id " +
                    "JOIN users b ON o.buyer_id = b.id " +
                    "WHERE o.id = ? AND o.farmer_id = ?";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{orderId, currentUser.getId()},
            rs -> {
                // Read ResultSet data BEFORE Platform.runLater
                String orderNum = null;
                String status = null;
                double total = 0.0;
                String cropName = null;
                String buyerName = null;
                boolean found = false;
                try {
                    if (rs.next()) {
                        orderNum = rs.getString("order_number");
                        status = rs.getString("status");
                        total = rs.getDouble("total_amount");
                        cropName = rs.getString("crop_name");
                        buyerName = rs.getString("buyer_name");
                        found = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // Now update UI on JavaFX thread with pre-loaded data
                final String finalOrderNum = orderNum;
                final String finalStatus = status;
                final double finalTotal = total;
                final String finalCropName = cropName;
                final String finalBuyerName = buyerName;
                final boolean finalFound = found;
                Platform.runLater(() -> {
                    try {
                        if (finalFound) {
                            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
                            info.setTitle("অর্ডার পাওয়া গেছে / Order Found");
                            info.setHeaderText(finalOrderNum);
                            info.setContentText(
                                "ফসল: " + finalCropName + "\n" +
                                "ক্রেতা: " + finalBuyerName + "\n" +
                                "মোট: ৳" + String.format("%.2f", finalTotal) + "\n" +
                                "স্ট্যাটাস: " + finalStatus + "\n\n" +
                                "বিস্তারিত দেখতে চান?"
                            );

                            info.showAndWait().ifPresent(response -> {
                                if (response == ButtonType.OK) {
                                    App.setCurrentOrderId(orderId);
                                    App.setCurrentOrderNumber(finalOrderNum);
                                    App.loadScene("order-detail-view.fxml", "অর্ডার বিবরণ");
                                }
                            });
                        } else {
                            showError("পাওয়া যায়নি", "এই ID এর কোনো অর্ডার আপনার তালিকায় নেই।");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("ত্রুটি", "অর্ডার খুঁজতে ব্যর্থ হয়েছে।");
                    }
                });
            },
            err -> {
                Platform.runLater(() -> showError("ডাটাবেস ত্রুটি", "অর্ডার সার্চ করতে সমস্যা হয়েছে।"));
                err.printStackTrace();
            }
        );
    }

    @FXML
    private void onAllChat() {
        App.loadScene("chat-list-view.fxml", "সব চ্যাট");
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
