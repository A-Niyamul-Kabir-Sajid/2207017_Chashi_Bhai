# 🔥 Firebase Setup Guide - Complete Instructions

## Overview
Your app now has **dual database architecture**:
- **SQLite (Local)**: Primary offline database, always available
- **Firestore (Cloud)**: Optional cloud sync for real-time features

## Prerequisites
- Google account
- Internet connection
- 10-15 minutes

---

## Step 1: Create Firebase Project

### 1.1 Go to Firebase Console
Visit: https://console.firebase.google.com/

### 1.2 Create New Project
1. Click **"Add project"** or **"Create a project"**
2. Enter project name: `chashi-bhai` (or your preferred name)
3. Click **Continue**

### 1.3 Google Analytics (Optional)
- **Recommended**: Disable Google Analytics (not needed for this project)
- Or enable if you want usage analytics
- Click **Create project**
- Wait 30-60 seconds for project creation

### 1.4 Open Your Project
- Click **Continue** once setup completes
- You'll see the Firebase Console dashboard

---

## Step 2: Enable Firestore Database

### 2.1 Navigate to Firestore
1. In left sidebar, click **"Build"** → **"Firestore Database"**
2. Click **"Create database"**

### 2.2 Choose Security Rules
**Select: "Start in test mode"**
- Allows read/write access for 30 days
- Perfect for development
- We'll add security rules later

Click **Next**

### 2.3 Choose Location
**Recommended locations:**
- `asia-south1` (Mumbai) - for Bangladesh/India
- `us-central1` (Iowa) - good global default
- `europe-west1` (Belgium) - for Europe

⚠️ **Location cannot be changed later!**

Click **Enable**

Wait 1-2 minutes for Firestore to initialize.

---

## Step 3: Generate Service Account Credentials

### 3.1 Go to Project Settings
1. Click **⚙️ gear icon** (top left, next to "Project Overview")
2. Select **"Project settings"**

### 3.2 Navigate to Service Accounts
1. Click **"Service accounts"** tab
2. You'll see: "Firebase Admin SDK"

### 3.3 Generate Private Key
1. Scroll down to **"Admin SDK configuration snippet"**
2. Select **Java** from language dropdown
3. Click **"Generate new private key"** button
4. Click **"Generate key"** in confirmation dialog
5. A JSON file will download automatically

### 3.4 Rename and Move File
1. Downloaded file name will be like: `chashi-bhai-xxxxx-firebase-adminsdk-yyyyy-zzzzzzzzzz.json`
2. **Rename** it to: `firebase-credentials.json`
3. **Move** it to your project root directory:
   ```
   D:\Codes\KUET\2207017_Chashi_Bhai\2207017_Chashi_Bhai\
   ```

**Final location:**
```
D:\Codes\KUET\2207017_Chashi_Bhai\2207017_Chashi_Bhai\firebase-credentials.json
```

⚠️ **SECURITY WARNING**: 
- NEVER commit this file to Git
- NEVER share this file publicly
- It contains secret keys that control your Firebase project

---

## Step 4: Verify Your Setup

### 4.1 Check File Structure
Your project should now have:
```
2207017_Chashi_Bhai/
├── firebase-credentials.json  ← NEW FILE
├── pom.xml
├── src/
├── data/
└── ...
```

### 4.2 Compile Project
Open terminal in VS Code:
```powershell
mvn clean compile
```

Should complete without errors.

### 4.3 Run Application
1. Press `F5` or click Run button in VS Code
2. Check console output for:
   ```
   ✅ Firebase initialized successfully with Firestore!
   ✅ Firebase cloud sync enabled
   ```

### 4.4 Offline Mode (If No Credentials)
If credentials missing, you'll see:
```
⚠️ Firebase not configured - running in offline mode
   To enable cloud sync, see FIREBASE_SETUP.md
```
**This is OK!** App works perfectly offline with SQLite.

---

## Step 5: Configure Security Rules (Before Production)

### 5.1 Go to Firestore Database
Firebase Console → Build → Firestore Database → Rules tab

