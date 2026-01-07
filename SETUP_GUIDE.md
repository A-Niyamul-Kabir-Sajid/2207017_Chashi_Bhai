# Database Setup Guide for Chashi Bhai App

## Overview
This application uses **two databases**:
1. **SQLite** - For local data storage and offline functionality
2. **Firebase Realtime Database** - For real-time features (chat, notifications, online status)

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

## Part 2: Firebase Setup (Real-time Database)

### 2.1 What You Need Outside VS Code

#### Step 1: Create Firebase Project
1. Go to https://console.firebase.google.com/
2. Click "Add project"
3. Enter project name: `chashi-bhai-app`
4. Follow the wizard (disable Google Analytics if not needed)
5. Click "Create project"

#### Step 2: Enable Realtime Database
1. In Firebase Console, click "Realtime Database" from left menu
2. Click "Create Database"
3. Choose location: `asia-southeast1` (Singapore - closest to Bangladesh)
4. Start in **Test Mode** (for development)
5. Click "Enable"

#### Step 3: Get Configuration Files

##### For Android (if you plan to make mobile app):
1. Go to Project Settings (gear icon)
2. Click "Add app" → Android
3. Enter package name: `com.sajid._207017_chashi_bhai`
4. Download `google-services.json`

##### For Java Desktop App:
1. Go to Project Settings → Service Accounts
2. Click "Generate new private key"
3. Save the JSON file as `firebase-credentials.json`
4. Put it in: `src/main/resources/`

#### Step 4: Set Security Rules
1. In Realtime Database, go to "Rules" tab
2. Replace with these rules:
```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null",
    
    "users": {
      "$userId": {
        ".read": true,
        ".write": "$userId === auth.uid"
      }
    },
    
    "crops": {
      ".read": true,
      "$cropId": {
        ".write": "auth != null && (data.child('farmerId').val() === auth.uid || !data.exists())"
      }
    },
    
    "conversations": {
      "$conversationId": {
        ".read": "auth != null && (data.child('participants').child(auth.uid).exists())",
        ".write": "auth != null && (data.child('participants').child(auth.uid).exists() || !data.exists())"
      }
    },
    
    "messages": {
      "$conversationId": {
        ".read": "auth != null && (root.child('conversations').child($conversationId).child('participants').child(auth.uid).exists())",
        ".write": "auth != null && (root.child('conversations').child($conversationId).child('participants').child(auth.uid).exists())"
      }
    },
    
    "notifications": {
      "$userId": {
        ".read": "$userId === auth.uid",
        ".write": "$userId === auth.uid"
      }
    }
  }
}
```

### 2.2 Add Firebase Dependencies to pom.xml
Add these to your `pom.xml`:
```xml
<!-- Firebase Admin SDK -->
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.2.0</version>
</dependency>

<!-- Gson for JSON parsing -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

### 2.3 Initialize Firebase in Your App
Create `FirebaseService.java` to initialize Firebase with your credentials.

---

## Part 3: Database Structure Explanation

### 3.1 How Product Quantity Works

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
- Real-time sync with Firebase

---

## Part 4: Testing Your Setup

### 4.1 Test SQLite
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

### 4.2 Test Firebase
```java
// Test Firebase connection:
FirebaseService.testConnection(
    success -> System.out.println("Firebase connected!"),
    error -> System.err.println("Firebase error: " + error)
);
```

---

## Part 5: Next Steps

### 5.1 What to Implement:

1. **DatabaseService.java** ✓ (Already exists)
   - Add methods for orders
   - Add quantity management

2. **FirebaseService.java** ⚠ (Need to create)
   - Initialize Firebase
   - Real-time listeners for chat
   - Sync data between SQLite and Firebase

3. **Order Management**:
   - Create order from buyer
   - Update crop quantity
   - Handle order status changes

4. **Chat System**:
   - Send/receive messages
   - Update conversation list
   - Mark messages as read

5. **Authentication**:
   - Phone + PIN login
   - OTP verification
   - Session management

---

## Part 6: Tools You Need to Install

### 6.1 Required Tools:

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
