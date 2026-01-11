package com.sajid._207017_chashi_bhai.services;

import com.google.cloud.firestore.*;
import javafx.application.Platform;

import java.sql.*;
import java.util.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * ChatService - Real-time chat service using Firebase Firestore + SQLite
 * 
 * Architecture:
 * - SQLite: Local storage for offline access and fast reads
 * - Firestore: Cloud sync for real-time messaging between users
 * 
 * Key Features:
 * - Real-time message delivery using Firestore listeners
 * - Offline-first: Messages saved locally first, then synced
 * - Optimistic UI: Messages appear instantly
 * - Automatic retry for failed messages
 */
public class ChatService {
    
    private static ChatService instance;
    private final FirebaseService firebaseService;
    private static final String DB_URL = "jdbc:sqlite:data/chashi_bhai.db";
    
    // Active listeners (conversation_id -> listener)
    private final Map<String, ListenerRegistration> activeListeners = new ConcurrentHashMap<>();
    
    // Message callbacks
    private Consumer<ChatMessage> onMessageReceived;
    private Consumer<ChatMessage> onMessageStatusChanged;
    private Consumer<Throwable> onError;
    
    // Background executor for async operations
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("ChatServiceWorker");
        return t;
    });
    
    private ChatService() {
        this.firebaseService = FirebaseService.getInstance();
    }
    
    public static ChatService getInstance() {
        if (instance == null) {
            instance = new ChatService();
        }
        return instance;
    }
    
    // ==========================================
    // CONVERSATION MANAGEMENT
    // ==========================================
    
    /**
     * Generate unique participant key for a conversation
     * Always returns smaller_larger format to ensure uniqueness
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return Participant key (e.g., "1_5")
     */
    public static String generateParticipantKey(int userId1, int userId2) {
        int smaller = Math.min(userId1, userId2);
        int larger = Math.max(userId1, userId2);
        return smaller + "_" + larger;
    }
    
    /**
     * Find or create a conversation between two users
     * 
     * Flow:
     * 1. Check SQLite for existing conversation
     * 2. If not found, check Firestore
     * 3. If still not found, create new in Firestore
     * 4. Always sync to SQLite
     * 
     * @param currentUserId Current user's ID
     * @param otherUserId Other participant's ID
     * @param cropId Optional crop context
     * @param onSuccess Callback with conversation data
     * @param onError Error callback
     */
    public void getOrCreateConversation(int currentUserId, int otherUserId, Integer cropId,
                                        Consumer<Conversation> onSuccess, 
                                        Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                // Ensure consistent ordering
                int user1Id = Math.min(currentUserId, otherUserId);
                int user2Id = Math.max(currentUserId, otherUserId);
                String participantKey = user1Id + "_" + user2Id;
                
                // Step 1: Check SQLite first
                Conversation localConv = findConversationInSQLite(user1Id, user2Id, cropId);
                if (localConv != null && localConv.getFirebaseId() != null) {
                    Platform.runLater(() -> onSuccess.accept(localConv));
                    return;
                }
                
                // Step 2: Check Firestore
                if (firebaseService.isInitialized()) {
                    Firestore db = firebaseService.getFirestore();
                    
                    // Build query - search by participantKey
                    Query query = db.collection(FirebaseService.COLLECTION_CONVERSATIONS)
                            .whereEqualTo("participantKey", participantKey);
                    
                    // Add crop filter if specified
                    if (cropId != null) {
                        query = query.whereEqualTo("cropId", cropId);
                    }
                    
                    QuerySnapshot snapshot = query.limit(1).get().get();
                    
                    if (!snapshot.isEmpty()) {
                        // Found in Firestore - sync to SQLite
                        DocumentSnapshot doc = snapshot.getDocuments().get(0);
                        Conversation conv = mapFirestoreToConversation(doc);
                        saveConversationToSQLite(conv);
                        Platform.runLater(() -> onSuccess.accept(conv));
                        return;
                    }
                    
                    // Step 3: Create new conversation
                    Conversation newConv = createNewConversation(user1Id, user2Id, cropId);
                    Platform.runLater(() -> onSuccess.accept(newConv));
                    
                } else {
                    // Offline mode - create locally
                    Conversation conv = createLocalConversation(user1Id, user2Id, cropId);
                    Platform.runLater(() -> onSuccess.accept(conv));
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }
    
    /**
     * Find conversation in SQLite
     */
    private Conversation findConversationInSQLite(int user1Id, int user2Id, Integer cropId) {
        String sql;
        Object[] params;
        
        if (cropId != null) {
            sql = "SELECT * FROM conversations WHERE user1_id = ? AND user2_id = ? AND crop_id = ? LIMIT 1";
            params = new Object[]{user1Id, user2Id, cropId};
        } else {
            sql = "SELECT * FROM conversations WHERE user1_id = ? AND user2_id = ? LIMIT 1";
            params = new Object[]{user1Id, user2Id};
        }
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToConversation(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Create new conversation in Firestore and SQLite
     */
    private Conversation createNewConversation(int user1Id, int user2Id, Integer cropId) 
            throws Exception {
        
        Firestore db = firebaseService.getFirestore();
        String participantKey = user1Id + "_" + user2Id;
        
        // Get user names
        String user1Name = getUserName(user1Id);
        String user2Name = getUserName(user2Id);
        String cropName = cropId != null ? getCropName(cropId) : null;
        
        // Build Firestore document
        Map<String, Object> data = new HashMap<>();
        data.put("participantIds", Arrays.asList(user1Id, user2Id));
        data.put("participantKey", participantKey);
        data.put("user1Id", user1Id);
        data.put("user2Id", user2Id);
        data.put("user1Name", user1Name);
        data.put("user2Name", user2Name);
        data.put("cropId", cropId);
        data.put("cropName", cropName);
        data.put("lastMessage", null);
        data.put("lastMessageTime", null);
        data.put("lastSenderId", null);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("unreadCount", Map.of(
            String.valueOf(user1Id), 0,
            String.valueOf(user2Id), 0
        ));
        
        // Add to Firestore
        DocumentReference docRef = db.collection(FirebaseService.COLLECTION_CONVERSATIONS).document();
        docRef.set(data).get();
        
        // Create Conversation object
        Conversation conv = new Conversation();
        conv.setFirebaseId(docRef.getId());
        conv.setUser1Id(user1Id);
        conv.setUser2Id(user2Id);
        conv.setUser1Name(user1Name);
        conv.setUser2Name(user2Name);
        conv.setCropId(cropId);
        conv.setCropName(cropName);
        conv.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        conv.setSyncStatus("synced");
        
        // Save to SQLite
        saveConversationToSQLite(conv);
        
        return conv;
    }
    
    /**
     * Create local-only conversation (offline mode)
     */
    private Conversation createLocalConversation(int user1Id, int user2Id, Integer cropId) {
        String user1Name = getUserName(user1Id);
        String user2Name = getUserName(user2Id);
        String cropName = cropId != null ? getCropName(cropId) : null;
        
        Conversation conv = new Conversation();
        conv.setUser1Id(user1Id);
        conv.setUser2Id(user2Id);
        conv.setUser1Name(user1Name);
        conv.setUser2Name(user2Name);
        conv.setCropId(cropId);
        conv.setCropName(cropName);
        conv.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        conv.setSyncStatus("pending");
        
        saveConversationToSQLite(conv);
        
        return conv;
    }
    
    /**
     * Save conversation to SQLite
     */
    private void saveConversationToSQLite(Conversation conv) {
        String sql = """
            INSERT OR REPLACE INTO conversations 
            (firebase_id, user1_id, user2_id, crop_id, user1_name, user2_name, crop_name,
             last_message, last_message_time, last_sender_id, unread_count, 
             created_at, updated_at, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, conv.getFirebaseId());
            stmt.setInt(2, conv.getUser1Id());
            stmt.setInt(3, conv.getUser2Id());
            stmt.setObject(4, conv.getCropId());
            stmt.setString(5, conv.getUser1Name());
            stmt.setString(6, conv.getUser2Name());
            stmt.setString(7, conv.getCropName());
            stmt.setString(8, conv.getLastMessage());
            stmt.setTimestamp(9, conv.getLastMessageTime());
            stmt.setObject(10, conv.getLastSenderId());
            stmt.setInt(11, conv.getUnreadCount());
            stmt.setTimestamp(12, conv.getCreatedAt());
            stmt.setTimestamp(13, conv.getUpdatedAt());
            stmt.setString(14, conv.getSyncStatus());
            
            stmt.executeUpdate();
            
            // Get generated ID if new
            if (conv.getId() == 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    conv.setId(keys.getInt(1));
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // ==========================================
    // MESSAGE SENDING
    // ==========================================
    
    /**
     * Send a message (optimistic UI approach)
     * 
     * Flow:
     * 1. Save to SQLite immediately (status: 'sending')
     * 2. Show in UI instantly
     * 3. Send to Firestore in background
     * 4. Update status on success/failure
     * 
     * @param conversationId Local conversation ID
     * @param firebaseConvId Firebase conversation ID
     * @param senderId Sender's user ID
     * @param senderName Sender's name
     * @param text Message text
     * @param onSuccess Callback with sent message
     * @param onError Error callback
     */
    public void sendMessage(int conversationId, String firebaseConvId,
                           int senderId, String senderName, String text,
                           Consumer<ChatMessage> onSuccess, Consumer<Exception> onError) {
        
        executor.submit(() -> {
            try {
                // Create message object
                ChatMessage msg = new ChatMessage();
                msg.setConversationId(conversationId);
                msg.setSenderId(senderId);
                msg.setSenderName(senderName);
                msg.setText(text);
                msg.setType("text");
                msg.setStatus("sending");
                msg.setSyncStatus("pending");
                msg.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                
                // Step 1: Save to SQLite immediately (optimistic)
                saveMessageToSQLite(msg);
                
                // Notify UI immediately
                Platform.runLater(() -> onSuccess.accept(msg));
                
                // Step 2: Send to Firestore
                if (firebaseService.isInitialized() && firebaseConvId != null) {
                    sendToFirestore(firebaseConvId, msg);
                } else {
                    // Offline - mark for later sync
                    msg.setStatus("pending");
                    updateMessageStatus(msg.getId(), "pending", "pending");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }
    
    /**
     * Send message to Firestore
     */
    private void sendToFirestore(String firebaseConvId, ChatMessage msg) {
        try {
            Objects.requireNonNull(firebaseConvId, "firebaseConvId");
            Firestore db = firebaseService.getFirestore();
            
            // Build message document
            Map<String, Object> data = new HashMap<>();
            data.put("senderId", msg.getSenderId());
            data.put("senderName", msg.getSenderName());
            data.put("text", msg.getText());
            data.put("type", msg.getType());
            data.put("isRead", false);
            data.put("readAt", null);
            data.put("status", "sent");
            data.put("createdAt", FieldValue.serverTimestamp());
            
            // Add message to subcollection
            DocumentReference msgRef = db
                    .collection(FirebaseService.COLLECTION_CONVERSATIONS)
                    .document(firebaseConvId)
                    .collection("messages")
                    .document();
            
            msgRef.set(data).get();
            
            // Update SQLite with Firebase ID
            msg.setFirebaseId(msgRef.getId());
            msg.setStatus("sent");
            msg.setSyncStatus("synced");
            updateMessageInSQLite(msg);
            
            // Update conversation's last message
            updateConversationLastMessage(firebaseConvId, msg);
            
            // Notify status change
            Platform.runLater(() -> {
                if (onMessageStatusChanged != null) {
                    onMessageStatusChanged.accept(msg);
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            // Mark as failed
            msg.setStatus("failed");
            msg.setSyncStatus("error");
            updateMessageStatus(msg.getId(), "failed", "error");
            
            Platform.runLater(() -> {
                if (onMessageStatusChanged != null) {
                    onMessageStatusChanged.accept(msg);
                }
            });
        }
    }
    
    /**
     * Update conversation's last message in Firestore
     */
    private void updateConversationLastMessage(String firebaseConvId, ChatMessage msg) {
        try {
            Objects.requireNonNull(firebaseConvId, "firebaseConvId");
            Firestore db = firebaseService.getFirestore();
            
            String preview = msg.getText();
            if (preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastMessage", preview);
            updates.put("lastMessageTime", FieldValue.serverTimestamp());
            updates.put("lastSenderId", msg.getSenderId());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            
            db.collection(FirebaseService.COLLECTION_CONVERSATIONS)
                    .document(firebaseConvId)
                    .update(updates);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Save message to SQLite
     */
    private void saveMessageToSQLite(ChatMessage msg) {
        String sql = """
            INSERT INTO messages 
            (firebase_id, conversation_id, sender_id, sender_name, message_text, message_type,
             is_read, status, created_at, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, msg.getFirebaseId());
            stmt.setInt(2, msg.getConversationId());
            stmt.setInt(3, msg.getSenderId());
            stmt.setString(4, msg.getSenderName());
            stmt.setString(5, msg.getText());
            stmt.setString(6, msg.getType());
            stmt.setInt(7, msg.isRead() ? 1 : 0);
            stmt.setString(8, msg.getStatus());
            stmt.setTimestamp(9, msg.getCreatedAt());
            stmt.setString(10, msg.getSyncStatus());
            
            stmt.executeUpdate();
            
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                msg.setId(keys.getInt(1));
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Update message in SQLite
     */
    private void updateMessageInSQLite(ChatMessage msg) {
        String sql = """
            UPDATE messages SET 
            firebase_id = ?, status = ?, sync_status = ?, is_read = ?, read_at = ?
            WHERE id = ?
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, msg.getFirebaseId());
            stmt.setString(2, msg.getStatus());
            stmt.setString(3, msg.getSyncStatus());
            stmt.setInt(4, msg.isRead() ? 1 : 0);
            stmt.setTimestamp(5, msg.getReadAt());
            stmt.setInt(6, msg.getId());
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Update message status only
     */
    private void updateMessageStatus(int messageId, String status, String syncStatus) {
        String sql = "UPDATE messages SET status = ?, sync_status = ? WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            stmt.setString(2, syncStatus);
            stmt.setInt(3, messageId);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // ==========================================
    // REAL-TIME LISTENING
    // ==========================================
    
    /**
     * Start listening for real-time messages in a conversation
     * 
     * @param firebaseConvId Firebase conversation ID
     * @param localConvId Local SQLite conversation ID
     * @param currentUserId Current user ID (to filter own messages)
     */
    public void startListening(String firebaseConvId, int localConvId, int currentUserId) {
        if (!firebaseService.isInitialized() || firebaseConvId == null) {
            System.out.println("⚠️ Cannot start real-time listening - Firebase not available");
            return;
        }
        
        // Stop existing listener for this conversation
        stopListening(firebaseConvId);
        
        try {
            Firestore db = firebaseService.getFirestore();
            
            // Query messages ordered by time
            Query query = db
                    .collection(FirebaseService.COLLECTION_CONVERSATIONS)
                    .document(firebaseConvId)
                    .collection("messages")
                    .orderBy("createdAt", Query.Direction.ASCENDING);
            
            // Add snapshot listener
            ListenerRegistration listener = query.addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    System.err.println("❌ Listen error: " + error.getMessage());
                    Platform.runLater(() -> {
                        if (onError != null) {
                            onError.accept(error);
                        }
                    });
                    return;
                }
                
                if (snapshots == null) return;
                
                // Process document changes
                for (DocumentChange change : snapshots.getDocumentChanges()) {
                    switch (change.getType()) {
                        case ADDED:
                            handleNewMessage(change.getDocument(), localConvId, currentUserId);
                            break;
                        case MODIFIED:
                            handleMessageUpdate(change.getDocument(), localConvId);
                            break;
                        case REMOVED:
                            // Handle message deletion if needed
                            break;
                    }
                }
            });
            
            // Store listener reference
            activeListeners.put(firebaseConvId, listener);
            System.out.println("✅ Started listening for messages in: " + firebaseConvId);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Handle new message from Firestore
     */
    private void handleNewMessage(DocumentSnapshot doc, int localConvId, int currentUserId) {
        ChatMessage msg = mapFirestoreToMessage(doc);
        msg.setConversationId(localConvId);
        
        // Check if we already have this message (avoid duplicates)
        if (messageExistsInSQLite(msg.getFirebaseId())) {
            return;
        }
        
        // Save to SQLite
        msg.setSyncStatus("synced");
        saveMessageToSQLite(msg);
        
        // Notify UI if message is from other user
        if (msg.getSenderId() != currentUserId) {
            Platform.runLater(() -> {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(msg);
                }
            });
            
            // Auto-mark as read if chat is open
            markMessageAsRead(doc.getReference());
        }
    }
    
    /**
     * Handle message update from Firestore (e.g., read status)
     */
    private void handleMessageUpdate(DocumentSnapshot doc, int localConvId) {
        ChatMessage msg = mapFirestoreToMessage(doc);
        msg.setConversationId(localConvId);
        
        // Update in SQLite
        String sql = """
            UPDATE messages SET is_read = ?, read_at = ?, status = ?
            WHERE firebase_id = ?
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, msg.isRead() ? 1 : 0);
            stmt.setTimestamp(2, msg.getReadAt());
            stmt.setString(3, msg.getStatus());
            stmt.setString(4, msg.getFirebaseId());
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        // Notify UI
        Platform.runLater(() -> {
            if (onMessageStatusChanged != null) {
                onMessageStatusChanged.accept(msg);
            }
        });
    }
    
    /**
     * Check if message already exists in SQLite
     */
    private boolean messageExistsInSQLite(String firebaseId) {
        if (firebaseId == null) return false;
        
        String sql = "SELECT COUNT(*) FROM messages WHERE firebase_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, firebaseId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Mark message as read in Firestore
     */
    private void markMessageAsRead(DocumentReference msgRef) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("isRead", true);
        updates.put("readAt", FieldValue.serverTimestamp());
        updates.put("status", "read");
        
        msgRef.update(updates);
    }
    
    /**
     * Stop listening for messages in a conversation
     */
    public void stopListening(String firebaseConvId) {
        ListenerRegistration listener = activeListeners.remove(firebaseConvId);
        if (listener != null) {
            listener.remove();
            System.out.println("🛑 Stopped listening for: " + firebaseConvId);
        }
    }
    
    /**
     * Stop all active listeners
     */
    public void stopAllListeners() {
        for (ListenerRegistration listener : activeListeners.values()) {
            listener.remove();
        }
        activeListeners.clear();
        System.out.println("🛑 Stopped all message listeners");
    }
    
    // ==========================================
    // MESSAGE RETRIEVAL
    // ==========================================
    
    /**
     * Get messages for a conversation from SQLite
     */
    public List<ChatMessage> getMessages(int conversationId, int limit, int offset) {
        List<ChatMessage> messages = new ArrayList<>();
        
        String sql = """
            SELECT * FROM messages 
            WHERE conversation_id = ?
            ORDER BY created_at ASC
            LIMIT ? OFFSET ?
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, conversationId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                messages.add(mapResultSetToMessage(rs));
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return messages;
    }
    
    /**
     * Get all conversations for a user
     */
    public List<Conversation> getUserConversations(int userId) {
        List<Conversation> conversations = new ArrayList<>();
        
        String sql = """
            SELECT * FROM conversations 
            WHERE user1_id = ? OR user2_id = ?
            ORDER BY last_message_time DESC
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                conversations.add(mapResultSetToConversation(rs));
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return conversations;
    }
    
    // ==========================================
    // CALLBACKS
    // ==========================================
    
    public void setOnMessageReceived(Consumer<ChatMessage> callback) {
        this.onMessageReceived = callback;
    }
    
    public void setOnMessageStatusChanged(Consumer<ChatMessage> callback) {
        this.onMessageStatusChanged = callback;
    }
    
    public void setOnError(Consumer<Throwable> callback) {
        this.onError = callback;
    }
    
    // ==========================================
    // RETRY FAILED MESSAGES
    // ==========================================
    
    /**
     * Retry sending failed messages
     * Call this when app starts or network is restored
     */
    public void retryFailedMessages() {
        executor.submit(() -> {
            String sql = """
                SELECT m.*, c.firebase_id as conv_firebase_id 
                FROM messages m
                JOIN conversations c ON m.conversation_id = c.id
                WHERE m.sync_status = 'error' OR m.sync_status = 'pending'
                ORDER BY m.created_at ASC
                """;
            
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    ChatMessage msg = mapResultSetToMessage(rs);
                    String firebaseConvId = rs.getString("conv_firebase_id");
                    
                    if (firebaseConvId != null && firebaseService.isInitialized()) {
                        sendToFirestore(firebaseConvId, msg);
                    }
                }
                
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    
    // ==========================================
    // HELPER METHODS
    // ==========================================
    
    private String getUserName(int userId) {
        String sql = "SELECT name FROM users WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "User " + userId;
    }
    
    private String getCropName(int cropId) {
        String sql = "SELECT name FROM crops WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, cropId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private Conversation mapResultSetToConversation(ResultSet rs) throws SQLException {
        Conversation conv = new Conversation();
        conv.setId(rs.getInt("id"));
        conv.setFirebaseId(rs.getString("firebase_id"));
        conv.setUser1Id(rs.getInt("user1_id"));
        conv.setUser2Id(rs.getInt("user2_id"));
        conv.setCropId((Integer) rs.getObject("crop_id"));
        conv.setUser1Name(rs.getString("user1_name"));
        conv.setUser2Name(rs.getString("user2_name"));
        conv.setLastMessage(rs.getString("last_message"));
        conv.setLastMessageTime(rs.getTimestamp("last_message_time"));
        conv.setLastSenderId((Integer) rs.getObject("last_sender_id"));
        conv.setUnreadCount(rs.getInt("unread_count"));
        conv.setCreatedAt(rs.getTimestamp("created_at"));
        conv.setUpdatedAt(rs.getTimestamp("updated_at"));
        conv.setSyncStatus(rs.getString("sync_status"));
        return conv;
    }
    
    private Conversation mapFirestoreToConversation(DocumentSnapshot doc) {
        Conversation conv = new Conversation();
        conv.setFirebaseId(doc.getId());
        Long user1 = doc.getLong("user1Id");
        Long user2 = doc.getLong("user2Id");
        if (user1 == null || user2 == null) {
            throw new IllegalStateException("Firestore conversation missing user ids: " + doc.getId());
        }
        conv.setUser1Id(user1.intValue());
        conv.setUser2Id(user2.intValue());
        
        Long cropIdLong = doc.getLong("cropId");
        conv.setCropId(cropIdLong != null ? cropIdLong.intValue() : null);
        
        conv.setUser1Name(doc.getString("user1Name"));
        conv.setUser2Name(doc.getString("user2Name"));
        conv.setCropName(doc.getString("cropName"));
        conv.setLastMessage(doc.getString("lastMessage"));
        
        Long lastSenderId = doc.getLong("lastSenderId");
        conv.setLastSenderId(lastSenderId != null ? lastSenderId.intValue() : null);
        
        com.google.cloud.Timestamp lastMsgTs = doc.getTimestamp("lastMessageTime");
        if (lastMsgTs != null) {
            conv.setLastMessageTime(new Timestamp(lastMsgTs.toDate().getTime()));
        }
        
        com.google.cloud.Timestamp createdTs = doc.getTimestamp("createdAt");
        if (createdTs != null) {
            conv.setCreatedAt(new Timestamp(createdTs.toDate().getTime()));
        }
        
        conv.setSyncStatus("synced");
        return conv;
    }
    
    private ChatMessage mapResultSetToMessage(ResultSet rs) throws SQLException {
        ChatMessage msg = new ChatMessage();
        msg.setId(rs.getInt("id"));
        msg.setFirebaseId(rs.getString("firebase_id"));
        msg.setConversationId(rs.getInt("conversation_id"));
        msg.setSenderId(rs.getInt("sender_id"));
        msg.setSenderName(rs.getString("sender_name"));
        msg.setText(rs.getString("message_text"));
        msg.setType(rs.getString("message_type"));
        msg.setRead(rs.getInt("is_read") == 1);
        msg.setStatus(rs.getString("status"));
        msg.setCreatedAt(rs.getTimestamp("created_at"));
        msg.setSyncStatus(rs.getString("sync_status"));
        return msg;
    }
    
    private ChatMessage mapFirestoreToMessage(DocumentSnapshot doc) {
        ChatMessage msg = new ChatMessage();
        msg.setFirebaseId(doc.getId());
        Long senderId = doc.getLong("senderId");
        if (senderId == null) {
            throw new IllegalStateException("Firestore message missing senderId: " + doc.getId());
        }
        msg.setSenderId(senderId.intValue());
        msg.setSenderName(doc.getString("senderName"));
        msg.setText(doc.getString("text"));
        msg.setType(doc.getString("type"));
        msg.setRead(Boolean.TRUE.equals(doc.getBoolean("isRead")));
        msg.setStatus(doc.getString("status"));
        
        com.google.cloud.Timestamp ts = doc.getTimestamp("createdAt");
        if (ts != null) {
            msg.setCreatedAt(new Timestamp(ts.toDate().getTime()));
        }
        
        com.google.cloud.Timestamp readTs = doc.getTimestamp("readAt");
        if (readTs != null) {
            msg.setReadAt(new Timestamp(readTs.toDate().getTime()));
        }
        
        msg.setSyncStatus("synced");
        return msg;
    }
    
    /**
     * Shutdown service
     */
    public void shutdown() {
        stopAllListeners();
        executor.shutdown();
    }
    
    // ==========================================
    // INNER CLASSES - DATA MODELS
    // ==========================================
    
    /**
     * Conversation data model
     */
    public static class Conversation {
        private int id;
        private String firebaseId;
        private int user1Id;
        private int user2Id;
        private Integer cropId;
        private String user1Name;
        private String user2Name;
        private String cropName;
        private String lastMessage;
        private Timestamp lastMessageTime;
        private Integer lastSenderId;
        private int unreadCount;
        private Timestamp createdAt;
        private Timestamp updatedAt;
        private String syncStatus;
        
        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        
        public String getFirebaseId() { return firebaseId; }
        public void setFirebaseId(String firebaseId) { this.firebaseId = firebaseId; }
        
        public int getUser1Id() { return user1Id; }
        public void setUser1Id(int user1Id) { this.user1Id = user1Id; }
        
        public int getUser2Id() { return user2Id; }
        public void setUser2Id(int user2Id) { this.user2Id = user2Id; }
        
        public Integer getCropId() { return cropId; }
        public void setCropId(Integer cropId) { this.cropId = cropId; }
        
        public String getUser1Name() { return user1Name; }
        public void setUser1Name(String user1Name) { this.user1Name = user1Name; }
        
        public String getUser2Name() { return user2Name; }
        public void setUser2Name(String user2Name) { this.user2Name = user2Name; }
        
        public String getCropName() { return cropName; }
        public void setCropName(String cropName) { this.cropName = cropName; }
        
        public String getLastMessage() { return lastMessage; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
        
        public Timestamp getLastMessageTime() { return lastMessageTime; }
        public void setLastMessageTime(Timestamp lastMessageTime) { this.lastMessageTime = lastMessageTime; }
        
        public Integer getLastSenderId() { return lastSenderId; }
        public void setLastSenderId(Integer lastSenderId) { this.lastSenderId = lastSenderId; }
        
        public int getUnreadCount() { return unreadCount; }
        public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
        
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
        
        public Timestamp getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
        
        public String getSyncStatus() { return syncStatus; }
        public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
        
        public String getParticipantKey() {
            return Math.min(user1Id, user2Id) + "_" + Math.max(user1Id, user2Id);
        }
        
        public String getOtherUserName(int currentUserId) {
            return currentUserId == user1Id ? user2Name : user1Name;
        }
        
        public int getOtherUserId(int currentUserId) {
            return currentUserId == user1Id ? user2Id : user1Id;
        }
    }
    
    /**
     * Chat message data model
     */
    public static class ChatMessage {
        private int id;
        private String firebaseId;
        private int conversationId;
        private int senderId;
        private String senderName;
        private String text;
        private String type;
        private String attachmentUrl;
        private Integer cropReferenceId;
        private boolean isRead;
        private Timestamp readAt;
        private String status;
        private Timestamp createdAt;
        private String syncStatus;
        
        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        
        public String getFirebaseId() { return firebaseId; }
        public void setFirebaseId(String firebaseId) { this.firebaseId = firebaseId; }
        
        public int getConversationId() { return conversationId; }
        public void setConversationId(int conversationId) { this.conversationId = conversationId; }
        
        public int getSenderId() { return senderId; }
        public void setSenderId(int senderId) { this.senderId = senderId; }
        
        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getAttachmentUrl() { return attachmentUrl; }
        public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
        
        public Integer getCropReferenceId() { return cropReferenceId; }
        public void setCropReferenceId(Integer cropReferenceId) { this.cropReferenceId = cropReferenceId; }
        
        public boolean isRead() { return isRead; }
        public void setRead(boolean read) { isRead = read; }
        
        public Timestamp getReadAt() { return readAt; }
        public void setReadAt(Timestamp readAt) { this.readAt = readAt; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
        
        public String getSyncStatus() { return syncStatus; }
        public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
        
        public boolean isFromMe(int currentUserId) {
            return senderId == currentUserId;
        }
    }
}
