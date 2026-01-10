# Firebase Cloud Setup Guide

## 🔥 Real Firebase Integration for Chashi Bhai

This guide will help you set up **real Firebase** connectivity for cloud features (realtime sync, cloud storage, notifications) while keeping your local SQLite database for offline functionality.

---

## 📋 Prerequisites

- Firebase Account (Google account)
- Internet connection
- Your project must be running

---

## 🚀 Step 1: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **"Add project"** or select existing project
3. Enter project name: **"chashi-bhai"** (or your choice)
4. Disable Google Analytics (optional, you can enable later)
5. Click **"Create project"**

---

## 🔑 Step 2: Get Service Account Credentials

### Generate Private Key:

1. In Firebase Console, click the **⚙️ gear icon** → **Project Settings**
2. Go to **"Service Accounts"** tab
3. Click **"Generate new private key"**
4. Click **"Generate key"** in the confirmation dialog
5. A JSON file will download (e.g., `chashi-bhai-firebase-adminsdk-xxxxx.json`)

### Save Credentials:

1. **Rename** the downloaded file to: `firebase-credentials.json`
2. **Move** it to your project root directory:
   ```
   D:\Codes\KUET\2207017_Chashi_Bhai\2207017_Chashi_Bhai\firebase-credentials.json
   ```
3. ⚠️ **IMPORTANT**: Add to `.gitignore` (already configured) to avoid committing secrets

---

## 📊 Step 3: Enable Realtime Database

1. In Firebase Console, click **"Build"** → **"Realtime Database"**
2. Click **"Create Database"**
3. Select location (recommended: **us-central1** or closest to your region)
4. Choose **"Start in test mode"** (⚠️ Change rules before production!)
5. Click **"Enable"**
6. **Copy your Database URL** (looks like: `https://chashi-bhai-xxxxx.firebaseio.com`)
   - You'll need this URL for testing

---

## 🧪 Step 4: Test Firebase Connection

### Option A: Run from VS Code Terminal

1. Open Terminal in VS Code (`Ctrl + ~`)
2. Make sure you're in project root
3. Run:
   ```powershell
   mvn clean compile
   java -cp "target/classes;target/dependency/*" com.sajid._207017_chashi_bhai.services.FirebaseCloudConnectionTest firebase-credentials.json https://YOUR-PROJECT-ID.firebaseio.com
   ```
   Replace `YOUR-PROJECT-ID` with your actual Firebase project ID

### Option B: Set Environment Variable

1. Set database URL as environment variable:
   ```powershell
   $env:FIREBASE_DB_URL="https://YOUR-PROJECT-ID.firebaseio.com"
   ```
2. Run test:
   ```powershell
   java -cp "target/classes;target/dependency/*" com.sajid._207017_chashi_bhai.services.FirebaseCloudConnectionTest
   ```

### Expected Output (Success):
```
[FirebaseCloud] Initialized with credentials: firebase-credentials.json, dbUrl=https://...
✅ Realtime Database ping successful. Value: ping-1736467890123
```

### If You See Errors:

#### ❌ "FileNotFoundException: firebase-credentials.json"
- **Fix**: Make sure file is in project root
- Check path: `D:\Codes\KUET\2207017_Chashi_Bhai\2207017_Chashi_Bhai\firebase-credentials.json`

#### ❌ "Permission denied" or "PERMISSION_DENIED"
- **Fix**: Update Realtime Database Rules
- Go to Firebase Console → Realtime Database → Rules
- Change to (for testing only):
  ```json
  {
    "rules": {
      ".read": true,
      ".write": true
    }
  }
  ```
- Click **"Publish"**

#### ❌ "Invalid credentials"
- **Fix**: Regenerate service account key (Step 2)
- Make sure you downloaded the correct JSON file

---

## 🏗️ Architecture Overview

### Your App Now Has TWO Services:

#### 1. **DatabaseService** (SQLite - Local)
- Handles all local CRUD operations
- Works offline
- Fast, reliable
- Used by all current controllers

#### 2. **FirebaseCloudService** (Real Firebase - Cloud)
- Realtime synchronization
- Cloud storage for photos
- Push notifications (future)
- Analytics (future)

---

## 🔐 Step 5: Secure Database Rules (Production)

⚠️ **BEFORE DEPLOYING TO PRODUCTION**, change your Realtime Database rules:

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    },
    "connectivity": {
      ".read": true,
      ".write": "auth != null"
    },
    "crops": {
      ".read": true,
      ".write": "auth != null"
    },
    "orders": {
      "$orderId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

---

## 📱 Step 6: Enable Additional Firebase Features (Optional)

### Cloud Storage (for crop/farm photos):
1. Go to **"Build"** → **"Storage"**
2. Click **"Get Started"**
3. Start in test mode → Enable

### Authentication (for enhanced security):
1. Go to **"Build"** → **"Authentication"**
2. Click **"Get Started"**
3. Enable **"Phone"** authentication
4. Add your phone numbers to test

### Cloud Messaging (for push notifications):
1. Go to **"Build"** → **"Cloud Messaging"**
2. Follow setup wizard

---

## 🧰 Troubleshooting

### Check Maven Dependencies

Run to verify Firebase SDK is installed:
```powershell
mvn dependency:tree | Select-String "firebase"
```

Should see:
```
[INFO] +- com.google.firebase:firebase-admin:jar:9.2.0:compile
[INFO] +- com.google.auth:google-auth-library-oauth2-http:jar:1.19.0:compile
```

### Re-download Dependencies

If Firebase classes not found:
```powershell
mvn clean install -U
```

---

## ✅ Verification Checklist

- [ ] Firebase project created
- [ ] `firebase-credentials.json` in project root
- [ ] Realtime Database enabled
- [ ] Database URL copied
- [ ] Test connection successful (✅ green checkmark)
- [ ] Database rules configured

---

## 📚 Next Steps

### Integrate Firebase into Your App:

1. **Realtime Crop Feed Sync**:
   ```java
   FirebaseCloudService.getInstance().initialize("firebase-credentials.json", databaseUrl);
   // Sync crops to cloud
   ```

2. **Upload Photos to Cloud Storage**:
   - Use Firebase Storage SDK
   - Store URLs in SQLite

3. **Push Notifications**:
   - Use Firebase Cloud Messaging
   - Notify farmers of new orders

---

## 🆘 Need Help?

### Firebase Documentation:
- [Admin SDK Setup](https://firebase.google.com/docs/admin/setup)
- [Realtime Database](https://firebase.google.com/docs/database)
- [Security Rules](https://firebase.google.com/docs/database/security)

### Common Issues:
1. **Network/Firewall**: Make sure ports 443 (HTTPS) and 5001 (Realtime DB) are open
2. **Antivirus**: Some antivirus software blocks Firebase SDK
3. **Java Version**: Make sure using Java 17+ (you're using Java 25 ✅)

---

## 🎯 Success!

Once you see `✅ Realtime Database ping successful`, your Firebase is connected!

Your app now has:
- ✅ Local SQLite database (DatabaseService)
- ✅ Cloud Firebase backend (FirebaseCloudService)
- ✅ Realtime sync capability
- ✅ Cloud storage ready

Ready to build cloud-powered features! 🚀
