# চ্যাট সিস্টেম - Quick Implementation Reference
## Firebase Firestore + SQLite Real-Time Chat

---

## 🏗️ Architecture Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                    ARCHITECTURE OVERVIEW                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   User A                             User B                     │
│     │                                  │                        │
│     ▼                                  ▼                        │
│  ┌──────────┐                     ┌──────────┐                 │
│  │ SQLite A │◄────────┬──────────►│ SQLite B │                 │
│  │ (Local)  │         │           │ (Local)  │                 │
│  └──────────┘         │           └──────────┘                 │
│                       │                                         │
│                       ▼                                         │
│            ┌──────────────────────┐                            │
│            │  Firebase Firestore  │                            │
│            │   (Cloud Real-time)  │                            │
│            └──────────────────────┘                            │
│                                                                 │
│  Strategy:                                                      │
│  - SQLite = Local cache + Offline access                       │
│  - Firestore = Real-time sync between users                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📊 Database Schemas

### Firestore Structure
```
conversations/
├── {convId}/
│   ├── participantIds: [1, 5]      # Array for querying
│   ├── participantKey: "1_5"       # Unique key (smaller_larger)
│   ├── user1Id: 1
│   ├── user2Id: 5
│   ├── user1Name: "রহিম"
│   ├── user2Name: "করিম"
│   ├── cropId: 12
│   ├── lastMessage: "হ্যাঁ, আছে"
│   ├── lastMessageTime: Timestamp
│   ├── createdAt: Timestamp
│   │
│   └── messages/                   # Subcollection
│       └── {msgId}/
│           ├── senderId: 1
│           ├── senderName: "রহিম"
│           ├── text: "আপনার ধান আছে?"
│           ├── type: "text"
│           ├── isRead: false
│           ├── status: "sent"
│           └── createdAt: Timestamp
```

### SQLite Schema (Key Tables)
```sql
-- Conversations
CREATE TABLE conversations (
    id INTEGER PRIMARY KEY,
    firebase_id TEXT UNIQUE,
    user1_id INTEGER,        -- Always smaller ID
    user2_id INTEGER,        -- Always larger ID
    crop_id INTEGER,
    last_message TEXT,
    last_message_time TIMESTAMP,
    sync_status TEXT DEFAULT 'pending',
    UNIQUE(user1_id, user2_id)
);

-- Messages
CREATE TABLE messages (
    id INTEGER PRIMARY KEY,
    firebase_id TEXT UNIQUE,
    conversation_id INTEGER,
    sender_id INTEGER,
    message_text TEXT,
    is_read INTEGER DEFAULT 0,
    status TEXT DEFAULT 'sending',  -- sending, sent, read, failed
    sync_status TEXT DEFAULT 'pending',
    created_at TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);
```

---

## 🔄 Message Flow

### 1. Opening a Chat
```
User clicks "Chat with Farmer"
         │
         ▼
Generate participantKey = min(myId, otherId) + "_" + max(myId, otherId)
         │
         ▼
Check SQLite ──Found──► Use existing conversation
         │
      Not Found
         │
         ▼
Check Firestore by participantKey ──Found──► Sync to SQLite
         │
      Not Found
         │
         ▼
Create new in Firestore → Save to SQLite → Open Chat UI
```

### 2. Sending a Message
```
User types message → Click Send
         │
         ▼
┌─────────────────────────────────────┐
│ 1. Save to SQLite (status: sending) │  ← Instant
│ 2. Show in UI immediately           │  ← Optimistic UI
└─────────────────────────────────────┘
         │
         ▼ (Background)
┌─────────────────────────────────────┐
│ 3. Send to Firestore                │
└─────────────────────────────────────┘
         │
    ┌────┴────┐
 Success    Failed
    │          │
    ▼          ▼
Update      Mark as
status      'failed'
= 'sent'    Show retry
```

### 3. Receiving Messages (Real-Time)
```
Firestore Listener (addSnapshotListener)
         │
         │ New message detected
         ▼
┌─────────────────────────────────────┐
│ 1. Check if duplicate (firebase_id) │
│ 2. Save to SQLite                   │
│ 3. Platform.runLater() → Update UI  │
│ 4. Mark as read if chat is open     │
└─────────────────────────────────────┘
```

