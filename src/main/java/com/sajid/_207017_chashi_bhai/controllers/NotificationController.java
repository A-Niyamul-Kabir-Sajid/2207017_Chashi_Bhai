package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.NotificationService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * NotificationController - Handles the notification list view
 */
public class NotificationController {

    @FXML private VBox vboxNotifications;
    @FXML private VBox vboxEmpty;
    @FXML private VBox vboxLoading;
    @FXML private Label lblUnreadCount;
    @FXML private Label lblTotalCount;
    @FXML private Button btnMarkAllRead;

    private User currentUser;
    private NotificationService notificationService;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        notificationService = NotificationService.getInstance();
        
        if (currentUser == null) {
            showError("অ্যাক্সেস অস্বীকার", "দয়া করে লগইন করুন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }
        
        loadNotifications();
    }

    private void loadNotifications() {
        vboxLoading.setVisible(true);
        vboxEmpty.setVisible(false);
        vboxNotifications.getChildren().clear();
        
        notificationService.getNotifications(currentUser.getId(),
            notifications -> {
                vboxLoading.setVisible(false);
                
                if (notifications.isEmpty()) {
                    vboxEmpty.setVisible(true);
                    lblTotalCount.setText("0");
                    lblUnreadCount.setText("0");
                } else {
                    vboxEmpty.setVisible(false);
                    lblTotalCount.setText(String.valueOf(notifications.size()));
                    
                    int unreadCount = 0;
                    for (Map<String, Object> notification : notifications) {
                        if (!(boolean) notification.get("isRead")) {
                            unreadCount++;
                        }
                        vboxNotifications.getChildren().add(createNotificationCard(notification));
                    }
                    lblUnreadCount.setText(String.valueOf(unreadCount));
                }
            },
            error -> {
                vboxLoading.setVisible(false);
                vboxEmpty.setVisible(true);
                showError("ত্রুটি", "নোটিফিকেশন লোড করতে ব্যর্থ হয়েছে।");
            }
        );
    }

    private VBox createNotificationCard(Map<String, Object> notification) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(15, 18, 15, 18));
        card.setCursor(Cursor.HAND);
        
        boolean isRead = (boolean) notification.get("isRead");
        String type = (String) notification.get("type");
        
        // Style based on read status and type
        String baseStyle = isRead ? "notification-card, notification-read" : "notification-card, notification-unread";
        card.getStyleClass().addAll(baseStyle.split(", "));
        
        // Add type-specific styling
        switch (type) {
            case "order":
                card.getStyleClass().add("notification-order");
                break;
            case "success":
                card.getStyleClass().add("notification-success");
                break;
            case "error":
                card.getStyleClass().add("notification-error");
                break;
            case "warning":
                card.getStyleClass().add("notification-warning");
                break;
            case "review":
                card.getStyleClass().add("notification-review");
                break;
            default:
                card.getStyleClass().add("notification-info");
        }
        
        // Header row with icon, title and time
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        // Type icon
        Label iconLabel = new Label(getTypeIcon(type));
        iconLabel.getStyleClass().add("notification-icon");
        
        // Title
        Label titleLabel = new Label((String) notification.get("title"));
        titleLabel.getStyleClass().add("notification-title");
        titleLabel.setWrapText(true);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        
        // Unread indicator
        if (!isRead) {
            Label unreadDot = new Label("●");
            unreadDot.getStyleClass().add("notification-unread-dot");
            header.getChildren().add(unreadDot);
        }
        
        header.getChildren().addAll(iconLabel, titleLabel);
        
        // Message
        Label messageLabel = new Label((String) notification.get("message"));
        messageLabel.getStyleClass().add("notification-message");
        messageLabel.setWrapText(true);
        
