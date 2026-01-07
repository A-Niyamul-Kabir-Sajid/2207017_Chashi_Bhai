# Database Schema - Chashi Bhai

## 📊 Entity Relationship Diagram

```
┌─────────────────┐
│     USERS       │
├─────────────────┤
│ id (PK)         │
│ phone (unique)  │
│ pin             │
│ name            │
│ role            │◄──────────┐
│ district        │           │
│ is_verified     │           │
│ profile_photo   │           │
└────────┬────────┘           │
         │                    │
         │ 1:N                │
         ▼                    │
┌─────────────────┐           │
│     CROPS       │           │
├─────────────────┤           │
│ id (PK)         │           │
│ farmer_id (FK)  │           │
│ name            │           │
│ category        │           │
│ initial_qty_kg  │           │
│ available_qty_kg│◄──┐       │
│ price_per_kg    │   │       │
│ description     │   │       │
│ district        │   │       │
│ status          │   │       │
└────────┬────────┘   │       │
         │            │       │
         │ 1:N        │       │
         ▼            │       │
┌─────────────────┐   │       │
│  CROP_PHOTOS    │   │       │
├─────────────────┤   │       │
│ id (PK)         │   │       │
│ crop_id (FK)    │   │       │
│ photo_path      │   │       │
│ photo_order     │   │       │
└─────────────────┘   │       │
                      │       │
         ┌────────────┘       │
         │                    │
         │ Auto-update        │
         │ via Triggers       │
         │                    │
┌─────────▼──────────┐        │
│     ORDERS         │        │
├────────────────────┤        │
│ id (PK)            │        │
│ order_number       │        │
│ crop_id (FK)       │        │
│ farmer_id (FK) ────┼────────┘
│ buyer_id (FK) ─────┼────────┐
│ quantity_kg        │        │
│ price_per_kg       │        │
│ total_amount       │        │
│ status             │        │
│ payment_status     │        │
└────────┬───────────┘        │
         │                    │
         │ 1:N                │
         ▼                    │
┌─────────────────┐           │
│ ORDER_HISTORY   │           │
├─────────────────┤           │
│ id (PK)         │           │
│ order_id (FK)   │           │
│ status          │           │
│ changed_by (FK) │           │
│ notes           │           │
│ created_at      │           │
└─────────────────┘           │
                              │
         ┌────────────────────┘
         │
         │ M:N (through conversations)
         ▼
┌─────────────────┐
│ CONVERSATIONS   │
├─────────────────┤
│ id (PK)         │
│ user1_id (FK)   │
│ user2_id (FK)   │
│ crop_id (FK)    │
│ last_message    │
│ unread_count    │
└────────┬────────┘
         │
         │ 1:N
         ▼
┌─────────────────┐
│   MESSAGES      │
├─────────────────┤
│ id (PK)         │
│ conversation_id │
│ sender_id (FK)  │
│ receiver_id (FK)│
│ message_text    │
│ message_type    │
│ is_read         │
└─────────────────┘

┌─────────────────┐
│    REVIEWS      │
├─────────────────┤
│ id (PK)         │
│ order_id (FK)   │
│ reviewer_id (FK)│
│ reviewee_id (FK)│
│ rating (1-5)    │
│ comment         │
└─────────────────┘

┌─────────────────┐
│ NOTIFICATIONS   │
├─────────────────┤
│ id (PK)         │
│ user_id (FK)    │
│ title           │
│ message         │
│ type            │
│ is_read         │
└─────────────────┘
```

## 📋 Table Details

### 1. users
**Purpose**: Store farmer and buyer accounts

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment ID |
| phone | TEXT UNIQUE | Phone number (login) |
| pin | TEXT | 4-digit PIN (hashed) |
| name | TEXT | User's full name |
| role | TEXT | "farmer" or "buyer" |
| district | TEXT | District name |
| upazila | TEXT | Upazila name |
| village | TEXT | Village name |
| nid | TEXT | National ID |
| is_verified | BOOLEAN | Verification status |
| profile_photo | TEXT | Photo path |
| created_at | TIMESTAMP | Registration date |
| updated_at | TIMESTAMP | Last update |

**Indexes**: phone, role

---

