-- ============================================
-- CHAT SYSTEM DATABASE SCHEMA
-- SQLite Tables for Real-Time Chat
-- ============================================

-- Drop existing tables if recreating
-- DROP TABLE IF EXISTS messages;
-- DROP TABLE IF EXISTS conversations;

-- ============================================
-- CONVERSATIONS TABLE
-- Stores chat threads between two users
-- ============================================
CREATE TABLE IF NOT EXISTS conversations (
    -- Primary Key: Auto-incrementing local ID
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    
    -- Firebase document ID for cloud sync
    firebase_id TEXT UNIQUE,
    
    -- Participant IDs (always store smaller ID as user1_id for consistency)
    user1_id INTEGER NOT NULL,
    user2_id INTEGER NOT NULL,
    
    -- Optional: Context for the conversation (e.g., crop being discussed)
    crop_id INTEGER,
    
    -- Denormalized fields for quick display (avoids JOINs)
    user1_name TEXT,
    user2_name TEXT,
    crop_name TEXT,
    
    -- Last message preview for conversation list
    last_message TEXT,
    last_message_time TIMESTAMP,
    last_sender_id INTEGER,
    
    -- Unread count for the current user viewing
    unread_count INTEGER DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Sync status with Firestore
    -- 'pending' = needs to be synced
    -- 'synced' = successfully synced
    -- 'error' = sync failed, needs retry
    sync_status TEXT DEFAULT 'pending',
    
    -- Foreign key constraints
    FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (crop_id) REFERENCES crops(id) ON DELETE SET NULL,
    
    -- Ensure unique conversation between any two users
    -- user1_id is always < user2_id for consistency
    UNIQUE(user1_id, user2_id),
    
    -- Constraint: user1_id must be less than user2_id
    CHECK(user1_id < user2_id)
);

-- Index for fast lookup by either participant
CREATE INDEX IF NOT EXISTS idx_conversations_user1 
    ON conversations(user1_id);

CREATE INDEX IF NOT EXISTS idx_conversations_user2 
    ON conversations(user2_id);

-- Index for listing conversations by recency (for chat list screen)
CREATE INDEX IF NOT EXISTS idx_conversations_last_message 
    ON conversations(last_message_time DESC);

-- Index for finding conversation by firebase ID
CREATE INDEX IF NOT EXISTS idx_conversations_firebase 
    ON conversations(firebase_id);

-- Index for sync status (to find pending syncs)
CREATE INDEX IF NOT EXISTS idx_conversations_sync 
    ON conversations(sync_status) WHERE sync_status != 'synced';


-- ============================================
-- MESSAGES TABLE
-- Stores individual chat messages
-- ============================================
CREATE TABLE IF NOT EXISTS messages (
    -- Primary Key: Auto-incrementing local ID
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    
    -- Firebase document ID for cloud sync
    firebase_id TEXT UNIQUE,
    
    -- Reference to conversation (local SQLite ID)
    conversation_id INTEGER NOT NULL,
    
    -- Sender information
    sender_id INTEGER NOT NULL,
    sender_name TEXT,
    
    -- Message content
    message_text TEXT NOT NULL,
    
    -- Message type
    -- 'text' = plain text message
    -- 'image' = image attachment
    -- 'crop_link' = link to a crop listing
    -- 'location' = GPS coordinates
    message_type TEXT DEFAULT 'text',
    
    -- Optional: Attachment URL (for images stored in Firebase Storage)
    attachment_url TEXT,
    
    -- Optional: Reference to a crop (for crop_link type)
    crop_reference_id INTEGER,
    
    -- Read status
    is_read INTEGER DEFAULT 0,  -- 0 = unread, 1 = read
    read_at TIMESTAMP,
    
    -- Delivery status
    -- 'sending' = message being sent
    -- 'sent' = message sent to server
    -- 'delivered' = message delivered to recipient device
    -- 'read' = message read by recipient
    -- 'failed' = message failed to send
    status TEXT DEFAULT 'sending',
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Sync status with Firestore
    sync_status TEXT DEFAULT 'pending',
    
    -- Last sync attempt (for retry logic)
    last_sync_attempt TIMESTAMP,
    sync_attempts INTEGER DEFAULT 0,
    
    -- Foreign key constraints
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (crop_reference_id) REFERENCES crops(id) ON DELETE SET NULL
);

-- Index for fetching messages in a conversation (ordered by time)
CREATE INDEX IF NOT EXISTS idx_messages_conversation_time 
    ON messages(conversation_id, created_at ASC);

-- Index for finding unread messages
CREATE INDEX IF NOT EXISTS idx_messages_unread 
    ON messages(conversation_id, is_read) WHERE is_read = 0;

-- Index for sync status (to find pending syncs)
CREATE INDEX IF NOT EXISTS idx_messages_sync 
    ON messages(sync_status) WHERE sync_status != 'synced';

-- Index for finding messages by firebase ID (for deduplication)
CREATE INDEX IF NOT EXISTS idx_messages_firebase 
    ON messages(firebase_id);