### 5.2 Replace Default Rules
Replace the test mode rules with:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Users collection - users can read all, write only their own
    match /users/{userId} {
      allow read: if true;  // Public profiles
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Crops collection - read all active, write only own crops
    match /crops/{cropId} {
      allow read: if true;  // Public marketplace
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && 
                               resource.data.farmer_id == request.auth.uid;
    }
    
    // Orders collection - involved parties only
    match /orders/{orderId} {
      allow read: if request.auth != null && 
                     (resource.data.farmer_id == request.auth.uid ||
                      resource.data.buyer_id == request.auth.uid);
      allow create: if request.auth != null;
      allow update: if request.auth != null && 
                       (resource.data.farmer_id == request.auth.uid ||
                        resource.data.buyer_id == request.auth.uid);
    }
    
    // Messages - conversation participants only
    match /messages/{messageId} {
      allow read, write: if request.auth != null && 
                            (resource.data.sender_id == request.auth.uid ||
                             resource.data.receiver_id == request.auth.uid);
    }
    
    // All other collections - authenticated users only
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

Click **Publish**

---

## Step 6: Test Firebase Connectivity (Optional)

Create a test file: `TestFirebase.java`

```java
package com.sajid._207017_chashi_bhai;

import com.sajid._207017_chashi_bhai.services.FirebaseService;

public class TestFirebase {
    public static void main(String[] args) {
        try {
            FirebaseService firebase = FirebaseService.getInstance();
            firebase.initialize();
            
            System.out.println("✅ Firebase connection successful!");
            System.out.println("✅ Firestore: " + firebase.getFirestore());
            
            firebase.shutdown();
            System.exit(0);
            
        } catch (Exception e) {
            System.err.println("❌ Firebase connection failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

Run it:
```powershell
mvn compile
java -cp "target/classes;target/dependency/*" com.sajid._207017_chashi_bhai.TestFirebase
```

---

## Troubleshooting

### Error: "credentials file not found"
**Solution:**
1. Check file exists: `firebase-credentials.json` in project root
2. Check file name is exact (no extra spaces)
3. Check file location matches project root

### Error: "PERMISSION_DENIED"
**Solution:**
1. Check Firestore is enabled in Firebase Console
2. Check security rules allow test mode
3. Wait 1-2 minutes after changing rules

### Error: "Invalid credentials"
**Solution:**
1. Re-download service account key from Firebase Console
2. Ensure downloaded file is valid JSON
3. Check file isn't corrupted

### App Says "Running in offline mode"
**This is normal if:**
- No credentials file exists
- Credentials path is wrong
- Firebase not configured yet

**App still works!** SQLite handles all operations offline.

### Cannot Access Firebase Console
**Solution:**
- Check internet connection
- Try different browser
- Clear browser cache
- Use incognito/private window

---

## Firebase Collections Schema

Your Firestore will automatically create these collections:

### **users** (mirrors SQLite users table)
```
{
  id: number,
  name: string,
  phone: string,
  pin: string (hashed),
  role: "farmer" | "buyer",
  district: string,
  upazila: string,
  village: string,
  profile_photo: string,
  is_verified: boolean,
  created_at: timestamp,
  updated_at: timestamp
}
```

### **crops** (mirrors SQLite crops table)
```
{
  id: number,
  product_code: string,
  farmer_id: number,
  name: string,
  category: string,
  initial_quantity_kg: number,
  available_quantity_kg: number,
  price_per_kg: number,
  description: string,
  district: string,
  status: "active" | "sold" | "expired",
  created_at: timestamp,
  updated_at: timestamp
}
```

### **orders** (mirrors SQLite orders table)
```
{
  id: number,
  order_number: string,
  crop_id: number,
  farmer_id: number,
  buyer_id: number,
  quantity_kg: number,
  price_per_kg: number,
  total_amount: number,
  status: "new" | "accepted" | "delivered" | "completed",
  payment_status: "pending" | "paid",
  created_at: timestamp
}
```

**All other collections follow the same structure as SQLite tables.**

---

## How Data Sync Works

### Write Operations (Create/Update)
1. **Write to SQLite first** (fast, local)
2. **Sync to Firebase** (background, optional)
3. If Firebase unavailable → still works (offline mode)

### Read Operations
1. **Read from SQLite** (fast, always available)
2. **Optional**: Fetch from Firebase for real-time updates

### Sync Strategy
- **Automatic**: New crops, orders sync to Firebase
- **Manual**: Use `FirebaseSyncService` for batch sync
- **Realtime**: Use `DataSyncManager` for polling updates

---

## Next Steps

1. ✅ **Done**: SQLite database working
2. ✅ **Done**: Firebase service implemented
3. ✅ **Done**: App initialization updated
4. ✅ **Done**: Sync service created

### To Use Firebase Sync in Controllers:

```java
// Example: Sync new crop to Firebase
FirebaseSyncService syncService = FirebaseSyncService.getInstance();
syncService.syncCropToFirebase(cropId);

// Example: Check if sync is available
if (syncService.isSyncAvailable()) {
    // Firebase is connected
} else {
    // Offline mode
}
```

---

## Security Best Practices

### ✅ DO:
- Keep `firebase-credentials.json` secret
- Add it to `.gitignore`
- Use environment variables in production
- Enable security rules before public launch
- Regularly rotate service account keys

### ❌ DON'T:
- Commit credentials to Git
- Share credentials publicly
- Use test mode rules in production
- Store credentials in code
- Leave admin SDK keys in client apps

---

## Support

### Firebase Documentation
- Firestore: https://firebase.google.com/docs/firestore
- Admin SDK: https://firebase.google.com/docs/admin/setup
- Security Rules: https://firebase.google.com/docs/firestore/security/get-started

### Common Questions

**Q: Do I need Firebase for the app to work?**
A: No! App works perfectly with SQLite alone. Firebase is optional for cloud features.

**Q: What if I don't want cloud sync?**
A: Just don't add `firebase-credentials.json`. App runs in offline mode automatically.

**Q: Is Firebase free?**
A: Yes, for small projects. Free tier includes:
- 1 GB storage
- 10 GB/month bandwidth
- 20K writes/day, 50K reads/day

**Q: Can I use Firebase Authentication?**
A: Yes, but current app uses PIN-based auth with SQLite. You can integrate later.

---

## Summary Checklist

- [ ] Firebase project created
- [ ] Firestore database enabled
- [ ] Service account key downloaded
- [ ] Credentials file renamed to `firebase-credentials.json`
- [ ] File moved to project root
- [ ] Project compiles successfully
- [ ] App shows "Firebase cloud sync enabled"
- [ ] Security rules configured (before production)

---

**🎉 Congratulations! Firebase is now integrated with your Chashi Bhai app!**

Your app now supports:
- ✅ Offline-first with SQLite
- ✅ Optional cloud sync with Firestore
- ✅ Real-time data updates
- ✅ Multi-device synchronization
