package com.sajid._207017_chashi_bhai.services;

import com.sajid._207017_chashi_bhai.App;
import javafx.application.Platform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * NotificationService - Handles all notification operations
 * Supports creating, fetching, and managing notifications for farmers and buyers
 */
public class NotificationService {
    
    private static NotificationService instance;
    
    private NotificationService() {}
    
    public static NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }
    
    // Notification types
    public static final String TYPE_ORDER_NEW = "order";
    public static final String TYPE_ORDER_ACCEPTED = "success";
    public static final String TYPE_ORDER_REJECTED = "error";
    public static final String TYPE_ORDER_TRANSIT = "info";
    public static final String TYPE_ORDER_DELIVERED = "success";
    public static final String TYPE_MESSAGE = "message";
    public static final String TYPE_REVIEW = "review";
    public static final String TYPE_INFO = "info";
    public static final String TYPE_SUCCESS = "success";
    public static final String TYPE_WARNING = "warning";
    
    /**
     * Create a new notification and sync to Firebase
     */
    public void createNotification(int userId, String title, String message, String type, Integer relatedId) {
        String sql = "INSERT INTO notifications (user_id, title, message, type, related_id, is_read, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, 0, datetime('now', 'localtime'))";
        
        DatabaseService.executeUpdateAsync(sql, new Object[]{userId, title, message, type, relatedId},
            rowsAffected -> {
                System.out.println("✓ Notification created for user " + userId + ": " + title);
                
                // Get the generated notification ID and sync to Firebase
                DatabaseService.executeQueryAsync(
                    "SELECT id, created_at FROM notifications WHERE user_id = ? AND title = ? ORDER BY created_at DESC LIMIT 1",
                    new Object[]{userId, title},
                    rs -> {
                        try {
                            if (rs.next()) {
                                int notificationId = rs.getInt("id");
                                String createdAt = rs.getString("created_at");
                                
                                // Sync to Firebase if authenticated
                                FirebaseService firebaseService = FirebaseService.getInstance();
                                if (firebaseService.isAuthenticated()) {
                                    firebaseService.syncNotificationToFirebase(
                                        notificationId, userId, title, message, type, relatedId, false, createdAt
                                    );
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Error syncing notification to Firebase: " + e.getMessage());
                        }
                    },
                    error -> {
                        System.err.println("Error querying notification for Firebase sync: " + error.getMessage());
                    }
                );
            },
            error -> {
                System.err.println("Failed to create notification: " + error.getMessage());
                error.printStackTrace();
            }
        );
    }
    
    /**
     * Create order notification for farmer when buyer places an order
     */
    public void notifyFarmerNewOrder(int farmerId, int orderId, String buyerName, String cropName, double quantity, String unit) {
        String title = "🛒 নতুন অর্ডার এসেছে!";
        String message = String.format("%s আপনার %s %.1f %s অর্ডার করেছেন।", buyerName, cropName, quantity, unit);
        createNotification(farmerId, title, message, TYPE_ORDER_NEW, orderId);
    }
    
    /**
     * Notify buyer when order is accepted
     */
    public void notifyBuyerOrderAccepted(int buyerId, int orderId, String farmerName, String cropName) {
        String title = "✅ অর্ডার গৃহীত হয়েছে!";
        String message = String.format("%s আপনার %s এর অর্ডার গ্রহণ করেছেন।", farmerName, cropName);
        createNotification(buyerId, title, message, TYPE_ORDER_ACCEPTED, orderId);
    }
    
    /**
     * Notify buyer when order is rejected
     */
    public void notifyBuyerOrderRejected(int buyerId, int orderId, String farmerName, String cropName) {
        String title = "❌ অর্ডার বাতিল হয়েছে";
        String message = String.format("%s আপনার %s এর অর্ডার বাতিল করেছেন।", farmerName, cropName);
        createNotification(buyerId, title, message, TYPE_ORDER_REJECTED, orderId);
    }
    
    /**
     * Notify buyer when order is in transit
     */
    public void notifyBuyerOrderInTransit(int buyerId, int orderId, String farmerName, String cropName) {
        String title = "🚚 অর্ডার পথে আছে!";
        String message = String.format("%s এর %s ডেলিভারির জন্য পাঠানো হয়েছে।", farmerName, cropName);
        createNotification(buyerId, title, message, TYPE_ORDER_TRANSIT, orderId);
    }
    
    /**
     * Notify buyer when order is delivered
     */
    public void notifyBuyerOrderDelivered(int buyerId, int orderId, String farmerName, String cropName) {
        String title = "📦 অর্ডার সম্পন্ন!";
        String message = String.format("%s এর %s ডেলিভারি সম্পন্ন হয়েছে। রিভিউ দিন!", farmerName, cropName);
        createNotification(buyerId, title, message, TYPE_ORDER_DELIVERED, orderId);
    }
    
    /**
     * Notify farmer when buyer has received the order (completed)
     */
    public void notifyFarmerOrderCompleted(int farmerId, int orderId, String buyerName, String cropName) {
        String title = "✅ অর্ডার সম্পন্ন!";
        String message = String.format("%s আপনার %s সফলভাবে গ্রহণ করেছেন।", buyerName, cropName);
        createNotification(farmerId, title, message, TYPE_SUCCESS, orderId);
    }
    
    /**
     * Notify farmer when they receive a review
     */
    public void notifyFarmerNewReview(int farmerId, int orderId, String buyerName, int rating) {
        String title = "⭐ নতুন রিভিউ পেয়েছেন!";
        String message = String.format("%s আপনাকে %d স্টার রেটিং দিয়েছেন।", buyerName, rating);
        createNotification(farmerId, title, message, TYPE_REVIEW, orderId);
    }
    
    /**
     * Notify farmer when order is cancelled by buyer
     */
    public void notifyFarmerOrderCancelled(int farmerId, int orderId, String buyerName, String cropName) {
        String title = "⚠️ অর্ডার বাতিল";
        String message = String.format("%s তাদের %s এর অর্ডার বাতিল করেছেন।", buyerName, cropName);
        createNotification(farmerId, title, message, TYPE_WARNING, orderId);
    }
    
    /**
     * Get all notifications for a user
     */
    public void getNotifications(int userId, Consumer<List<Map<String, Object>>> onSuccess, Consumer<Exception> onError) {
        String sql = "SELECT n.*, " +
                     "CASE WHEN n.related_id IS NOT NULL THEN " +
                     "(SELECT c.name FROM orders o JOIN crops c ON o.crop_id = c.id WHERE o.id = n.related_id) " +
                     "ELSE NULL END as crop_name " +
                     "FROM notifications n " +
                     "WHERE n.user_id = ? " +
                     "ORDER BY n.created_at DESC " +
                     "LIMIT 50";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{userId},
            rs -> {
                List<Map<String, Object>> notifications = new ArrayList<>();
                try {
                    while (rs.next()) {
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("id", rs.getInt("id"));
                        notification.put("userId", rs.getInt("user_id"));
                        notification.put("title", rs.getString("title"));
                        notification.put("message", rs.getString("message"));
                        notification.put("type", rs.getString("type"));
                        notification.put("isRead", rs.getBoolean("is_read"));
                        notification.put("relatedId", rs.getObject("related_id"));
                        notification.put("createdAt", rs.getString("created_at"));
                        notification.put("cropName", rs.getString("crop_name"));
                        notifications.add(notification);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Platform.runLater(() -> onSuccess.accept(notifications));
            },
            error -> Platform.runLater(() -> onError.accept(error))
        );
    }
    
    /**
     * Get unread notification count for a user
     */
    public void getUnreadCount(int userId, Consumer<Integer> onSuccess) {
        String sql = "SELECT COUNT(*) as count FROM notifications WHERE user_id = ? AND is_read = 0";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{userId},
            rs -> {
                int count = 0;
                try {
                    if (rs.next()) {
                        count = rs.getInt("count");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final int finalCount = count;
                Platform.runLater(() -> onSuccess.accept(finalCount));
            },
            error -> Platform.runLater(() -> onSuccess.accept(0))
        );
    }
    
    /**
     * Mark a notification as read and sync to Firebase
     */
    public void markAsRead(int notificationId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE id = ?";
        DatabaseService.executeUpdateAsync(sql, new Object[]{notificationId},
            rowsAffected -> {
                // Sync read status to Firebase
                DatabaseService.executeQueryAsync(
                    "SELECT user_id, title, message, type, related_id, created_at FROM notifications WHERE id = ?",
                    new Object[]{notificationId},
                    rs -> {
                        try {
                            if (rs.next()) {
                                FirebaseService firebaseService = FirebaseService.getInstance();
                                if (firebaseService.isAuthenticated()) {
                                    firebaseService.syncNotificationToFirebase(
                                        notificationId,
                                        rs.getInt("user_id"),
                                        rs.getString("title"),
                                        rs.getString("message"),
                                        rs.getString("type"),
                                        rs.getObject("related_id") != null ? rs.getInt("related_id") : null,
                                        true, // is_read = true
                                        rs.getString("created_at")
                                    );
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Error syncing read status to Firebase: " + e.getMessage());
                        }
                    },
                    error -> {}
                );
            },
            error -> error.printStackTrace()
        );
    }
    
    /**
     * Sync all notifications between SQLite and Firebase
     * This performs a full bidirectional sync
     */
    public void syncWithFirebase(int userId, Runnable onComplete) {
        FirebaseService firebaseService = FirebaseService.getInstance();
        
        if (!firebaseService.isAuthenticated()) {
            System.out.println("[Notification] Firebase not authenticated, skipping sync");
            if (onComplete != null) {
                Platform.runLater(onComplete);
            }
            return;
        }
        
        System.out.println("[Notification] Starting Firebase sync for user " + userId);
        
        // Step 1: Push local notifications to Firebase
        DatabaseService.executeQueryAsync(
            "SELECT id, user_id, title, message, type, related_id, is_read, created_at " +
            "FROM notifications WHERE user_id = ?",
            new Object[]{userId},
            rs -> {
                try {
                    while (rs.next()) {
                        int notificationId = rs.getInt("id");
                        String title = rs.getString("title");
                        String message = rs.getString("message");
                        String type = rs.getString("type");
                        Integer relatedId = rs.getObject("related_id") != null ? rs.getInt("related_id") : null;
                        boolean isRead = rs.getBoolean("is_read");
                        String createdAt = rs.getString("created_at");
                        
                        firebaseService.syncNotificationToFirebase(
                            notificationId, userId, title, message, type, relatedId, isRead, createdAt
                        );
                    }
                    System.out.println("✓ Pushed local notifications to Firebase");
                } catch (Exception e) {
                    System.err.println("Error pushing notifications to Firebase: " + e.getMessage());
                }
                
                // Step 2: Pull notifications from Firebase and merge
                firebaseService.syncNotificationsFromFirebase(userId,
                    firebaseNotifications -> {
                        // Merge Firebase notifications into local database
                        for (Map<String, Object> notification : firebaseNotifications) {
                            mergeNotificationFromFirebase(notification);
                        }
                        System.out.println("✓ Notification sync completed");
                        if (onComplete != null) {
                            Platform.runLater(onComplete);
                        }
                    },
                    error -> {
                        System.err.println("Error pulling notifications from Firebase: " + error.getMessage());
                        if (onComplete != null) {
                            Platform.runLater(onComplete);
                        }
                    }
                );
            },
            error -> {
                System.err.println("Error reading local notifications: " + error.getMessage());
                if (onComplete != null) {
                    Platform.runLater(onComplete);
                }
            }
        );
    }
    
    /**
     * Merge a notification from Firebase into local database
     */
    private void mergeNotificationFromFirebase(Map<String, Object> notification) {
        int notificationId = (int) notification.get("id");
        int userId = (int) notification.get("userId");
        String title = (String) notification.get("title");
        String message = (String) notification.get("message");
        String type = (String) notification.get("type");
        Integer relatedId = (Integer) notification.get("relatedId");
        boolean isRead = (boolean) notification.get("isRead");
        String createdAt = (String) notification.get("createdAt");
        
        // Check if notification exists in local database
        DatabaseService.executeQueryAsync(
            "SELECT id FROM notifications WHERE id = ?",
            new Object[]{notificationId},
            rs -> {
                try {
                    if (rs.next()) {
                        // Update existing notification
                        String updateSql = "UPDATE notifications SET is_read = ? WHERE id = ?";
                        DatabaseService.executeUpdateAsync(updateSql, new Object[]{isRead, notificationId},
                            rowsAffected -> {},
                            error -> error.printStackTrace()
                        );
                    } else {
                        // Insert new notification from Firebase
                        String insertSql = "INSERT INTO notifications (id, user_id, title, message, type, related_id, is_read, created_at) " +
                                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                        DatabaseService.executeUpdateAsync(insertSql, 
                            new Object[]{notificationId, userId, title, message, type, relatedId, isRead, createdAt},
                            rowsAffected -> {},
                            error -> error.printStackTrace()
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
    }
    
    /**
     * Mark all notifications as read for a user
     */
    public void markAllAsRead(int userId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        DatabaseService.executeUpdateAsync(sql, new Object[]{userId},
            rowsAffected -> System.out.println("Marked " + rowsAffected + " notifications as read"),
            error -> error.printStackTrace()
        );
    }
    
    /**
     * Delete old notifications (older than 30 days)
     */
    public void cleanupOldNotifications() {
        String sql = "DELETE FROM notifications WHERE created_at < datetime('now', '-30 days')";
        DatabaseService.executeUpdateAsync(sql, new Object[]{},
            rowsAffected -> {
                if (rowsAffected > 0) {
                    System.out.println("Cleaned up " + rowsAffected + " old notifications");
                }
            },
            error -> error.printStackTrace()
        );
    }
}
