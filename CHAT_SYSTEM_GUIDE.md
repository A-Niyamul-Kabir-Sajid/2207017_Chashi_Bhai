# Real-Time Chat System Implementation Guide
## Firebase Firestore + SQLite Hybrid Architecture

---

## Table of Contents
1. [Architecture Overview](#1-architecture-overview)
2. [Database Schema Design](#2-database-schema-design)
3. [Message Flow](#3-message-flow)
4. [Data Storage & Retrieval](#4-data-storage--retrieval)
5. [Real-Time Updates Implementation](#5-real-time-updates-implementation)
6. [Code Implementation](#6-code-implementation)

---

## 1. Architecture Overview

### 1.1 System Components

```
┌─────────────────────────────────────────────────────────────────┐
│                      CHAT ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐    ┌──────────┐    ┌──────────────────────────┐  │
│  │  User A  │◄──►│  SQLite  │◄──►│  Firebase Firestore     │  │
│  │  (JavaFX)│    │  (Local) │    │  (Cloud - Real-time)    │  │
│  └──────────┘    └──────────┘    └──────────────────────────┘  │
│       │                                      ▲                  │
│       │         ┌──────────┐                 │                  │
│       └────────►│  User B  │◄────────────────┘                  │
│                 │  (JavaFX)│                                    │
│                 └──────────┘                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Entity Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                    ENTITY RELATIONSHIP DIAGRAM                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────┐         ┌──────────────────┐        ┌──────────┐ │
│   │  USER   │◄───────►│  CONVERSATION    │◄──────►│  USER    │ │
│   │ (user1) │   1:N   │                  │   N:1  │ (user2)  │ │
│   └─────────┘         └────────┬─────────┘        └──────────┘ │
│                                │                               │
│                                │ 1:N                           │
│                                ▼                               │
│                        ┌──────────────┐                        │
│                        │   MESSAGE    │                        │
│                        │              │                        │
│                        └──────────────┘                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 Key Concepts

| Entity | Description | Storage |
|--------|-------------|---------|
| **User** | Application users (farmers/buyers) | SQLite (primary), Firestore (sync) |
| **Conversation** | Chat thread between exactly 2 users | Both SQLite & Firestore |
| **Message** | Individual chat message | Both SQLite & Firestore |

### 1.4 Why Hybrid Architecture?

| Feature | SQLite (Local) | Firestore (Cloud) |
|---------|----------------|-------------------|
| **Offline Access** | ✅ Full support | ❌ Requires internet |
| **Speed** | ✅ Instant | ⚡ Network dependent |
| **Real-time Sync** | ❌ No | ✅ Native support |
| **Data Persistence** | ✅ Device only | ✅ Cloud backup |
| **Cross-device** | ❌ No | ✅ Yes |

**Strategy**: SQLite for offline-first, Firestore for real-time sync between users.

---

## 2. Database Schema Design

### 2.1 SQLite Schema (Local Database)

```sql
-- ============================================
-- CONVERSATIONS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS conversations (
    -- Primary Key: Auto-incrementing local ID
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    
    -- Firebase document ID for sync
    firebase_id TEXT UNIQUE,
    
    -- Participant IDs (always store smaller ID first for consistency)
    user1_id INTEGER NOT NULL,
    user2_id INTEGER NOT NULL,
    
    -- Optional: Context for the conversation (e.g., crop being discussed)
    crop_id INTEGER,
    
    -- Denormalized fields for quick display (avoid JOINs)
    user1_name TEXT,
    user2_name TEXT,
    
    -- Last message preview for conversation list
    last_message TEXT,
    last_message_time TIMESTAMP,
    last_sender_id INTEGER,
    
    -- Unread count for current user
    unread_count INTEGER DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Sync status
    sync_status TEXT DEFAULT 'pending', -- 'pending', 'synced', 'error'
    
    -- Constraints
    FOREIGN KEY (user1_id) REFERENCES users(id),
    FOREIGN KEY (user2_id) REFERENCES users(id),
    FOREIGN KEY (crop_id) REFERENCES crops(id),
    
    -- Ensure unique conversation between two users
    UNIQUE(user1_id, user2_id)
);

-- Index for fast lookup by participants
CREATE INDEX IF NOT EXISTS idx_conversations_users 
    ON conversations(user1_id, user2_id);

-- Index for listing conversations by recency
CREATE INDEX IF NOT EXISTS idx_conversations_last_message 
    ON conversations(last_message_time DESC);

-- ============================================
-- MESSAGES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS messages (
    -- Primary Key
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    
    -- Firebase document ID for sync
    firebase_id TEXT UNIQUE,
    
    -- Reference to conversation
    conversation_id INTEGER NOT NULL,
    
    -- Sender information
    sender_id INTEGER NOT NULL,
    
    -- Message content
    message_text TEXT NOT NULL,
    
    -- Message type: 'text', 'image', 'location', 'crop_link'
    message_type TEXT DEFAULT 'text',
    
    -- Optional: Attachment URL or crop reference
    attachment_url TEXT,
    crop_reference_id INTEGER,
    
    -- Read status
    is_read INTEGER DEFAULT 0,
    read_at TIMESTAMP,
    
    -- Delivery status: 'sending', 'sent', 'delivered', 'read', 'failed'
    status TEXT DEFAULT 'sending',
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Sync status
    sync_status TEXT DEFAULT 'pending',
    
    -- Constraints
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id)
);

-- Index for fetching messages in a conversation
CREATE INDEX IF NOT EXISTS idx_messages_conversation 
    ON messages(conversation_id, created_at DESC);

-- Index for unread messages
CREATE INDEX IF NOT EXISTS idx_messages_unread 
    ON messages(conversation_id, is_read) WHERE is_read = 0;

-- Index for sync
CREATE INDEX IF NOT EXISTS idx_messages_sync 
    ON messages(sync_status) WHERE sync_status = 'pending';
```

### 2.2 Firestore Schema (Cloud Database)

```
Firestore Database Structure:
├── conversations/                    # Collection
│   ├── {conversationId}/            # Document (auto-generated ID)
│   │   ├── participantIds: [1, 5]   # Array of user IDs (sorted)
│   │   ├── participantKey: "1_5"    # Unique key for querying
│   │   ├── user1Id: 1
│   │   ├── user2Id: 5
│   │   ├── user1Name: "Rahim"
│   │   ├── user2Name: "Karim"
│   │   ├── cropId: 12               # Optional context
│   │   ├── lastMessage: "হ্যাঁ, পণ্য..."
│   │   ├── lastMessageTime: Timestamp
│   │   ├── lastSenderId: 1
│   │   ├── createdAt: Timestamp
│   │   ├── updatedAt: Timestamp
│   │   │
│   │   └── messages/                # Subcollection
│   │       ├── {messageId}/         # Document (auto-generated ID)
│   │       │   ├── senderId: 1
│   │       │   ├── text: "হ্যালো"
│   │       │   ├── type: "text"
│   │       │   ├── isRead: false
│   │       │   ├── readAt: null
│   │       │   ├── createdAt: Timestamp
│   │       │   └── status: "sent"
│   │       │
│   │       └── {messageId2}/...
│   │
│   └── {conversationId2}/...
│
└── users/                           # Collection (for user presence)
    └── {userId}/
        ├── name: "Rahim"
        ├── isOnline: true
        ├── lastSeen: Timestamp
        └── fcmToken: "..."          # For push notifications
```

### 2.3 Firestore Document Schemas

#### Conversation Document
```javascript
{
  // Identification
  "participantIds": [1, 5],           // Sorted array for querying
  "participantKey": "1_5",            // Unique string key (smaller_larger)
  
  // User info (denormalized for display)
  "user1Id": 1,
  "user2Id": 5,
  "user1Name": "রহিম উদ্দিন",
  "user2Name": "করিম খান",
  
  // Optional context
  "cropId": 12,
  "cropName": "ধান (Rice)",
  
  // Last message preview
  "lastMessage": "হ্যাঁ, পণ্য এখনও আছে",
  "lastMessageTime": Timestamp,
  "lastSenderId": 1,
  
  // Metadata
  "createdAt": Timestamp,
  "updatedAt": Timestamp,
  
  // Unread counts per user
  "unreadCount": {
    "1": 0,
    "5": 2
  }
}
```

#### Message Document
```javascript
{
  // Sender
  "senderId": 1,
  "senderName": "রহিম উদ্দিন",
  
  // Content
  "text": "আপনার ধান এখনও আছে?",
  "type": "text",                     // 'text', 'image', 'crop_link'
  
  // Optional attachments
  "attachmentUrl": null,
  "cropReferenceId": null,
  
  // Status
  "isRead": false,
  "readAt": null,
  "status": "sent",                   // 'sending', 'sent', 'delivered', 'read'
  
  // Timestamp
  "createdAt": Timestamp
}
```

### 2.4 Key Design Decisions

#### Unique Conversation Identification
```
Formula: participantKey = min(userId1, userId2) + "_" + max(userId1, userId2)

Example:
- User 5 chats with User 1
- participantKey = "1_5" (always the same regardless of who initiates)

This ensures:
✅ No duplicate conversations between same users
✅ Easy lookup from either user's perspective
✅ Consistent ordering for queries
```

#### Why Subcollections for Messages?
```
Option A: Messages as subcollection (CHOSEN)
├── conversations/{convId}/messages/{msgId}
│
│ Pros:
│ ✅ Automatic scoping - messages belong to conversation
│ ✅ Efficient queries - fetch only messages for one conversation
│ ✅ Security rules can cascade from parent
│ ✅ Deleting conversation can cascade delete messages
│
│ Cons:
│ ❌ Can't query messages across all conversations (rarely needed)

Option B: Messages as root collection
├── messages/{msgId}
│   └── conversationId: "abc123"
│
│ Pros:
│ ✅ Can query all messages globally
│
│ Cons:
│ ❌ Need explicit conversationId filter every time
│ ❌ More complex security rules
```

---

## 3. Message Flow

### 3.1 Opening a Chat (Finding or Creating Conversation)

```
┌─────────────────────────────────────────────────────────────────┐
│                    OPEN CHAT FLOW                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User clicks "Chat with Farmer"                                 │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ Generate participantKey         │                           │
│  │ key = min(myId, otherId) + "_"  │                           │
│  │       + max(myId, otherId)      │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ Check SQLite for existing       │                           │
│  │ conversation with this key      │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│     ┌─────┴─────┐                                              │
│     │           │                                              │
│   Found      Not Found                                          │
│     │           │                                              │
│     │           ▼                                              │
│     │    ┌─────────────────────────────────┐                   │
│     │    │ Query Firestore by              │                   │
│     │    │ participantKey                  │                   │
│     │    └─────────────────────────────────┘                   │
│     │           │                                              │
│     │     ┌─────┴─────┐                                        │
│     │     │           │                                        │
│     │   Found      Not Found                                    │
│     │     │           │                                        │
│     │     │           ▼                                        │
│     │     │    ┌─────────────────────────────────┐             │
│     │     │    │ Create new conversation         │             │
│     │     │    │ in Firestore                    │             │
│     │     │    └─────────────────────────────────┘             │
│     │     │           │                                        │
│     │     └─────┬─────┘                                        │
│     │           │                                              │
│     │           ▼                                              │
│     │    ┌─────────────────────────────────┐                   │
│     │    │ Save/Update in SQLite           │                   │
│     │    └─────────────────────────────────┘                   │
│     │           │                                              │
│     └─────┬─────┘                                              │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ Open Chat UI with               │                           │
│  │ conversation ID                 │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ Start listening for real-time   │                           │
│  │ messages from Firestore         │                           │
│  └─────────────────────────────────┘                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Sending a Message

```
┌─────────────────────────────────────────────────────────────────┐
│                    SEND MESSAGE FLOW                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User types message and clicks Send                             │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ 1. Create message object        │                           │
│  │    - Generate temp local ID     │                           │
│  │    - Set status = 'sending'     │                           │
│  │    - Set timestamp              │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ 2. Save to SQLite immediately   │◄─── Optimistic UI Update  │
│  │    (offline-first)              │     User sees message     │
│  └─────────────────────────────────┘     instantly             │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ 3. Send to Firestore            │                           │
│  │    (async, background)          │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│     ┌─────┴─────┐                                              │
│     │           │                                              │
│  Success      Failed                                            │
│     │           │                                              │
│     ▼           ▼                                              │
│  ┌──────────┐  ┌──────────────────────────────┐                │
│  │ Update   │  │ Update SQLite:               │                │
│  │ SQLite:  │  │ - status = 'failed'          │                │
│  │ - status │  │ - sync_status = 'error'      │                │
│  │   = sent │  │ Show retry option to user    │                │
│  │ - Store  │  └──────────────────────────────┘                │
│  │   firebase│                                                 │
│  │   _id    │                                                  │
│  └──────────┘                                                  │
│     │                                                          │
│     ▼                                                          │
│  ┌─────────────────────────────────┐                           │
│  │ 4. Update conversation in       │                           │
│  │    Firestore:                   │                           │
│  │    - lastMessage                │                           │
│  │    - lastMessageTime            │                           │
│  │    - increment unreadCount      │                           │
│  └─────────────────────────────────┘                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 Receiving Messages in Real-Time

```
┌─────────────────────────────────────────────────────────────────┐
│                    RECEIVE MESSAGE FLOW                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────┐                           │
│  │ Firestore Listener Active       │                           │
│  │ (onSnapshot on messages         │                           │
│  │  subcollection)                 │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│           │ New message arrives                                 │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ 1. Firestore triggers callback  │                           │
│  │    with document changes        │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ 2. Parse message data           │                           │
│  │    - Check if message is new    │                           │
│  │    - Check if from other user   │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ 3. Save to SQLite               │                           │
│  │    - Use firebase_id to avoid   │                           │
│  │      duplicates (UPSERT)        │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ 4. Update UI on JavaFX thread   │                           │
│  │    Platform.runLater(() -> {    │                           │
│  │        addMessageToList(msg);   │                           │
│  │        scrollToBottom();        │                           │
│  │    });                          │                           │
│  └─────────────────────────────────┘                           │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────┐                           │
│  │ 5. Mark as read if chat is open │                           │
│  │    - Update Firestore           │                           │
│  │    - Update SQLite              │                           │
│  │    - Reset unread count         │                           │
│  └─────────────────────────────────┘                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.4 Message Ordering

```
┌─────────────────────────────────────────────────────────────────┐
│                    MESSAGE ORDERING STRATEGY                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Problem: Messages can arrive out of order due to:              │
│  - Network latency                                              │
│  - Clock differences between devices                            │
│  - Offline messages syncing later                               │
│                                                                 │
│  Solution: Use Firestore Server Timestamp                       │
│                                                                 │
│  When sending:                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ {                                                        │   │
│  │   "text": "Hello",                                       │   │
│  │   "createdAt": FieldValue.serverTimestamp() // Server    │   │
│  │ }                                                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  When querying:                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ messagesRef                                              │   │
│  │   .orderBy("createdAt", Query.Direction.ASCENDING)       │   │
│  │   .limit(50)                                             │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Local display order (SQLite):                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ SELECT * FROM messages                                   │   │
│  │ WHERE conversation_id = ?                                │   │
│  │ ORDER BY created_at ASC, id ASC                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Data Storage & Retrieval

### 4.1 Java Service Class Structure

```java
/**
 * ChatService - Main service for chat operations
 * Handles both SQLite (local) and Firestore (cloud) operations
 */
public class ChatService {
    
    private static final String CONVERSATIONS_COLLECTION = "conversations";
    private static final String MESSAGES_SUBCOLLECTION = "messages";
    
    private Firestore firestore;
    private ListenerRegistration messageListener;
    
    // ==========================================
    // CONVERSATION OPERATIONS
    // ==========================================
    
    /**
     * Find or create a conversation between two users
     */
    public CompletableFuture<Conversation> getOrCreateConversation(
            int currentUserId, 
            int otherUserId,
            Integer cropId) {
        
        // Generate unique key
        String participantKey = generateParticipantKey(currentUserId, otherUserId);
        
        return CompletableFuture.supplyAsync(() -> {
            // Step 1: Check SQLite first
            Conversation local = findConversationInSQLite(participantKey);
            if (local != null) {
                return local;
            }
            
            // Step 2: Check Firestore
            Conversation remote = findConversationInFirestore(participantKey);
            if (remote != null) {
                // Save to SQLite for offline access
                saveConversationToSQLite(remote);
                return remote;
            }
            
            // Step 3: Create new conversation
            return createNewConversation(currentUserId, otherUserId, cropId);
        });
    }
    
    /**
     * Generate unique participant key (always smaller_larger)
     */
    public static String generateParticipantKey(int userId1, int userId2) {
        int smaller = Math.min(userId1, userId2);
        int larger = Math.max(userId1, userId2);
        return smaller + "_" + larger;
    }
}
```

### 4.2 SQLite Operations

```java
// ==========================================
// SQLite Data Access
// ==========================================

/**
 * Find conversation in local SQLite database
 */
private Conversation findConversationInSQLite(String participantKey) {
    String sql = """
        SELECT * FROM conversations 
        WHERE (user1_id || '_' || user2_id) = ? 
           OR (user2_id || '_' || user1_id) = ?
        LIMIT 1
        """;
    
    // Execute query and map result
    // ...
}

/**
 * Save conversation to SQLite
 */
public void saveConversationToSQLite(Conversation conv) {
    String sql = """
        INSERT OR REPLACE INTO conversations 
        (firebase_id, user1_id, user2_id, crop_id, user1_name, user2_name,
         last_message, last_message_time, last_sender_id, created_at, updated_at, sync_status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'synced')
        """;
    
    DatabaseService.executeUpdate(sql, new Object[]{
        conv.getFirebaseId(),
        conv.getUser1Id(),
        conv.getUser2Id(),
        conv.getCropId(),
        conv.getUser1Name(),
        conv.getUser2Name(),
        conv.getLastMessage(),
        conv.getLastMessageTime(),
        conv.getLastSenderId(),
        conv.getCreatedAt(),
        conv.getUpdatedAt()
    });
}

/**
 * Save message to SQLite
 */
public void saveMessageToSQLite(Message msg) {
    String sql = """
        INSERT OR REPLACE INTO messages 
        (firebase_id, conversation_id, sender_id, message_text, message_type,
         is_read, status, created_at, sync_status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    
    DatabaseService.executeUpdate(sql, new Object[]{
        msg.getFirebaseId(),
        msg.getConversationId(),
        msg.getSenderId(),
        msg.getText(),
        msg.getType(),
        msg.isRead() ? 1 : 0,
        msg.getStatus(),
        msg.getCreatedAt(),
        msg.getSyncStatus()
    });
}

/**
 * Get messages for a conversation (with pagination)
 */
public List<Message> getMessagesFromSQLite(int conversationId, int limit, int offset) {
    String sql = """
        SELECT * FROM messages 
        WHERE conversation_id = ?
        ORDER BY created_at ASC
        LIMIT ? OFFSET ?
        """;
    
    List<Message> messages = new ArrayList<>();
    DatabaseService.executeQueryAsync(sql, 
        new Object[]{conversationId, limit, offset},
        rs -> {
            while (rs.next()) {
                messages.add(mapResultSetToMessage(rs));
            }
        },
        error -> error.printStackTrace()
    );
    return messages;
}

/**
 * Get all conversations for a user
 */
public List<Conversation> getUserConversations(int userId) {
    String sql = """
        SELECT c.*, 
               CASE WHEN c.user1_id = ? THEN c.user2_name ELSE c.user1_name END as other_user_name,
               CASE WHEN c.user1_id = ? THEN c.user2_id ELSE c.user1_id END as other_user_id
        FROM conversations c
        WHERE c.user1_id = ? OR c.user2_id = ?
        ORDER BY c.last_message_time DESC
        """;
    
    // Execute and return list
    // ...
}
```

### 4.3 Firestore Operations

```java
// ==========================================
// Firestore Data Access
// ==========================================

/**
 * Find conversation in Firestore by participant key
 */
private Conversation findConversationInFirestore(String participantKey) {
    try {
        QuerySnapshot snapshot = firestore.collection(CONVERSATIONS_COLLECTION)
            .whereEqualTo("participantKey", participantKey)
            .limit(1)
            .get()
            .get(); // Blocking call
        
        if (!snapshot.isEmpty()) {
            DocumentSnapshot doc = snapshot.getDocuments().get(0);
            return mapDocumentToConversation(doc);
        }
        return null;
    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}

/**
 * Create new conversation in Firestore
 */
private Conversation createNewConversation(int user1Id, int user2Id, Integer cropId) {
    // Ensure consistent ordering
    int smallerId = Math.min(user1Id, user2Id);
    int largerId = Math.max(user1Id, user2Id);
    String participantKey = smallerId + "_" + largerId;
    
    // Get user names from SQLite
    String user1Name = getUserName(smallerId);
    String user2Name = getUserName(largerId);
    
    // Build document data
    Map<String, Object> data = new HashMap<>();
    data.put("participantIds", Arrays.asList(smallerId, largerId));
    data.put("participantKey", participantKey);
    data.put("user1Id", smallerId);
    data.put("user2Id", largerId);
    data.put("user1Name", user1Name);
    data.put("user2Name", user2Name);
    data.put("cropId", cropId);
    data.put("lastMessage", null);
    data.put("lastMessageTime", null);
    data.put("lastSenderId", null);
    data.put("createdAt", FieldValue.serverTimestamp());
    data.put("updatedAt", FieldValue.serverTimestamp());
    data.put("unreadCount", Map.of(
        String.valueOf(smallerId), 0,
        String.valueOf(largerId), 0
    ));
    
    // Add to Firestore
    DocumentReference docRef = firestore.collection(CONVERSATIONS_COLLECTION).document();
    docRef.set(data).get(); // Blocking
    
    // Create local Conversation object
    Conversation conv = new Conversation();
    conv.setFirebaseId(docRef.getId());
    conv.setUser1Id(smallerId);
    conv.setUser2Id(largerId);
    conv.setUser1Name(user1Name);
    conv.setUser2Name(user2Name);
    conv.setCropId(cropId);
    conv.setCreatedAt(new Timestamp(System.currentTimeMillis()));
    
    // Save to SQLite
    saveConversationToSQLite(conv);
    
    return conv;
}

/**
 * Send message to Firestore
 */
public CompletableFuture<Message> sendMessage(
        String conversationFirebaseId, 
        int senderId, 
        String senderName,
        String text) {
    
    return CompletableFuture.supplyAsync(() -> {
        try {
            // Build message data
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("senderId", senderId);
            messageData.put("senderName", senderName);
            messageData.put("text", text);
            messageData.put("type", "text");
            messageData.put("isRead", false);
            messageData.put("readAt", null);
            messageData.put("status", "sent");
            messageData.put("createdAt", FieldValue.serverTimestamp());
            
            // Add message to subcollection
            DocumentReference msgRef = firestore
                .collection(CONVERSATIONS_COLLECTION)
                .document(conversationFirebaseId)
                .collection(MESSAGES_SUBCOLLECTION)
                .document();
            
            msgRef.set(messageData).get();
            
            // Update conversation's last message
            updateConversationLastMessage(conversationFirebaseId, text, senderId);
            
            // Create local Message object
            Message msg = new Message();
            msg.setFirebaseId(msgRef.getId());
            msg.setSenderId(senderId);
            msg.setText(text);
            msg.setStatus("sent");
            msg.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            
            return msg;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message", e);
        }
    });
}

/**
 * Update conversation's last message info
 */
private void updateConversationLastMessage(
        String conversationFirebaseId, 
        String messageText, 
        int senderId) {
    
    Map<String, Object> updates = new HashMap<>();
    updates.put("lastMessage", messageText.length() > 50 
        ? messageText.substring(0, 50) + "..." 
        : messageText);
    updates.put("lastMessageTime", FieldValue.serverTimestamp());
    updates.put("lastSenderId", senderId);
    updates.put("updatedAt", FieldValue.serverTimestamp());
    
    // Increment unread count for the OTHER user
    // This requires knowing who the other user is
    
    firestore.collection(CONVERSATIONS_COLLECTION)
        .document(conversationFirebaseId)
        .update(updates);
}
```

---

## 5. Real-Time Updates Implementation

### 5.1 Firestore Real-Time Listener

```java
/**
 * ChatRealtimeService - Handles real-time message synchronization
 */
public class ChatRealtimeService {
    
    private Firestore firestore;
    private ListenerRegistration currentListener;
    private Consumer<Message> onMessageReceived;
    private int currentUserId;
    
    /**
     * Start listening for new messages in a conversation
     */
    public void startListening(
            String conversationFirebaseId,
            int userId,
            Consumer<Message> messageCallback,
            Consumer<Throwable> errorCallback) {
        
        this.currentUserId = userId;
        this.onMessageReceived = messageCallback;
        
        // Stop any existing listener
        stopListening();
        
        // Create query for messages, ordered by time
        Query messagesQuery = firestore
            .collection("conversations")
            .document(conversationFirebaseId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING);
        
        // Add real-time listener
        currentListener = messagesQuery.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot snapshots, FirestoreException error) {
                if (error != null) {
                    errorCallback.accept(error);
                    return;
                }
                
                if (snapshots == null) return;
                
                // Process document changes
                for (DocumentChange change : snapshots.getDocumentChanges()) {
                    switch (change.getType()) {
                        case ADDED:
                            handleNewMessage(change.getDocument());
                            break;
                        case MODIFIED:
                            handleMessageUpdate(change.getDocument());
                            break;
                        case REMOVED:
                            handleMessageDeleted(change.getDocument());
                            break;
                    }
                }
            }
        });
    }
    
    /**
     * Handle incoming new message
     */
    private void handleNewMessage(DocumentSnapshot doc) {
        Message message = mapDocumentToMessage(doc);
        
        // Save to SQLite (idempotent - uses UPSERT)
        saveMessageToSQLite(message);
        
        // Update UI on JavaFX thread
        Platform.runLater(() -> {
            if (onMessageReceived != null) {
                onMessageReceived.accept(message);
            }
        });
        
        // Auto-mark as read if from other user and chat is open
        if (message.getSenderId() != currentUserId) {
            markMessageAsRead(doc.getReference());
        }
    }
    
    /**
     * Handle message update (e.g., read status changed)
     */
    private void handleMessageUpdate(DocumentSnapshot doc) {
        Message message = mapDocumentToMessage(doc);
        
        // Update in SQLite
        updateMessageInSQLite(message);
        
        // Notify UI
        Platform.runLater(() -> {
            // Update message status in UI (e.g., show "read" checkmarks)
        });
    }
    
    /**
     * Stop listening when leaving chat screen
     */
    public void stopListening() {
        if (currentListener != null) {
            currentListener.remove();
            currentListener = null;
        }
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
     * Convert Firestore document to Message object
     */
    private Message mapDocumentToMessage(DocumentSnapshot doc) {
        Message msg = new Message();
        msg.setFirebaseId(doc.getId());
        msg.setSenderId(doc.getLong("senderId").intValue());
        msg.setText(doc.getString("text"));
        msg.setType(doc.getString("type"));
        msg.setRead(Boolean.TRUE.equals(doc.getBoolean("isRead")));
        msg.setStatus(doc.getString("status"));
        
        com.google.cloud.Timestamp ts = doc.getTimestamp("createdAt");
        if (ts != null) {
            msg.setCreatedAt(new java.sql.Timestamp(ts.toDate().getTime()));
        }
        
        return msg;
    }
}
```

### 5.2 Conversation List Real-Time Updates

```java
/**
 * Listen for updates to user's conversations (for chat list screen)
 */
public void startConversationListListener(
        int userId,
        Consumer<List<Conversation>> onUpdate,
        Consumer<Throwable> onError) {
    
    // Query conversations where user is a participant
    Query query = firestore.collection("conversations")
        .whereArrayContains("participantIds", userId)
        .orderBy("lastMessageTime", Query.Direction.DESCENDING);
    
    query.addSnapshotListener((snapshots, error) -> {
        if (error != null) {
            onError.accept(error);
            return;
        }
        
        List<Conversation> conversations = new ArrayList<>();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Conversation conv = mapDocumentToConversation(doc);
            conversations.add(conv);
            
            // Sync to SQLite
            saveConversationToSQLite(conv);
        }
        
        // Update UI
        Platform.runLater(() -> onUpdate.accept(conversations));
    });
}
```

### 5.3 SQLite Change Notification (for Offline Data)

```java
/**
 * Poll SQLite for changes when offline
 * (SQLite doesn't have native change notifications in JDBC)
 */
public class SQLiteChangePoller {
    
    private ScheduledExecutorService scheduler;
    private long lastCheckTimestamp;
    
    public void startPolling(int conversationId, Consumer<List<Message>> onNewMessages) {
        lastCheckTimestamp = System.currentTimeMillis();
        
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            checkForNewMessages(conversationId, onNewMessages);
        }, 0, 2, TimeUnit.SECONDS); // Check every 2 seconds
    }
    
    private void checkForNewMessages(int conversationId, Consumer<List<Message>> callback) {
        String sql = """
            SELECT * FROM messages 
            WHERE conversation_id = ? 
            AND created_at > ?
            ORDER BY created_at ASC
            """;
        
        List<Message> newMessages = new ArrayList<>();
        // Execute query...
        
        if (!newMessages.isEmpty()) {
            lastCheckTimestamp = System.currentTimeMillis();
            Platform.runLater(() -> callback.accept(newMessages));
        }
    }
    
    public void stopPolling() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
```

### 5.4 UI Integration in JavaFX Controller

```java
/**
 * ChatConversationController - UI Controller for chat screen
 */
public class ChatConversationController {
    
    @FXML private ListView<Message> messageListView;
    @FXML private TextField txtMessage;
    @FXML private Button btnSend;
    
    private ChatService chatService;
    private ChatRealtimeService realtimeService;
    private Conversation currentConversation;
    private ObservableList<Message> messages = FXCollections.observableArrayList();
    
    public void initialize() {
        chatService = new ChatService();
        realtimeService = new ChatRealtimeService();
        
        // Bind messages to ListView
        messageListView.setItems(messages);
        messageListView.setCellFactory(this::createMessageCell);
    }
    
    /**
     * Called when opening a chat with another user
     */
    public void openChat(int otherUserId, Integer cropId) {
        int currentUserId = App.getCurrentUser().getId();
        
        // Get or create conversation
        chatService.getOrCreateConversation(currentUserId, otherUserId, cropId)
            .thenAccept(conversation -> {
                this.currentConversation = conversation;
                
                Platform.runLater(() -> {
                    // Load existing messages from SQLite
                    loadMessagesFromSQLite();
                    
                    // Start real-time listener
                    startRealtimeListener();
                });
            })
            .exceptionally(e -> {
                Platform.runLater(() -> showError("চ্যাট খুলতে ব্যর্থ", e.getMessage()));
                return null;
            });
    }
    
    /**
     * Load cached messages from SQLite
     */
    private void loadMessagesFromSQLite() {
        List<Message> cachedMessages = chatService.getMessagesFromSQLite(
            currentConversation.getId(), 
            50, // limit
            0   // offset
        );
        
        messages.clear();
        messages.addAll(cachedMessages);
        scrollToBottom();
    }
    
    /**
     * Start listening for real-time updates from Firestore
     */
    private void startRealtimeListener() {
        realtimeService.startListening(
            currentConversation.getFirebaseId(),
            App.getCurrentUser().getId(),
            this::onMessageReceived,  // Success callback
            this::onListenerError     // Error callback
        );
    }
    
    /**
     * Called when a new message is received from Firestore
     */
    private void onMessageReceived(Message message) {
        // Check if message already exists (avoid duplicates)
        boolean exists = messages.stream()
            .anyMatch(m -> m.getFirebaseId().equals(message.getFirebaseId()));
        
        if (!exists) {
            messages.add(message);
            scrollToBottom();
            
            // Play notification sound if from other user
            if (message.getSenderId() != App.getCurrentUser().getId()) {
                playNotificationSound();
            }
        }
    }
    
    /**
     * Send button clicked
     */
    @FXML
    private void onSendMessage() {
        String text = txtMessage.getText().trim();
        if (text.isEmpty()) return;
        
        int senderId = App.getCurrentUser().getId();
        String senderName = App.getCurrentUser().getName();
        
        // Create optimistic local message
        Message localMsg = new Message();
        localMsg.setSenderId(senderId);
        localMsg.setText(text);
        localMsg.setStatus("sending");
        localMsg.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        
        // Add to UI immediately (optimistic update)
        messages.add(localMsg);
        txtMessage.clear();
        scrollToBottom();
        
        // Send to Firestore
        chatService.sendMessage(
            currentConversation.getFirebaseId(),
            senderId,
            senderName,
            text
        ).thenAccept(sentMsg -> {
            Platform.runLater(() -> {
                // Update local message with firebase ID and status
                localMsg.setFirebaseId(sentMsg.getFirebaseId());
                localMsg.setStatus("sent");
                refreshMessageList();
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                localMsg.setStatus("failed");
                refreshMessageList();
                showError("বার্তা পাঠাতে ব্যর্থ", e.getMessage());
            });
            return null;
        });
    }
    
    /**
     * Cleanup when leaving the chat screen
     */
    public void cleanup() {
        realtimeService.stopListening();
    }
    
    private void scrollToBottom() {
        if (!messages.isEmpty()) {
            messageListView.scrollTo(messages.size() - 1);
        }
    }
    
    private void refreshMessageList() {
        messageListView.refresh();
    }
}
```

### 5.5 Complete Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    COMPLETE REAL-TIME DATA FLOW                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  USER A (Sender)                           USER B (Receiver)            │
│  ─────────────────                         ─────────────────            │
│        │                                          │                     │
│        │ 1. Type message                          │                     │
│        │    Click Send                            │                     │
│        ▼                                          │                     │
│  ┌───────────┐                                    │                     │
│  │ SQLite A  │ 2. Save locally                    │                     │
│  │ (pending) │    (optimistic)                    │                     │
│  └───────────┘                                    │                     │
│        │                                          │                     │
│        │ 3. Upload to Firestore                   │                     │
│        ▼                                          │                     │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     FIREBASE FIRESTORE                          │   │
│  │  ┌─────────────────────────────────────────────────────────┐   │   │
│  │  │ conversations/{convId}/messages/{msgId}                  │   │   │
│  │  │ {                                                        │   │   │
│  │  │   senderId: 1,                                          │   │   │
│  │  │   text: "Hello",                                        │   │   │
│  │  │   createdAt: <ServerTimestamp>                          │   │   │
│  │  │ }                                                        │   │   │
│  │  └─────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│        │                                          │                     │
│        │ 4. Firestore triggers                    │                     │
│        │    onSnapshot listener                   │                     │
│        │    for User B                            ▼                     │
│        │                                   ┌─────────────┐              │
│        │                                   │ Listener    │              │
│        │                                   │ receives    │              │
│        │                                   │ new doc     │              │
│        │                                   └─────────────┘              │
│        │                                          │                     │
│        │                                          │ 5. Save to          │
│        │                                          │    SQLite B         │
│        │                                          ▼                     │
│        │                                   ┌───────────┐                │
│        │                                   │ SQLite B  │                │
│        │                                   │ (synced)  │                │
│        │                                   └───────────┘                │
│        │                                          │                     │
│        │                                          │ 6. Update UI        │
│        │                                          │    Platform.        │
│        │                                          │    runLater()       │
│        │                                          ▼                     │
│        │                                   ┌───────────┐                │
│        │                                   │  JavaFX   │                │
│        │                                   │  ListView │                │
│        │                                   │  updated  │                │
│        │                                   └───────────┘                │
│        │                                          │                     │
│        │ 7. User A receives                       │ 7. User B sees      │
│        │    confirmation                          │    new message      │
│        │    (status: sent)                        │    instantly        │
│        ▼                                          ▼                     │
│  ┌───────────┐                             ┌───────────┐                │
│  │  UI shows │                             │  UI shows │                │
│  │  ✓ sent   │                             │  message  │                │
│  └───────────┘                             └───────────┘                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Code Implementation

### 6.1 Model Classes

```java
// Conversation.java
public class Conversation {
    private int id;                    // Local SQLite ID
    private String firebaseId;         // Firestore document ID
    private int user1Id;
    private int user2Id;
    private Integer cropId;
    private String user1Name;
    private String user2Name;
    private String lastMessage;
    private Timestamp lastMessageTime;
    private int lastSenderId;
    private int unreadCount;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String syncStatus;
    
    // Getters and setters...
    
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

// Message.java
public class Message {
    private int id;                    // Local SQLite ID
    private String firebaseId;         // Firestore document ID
    private int conversationId;        // Local conversation ID
    private int senderId;
    private String senderName;
    private String text;
    private String type;               // 'text', 'image', 'crop_link'
    private String attachmentUrl;
    private Integer cropReferenceId;
    private boolean isRead;
    private Timestamp readAt;
    private String status;             // 'sending', 'sent', 'delivered', 'read', 'failed'
    private Timestamp createdAt;
    private String syncStatus;
    
    // Getters and setters...
    
    public boolean isFromMe(int currentUserId) {
        return senderId == currentUserId;
    }
}
```

### 6.2 Firestore Security Rules

```javascript
// firestore.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Conversations collection
    match /conversations/{conversationId} {
      // Allow read if user is a participant
      allow read: if request.auth != null && 
                    request.auth.uid in resource.data.participantIds;
      
      // Allow create if user is one of the participants
      allow create: if request.auth != null &&
                      request.auth.uid in request.resource.data.participantIds;
      
      // Allow update if user is a participant
      allow update: if request.auth != null &&
                      request.auth.uid in resource.data.participantIds;
      
      // Messages subcollection
      match /messages/{messageId} {
        // Allow read if user is a conversation participant
        allow read: if request.auth != null &&
                      request.auth.uid in get(/databases/$(database)/documents/conversations/$(conversationId)).data.participantIds;
        
        // Allow create if user is sender and participant
        allow create: if request.auth != null &&
                        request.auth.uid == request.resource.data.senderId &&
                        request.auth.uid in get(/databases/$(database)/documents/conversations/$(conversationId)).data.participantIds;
        
        // Allow update (for read status) if user is participant
        allow update: if request.auth != null &&
                        request.auth.uid in get(/databases/$(database)/documents/conversations/$(conversationId)).data.participantIds;
      }
    }
    
    // User presence
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

### 6.3 Error Handling & Retry Logic

```java
/**
 * Message sending with retry logic
 */
public class MessageSendManager {
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    
    public CompletableFuture<Message> sendWithRetry(
            String conversationId,
            Message message) {
        
        return CompletableFuture.supplyAsync(() -> {
            int attempts = 0;
            Exception lastError = null;
            
            while (attempts < MAX_RETRIES) {
                try {
                    return sendToFirestore(conversationId, message);
                } catch (Exception e) {
                    lastError = e;
                    attempts++;
                    
                    // Update UI to show retry status
                    Platform.runLater(() -> {
                        message.setStatus("retrying");
                    });
                    
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            // All retries failed
            message.setStatus("failed");
            message.setSyncStatus("error");
            updateMessageInSQLite(message);
            
            throw new RuntimeException("Failed after " + MAX_RETRIES + " attempts", lastError);
        });
    }
    
    /**
     * Retry failed messages (called on app startup or when connection restored)
     */
    public void retryFailedMessages() {
        String sql = "SELECT * FROM messages WHERE sync_status = 'error' ORDER BY created_at ASC";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{}, rs -> {
            while (rs.next()) {
                Message msg = mapResultSetToMessage(rs);
                String convFirebaseId = getConversationFirebaseId(msg.getConversationId());
                
                sendWithRetry(convFirebaseId, msg)
                    .thenAccept(sent -> {
                        msg.setSyncStatus("synced");
                        msg.setStatus("sent");
                        updateMessageInSQLite(msg);
                    });
            }
        }, error -> error.printStackTrace());
    }
}
```

---

## Summary

### Key Implementation Points:

1. **Unique Conversation ID**: Use `participantKey = min(id1, id2) + "_" + max(id1, id2)`

2. **Offline-First**: Always save to SQLite first, then sync to Firestore

3. **Real-Time Updates**: Use Firestore `addSnapshotListener()` for live updates

4. **Message Ordering**: Use `FieldValue.serverTimestamp()` for consistent ordering

5. **Optimistic UI**: Show messages immediately, update status when confirmed

6. **Error Handling**: Implement retry logic for failed messages

7. **Security**: Use Firestore security rules to restrict access to conversation participants

### Next Steps:
1. Implement the database schema in SQLite
2. Set up Firebase project and credentials
3. Create the service classes (ChatService, ChatRealtimeService)
4. Build the UI controllers
5. Test offline/online scenarios
6. Add push notifications (optional)

---

*Document Version: 1.0*
*Last Updated: January 11, 2026*