        // Time row
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        
        String createdAt = (String) notification.get("createdAt");
        Label timeLabel = new Label(formatTimeAgo(createdAt));
        timeLabel.getStyleClass().add("notification-time");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Action button if has related order
        Object relatedId = notification.get("relatedId");
        if (relatedId != null && (type.equals("order") || type.equals("success") || 
            type.equals("error") || type.equals("warning") || type.equals("review"))) {
            Button btnView = new Button("বিস্তারিত দেখুন →");
            btnView.getStyleClass().add("button-link");
            btnView.setOnAction(e -> {
                int orderId = (int) relatedId;
                int notificationId = (int) notification.get("id");
                
                // Mark as read
                notificationService.markAsRead(notificationId);
                
                // Navigate to order detail
                navigateToOrder(orderId);
            });
            footer.getChildren().addAll(timeLabel, spacer, btnView);
        } else {
            footer.getChildren().addAll(timeLabel, spacer);
        }
        
        card.getChildren().addAll(header, messageLabel, footer);
        
        // Click handler to mark as read and navigate
        card.setOnMouseClicked(e -> {
            System.out.println("[Notification] Card clicked");
            
            if (!isRead) {
                int notificationId = (int) notification.get("id");
                notificationService.markAsRead(notificationId);
                card.getStyleClass().remove("notification-unread");
                card.getStyleClass().add("notification-read");
                
                // Update unread count
                int currentUnread = Integer.parseInt(lblUnreadCount.getText());
                if (currentUnread > 0) {
                    lblUnreadCount.setText(String.valueOf(currentUnread - 1));
                }
            }
            
            // Navigate to order if applicable
            Object relId = notification.get("relatedId");
            String nType = (String) notification.get("type");
            System.out.println("[Notification] relatedId: " + relId + ", type: " + nType);
            
            if (relId != null && (nType.equals("order") || nType.equals("success") || 
                nType.equals("error") || nType.equals("warning") || nType.equals("review"))) {
                System.out.println("[Notification] Navigating to order: " + relId);
                navigateToOrder((int) relId);
            } else {
                System.out.println("[Notification] No navigation - not an order-related notification");
            }
        });
        
