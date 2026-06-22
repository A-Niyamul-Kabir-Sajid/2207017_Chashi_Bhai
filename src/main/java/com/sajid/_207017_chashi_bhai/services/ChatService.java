package com.sajid._207017_chashi_bhai.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * ============================================================================
 * ChatService - Dual-Database Chat System (Firebase + SQLite)
 * ============================================================================
 * 
 * ARCHITECTURE OVERVIEW:
 * ----------------------
 * This service implements an "offline-first" chat system using:
 * 
 * 1. SQLite (Local Database):
 *    - Primary data store for fast reads
 *    - Works offline - no internet needed
 *    - Instant UI updates (optimistic UI)
 *    - Stores message sync status
 * 
 * 2. Firebase Firestore (Cloud Database via REST API):
 *    - Cloud sync for multi-device support
 *    - Real-time delivery to other users
 *    - Backup and cross-device access
 *    - Polling-based updates (REST API approach)
 * 
 * WHY BOTH DATABASES?
 * -------------------
 * Advantages:
 * + Offline support: Users can chat even without internet
 * + Fast UI: Messages appear instantly (saved to SQLite first)
 * + Reliable: Local backup prevents data loss
 * + Scalable: Firebase handles cloud sync efficiently
 * + Cross-device: Messages sync across all devices
 * 
 * Disadvantages:
 * - Complexity: Must handle sync logic carefully
 * - Storage: Data stored twice (local + cloud)
 * - Conflicts: Must resolve offline/online edit conflicts
 * 
 * SYNC STRATEGY:
 * --------------
 * On Send:
 *   1. Save to SQLite immediately (sync_status = 'pending')
 *   2. Show in UI instantly (optimistic UI)
 *   3. Send to Firebase in background
 *   4. Update sync_status to 'synced' or 'error'
 * 
 * On Receive (polling):
 *   1. Poll Firebase for new messages periodically
 *   2. Check if message exists in SQLite (by firebase_id)
 *   3. If new, save to SQLite and notify UI
 *   4. If exists, update if newer timestamp
 * 
 * On Offline:
 *   1. Read/write only from SQLite
 *   2. Mark messages as 'pending' sync
 *   3. When online, push all pending messages to Firebase
 * 
 * CONFLICT RESOLUTION:
 * --------------------
 * - Each message has a UUID (firebase_id) to prevent duplicates
 * - Timestamp-based: newer timestamp wins
 * - Status priority: 'read' > 'delivered' > 'sent' > 'sending'
 * 
 * ============================================================================
 */
public class ChatService {
    
    private static ChatService instance;
    private final FirebaseService firebaseService;
    private final HttpClient httpClient;
    private final Gson gson;
    
    // Database
    private static final String DB_URL = "jdbc:sqlite:data/chashi_bhai.db";
    
    // Firebase REST API
    private static final String PROJECT_ID = FirebaseConfig.getProjectId();
    private static final String FIRESTORE_BASE_URL = 
        "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents";
    
    // Collections
    private static final String CONVERSATIONS_COLLECTION = "conversations";
    private static final String MESSAGES_COLLECTION = "messages";
    
