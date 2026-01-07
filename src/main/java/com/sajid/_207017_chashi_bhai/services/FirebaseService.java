package com.sajid._207017_chashi_bhai.services;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;
import javafx.application.Platform;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * FirebaseService - Real-time database operations using Firebase
 * Use this for: Chat, Notifications, Online Status, Real-time Updates
 */
public class FirebaseService {

    private static DatabaseReference database;
    private static boolean initialized = false;

    /**
     * Initialize Firebase with credentials
     * Call this once at app startup
     */
    public static void initialize() {
        try {
            // Try to load credentials from resources first
            InputStream serviceAccount = FirebaseService.class
                .getResourceAsStream("/firebase-credentials.json");
            
            // If not in resources, try from file system
            if (serviceAccount == null) {
                try {
                    serviceAccount = new FileInputStream("src/main/resources/firebase-credentials.json");
                } catch (Exception e) {
                    System.err.println("Firebase credentials not found. Firebase features will be disabled.");
                    System.err.println("Please follow the setup guide to add firebase-credentials.json");
                    return;
                }
            }

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setDatabaseUrl("https://your-project-id.firebaseio.com") // Replace with your URL
                .build();

            FirebaseApp.initializeApp(options);
            database = FirebaseDatabase.getInstance().getReference();
            initialized = true;
            
            System.out.println("Firebase initialized successfully");
            
        } catch (Exception e) {
            System.err.println("Firebase initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if Firebase is initialized and ready
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Get reference to a specific path in Firebase
     */
    public static DatabaseReference getRef(String path) {
        if (!initialized) {
            throw new IllegalStateException("Firebase not initialized. Call initialize() first.");
        }
        return database.child(path);
    }

    // ============================================
    // USER OPERATIONS
    // ============================================

    /**
     * Set user online status
     */
    public static void setUserOnline(String userId, boolean online) {
        if (!initialized) return;
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("online", online);
        updates.put("lastSeen", ServerValue.TIMESTAMP);
        
        getRef("users/" + userId).updateChildren(updates);
    }

    /**
     * Listen to user online status
     */
    public static void listenToUserStatus(String userId, Consumer<Boolean> callback) {
        if (!initialized) return;
        
        getRef("users/" + userId + "/online").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean online = snapshot.getValue(Boolean.class);
                Platform.runLater(() -> callback.accept(online != null ? online : false));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Error listening to user status: " + error.getMessage());
            }
        });
    }

    // ============================================
    // CHAT OPERATIONS
    // ============================================

    /**
     * Send a message
     */
    public static void sendMessage(String conversationId, String senderId, String receiverId, 
                                   String text, String type, Consumer<String> onSuccess, 
                                   Consumer<Exception> onError) {
        if (!initialized) {
            if (onError != null) onError.accept(new Exception("Firebase not initialized"));
            return;
        }

        DatabaseReference messageRef = getRef("messages/" + conversationId).push();
        String messageId = messageRef.getKey();

        Map<String, Object> message = new HashMap<>();
        message.put("senderId", senderId);
        message.put("receiverId", receiverId);
        message.put("text", text);
        message.put("type", type);
        message.put("isRead", false);
        message.put("timestamp", ServerValue.TIMESTAMP);

        messageRef.setValue(message, (error, ref) -> {
            if (error != null) {
                if (onError != null) Platform.runLater(() -> onError.accept(error.toException()));
            } else {
                // Update conversation
                updateConversation(conversationId, text, senderId);
                if (onSuccess != null) Platform.runLater(() -> onSuccess.accept(messageId));
            }
        });
    }

    /**
     * Update conversation with last message
     */
    private static void updateConversation(String conversationId, String lastMessage, String senderId) {
        if (!initialized) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", lastMessage);
        updates.put("lastMessageTime", ServerValue.TIMESTAMP);
        updates.put("lastMessageSender", senderId);

        getRef("conversations/" + conversationId).updateChildren(updates);
    }

    /**
     * Listen to new messages in a conversation
     */
    public static void listenToMessages(String conversationId, Consumer<DataSnapshot> onNewMessage) {
        if (!initialized) return;

        getRef("messages/" + conversationId)
            .orderByChild("timestamp")
            .addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    Platform.runLater(() -> onNewMessage.accept(snapshot));
                }

