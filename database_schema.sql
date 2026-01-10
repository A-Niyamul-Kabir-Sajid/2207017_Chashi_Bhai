-- SQLite Database Schema for Chashi Bhai (Farmer-Buyer Marketplace)
-- Author: Generated for 2207017_Chashi_Bhai
-- Date: 2026-01-07

-- ============================================
-- USERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone TEXT UNIQUE NOT NULL,
    pin TEXT NOT NULL,
    name TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('farmer', 'buyer')),
    district TEXT,
    upazila TEXT,
    village TEXT,
    nid TEXT,
    is_verified BOOLEAN DEFAULT 0,
    profile_photo TEXT,
    total_accepted_orders INTEGER DEFAULT 0,
    most_sold_crop TEXT,
    total_income REAL DEFAULT 0.0,
    rating REAL DEFAULT 0.0,
    total_buyer_orders INTEGER DEFAULT 0,
    most_bought_crop TEXT,
    total_expense REAL DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- ============================================
-- CROPS/PRODUCTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS crops (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_code TEXT UNIQUE NOT NULL, -- Auto-generated: CRP-YYYYMMDD-0001
    farmer_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    initial_quantity_kg REAL NOT NULL, -- Original quantity posted
    available_quantity_kg REAL NOT NULL, -- Current available quantity
    price_per_kg REAL NOT NULL,
    description TEXT,
    district TEXT NOT NULL,
    upazila TEXT,
    village TEXT,
    harvest_date DATE,
    status TEXT DEFAULT 'active' CHECK(status IN ('active', 'sold', 'expired', 'deleted')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (farmer_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for faster queries
CREATE INDEX IF NOT EXISTS idx_crops_farmer ON crops(farmer_id);
CREATE INDEX IF NOT EXISTS idx_crops_status ON crops(status);
CREATE INDEX IF NOT EXISTS idx_crops_category ON crops(category);
CREATE INDEX IF NOT EXISTS idx_crops_district ON crops(district);

-- ============================================
-- CROP PHOTOS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS crop_photos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    crop_id INTEGER NOT NULL,
    photo_path TEXT NOT NULL,
    photo_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (crop_id) REFERENCES crops(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_crop_photos_crop ON crop_photos(crop_id);

-- ============================================
-- ORDERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_number TEXT UNIQUE NOT NULL, -- e.g., "ORD-20260107-0001"
    crop_id INTEGER NOT NULL,
    farmer_id INTEGER NOT NULL,
    buyer_id INTEGER NOT NULL,
    quantity_kg REAL NOT NULL,
    price_per_kg REAL NOT NULL,
    total_amount REAL NOT NULL,
    delivery_address TEXT,
    delivery_district TEXT,
    delivery_upazila TEXT,
    buyer_phone TEXT NOT NULL,
    buyer_name TEXT NOT NULL,
    status TEXT DEFAULT 'new' CHECK(status IN ('new', 'accepted', 'rejected', 'in_transit', 'delivered', 'cancelled', 'completed')),
    payment_status TEXT DEFAULT 'pending' CHECK(payment_status IN ('pending', 'partial', 'paid', 'refunded')),
    payment_method TEXT CHECK(payment_method IN ('cash', 'bkash', 'nagad', 'rocket', 'bank')),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP,
    delivered_at TIMESTAMP,
    completed_at TIMESTAMP,
    FOREIGN KEY (crop_id) REFERENCES crops(id) ON DELETE CASCADE,
    FOREIGN KEY (farmer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for faster queries
CREATE INDEX IF NOT EXISTS idx_orders_crop ON orders(crop_id);
CREATE INDEX IF NOT EXISTS idx_orders_farmer ON orders(farmer_id);
CREATE INDEX IF NOT EXISTS idx_orders_buyer ON orders(buyer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_number ON orders(order_number);

-- ============================================
-- ORDER HISTORY TABLE (For tracking status changes)
-- ============================================
CREATE TABLE IF NOT EXISTS order_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    changed_by INTEGER, -- user_id who made the change
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_order_history_order ON order_history(order_id);

-- ============================================
-- CHATS/CONVERSATIONS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS conversations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user1_id INTEGER NOT NULL,
    user2_id INTEGER NOT NULL,
    crop_id INTEGER, -- Optional: which crop the chat is about
    last_message TEXT,
    last_message_time TIMESTAMP,
    unread_count_user1 INTEGER DEFAULT 0,
    unread_count_user2 INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (crop_id) REFERENCES crops(id) ON DELETE SET NULL,
    UNIQUE(user1_id, user2_id, crop_id)
);

CREATE INDEX IF NOT EXISTS idx_conversations_user1 ON conversations(user1_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user2 ON conversations(user2_id);

-- ============================================
-- MESSAGES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id INTEGER NOT NULL,
    sender_id INTEGER NOT NULL,
    receiver_id INTEGER NOT NULL,
    message_text TEXT,
    message_type TEXT DEFAULT 'text' CHECK(message_type IN ('text', 'image', 'file', 'location')),
    attachment_path TEXT,
    is_read BOOLEAN DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_receiver ON messages(receiver_id);

-- ============================================
-- REVIEWS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS reviews (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL,
    reviewer_id INTEGER NOT NULL,
    reviewee_id INTEGER NOT NULL,
    rating INTEGER NOT NULL CHECK(rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewee_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(order_id, reviewer_id)
);

CREATE INDEX IF NOT EXISTS idx_reviews_reviewee ON reviews(reviewee_id);

-- ============================================
-- NOTIFICATIONS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    type TEXT CHECK(type IN ('order', 'chat', 'review', 'system')),
    related_id INTEGER, -- order_id, message_id, etc.
    is_read BOOLEAN DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_read ON notifications(is_read);

-- ============================================
-- TRIGGERS
-- ============================================

-- Trigger to update available quantity when order is placed
CREATE TRIGGER IF NOT EXISTS update_crop_quantity_after_order
AFTER INSERT ON orders
WHEN NEW.status = 'accepted'
BEGIN
    UPDATE crops 
    SET available_quantity_kg = available_quantity_kg - NEW.quantity_kg,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.crop_id;
    
    -- Mark as sold if quantity reaches 0
    UPDATE crops 
    SET status = 'sold'
    WHERE id = NEW.crop_id AND available_quantity_kg <= 0;
END;

-- Trigger to restore quantity when order is cancelled
CREATE TRIGGER IF NOT EXISTS restore_crop_quantity_on_cancel
AFTER UPDATE ON orders
WHEN OLD.status IN ('new', 'accepted') AND NEW.status IN ('cancelled', 'rejected')
BEGIN
    UPDATE crops 
    SET available_quantity_kg = available_quantity_kg + OLD.quantity_kg,
        status = 'active',
        updated_at = CURRENT_TIMESTAMP
    WHERE id = OLD.crop_id;
END;

-- Trigger to log order status changes
CREATE TRIGGER IF NOT EXISTS log_order_status_change
AFTER UPDATE ON orders
WHEN OLD.status != NEW.status
BEGIN
    INSERT INTO order_history (order_id, status, notes)
    VALUES (NEW.id, NEW.status, 'Status changed from ' || OLD.status || ' to ' || NEW.status);
END;

-- Trigger to update conversation last message
CREATE TRIGGER IF NOT EXISTS update_conversation_last_message
AFTER INSERT ON messages
BEGIN
    UPDATE conversations
    SET last_message = NEW.message_text,
        last_message_time = NEW.created_at,
        updated_at = NEW.created_at,
        unread_count_user1 = CASE 
            WHEN NEW.receiver_id = user1_id THEN unread_count_user1 + 1 
            ELSE unread_count_user1 
        END,
        unread_count_user2 = CASE 
            WHEN NEW.receiver_id = user2_id THEN unread_count_user2 + 1 
            ELSE unread_count_user2 
        END
    WHERE id = NEW.conversation_id;
END;

-- ============================================
-- VIEWS FOR COMMON QUERIES
-- ============================================

-- View for crop listings with farmer info
CREATE VIEW IF NOT EXISTS v_crop_listings AS
SELECT 
    c.*,
    u.name as farmer_name,
    u.phone as farmer_phone,
    u.is_verified as farmer_verified,
    u.district as farmer_district,
    (SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as primary_photo,
    (SELECT COUNT(*) FROM orders WHERE crop_id = c.id) as total_orders
FROM crops c
JOIN users u ON c.farmer_id = u.id;

-- View for order details
CREATE VIEW IF NOT EXISTS v_order_details AS
SELECT 
    o.*,
    c.name as crop_name,
    c.category as crop_category,
    f.name as farmer_name,
    f.phone as farmer_phone,
    f.district as farmer_district,
    b.name as buyer_name,
    b.phone as buyer_phone,
    b.district as buyer_district,
    (SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as crop_photo
FROM orders o
JOIN crops c ON o.crop_id = c.id
JOIN users f ON o.farmer_id = f.id
JOIN users b ON o.buyer_id = b.id;

-- View for conversation list with user details
CREATE VIEW IF NOT EXISTS v_conversation_list AS
SELECT 
    c.*,
    u1.name as user1_name,
    u1.phone as user1_phone,
    u1.role as user1_role,
    u2.name as user2_name,
    u2.phone as user2_phone,
    u2.role as user2_role,
    cr.name as crop_name,
    cr.price_per_kg as crop_price
FROM conversations c
JOIN users u1 ON c.user1_id = u1.id
JOIN users u2 ON c.user2_id = u2.id
LEFT JOIN crops cr ON c.crop_id = cr.id;

-- ============================================
-- SAMPLE DATA (Optional - for testing)
-- ============================================

-- Insert sample users
INSERT OR IGNORE INTO users (phone, pin, name, role, district, is_verified) VALUES
('01711111111', '1234', 'রহিম মিয়া', 'farmer', 'ঢাকা', 1),
('01722222222', '1234', 'করিম সাহেব', 'buyer', 'চট্টগ্রাম', 1),
('01733333333', '1234', 'সালমা বেগম', 'farmer', 'রাজশাহী', 0),
('01744444444', '1234', 'জামাল উদ্দিন', 'buyer', 'খুলনা', 1);

-- Insert sample crops
INSERT OR IGNORE INTO crops (farmer_id, name, category, initial_quantity_kg, available_quantity_kg, price_per_kg, description, district, status) VALUES
(1, 'ধান (Rice)', 'শস্য', 100.0, 100.0, 45.0, 'উচ্চ মানের ধান', 'ঢাকা', 'active'),
(1, 'আলু (Potato)', 'সবজি', 50.0, 50.0, 30.0, 'তাজা আলু', 'ঢাকা', 'active'),
(3, 'টমেটো (Tomato)', 'সবজি', 30.0, 30.0, 60.0, 'টাটকা টমেটো', 'রাজশাহী', 'active');

-- ============================================
-- UTILITY FUNCTIONS
-- ============================================

-- Note: SQLite doesn't support stored procedures, but you can create these functions in your Java code

-- Function to generate order number (implement in Java):
-- Format: ORD-YYYYMMDD-XXXX (e.g., ORD-20260107-0001)

-- Function to check if user can place order:
-- 1. Check if crop has enough quantity
-- 2. Check if crop is active
-- 3. Check if buyer is not the farmer

-- Function to calculate statistics:
-- 1. Total sales for farmer
-- 2. Total orders for buyer
-- 3. Average rating for user