### 2. crops
**Purpose**: Store farmer's products/crops

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment ID |
| farmer_id | INTEGER FK | Reference to users |
| name | TEXT | Crop name (e.g., "ধান") |
| category | TEXT | Category (শস্য, সবজি) |
| initial_quantity_kg | REAL | Original quantity posted |
| available_quantity_kg | REAL | Current available (auto-updated) |
| price_per_kg | REAL | Price per kilogram |
| description | TEXT | Crop description |
| district | TEXT | Location |
| upazila | TEXT | Sub-location |
| village | TEXT | Village |
| harvest_date | DATE | Harvest date |
| status | TEXT | active/sold/expired/deleted |
| created_at | TIMESTAMP | Post date |
| updated_at | TIMESTAMP | Last update |

**Indexes**: farmer_id, status, category, district

**Business Logic**:
- `available_quantity_kg` is auto-reduced when orders are accepted
- Status changes to "sold" when `available_quantity_kg` reaches 0
- Quantity is restored if order is cancelled

---

### 3. orders
**Purpose**: Store purchase orders from buyers

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment ID |
| order_number | TEXT UNIQUE | Format: ORD-YYYYMMDD-XXXX |
| crop_id | INTEGER FK | Reference to crops |
| farmer_id | INTEGER FK | Seller |
| buyer_id | INTEGER FK | Purchaser |
| quantity_kg | REAL | Ordered quantity |
| price_per_kg | REAL | Price at order time |
| total_amount | REAL | quantity × price |
| delivery_address | TEXT | Delivery location |
| delivery_district | TEXT | Delivery district |
| delivery_upazila | TEXT | Delivery upazila |
| buyer_phone | TEXT | Buyer contact |
| buyer_name | TEXT | Buyer name |
| status | TEXT | Order status |
| payment_status | TEXT | Payment status |
| payment_method | TEXT | cash/bkash/nagad/rocket |
| notes | TEXT | Additional notes |
| created_at | TIMESTAMP | Order date |
| accepted_at | TIMESTAMP | Acceptance date |
| delivered_at | TIMESTAMP | Delivery date |
| completed_at | TIMESTAMP | Completion date |

**Indexes**: crop_id, farmer_id, buyer_id, status, order_number

**Status Flow**:
1. `new` - Order placed by buyer
2. `accepted` - Farmer accepts (quantity reduced)
3. `rejected` - Farmer rejects (quantity restored)
4. `in_transit` - Being delivered
5. `delivered` - Reached buyer
6. `cancelled` - Cancelled (quantity restored)
7. `completed` - Transaction finished

---

### 4. crop_photos
**Purpose**: Store multiple photos for each crop

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment ID |
| crop_id | INTEGER FK | Reference to crops |
| photo_path | TEXT | File path/URL |
| photo_order | INTEGER | Display order (0, 1, 2...) |
| created_at | TIMESTAMP | Upload date |

**Indexes**: crop_id

---

### 5. conversations
**Purpose**: Chat conversations between users

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment ID |
| user1_id | INTEGER FK | First user |
| user2_id | INTEGER FK | Second user |
| crop_id | INTEGER FK | Related crop (optional) |
| last_message | TEXT | Last message text |
| last_message_time | TIMESTAMP | Last message time |
| unread_count_user1 | INTEGER | Unread for user1 |
| unread_count_user2 | INTEGER | Unread for user2 |
| created_at | TIMESTAMP | Conversation start |
| updated_at | TIMESTAMP | Last update |

**Indexes**: user1_id, user2_id

**Constraint**: UNIQUE(user1_id, user2_id, crop_id)

---

### 6. messages
**Purpose**: Individual chat messages

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment ID |
| conversation_id | INTEGER FK | Reference to conversations |
| sender_id | INTEGER FK | Message sender |
| receiver_id | INTEGER FK | Message receiver |
| message_text | TEXT | Message content |
| message_type | TEXT | text/image/file/location |
| attachment_path | TEXT | File path (if applicable) |
| is_read | BOOLEAN | Read status |
| created_at | TIMESTAMP | Send time |
| read_at | TIMESTAMP | Read time |

**Indexes**: conversation_id, sender_id, receiver_id

---

### 7. order_history
**Purpose**: Log all order status changes

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment ID |
| order_id | INTEGER FK | Reference to orders |
| status | TEXT | New status |
| changed_by | INTEGER FK | User who changed |
| notes | TEXT | Change reason/notes |
| created_at | TIMESTAMP | Change time |

**Indexes**: order_id

**Auto-populated by trigger** when order status changes