---

## 💻 Key Code Examples

### Find or Create Conversation
```java
ChatService chatService = ChatService.getInstance();

chatService.getOrCreateConversation(
    currentUserId,    // 5
    otherUserId,      // 2
    cropId,           // 12 (optional)
    conversation -> {
        // Open chat UI with conversation
        openChatScreen(conversation);
        
        // Start real-time listener
        chatService.startListening(
            conversation.getFirebaseId(),
            conversation.getId(),
            currentUserId
        );
    },
    error -> showError("চ্যাট খুলতে ব্যর্থ", error.getMessage())
);
```

### Send Message
```java
chatService.sendMessage(
    conversationId,           // Local SQLite ID
    conversation.getFirebaseId(), // Firestore document ID
    currentUserId,
    currentUserName,
    "আপনার ধান এখনও আছে?",
    message -> {
        // Message added to UI instantly
        messageList.add(message);
        scrollToBottom();
    },
    error -> showError("বার্তা পাঠাতে ব্যর্থ", error.getMessage())
);
```

### Listen for Real-Time Updates
```java
// Set up callbacks
chatService.setOnMessageReceived(message -> {
    // New message from other user
    messageList.add(message);
    scrollToBottom();
    playNotificationSound();
});

chatService.setOnMessageStatusChanged(message -> {
    // Message status updated (sent, read, etc.)
    updateMessageInList(message);
});

// Start listening
chatService.startListening(
    firebaseConversationId,
    localConversationId,
    currentUserId
);

// Stop when leaving chat
chatService.stopListening(firebaseConversationId);
```

---

## 🔑 Key Design Decisions

### 1. Unique Conversation Identification
```java
// Always use smaller_larger format for consistency
String participantKey = Math.min(userId1, userId2) + "_" + Math.max(userId1, userId2);

// Example:
// User 5 chats with User 2 → participantKey = "2_5"
// User 2 chats with User 5 → participantKey = "2_5" (same!)
```

### 2. Why Messages as Subcollection?
```
✅ Automatic scoping - messages belong to conversation
✅ Efficient queries - fetch only relevant messages  
✅ Security rules cascade from parent
✅ Easy cleanup - delete conversation cascades
```

### 3. Server Timestamp for Ordering
```java
// Always use server timestamp, never client time
data.put("createdAt", FieldValue.serverTimestamp());

// Query with ordering
query.orderBy("createdAt", Query.Direction.ASCENDING);
```

### 4. Optimistic UI Pattern
```java
// 1. Show message immediately in UI
messages.add(localMessage);

// 2. Send to server in background
sendToFirestore(message);

// 3. Update status when confirmed
message.setStatus("sent");
refreshUI();
```

---

## 📁 Files Created/Modified

| File | Purpose |
|------|---------|
| [CHAT_SYSTEM_GUIDE.md](CHAT_SYSTEM_GUIDE.md) | Comprehensive documentation |
| [chat_schema.sql](chat_schema.sql) | SQLite table definitions |
| [ChatService.java](src/main/java/com/sajid/_207017_chashi_bhai/services/ChatService.java) | Main chat service implementation |

---

## ⚡ Quick Setup Checklist

- [ ] Run `chat_schema.sql` to create SQLite tables
- [ ] Firebase project configured with Firestore
- [ ] `firebase-credentials.json` in project root
- [ ] Firebase initialized in App.java startup
- [ ] ChatService callbacks connected in UI controllers

---

## 🔒 Security Rules (Firestore)

```javascript
match /conversations/{convId} {
  // Only participants can read/write
  allow read, write: if request.auth.uid in resource.data.participantIds;
  
  match /messages/{msgId} {
    // Only participants can access messages
    allow read, write: if request.auth.uid in 
      get(/databases/$(database)/documents/conversations/$(convId)).data.participantIds;
  }
}
```

---

*Quick Reference v1.0 - January 11, 2026*