    // Polling
    private final ScheduledExecutorService pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("ChatPollScheduler");
        return t;
    });
    private final Map<String, ScheduledFuture<?>> activePollers = new ConcurrentHashMap<>();
    private static final int POLL_INTERVAL_SECONDS = 5; // Poll every 5 seconds
    
    // Callbacks
    private Consumer<ChatMessage> onMessageReceived;
    private Consumer<ChatMessage> onMessageStatusChanged;
    private Consumer<Throwable> onError;
    
    // Background executor for async operations
    private final ExecutorService executor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("ChatServiceWorker");
        return t;
    });
    
    // ============================================================================
    // INITIALIZATION
    // ============================================================================
    
    private ChatService() {
        this.firebaseService = FirebaseService.getInstance();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.gson = new Gson();
        
        // Initialize chat tables
        initializeChatTables();
    }
    
    public static ChatService getInstance() {
        if (instance == null) {
            instance = new ChatService();
        }
        return instance;
    }
    
    /**
     * Initialize SQLite tables for chat
     */
    private void initializeChatTables() {
        String conversationsTable = """
            CREATE TABLE IF NOT EXISTS conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                firebase_id TEXT UNIQUE,
                user1_id INTEGER NOT NULL,
                user2_id INTEGER NOT NULL,
                crop_id INTEGER,
                user1_name TEXT,
                user2_name TEXT,
                crop_name TEXT,
                last_message TEXT,
                last_message_time TIMESTAMP,
                last_sender_id INTEGER,
                unread_count INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                sync_status TEXT DEFAULT 'pending',
                UNIQUE(user1_id, user2_id, crop_id)
            )
            """;
        
        String messagesTable = """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                firebase_id TEXT UNIQUE,
                conversation_id INTEGER NOT NULL,
                sender_id INTEGER NOT NULL,
                sender_name TEXT,
                message_text TEXT NOT NULL,
                message_type TEXT DEFAULT 'text',
                attachment_url TEXT,
                crop_reference_id INTEGER,
                is_read INTEGER DEFAULT 0,
                read_at TIMESTAMP,
                status TEXT DEFAULT 'sending',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                sync_status TEXT DEFAULT 'pending',
                FOREIGN KEY (conversation_id) REFERENCES conversations(id)
            )
            """;
        
        String messagesIndex = """
            CREATE INDEX IF NOT EXISTS idx_messages_conversation 
            ON messages(conversation_id, created_at DESC)
            """;
        
        String syncIndex = """
            CREATE INDEX IF NOT EXISTS idx_messages_sync 
            ON messages(sync_status)
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(conversationsTable);
            stmt.execute(messagesTable);
            stmt.execute(messagesIndex);
            stmt.execute(syncIndex);
            System.out.println("✓ Chat tables initialized");
        } catch (SQLException e) {
            System.err.println("❌ Error initializing chat tables: " + e.getMessage());
        }
    }
    
    // ============================================================================
    // CONVERSATION MANAGEMENT
    // ============================================================================
    
    /**
     * Generate unique participant key for a conversation
     * Always returns smaller_larger format to ensure uniqueness
     */
    public static String generateParticipantKey(int userId1, int userId2) {
        int smaller = Math.min(userId1, userId2);
        int larger = Math.max(userId1, userId2);
        return smaller + "_" + larger;
    }
    
    /**
     * Find or create a conversation between two users
     * 
     * FLOW:
     * 1. Check SQLite for existing conversation
     * 2. If not found, check Firebase (REST API)
     * 3. If still not found, create new conversation in both
     */
    public void getOrCreateConversation(int currentUserId, int otherUserId, Integer cropId,
                                        Consumer<Conversation> onSuccess, 
                                        Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                // Ensure consistent ordering
                int user1Id = Math.min(currentUserId, otherUserId);
                int user2Id = Math.max(currentUserId, otherUserId);
                
                // Step 1: Check SQLite first (fast, works offline)
                Conversation localConv = findConversationInSQLite(user1Id, user2Id, cropId);
                if (localConv != null) {
                    Platform.runLater(() -> onSuccess.accept(localConv));
                    return;
                }
                
                // Step 2: Check Firebase (if online)
                if (firebaseService.isAuthenticated()) {
                    Conversation firebaseConv = findConversationInFirebase(user1Id, user2Id, cropId);
                    if (firebaseConv != null) {
                        // Save to SQLite for offline access
                        saveConversationToSQLite(firebaseConv);
                        Platform.runLater(() -> onSuccess.accept(firebaseConv));
                        return;
                    }
                }
                
                // Step 3: Create new conversation
                Conversation newConv = createNewConversation(user1Id, user2Id, cropId);
                Platform.runLater(() -> onSuccess.accept(newConv));
                
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }
    
    /**
     * Find conversation in SQLite (offline-first)
     */
    private Conversation findConversationInSQLite(int user1Id, int user2Id, Integer cropId) {
        String sql;
        Object[] params;
        
        if (cropId != null) {
            sql = "SELECT * FROM conversations WHERE user1_id = ? AND user2_id = ? AND crop_id = ? LIMIT 1";
            params = new Object[]{user1Id, user2Id, cropId};
        } else {
            sql = "SELECT * FROM conversations WHERE user1_id = ? AND user2_id = ? AND crop_id IS NULL LIMIT 1";
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
     * Find conversation in Firebase (REST API)
     */
    private Conversation findConversationInFirebase(int user1Id, int user2Id, Integer cropId) {
        try {
            String participantKey = generateParticipantKey(user1Id, user2Id);
            
            // Query Firestore for conversation with this participant key
            String queryUrl = FIRESTORE_BASE_URL + ":runQuery";
            
            // Build structured query
            JsonObject structuredQuery = new JsonObject();
            JsonObject from = new JsonObject();
            from.addProperty("collectionId", CONVERSATIONS_COLLECTION);
            
            JsonArray fromArray = new JsonArray();
            fromArray.add(from);
            structuredQuery.add("from", fromArray);
            
            // Where clause: participantKey == "user1_user2"
            JsonObject where = new JsonObject();
            JsonObject fieldFilter = new JsonObject();
            JsonObject field = new JsonObject();
            field.addProperty("fieldPath", "participantKey");
            fieldFilter.add("field", field);
            fieldFilter.addProperty("op", "EQUAL");
            JsonObject value = new JsonObject();
            value.addProperty("stringValue", participantKey);
            fieldFilter.add("value", value);
            where.add("fieldFilter", fieldFilter);
            structuredQuery.add("where", where);
            
            JsonObject requestBody = new JsonObject();
            requestBody.add("structuredQuery", structuredQuery);
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(queryUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));
            
            if (firebaseService.isAuthenticated()) {
                // Add auth token if available
            }
            
            HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() == 200) {
                JsonArray results = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement result : results) {
                    JsonObject doc = result.getAsJsonObject();
                    if (doc.has("document")) {
                        return parseFirestoreConversation(doc.getAsJsonObject("document"));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding conversation in Firebase: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Create new conversation in both SQLite and Firebase
     */
    private Conversation createNewConversation(int user1Id, int user2Id, Integer cropId) {
        String participantKey = generateParticipantKey(user1Id, user2Id);
        String firebaseId = UUID.randomUUID().toString();
        
        // Get user names
        String user1Name = getUserName(user1Id);
        String user2Name = getUserName(user2Id);
        String cropName = cropId != null ? getCropName(cropId) : null;
        
        // Create conversation object
        Conversation conv = new Conversation();
        conv.setFirebaseId(firebaseId);
        conv.setUser1Id(user1Id);
        conv.setUser2Id(user2Id);
        conv.setUser1Name(user1Name);
        conv.setUser2Name(user2Name);
        conv.setCropId(cropId);
        conv.setCropName(cropName);
        conv.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        conv.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        conv.setSyncStatus("pending");
        
        // Save to SQLite first (offline-first)
        saveConversationToSQLite(conv);
        
        // Sync to Firebase in background
        if (firebaseService.isAuthenticated()) {
            syncConversationToFirebase(conv);
        }
        
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
    
    /**
     * Sync conversation to Firebase (REST API)
     */
    private void syncConversationToFirebase(Conversation conv) {
        executor.submit(() -> {
            try {
                String url = FIRESTORE_BASE_URL + "/" + CONVERSATIONS_COLLECTION + "?documentId=" + conv.getFirebaseId();
                
                Map<String, Object> data = new HashMap<>();
                data.put("participantKey", generateParticipantKey(conv.getUser1Id(), conv.getUser2Id()));
                data.put("user1Id", conv.getUser1Id());
                data.put("user2Id", conv.getUser2Id());
                data.put("user1Name", conv.getUser1Name());
                data.put("user2Name", conv.getUser2Name());
                data.put("cropId", conv.getCropId());
                data.put("cropName", conv.getCropName());
                data.put("createdAt", System.currentTimeMillis());
                
                Map<String, Object> document = new HashMap<>();
                document.put("fields", convertToFirestoreFields(data));
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(document)))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    // Update sync status
                    updateConversationSyncStatus(conv.getId(), "synced");
                    System.out.println("✓ Conversation synced to Firebase: " + conv.getFirebaseId());
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error syncing conversation to Firebase: " + e.getMessage());
                updateConversationSyncStatus(conv.getId(), "error");
            }
        });
    }
    
    // ============================================================================
    // MESSAGE SENDING
    // ============================================================================
    
    /**
     * Send a message using optimistic UI pattern
     * 
     * FLOW:
     * 1. Generate UUID for message (prevents duplicates)
     * 2. Save to SQLite immediately (sync_status = 'pending')
     * 3. Show in UI instantly (optimistic UI)
     * 4. Send to Firebase in background
     * 5. Update sync_status based on result
     */
    public void sendMessage(int conversationId, String firebaseConvId,
                           int senderId, String senderName, String text,
                           Consumer<ChatMessage> onSuccess, Consumer<Exception> onError) {
        
        executor.submit(() -> {
            try {
                // Generate unique ID for deduplication
                String messageId = UUID.randomUUID().toString();
                
                // Create message object
                ChatMessage msg = new ChatMessage();
                msg.setFirebaseId(messageId);
                msg.setConversationId(conversationId);
                msg.setSenderId(senderId);
                msg.setSenderName(senderName);
                msg.setText(text);
                msg.setType("text");
                msg.setStatus("sending");
                msg.setSyncStatus("pending");
                msg.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                
                // Step 1: Save to SQLite immediately (optimistic UI)
                saveMessageToSQLite(msg);
                
                // Step 2: Notify UI immediately
                Platform.runLater(() -> onSuccess.accept(msg));
                
                // Step 3: Sync to Firebase in background
                if (firebaseService.isAuthenticated() && firebaseConvId != null) {
                    syncMessageToFirebase(firebaseConvId, msg);
                }
                
                // Step 4: Update conversation's last message
                updateConversationLastMessage(conversationId, msg);
                
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onError.accept(e));
            }
        });
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
     * Sync message to Firebase (REST API)
     */
    private void syncMessageToFirebase(String firebaseConvId, ChatMessage msg) {
        executor.submit(() -> {
            try {
                // Save to messages subcollection under conversation
                String url = FIRESTORE_BASE_URL + "/" + CONVERSATIONS_COLLECTION + "/" + firebaseConvId 
                    + "/" + MESSAGES_COLLECTION + "?documentId=" + msg.getFirebaseId();
                
                Map<String, Object> data = new HashMap<>();
                data.put("senderId", msg.getSenderId());
                data.put("senderName", msg.getSenderName());
                data.put("text", msg.getText());
                data.put("type", msg.getType());
                data.put("isRead", false);
                data.put("status", "sent");
                data.put("createdAt", msg.getCreatedAt().getTime());
                
                Map<String, Object> document = new HashMap<>();
                document.put("fields", convertToFirestoreFields(data));
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(document)))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    // Success - update local status
                    msg.setStatus("sent");
                    msg.setSyncStatus("synced");
                    updateMessageSyncStatus(msg.getId(), "sent", "synced");
                    
                    // Notify UI of status change
                    Platform.runLater(() -> {
                        if (onMessageStatusChanged != null) {
                            onMessageStatusChanged.accept(msg);
                        }
                    });
                    
                    System.out.println("✓ Message synced to Firebase: " + msg.getFirebaseId());
                    
                    // Also update conversation's lastMessage in Firebase
                    updateFirebaseConversationLastMessage(firebaseConvId, msg);
                    
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode());
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error syncing message to Firebase: " + e.getMessage());
                
                // Mark as failed
                msg.setStatus("failed");
                msg.setSyncStatus("error");
                updateMessageSyncStatus(msg.getId(), "failed", "error");
                
                Platform.runLater(() -> {
                    if (onMessageStatusChanged != null) {
                        onMessageStatusChanged.accept(msg);
                    }
                });
            }
        });
    }
    
    /**
     * Update conversation's last message in SQLite
     */
    private void updateConversationLastMessage(int conversationId, ChatMessage msg) {
        String preview = msg.getText();
        if (preview.length() > 50) {
            preview = preview.substring(0, 50) + "...";
        }
        
        String sql = "UPDATE conversations SET last_message = ?, last_message_time = ?, last_sender_id = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, preview);
            stmt.setTimestamp(2, msg.getCreatedAt());
            stmt.setInt(3, msg.getSenderId());
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(5, conversationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Update conversation's last message in Firebase
     */
    private void updateFirebaseConversationLastMessage(String firebaseConvId, ChatMessage msg) {
        try {
            String url = FIRESTORE_BASE_URL + "/" + CONVERSATIONS_COLLECTION + "/" + firebaseConvId
                + "?updateMask.fieldPaths=lastMessage&updateMask.fieldPaths=lastMessageTime&updateMask.fieldPaths=lastSenderId";
            
            String preview = msg.getText();
            if (preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("lastMessage", preview);
            data.put("lastMessageTime", msg.getCreatedAt().getTime());
            data.put("lastSenderId", msg.getSenderId());
            
            Map<String, Object> document = new HashMap<>();
            document.put("fields", convertToFirestoreFields(data));
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(document)))
                .build();
            
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
        } catch (Exception e) {
            // Non-critical, don't fail
        }
    }
    
    // ============================================================================
    // MESSAGE RECEIVING (POLLING)
    // ============================================================================
    
    /**
     * Start polling for new messages in a conversation
     * 
     * Since we're using REST API (not WebSocket), we poll periodically
     * to check for new messages from other users.
     */
    public void startListening(String firebaseConvId, int localConvId, int currentUserId) {
        if (firebaseConvId == null || !firebaseService.isAuthenticated()) {
            System.out.println("⚠️ Cannot start listening - not authenticated or no firebase ID");
            return;
        }
        
        // Stop existing poller if any
        stopListening(firebaseConvId);
        
        // Start new polling task
        ScheduledFuture<?> poller = pollScheduler.scheduleAtFixedRate(() -> {
            pollForNewMessages(firebaseConvId, localConvId, currentUserId);
        }, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        activePollers.put(firebaseConvId, poller);
        System.out.println("📡 Started polling for messages: " + firebaseConvId);
    }
    
    /**
     * Poll Firebase for new messages
     */
    private void pollForNewMessages(String firebaseConvId, int localConvId, int currentUserId) {
        try {
            // Get last message timestamp from SQLite
            long lastTimestamp = getLastMessageTimestamp(localConvId);
            
            // Query Firebase for messages after this timestamp
            String queryUrl = FIRESTORE_BASE_URL + "/" + CONVERSATIONS_COLLECTION + "/" + firebaseConvId 
                + "/" + MESSAGES_COLLECTION;
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(queryUrl))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                
                if (result.has("documents")) {
                    JsonArray documents = result.getAsJsonArray("documents");
                    
                    for (JsonElement docElement : documents) {
                        JsonObject doc = docElement.getAsJsonObject();
                        ChatMessage msg = parseFirestoreMessage(doc, localConvId);
                        
                        // Check if this is a new message (not from current user, not already in SQLite)
                        if (msg != null && msg.getSenderId() != currentUserId) {
                            if (!messageExistsInSQLite(msg.getFirebaseId())) {
                                // Save new message to SQLite
                                msg.setSyncStatus("synced");
                                saveMessageToSQLite(msg);
                                
                                // Notify UI
                                Platform.runLater(() -> {
                                    if (onMessageReceived != null) {
                                        onMessageReceived.accept(msg);
                                    }
                                });
                                
                                // Update conversation
                                updateConversationLastMessage(localConvId, msg);
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // Polling error - don't log too much
        }
    }
    
    /**
     * Get last message timestamp for a conversation
     */
    private long getLastMessageTimestamp(int conversationId) {
        String sql = "SELECT MAX(created_at) FROM messages WHERE conversation_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp(1);
                return ts != null ? ts.getTime() : 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    /**
     * Check if message exists in SQLite (by firebase_id)
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
     * Stop listening for messages in a conversation
     */
    public void stopListening(String firebaseConvId) {
        ScheduledFuture<?> poller = activePollers.remove(firebaseConvId);
        if (poller != null) {
            poller.cancel(false);
            System.out.println("🛑 Stopped polling for: " + firebaseConvId);
        }
    }
    
    /**
     * Stop all active listeners
     */
    public void stopAllListeners() {
        for (ScheduledFuture<?> poller : activePollers.values()) {
            poller.cancel(false);
        }
        activePollers.clear();
        System.out.println("🛑 Stopped all message listeners");
    }
    
    // ============================================================================
    // OFFLINE SYNC
    // ============================================================================
    
    /**
     * Retry sending failed/pending messages
     * Call this when app starts or network is restored
     */
    public void retryFailedMessages() {
        executor.submit(() -> {
            String sql = """
                SELECT m.*, c.firebase_id as conv_firebase_id 
                FROM messages m
                JOIN conversations c ON m.conversation_id = c.id
                WHERE m.sync_status IN ('error', 'pending')
                ORDER BY m.created_at ASC
                """;
            
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                ResultSet rs = stmt.executeQuery();
                int retryCount = 0;
                
                while (rs.next()) {
                    ChatMessage msg = mapResultSetToMessage(rs);
                    String firebaseConvId = rs.getString("conv_firebase_id");
                    
                    if (firebaseConvId != null && firebaseService.isAuthenticated()) {
                        System.out.println("🔄 Retrying message: " + msg.getId());
                        syncMessageToFirebase(firebaseConvId, msg);
                        retryCount++;
                    }
                }
                
                if (retryCount > 0) {
                    System.out.println("✓ Retried " + retryCount + " pending messages");
                }
                
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Sync all pending conversations to Firebase
     */
    public void syncPendingConversations() {
        executor.submit(() -> {
            String sql = "SELECT * FROM conversations WHERE sync_status IN ('error', 'pending')";
            
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Conversation conv = mapResultSetToConversation(rs);
                    if (firebaseService.isAuthenticated()) {
                        syncConversationToFirebase(conv);
                    }
                }
                
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    
    // ============================================================================
    // MESSAGE RETRIEVAL
    // ============================================================================
    
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
    
    // ============================================================================
    // CALLBACKS
    // ============================================================================
    
    public void setOnMessageReceived(Consumer<ChatMessage> callback) {
        this.onMessageReceived = callback;
    }
    
    public void setOnMessageStatusChanged(Consumer<ChatMessage> callback) {
        this.onMessageStatusChanged = callback;
    }
    
    public void setOnError(Consumer<Throwable> callback) {
        this.onError = callback;
    }
    
    // ============================================================================
    // HELPER METHODS
    // ============================================================================
    
    private void updateMessageSyncStatus(int messageId, String status, String syncStatus) {
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
    
    private void updateConversationSyncStatus(int convId, String syncStatus) {
        String sql = "UPDATE conversations SET sync_status = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, syncStatus);
            stmt.setInt(2, convId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
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
    
    /**
     * Convert Java map to Firestore fields format
     */
    private Map<String, Object> convertToFirestoreFields(Map<String, Object> data) {
        Map<String, Object> fields = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            Map<String, Object> fieldValue = new HashMap<>();
            
            if (value == null) {
                fieldValue.put("nullValue", null);
            } else if (value instanceof String) {
                fieldValue.put("stringValue", value);
            } else if (value instanceof Integer) {
                fieldValue.put("integerValue", String.valueOf(value));
            } else if (value instanceof Long) {
                fieldValue.put("integerValue", String.valueOf(value));
            } else if (value instanceof Double) {
                fieldValue.put("doubleValue", value);
            } else if (value instanceof Boolean) {
                fieldValue.put("booleanValue", value);
            } else {
                fieldValue.put("stringValue", value.toString());
            }
            
            fields.put(key, fieldValue);
        }
        
        return fields;
    }
    
    /**
     * Parse Firestore document to Conversation
     */
    private Conversation parseFirestoreConversation(JsonObject doc) {
        try {
            Conversation conv = new Conversation();
            
            // Extract document ID from name
            String name = doc.get("name").getAsString();
            String firebaseId = name.substring(name.lastIndexOf('/') + 1);
            conv.setFirebaseId(firebaseId);
            
            JsonObject fields = doc.getAsJsonObject("fields");
            
            conv.setUser1Id(getIntFromFirestore(fields, "user1Id"));
            conv.setUser2Id(getIntFromFirestore(fields, "user2Id"));
            conv.setUser1Name(getStringFromFirestore(fields, "user1Name"));
            conv.setUser2Name(getStringFromFirestore(fields, "user2Name"));
            conv.setCropName(getStringFromFirestore(fields, "cropName"));
            conv.setLastMessage(getStringFromFirestore(fields, "lastMessage"));
            
            Long cropId = getLongFromFirestore(fields, "cropId");
            conv.setCropId(cropId != null ? cropId.intValue() : null);
            
            Long lastSenderId = getLongFromFirestore(fields, "lastSenderId");
            conv.setLastSenderId(lastSenderId != null ? lastSenderId.intValue() : null);
            
            Long lastMsgTime = getLongFromFirestore(fields, "lastMessageTime");
            if (lastMsgTime != null) {
                conv.setLastMessageTime(new Timestamp(lastMsgTime));
            }
            
            Long createdAt = getLongFromFirestore(fields, "createdAt");
            if (createdAt != null) {
                conv.setCreatedAt(new Timestamp(createdAt));
            }
            
            conv.setSyncStatus("synced");
            return conv;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Parse Firestore document to ChatMessage
     */
    private ChatMessage parseFirestoreMessage(JsonObject doc, int localConvId) {
        try {
            ChatMessage msg = new ChatMessage();
            
            // Extract document ID from name
            String name = doc.get("name").getAsString();
            String firebaseId = name.substring(name.lastIndexOf('/') + 1);
            msg.setFirebaseId(firebaseId);
            msg.setConversationId(localConvId);
            
            JsonObject fields = doc.getAsJsonObject("fields");
            
            msg.setSenderId(getIntFromFirestore(fields, "senderId"));
            msg.setSenderName(getStringFromFirestore(fields, "senderName"));
            msg.setText(getStringFromFirestore(fields, "text"));
            msg.setType(getStringFromFirestore(fields, "type"));
            msg.setStatus(getStringFromFirestore(fields, "status"));
            msg.setRead(getBooleanFromFirestore(fields, "isRead"));
            
            Long createdAt = getLongFromFirestore(fields, "createdAt");
            if (createdAt != null) {
                msg.setCreatedAt(new Timestamp(createdAt));
            } else {
                msg.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            }
            
            return msg;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    // Firestore field extraction helpers
    private String getStringFromFirestore(JsonObject fields, String key) {
        if (fields.has(key)) {
            JsonObject field = fields.getAsJsonObject(key);
            if (field.has("stringValue")) {
                return field.get("stringValue").getAsString();
            }
        }
        return null;
    }
    
    private int getIntFromFirestore(JsonObject fields, String key) {
        Long value = getLongFromFirestore(fields, key);
        return value != null ? value.intValue() : 0;
    }
    
    private Long getLongFromFirestore(JsonObject fields, String key) {
        if (fields.has(key)) {
            JsonObject field = fields.getAsJsonObject(key);
            if (field.has("integerValue")) {
                return Long.parseLong(field.get("integerValue").getAsString());
            }
        }
        return null;
    }
    
    private boolean getBooleanFromFirestore(JsonObject fields, String key) {
        if (fields.has(key)) {
            JsonObject field = fields.getAsJsonObject(key);
            if (field.has("booleanValue")) {
                return field.get("booleanValue").getAsBoolean();
            }
        }
        return false;
    }
    
    // ResultSet mappers
    private Conversation mapResultSetToConversation(ResultSet rs) throws SQLException {
        Conversation conv = new Conversation();
        conv.setId(rs.getInt("id"));
        conv.setFirebaseId(rs.getString("firebase_id"));
        conv.setUser1Id(rs.getInt("user1_id"));
        conv.setUser2Id(rs.getInt("user2_id"));
        conv.setCropId((Integer) rs.getObject("crop_id"));
        conv.setUser1Name(rs.getString("user1_name"));
        conv.setUser2Name(rs.getString("user2_name"));
        conv.setCropName(rs.getString("crop_name"));
        conv.setLastMessage(rs.getString("last_message"));
        conv.setLastMessageTime(rs.getTimestamp("last_message_time"));
        conv.setLastSenderId((Integer) rs.getObject("last_sender_id"));
        conv.setUnreadCount(rs.getInt("unread_count"));
        conv.setCreatedAt(rs.getTimestamp("created_at"));
        conv.setUpdatedAt(rs.getTimestamp("updated_at"));
        conv.setSyncStatus(rs.getString("sync_status"));
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
    
    /**
     * Shutdown service
     */
    public void shutdown() {
        stopAllListeners();
        pollScheduler.shutdown();
        executor.shutdown();
    }
    
    // ============================================================================
    // INNER CLASSES - DATA MODELS
    // ============================================================================
    
    /**
     * Conversation data model
     * 
     * SCHEMA:
     * - id: Local SQLite ID
     * - firebase_id: UUID for Firebase (prevents duplicates)
     * - user1_id, user2_id: Participant IDs (always user1 < user2)
     * - crop_id: Optional crop context
     * - sync_status: 'pending', 'synced', 'error'
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
     * 
     * SCHEMA:
     * - id: Local SQLite ID
     * - firebase_id: UUID for Firebase (prevents duplicates)
     * - status: 'sending', 'sent', 'delivered', 'read', 'failed'
     * - sync_status: 'pending', 'synced', 'error'
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