---

### 8. reviews
**Purpose**: Ratings and feedback

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment ID |
| order_id | INTEGER FK | Related order |
| reviewer_id | INTEGER FK | Who gave review |
| reviewee_id | INTEGER FK | Who received review |
| rating | INTEGER | 1 to 5 stars |
| comment | TEXT | Review text |
| created_at | TIMESTAMP | Review date |

**Indexes**: reviewee_id

**Constraint**: UNIQUE(order_id, reviewer_id) - One review per order per user

---

### 9. notifications
**Purpose**: System and user notifications

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment ID |
| user_id | INTEGER FK | Recipient |
| title | TEXT | Notification title |
| message | TEXT | Notification content |
| type | TEXT | order/chat/review/system |
| related_id | INTEGER | Related entity ID |
| is_read | BOOLEAN | Read status |
| created_at | TIMESTAMP | Notification time |

**Indexes**: user_id, is_read

---

## ⚡ Automatic Triggers

### 1. update_crop_quantity_after_order
**Event**: AFTER INSERT on orders (when status = 'accepted')

**Action**:
1. Reduces `available_quantity_kg` in crops table
2. Marks crop as "sold" if quantity reaches 0

**Example**:
```sql
Crop: 10 kg rice
Order: 3 kg accepted
→ Available: 7 kg
```

---

### 2. restore_crop_quantity_on_cancel
**Event**: AFTER UPDATE on orders (status changes to cancelled/rejected)

**Action**:
1. Restores `available_quantity_kg` in crops table
2. Changes crop status back to "active"

**Example**:
```sql
Crop: 7 kg rice (after order)
Order: 3 kg cancelled
→ Available: 10 kg (restored)
```

---

### 3. log_order_status_change
**Event**: AFTER UPDATE on orders (status changes)

**Action**: Inserts entry in `order_history` table

**Example**:
```sql
Order status: new → accepted
→ History entry: "Status changed from new to accepted"
```

---

### 4. update_conversation_last_message
**Event**: AFTER INSERT on messages

**Action**:
1. Updates `last_message` and `last_message_time` in conversations
2. Increments `unread_count` for receiver

---

## 📊 Views (Precomputed Queries)

### 1. v_crop_listings
**Purpose**: Crops with farmer information

**Columns**: All crop columns + farmer_name, farmer_phone, farmer_verified, primary_photo, total_orders

**Usage**: Display crop feed with farmer details

---

### 2. v_order_details
**Purpose**: Orders with complete information

**Columns**: All order columns + crop_name, farmer_name, buyer_name, crop_photo

**Usage**: Display order details in dashboards

---

### 3. v_conversation_list
**Purpose**: Conversations with user details

**Columns**: All conversation columns + user1_name, user2_name, user1_role, user2_role, crop_name

**Usage**: Display chat list with names and context

---

## 🔍 Common Queries

### Get all active crops in a district
```sql
SELECT * FROM v_crop_listings 
WHERE status = 'active' 
AND district = 'ঢাকা'
ORDER BY created_at DESC;
```

### Get farmer's orders
```sql
SELECT * FROM v_order_details 
WHERE farmer_id = ?
ORDER BY created_at DESC;
```

### Get buyer's orders
```sql
SELECT * FROM v_order_details 
WHERE buyer_id = ?
ORDER BY created_at DESC;
```

### Get user's conversations
```sql
SELECT * FROM v_conversation_list 
WHERE user1_id = ? OR user2_id = ?
ORDER BY last_message_time DESC;
```

### Get messages in a conversation
```sql
SELECT * FROM messages 
WHERE conversation_id = ?
ORDER BY created_at ASC;
```

---

## 📈 Sample Data

The schema includes sample data for testing:

**Users**:
- Farmer: রহিম মিয়া (01711111111, PIN: 1234)
- Buyer: করিম সাহেব (01722222222, PIN: 1234)
- Farmer: সালমা বেগম (01733333333, PIN: 1234)
- Buyer: জামাল উদ্দিন (01744444444, PIN: 1234)

**Crops**:
- 100 kg ধান (Rice) @ ৳45/kg - ঢাকা
- 50 kg আলু (Potato) @ ৳30/kg - ঢাকা
- 30 kg টমেটো (Tomato) @ ৳60/kg - রাজশাহী

---

**Full schema file**: [database_schema.sql](../database_schema.sql)