-- Index for finding failed messages (for retry)
CREATE INDEX IF NOT EXISTS idx_messages_failed 
    ON messages(status) WHERE status = 'failed';


-- ============================================
-- HELPER VIEWS
-- ============================================

-- View: Get all conversations for a specific user with other user's info
CREATE VIEW IF NOT EXISTS v_user_conversations AS
SELECT 
    c.id,
    c.firebase_id,
    c.user1_id,
    c.user2_id,
    c.crop_id,
    c.crop_name,
    c.last_message,
    c.last_message_time,
    c.last_sender_id,
    c.unread_count,
    c.created_at,
    c.updated_at,
    -- Determine the "other" user based on who's viewing
    c.user1_name,
    c.user2_name
FROM conversations c;


-- View: Recent messages with conversation info
CREATE VIEW IF NOT EXISTS v_recent_messages AS
SELECT 
    m.id,
    m.firebase_id,
    m.conversation_id,
    m.sender_id,
    m.sender_name,
    m.message_text,
    m.message_type,
    m.is_read,
    m.status,
    m.created_at,
    c.user1_id,
    c.user2_id,
    c.crop_id
FROM messages m
JOIN conversations c ON m.conversation_id = c.id
ORDER BY m.created_at DESC;


-- ============================================
-- TRIGGERS
-- ============================================

-- Trigger: Update conversation's last_message when a new message is inserted
CREATE TRIGGER IF NOT EXISTS trg_update_conversation_on_message
AFTER INSERT ON messages
BEGIN
    UPDATE conversations
    SET 
        last_message = CASE 
            WHEN LENGTH(NEW.message_text) > 50 
            THEN SUBSTR(NEW.message_text, 1, 50) || '...'
            ELSE NEW.message_text
        END,
        last_message_time = NEW.created_at,
        last_sender_id = NEW.sender_id,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.conversation_id;
END;


-- Trigger: Update updated_at timestamp when conversation is modified
CREATE TRIGGER IF NOT EXISTS trg_conversation_updated
AFTER UPDATE ON conversations
BEGIN
    UPDATE conversations
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.id AND OLD.updated_at = NEW.updated_at;
END;


-- ============================================
-- USEFUL QUERIES (Examples)
-- ============================================

-- Query: Find or create conversation between two users
-- (Use in application code)
/*
-- Step 1: Ensure user1_id < user2_id
-- If currentUserId = 5 and otherUserId = 2
-- Then user1_id = 2, user2_id = 5

SELECT * FROM conversations 
WHERE user1_id = MIN(?, ?) AND user2_id = MAX(?, ?);

-- If not found, INSERT new conversation
INSERT INTO conversations (user1_id, user2_id, user1_name, user2_name, crop_id, crop_name)
VALUES (MIN(?, ?), MAX(?, ?), ?, ?, ?, ?);
*/


-- Query: Get all conversations for a user, sorted by recency
/*
SELECT 
    c.*,
    CASE WHEN c.user1_id = ? THEN c.user2_name ELSE c.user1_name END as other_user_name,
    CASE WHEN c.user1_id = ? THEN c.user2_id ELSE c.user1_id END as other_user_id,
    (SELECT COUNT(*) FROM messages m 
     WHERE m.conversation_id = c.id 
     AND m.is_read = 0 
     AND m.sender_id != ?) as unread_count
FROM conversations c
WHERE c.user1_id = ? OR c.user2_id = ?
ORDER BY c.last_message_time DESC;
*/


-- Query: Get messages for a conversation with pagination
/*
SELECT * FROM messages
WHERE conversation_id = ?
ORDER BY created_at ASC
LIMIT ? OFFSET ?;
*/


-- Query: Mark all messages in a conversation as read
/*
UPDATE messages
SET is_read = 1, read_at = CURRENT_TIMESTAMP, status = 'read'
WHERE conversation_id = ?
  AND sender_id != ?  -- Only mark other user's messages
  AND is_read = 0;
*/


-- Query: Get count of unread messages for a user
/*
SELECT COUNT(*) as total_unread
FROM messages m
JOIN conversations c ON m.conversation_id = c.id
WHERE (c.user1_id = ? OR c.user2_id = ?)
  AND m.sender_id != ?
  AND m.is_read = 0;
*/


-- Query: Get pending messages to sync to Firestore
/*
SELECT m.*, c.firebase_id as conversation_firebase_id
FROM messages m
JOIN conversations c ON m.conversation_id = c.id
WHERE m.sync_status = 'pending'
   OR (m.sync_status = 'error' AND m.sync_attempts < 3)
ORDER BY m.created_at ASC;
*/


-- Query: Update message sync status after successful sync
/*
UPDATE messages
SET firebase_id = ?,
    sync_status = 'synced',
    status = 'sent'
WHERE id = ?;
*/


-- Query: Search messages in conversations
/*
SELECT m.*, c.user1_name, c.user2_name
FROM messages m
JOIN conversations c ON m.conversation_id = c.id
WHERE (c.user1_id = ? OR c.user2_id = ?)
  AND m.message_text LIKE '%' || ? || '%'
ORDER BY m.created_at DESC
LIMIT 50;
*/
