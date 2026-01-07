# Database Setup Guide for Chashi Bhai App

## Overview
This application uses **SQLite** for local data storage and offline functionality.

---

## Part 1: SQLite Setup (Local Database)

### 1.1 What You Need
- SQLite JDBC Driver (already in your `pom.xml`)
- The `database_schema.sql` file (created)

### 1.2 Setup Steps in VS Code

#### Step 1: Verify Dependencies
Check your `pom.xml` has this dependency:
```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.44.1.0</version>
</dependency>
```

#### Step 2: Database Location
The SQLite database file will be created at:
```
d:\Codes\KUET\2207017_Chashi_Bhai\2207017_Chashi_Bhai\data\chashi_bhai.db
```

#### Step 3: Initialize Database
Your `DatabaseService.java` should:
1. Create the database file on first run
2. Execute the `database_schema.sql` file to create tables
3. Use connection pooling for better performance

### 1.3 Testing SQLite
After running your app:
1. Check if `data/chashi_bhai.db` file is created
2. You can view the database using:
   - **DB Browser for SQLite** (https://sqlitebrowser.org/) - Download and install
   - Or VS Code extension: "SQLite Viewer"

---

## Part 2: Database Structure Explanation

### 2.1 How Product Quantity Works

#### Example Scenario:
1. **Farmer posts crop**: 
   - `initial_quantity_kg = 10`
   - `available_quantity_kg = 10`

2. **Buyer 1 orders 2kg**:
   - Order created: `quantity_kg = 2`
   - Crop updated: `available_quantity_kg = 8`

3. **Buyer 2 orders 3kg**:
   - Order created: `quantity_kg = 3`
   - Crop updated: `available_quantity_kg = 5`

4. **Result**:
   - Farmer has `5kg remaining` to sell
   - Total orders: 2 orders (2kg + 3kg = 5kg sold)

### 3.2 Order Status Flow
```
new → accepted → in_transit → delivered → completed
  ↘
   rejected/cancelled
```

### 3.3 Important Tables

#### `crops` table:
- Stores farmer's products
- Tracks `available_quantity_kg` (decreases with orders)
- Status: active, sold, expired, deleted

#### `orders` table:
- Stores all orders from buyers
- Links to crop, farmer, and buyer
- Tracks quantity and payment

#### `conversations` & `messages`:
- Enable chat between farmers and buyers
- Messages stored in SQLite

---

## Part 3: Testing Your Setup

### 3.1 Test SQLite
```java
// In your DatabaseService initialization:
DatabaseService.executeQuery(
    "SELECT COUNT(*) as count FROM users",
    rs -> {
        if (rs.next()) {
            System.out.println("Users in database: " + rs.getInt("count"));
        }
    },
    err -> err.printStackTrace()
);
```

---

## Part 4: Next Steps

### 4.1 What to Implement:

1. **DatabaseService.java** ✓ (Already exists)
   - Methods for orders
   - Quantity management
   - Chat operations

2. **Order Management**:
   - Create order from buyer
   - Update crop quantity
   - Handle order status changes

3. **Chat System**:
   - Send/receive messages
   - Update conversation list
   - Mark messages as read

4. **Authentication**:
   - Phone + PIN login
   - OTP verification
   - Session management

---

## Part 5: Tools You Need to Install

### 5.1 Required Tools:

1. **DB Browser for SQLite** (Free)
   - Download: https://sqlitebrowser.org/
   - Purpose: View and edit SQLite database

2. **Postman** (Optional, for API testing)
   - Download: https://www.postman.com/

3. **Firebase Console** (Web-based)
   - No installation needed
   - Access: https://console.firebase.google.com/

### 6.2 VS Code Extensions (Recommended):

1. **SQLite Viewer**
   - Install from VS Code marketplace
   - View `.db` files directly in VS Code

2. **Firebase Explorer**
   - Install from VS Code marketplace
   - Browse Firebase data from VS Code

---

## Part 7: Common Issues & Solutions

### Issue 1: SQLite file locked
**Solution**: Close DB Browser before running your app

### Issue 2: Firebase credentials not found
**Solution**: Make sure `firebase-credentials.json` is in `src/main/resources/`

### Issue 3: Trigger not working
**Solution**: SQLite triggers need proper setup, check syntax

### Issue 4: Quantity not updating
**Solution**: Check trigger execution and transaction handling

---

## Need Help?

If you encounter any issues:
1. Check console logs for errors
2. Verify database file exists
3. Test queries individually
4. Check Firebase console for data

---

## Summary Checklist

- [ ] SQLite database created at `data/chashi_bhai.db`
- [ ] All tables created from `database_schema.sql`
- [ ] Firebase project created
- [ ] Firebase credentials downloaded
- [ ] Firebase dependencies added to `pom.xml`
- [ ] Security rules set in Firebase Console
- [ ] DB Browser for SQLite installed
- [ ] Test data inserted successfully
- [ ] DatabaseService methods working
- [ ] FirebaseService initialized

---

**Note**: Start with SQLite first, get it working, then add Firebase for real-time features!