                @Override
                public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                    // Message updated (e.g., marked as read)
                }

                @Override
                public void onChildRemoved(DataSnapshot snapshot) {
                    // Message deleted
                }

                @Override
                public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                    // Not used
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    System.err.println("Error listening to messages: " + error.getMessage());
                }
            });
    }

    /**
     * Mark message as read
     */
    public static void markMessageAsRead(String conversationId, String messageId) {
        if (!initialized) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("isRead", true);
        updates.put("readAt", ServerValue.TIMESTAMP);

        getRef("messages/" + conversationId + "/" + messageId).updateChildren(updates);
    }

    /**
     * Get or create conversation between two users
     */
    public static void getOrCreateConversation(String user1Id, String user2Id, String cropId,
                                              Consumer<String> onSuccess, Consumer<Exception> onError) {
        if (!initialized) {
            if (onError != null) onError.accept(new Exception("Firebase not initialized"));
            return;
        }

        // Create conversation ID (sorted to ensure uniqueness)
        String conversationId = user1Id.compareTo(user2Id) < 0 
            ? user1Id + "_" + user2Id + "_" + (cropId != null ? cropId : "general")
            : user2Id + "_" + user1Id + "_" + (cropId != null ? cropId : "general");

        DatabaseReference convRef = getRef("conversations/" + conversationId);
        
        convRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Create new conversation
                    Map<String, Object> conversation = new HashMap<>();
                    Map<String, Boolean> participants = new HashMap<>();
                    participants.put(user1Id, true);
                    participants.put(user2Id, true);
                    
                    conversation.put("participants", participants);
                    if (cropId != null) conversation.put("cropId", cropId);
                    conversation.put("createdAt", ServerValue.TIMESTAMP);
                    conversation.put("updatedAt", ServerValue.TIMESTAMP);
                    
                    Map<String, Integer> unreadCount = new HashMap<>();
                    unreadCount.put(user1Id, 0);
                    unreadCount.put(user2Id, 0);
                    conversation.put("unreadCount", unreadCount);

                    convRef.setValue(conversation, (error, ref) -> {
                        if (error != null) {
                            if (onError != null) Platform.runLater(() -> onError.accept(error.toException()));
                        } else {
                            if (onSuccess != null) Platform.runLater(() -> onSuccess.accept(conversationId));
                        }
                    });
                } else {
                    // Conversation exists
                    if (onSuccess != null) Platform.runLater(() -> onSuccess.accept(conversationId));
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                if (onError != null) Platform.runLater(() -> onError.accept(error.toException()));
            }
        });
    }

    /**
     * Listen to user's conversations
     */
    public static void listenToUserConversations(String userId, Consumer<DataSnapshot> onUpdate) {
        if (!initialized) return;

        getRef("conversations")
            .orderByChild("participants/" + userId)
            .equalTo(true)
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Platform.runLater(() -> onUpdate.accept(snapshot));
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    System.err.println("Error listening to conversations: " + error.getMessage());
                }
            });
    }

    /**
     * Set typing status
     */
    public static void setTypingStatus(String conversationId, String userId, boolean typing) {
        if (!initialized) return;

        if (typing) {
            Map<String, Object> status = new HashMap<>();
            status.put("isTyping", true);
            status.put("timestamp", ServerValue.TIMESTAMP);
            getRef("typingStatus/" + conversationId + "/" + userId).setValue(status);
        } else {
            getRef("typingStatus/" + conversationId + "/" + userId).removeValue();
        }
    }

    // ============================================
    // NOTIFICATION OPERATIONS
    // ============================================

    /**
     * Send notification to user
     */
    public static void sendNotification(String userId, String title, String message, 
                                       String type, String relatedId) {
        if (!initialized) return;

        DatabaseReference notifRef = getRef("notifications/" + userId).push();

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("message", message);
        notification.put("type", type);
        notification.put("relatedId", relatedId);
        notification.put("isRead", false);
        notification.put("timestamp", ServerValue.TIMESTAMP);

        notifRef.setValue(notification);
    }

    /**
     * Listen to user notifications
     */
    public static void listenToNotifications(String userId, Consumer<DataSnapshot> onNewNotification) {
        if (!initialized) return;

        getRef("notifications/" + userId)
            .orderByChild("timestamp")
            .addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    Platform.runLater(() -> onNewNotification.accept(snapshot));
                }

                @Override
                public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

                @Override
                public void onChildRemoved(DataSnapshot snapshot) {}

                @Override
                public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

                @Override
                public void onCancelled(DatabaseError error) {
                    System.err.println("Error listening to notifications: " + error.getMessage());
                }
            });
    }

    // ============================================
    // ORDER REAL-TIME UPDATES
    // ============================================

    /**
     * Update order status in real-time
     */
    public static void updateOrderStatus(String orderId, String status, String notes) {
        if (!initialized) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("updatedAt", ServerValue.TIMESTAMP);
        if (notes != null) updates.put("notes", notes);

        getRef("orders/" + orderId).updateChildren(updates);
    }

    /**
     * Listen to order updates
     */
    public static void listenToOrder(String orderId, Consumer<DataSnapshot> onUpdate) {
        if (!initialized) return;

        getRef("orders/" + orderId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Platform.runLater(() -> onUpdate.accept(snapshot));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Error listening to order: " + error.getMessage());
            }
        });
    }

    /**
     * Cleanup - Remove listeners and disconnect
     */
    public static void cleanup() {
        if (initialized && database != null) {
            database.getDatabase().goOffline();
        }
    }
}