        return card;
    }

    private String getTypeIcon(String type) {
        switch (type) {
            case "order": return "🛒";
            case "success": return "✅";
            case "error": return "❌";
            case "warning": return "⚠️";
            case "review": return "⭐";
            case "message": return "💬";
            default: return "🔔";
        }
    }

    private String formatTimeAgo(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "";
        }
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime notifTime = LocalDateTime.parse(timestamp, formatter);
            LocalDateTime now = LocalDateTime.now();
            
            long minutes = ChronoUnit.MINUTES.between(notifTime, now);
            long hours = ChronoUnit.HOURS.between(notifTime, now);
            long days = ChronoUnit.DAYS.between(notifTime, now);
            
            if (minutes < 1) {
                return "এইমাত্র";
            } else if (minutes < 60) {
                return minutes + " মিনিট আগে";
            } else if (hours < 24) {
                return hours + " ঘণ্টা আগে";
            } else if (days < 7) {
                return days + " দিন আগে";
            } else {
                return notifTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            }
        } catch (Exception e) {
            return timestamp;
        }
    }

    @FXML
    private void onMarkAllRead() {
        if (currentUser != null) {
            notificationService.markAllAsRead(currentUser.getId());
            
            // Update UI
            lblUnreadCount.setText("0");
            
            // Update all cards
            for (var node : vboxNotifications.getChildren()) {
                if (node instanceof VBox) {
                    VBox card = (VBox) node;
                    card.getStyleClass().remove("notification-unread");
                    if (!card.getStyleClass().contains("notification-read")) {
                        card.getStyleClass().add("notification-read");
                    }
                }
            }
            
            showInfo("সম্পন্ন", "সব নোটিফিকেশন পঠিত হিসেবে চিহ্নিত করা হয়েছে।");
        }
    }
    
    @FXML
    private void onRefresh() {
        System.out.println("[Notification] Syncing with Firebase...");
        vboxLoading.setVisible(true);
        
        notificationService.syncWithFirebase(currentUser.getId(), () -> {
            System.out.println("[Notification] Sync complete, reloading notifications...");
            loadNotifications();
        });
    }

    @FXML
    private void onBack() {
        String previousScene = App.getPreviousScene();
        if (previousScene != null && !previousScene.isEmpty()) {
            App.loadScene(previousScene, getSceneTitle(previousScene));
        } else {
            // Go to dashboard based on user role
            if (currentUser.getRole().equals("farmer")) {
                App.loadScene("farmer-dashboard-view.fxml", "Dashboard");
            } else {
                App.loadScene("buyer-dashboard-view.fxml", "Dashboard");
            }
        }
    }
    
    /**
     * Navigate to order detail page or orders list based on order state
     */
    private void navigateToOrder(int orderId) {
        System.out.println("[Notification] Navigating to order: " + orderId);
        
        // First, fetch the order to get its status
        com.sajid._207017_chashi_bhai.services.DatabaseService.executeQueryAsync(
            "SELECT status FROM orders WHERE id = ?",
            new Object[]{orderId},
            rs -> {
                try {
                    if (rs.next()) {
                        String orderStatus = rs.getString("status");
                        System.out.println("[Notification] Order status: " + orderStatus);
                        Platform.runLater(() -> navigateToOrderWithStatus(orderId, orderStatus));
                    } else {
                        System.out.println("[Notification] Order not found: " + orderId);
                        Platform.runLater(() -> {
                            showError("ত্রুটি", "অর্ডার খুঁজে পাওয়া যায়নি।");
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> showError("ত্রুটি", "অর্ডার লোড করতে ব্যর্থ হয়েছে।"));
                }
            },
            error -> {
                System.out.println("[Notification] Database error: " + error.getMessage());
                Platform.runLater(() -> showError("ত্রুটি", "অর্ডার লোড করতে ব্যর্থ হয়েছে।"));
                error.printStackTrace();
            }
        );
    }
    
    /**
     * Navigate to appropriate view based on order status and user role
     */
    private void navigateToOrderWithStatus(int orderId, String orderStatus) {
        System.out.println("[Notification] Navigating to orders view - orderId: " + orderId + ", status: " + orderStatus);
        
        App.setCurrentOrderId(orderId);
        App.setPreviousScene("notifications-view.fxml");
        
        String role = currentUser.getRole();
        System.out.println("[Notification] User role: " + role);
        
        // Map order status to filter state
        String filterState = mapOrderStatusToFilter(orderStatus, role);
        System.out.println("[Notification] Filter state: " + filterState);
        App.setOrderFilterState(filterState);
        
        // Navigate to orders view with the filter set
        if ("farmer".equals(role)) {
            App.loadScene("farmer-orders-view.fxml", "আমার অর্ডারসমূহ");
        } else {
            App.loadScene("buyer-orders-view.fxml", "আমার অর্ডারসমূহ");
        }
    }
    
    /**
     * Map order status to filter state based on user role
     */
    private String mapOrderStatusToFilter(String orderStatus, String role) {
        if ("farmer".equals(role)) {
            // Farmer filters: all, new, accepted, in_transit, delivered
            switch (orderStatus) {
                case "new": return "new";
                case "processing": return "new";
                case "accepted": return "accepted";
                case "shipped": return "in_transit";
                case "in_transit": return "in_transit";
                case "delivered": return "delivered";
                case "completed": return "delivered";
                default: return "all";
            }
        } else { // buyer
            // Buyer filters: all, pending, confirmed, in_transit, delivered
            switch (orderStatus) {
                case "new": return "pending";
                case "processing": return "pending";
                case "accepted": return "confirmed";
                case "shipped": return "in_transit";
                case "in_transit": return "in_transit";
                case "delivered": return "delivered";
                case "completed": return "delivered";
                default: return "all";
            }
        }
    }
    
    private String getSceneTitle(String sceneName) {
        switch (sceneName) {
            case "farmer-dashboard-view.fxml": return "Dashboard";
            case "buyer-dashboard-view.fxml": return "Dashboard";
            case "crop-feed-view.fxml": return "সকল ফসল";
            default: return "চাষী ভাই";
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
