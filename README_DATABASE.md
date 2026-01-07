# Database Implementation Summary

## ✅ What Has Been Created

### 1. Database Schema Files
- **`database_schema.sql`** - Complete SQLite schema with:
  - 9 main tables (users, crops, orders, conversations, messages, etc.)
  - Indexes for performance
  - Triggers for automatic quantity management
  - Views for complex queries
  - Sample data for testing

- **`firebase_structure.json`** - Firebase Realtime Database structure for:
  - Real-time chat
  - Online status
  - Notifications
  - Live order updates

### 2. Service Classes
- **`DatabaseInitializer.java`** - Initializes SQLite database on app startup
- **`FirebaseService.java`** - Handles real-time features (chat, notifications)
- **`DatabaseService.java`** - Already exists, handles SQLite operations

### 3. Documentation
- **`SETUP_GUIDE.md`** - Comprehensive setup guide with:
  - Step-by-step SQLite setup
  - Firebase project creation steps
  - Security rules configuration
  - Testing procedures
  - Tools you need to install

## 🎯 How the Product/Order System Works

### Example Workflow:

**Step 1: Farmer Posts Crop**
```
Farmer: রহিম মিয়া
Crop: ধান (Rice)
Initial Quantity: 10 kg
Available Quantity: 10 kg
Price: ৳45/kg
```

**Step 2: Buyer 1 Orders**
```
Buyer: করিম সাহেব
Orders: 2 kg of rice
→ Order created (order_number: ORD-20260107-0001)
→ Available quantity reduced: 10kg - 2kg = 8kg
```

**Step 3: Buyer 2 Orders**
```
Buyer: জামাল উদ্দিন
Orders: 3 kg of rice
→ Order created (order_number: ORD-20260107-0002)
→ Available quantity reduced: 8kg - 3kg = 5kg
```

**Result:**
- Farmer has **5 kg remaining** to sell
- Total orders: **2 orders** (2kg + 3kg = 5kg sold)
- Both buyers have their orders in `orders` table
- Farmer can track all orders in `orders` WHERE `farmer_id = 1`

### Order Status Flow:
```
new → accepted → in_transit → delivered → completed
  ↘
   rejected/cancelled (quantity restored)
```

## 📊 Database Tables Overview

### Core Tables:

1. **`users`** - Stores farmers and buyers
   - phone, pin, name, role, district, is_verified

2. **`crops`** - Farmer's products
   - farmer_id, name, category
   - **initial_quantity_kg** - Original amount posted
   - **available_quantity_kg** - Current available (auto-updated)
   - price_per_kg, status

3. **`orders`** - All purchase orders
   - order_number, crop_id, farmer_id, buyer_id
   - quantity_kg, price_per_kg, total_amount
   - status, payment_status
   - delivery_address, buyer_phone

4. **`conversations`** & **`messages`** - Chat system
   - Links farmers and buyers
   - Stores all messages
   - Unread count tracking

5. **`crop_photos`** - Multiple photos per crop

6. **`order_history`** - Logs all status changes

7. **`reviews`** - Ratings and feedback

8. **`notifications`** - System notifications

## 🔧 What You Need to Do

### In VS Code:
1. ✅ Add Firebase dependency to pom.xml (DONE)
2. ✅ Create FirebaseService.java (DONE)
3. ✅ Create DatabaseInitializer.java (DONE)
4. Run your app - database will auto-initialize

### Outside VS Code:
1. **Install DB Browser for SQLite**
   - Download: https://sqlitebrowser.org/
   - Use it to view your `data/chashi_bhai.db` file

2. **Create Firebase Project**
   - Go to: https://console.firebase.google.com/
   - Create new project: "chashi-bhai-app"
   - Enable Realtime Database
   - Download credentials JSON
   - Place in: `src/main/resources/firebase-credentials.json`

3. **Set Firebase Security Rules** (in Firebase Console)
   - Copy rules from `SETUP_GUIDE.md`
   - Paste in Firebase Console → Realtime Database → Rules

## 🚀 Quick Start

### Step 1: Initialize Database
Add to your `App.java` or `Launcher.java`:

```java
@Override
public void start(Stage stage) throws IOException {
    // Initialize databases
    DatabaseInitializer.initialize();
    DatabaseInitializer.testConnection();
    
    // Initialize Firebase (optional, for real-time features)
    FirebaseService.initialize();
    
    // Your existing code...
}
```

### Step 2: Test SQLite
After running your app, check:
- File `data/chashi_bhai.db` should be created
- Open it with DB Browser for SQLite
- You should see 4 sample users and 3 sample crops

### Step 3: Query Examples

**Get all active crops:**
```java
DatabaseService.executeQueryAsync(
    "SELECT * FROM crops WHERE status = 'active'",
    null,
    rs -> {
        while (rs.next()) {
            System.out.println(rs.getString("name") + " - " + 
                rs.getDouble("available_quantity_kg") + " kg");
        }
    },
    err -> err.printStackTrace()
);
```

**Create an order:**
```java
String sql = "INSERT INTO orders (order_number, crop_id, farmer_id, buyer_id, " +
             "quantity_kg, price_per_kg, total_amount, buyer_phone, buyer_name, status) " +
             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

Object[] params = {"ORD-20260107-0001", 1, 1, 2, 5.0, 45.0, 225.0, 
                   "01722222222", "করিম সাহেব", "new"};

DatabaseService.executeUpdateAsync(sql, params,
    rows -> System.out.println("Order created!"),
    err -> err.printStackTrace()
);
```

**Get farmer's orders:**
```java
DatabaseService.executeQueryAsync(
    "SELECT * FROM v_order_details WHERE farmer_id = ?",
    new Object[]{1},
    rs -> {
        while (rs.next()) {
            System.out.println("Order: " + rs.getString("order_number"));
            System.out.println("Buyer: " + rs.getString("buyer_name"));
            System.out.println("Quantity: " + rs.getDouble("quantity_kg") + " kg");
            System.out.println("Status: " + rs.getString("status"));
        }
    },
    err -> err.printStackTrace()
);
```

## 📝 Important Notes

### Automatic Quantity Management
The database has **triggers** that automatically:
- ✅ Reduce `available_quantity_kg` when order is accepted
- ✅ Restore quantity when order is cancelled/rejected
- ✅ Mark crop as "sold" when quantity reaches 0
- ✅ Log all order status changes

### Firebase vs SQLite
- **SQLite** - Main database, works offline, faster
- **Firebase** - Real-time features only (chat, online status, notifications)

### Sample Login Credentials
Use these to test:
```
Farmer 1:
Phone: 01711111111
PIN: 1234

Buyer 1:
Phone: 01722222222
PIN: 1234
```

## 🐛 Troubleshooting

**Database file not created?**
- Check console for errors
- Ensure `data/` folder has write permissions

**Triggers not working?**
- SQLite triggers are automatic, but check if schema loaded correctly
- View database in DB Browser to verify triggers exist

**Firebase not connecting?**
- Ensure `firebase-credentials.json` is in `src/main/resources/`
- Update database URL in `FirebaseService.java`
- Check Firebase Console for project status

## 📚 Next Steps

1. ✅ Database schema - DONE
2. ✅ Service classes - DONE
3. ⚠️ Update controllers to use new database structure
4. ⚠️ Implement order creation logic
5. ⚠️ Implement chat functionality
6. ⚠️ Add authentication with phone + PIN
7. ⚠️ Test with real data

## 🎨 Files Created

```
d:\Codes\KUET\2207017_Chashi_Bhai\2207017_Chashi_Bhai\
├── database_schema.sql          (Complete SQL schema)
├── firebase_structure.json      (Firebase data structure)
├── SETUP_GUIDE.md              (Detailed setup instructions)
├── README_DATABASE.md          (This file)
└── src/main/java/.../services/
    ├── DatabaseInitializer.java (Initializes SQLite)
    ├── FirebaseService.java     (Firebase operations)
    └── DatabaseService.java     (Already exists)
```

---

**Ready to use! Start your app and the database will initialize automatically.** 🚀
