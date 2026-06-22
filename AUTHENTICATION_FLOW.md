# Authentication Flow Documentation

## Overview

The authentication system uses a **hybrid approach** combining Firebase Authentication and local SQLite storage, with **one-time login** (session caching) for improved user experience.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    USER REGISTRATION                     │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │  1. Firebase Auth       │ ✅ Primary (if enabled)
              │     (Phone + PIN)       │
              └─────────────────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │  2. SQLite Database     │ ✅ Always (offline support)
              │     (User record)       │
              └─────────────────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │  3. Session Cache       │ ✅ For one-time login
              │     (AuthSessionManager)│
              └─────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                      USER LOGIN                          │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │  Check Cached Session?  │
              └─────────────────────────┘
                    │              │
                 YES│              │NO
                    ▼              ▼
         ┌──────────────┐  ┌──────────────────┐
         │ Auto-Login   │  │ 1. Firebase Auth │ (Try first)
         │ (Fast!)      │  └──────────────────┘
         └──────────────┘           │
                                    │
                              Success│ │Fail
                                    │ │
                                    ▼ ▼
                          ┌──────────────────┐
                          │ 2. SQLite Verify │ (Fallback)
                          └──────────────────┘
                                    │
                                    ▼
                          ┌──────────────────┐
                          │ 3. Save Session  │
                          └──────────────────┘
```

---

## Components

### 1. FirebaseAuthService
**Location:** `services/FirebaseAuthService.java`

**Purpose:** Handle Firebase Authentication via REST API

**Methods:**
- `signUp(phone, pin, name)` - Register new user in Firebase
- `signIn(phone, pin)` - Authenticate existing user with Firebase

**When it's used:**
- ✅ **Signup:** Always attempted (may fail if phone auth disabled)
- ✅ **Login (first time):** Always attempted
- ❌ **Login (subsequent):** Skipped if valid session exists

**Important Notes:**
- Converts phone to email format: `01712345678` → `8801712345678@chashi-bhai.app`
- Returns `AuthResult` with `idToken`, `refreshToken`, `firebaseUserId`
- Falls back gracefully to local auth if Firebase is unavailable

---

### 2. SQLite Database
**Location:** `services/DatabaseService.java`

**Purpose:** Local storage for all user data (offline-first)

**Schema:**
```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone TEXT UNIQUE NOT NULL,
    pin TEXT NOT NULL,
    name TEXT NOT NULL,
    role TEXT NOT NULL,  -- 'farmer' or 'buyer'
    district TEXT,
    upazila TEXT,
    is_verified INTEGER DEFAULT 0,
    profile_photo TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Why SQLite is Always Required:**
- Stores complete user profile (name, district, role, etc.)
- Works offline (no internet needed)
- Fast local queries
- Firebase Auth only stores authentication, NOT user profile data

---

### 3. AuthSessionManager
**Location:** `services/AuthSessionManager.java`

**Purpose:** Cache Firebase authentication sessions for one-time login

**Schema:**
```sql
CREATE TABLE auth_sessions (
    user_id INTEGER PRIMARY KEY,
    firebase_uid TEXT,
    id_token TEXT,
    refresh_token TEXT,
    phone TEXT NOT NULL,
    role TEXT NOT NULL,
    name TEXT,
    district TEXT,
    upazila TEXT,
    is_verified INTEGER DEFAULT 0,
    profile_photo TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);
```

**Session Lifetime:** 7 days

**Methods:**
- `saveSession()` - Cache authentication after successful login
- `getActiveSession()` - Retrieve valid cached session
- `hasValidSession()` - Check if session exists and hasn't expired
- `logout()` - Clear cached session

**When sessions are saved:**
- ✅ After successful signup (if Firebase auth succeeded)
- ✅ After successful login (if Firebase auth succeeded)
- ❌ If Firebase auth failed (no token to cache)

---

## Flow Details

### 🆕 SIGNUP FLOW

**File:** `controllers/SignupController.java`

**Steps:**

1. **User fills signup form:**
   - Name, Phone (01XXXXXXXXX), District, Role, PIN (4-6 digits)

2. **Validate inputs:**
   - Phone format: `01[0-9]{9}`
   - PIN format: `[0-9]{4,6}`
   - All fields required

3. **Register in Firebase Auth (attempted):**
   ```java
   FirebaseAuthService.AuthResult authResult = firebaseAuth.signUp(phone, pin, name);
   ```
   - ✅ **Success:** Get `idToken`, `refreshToken`, `firebaseUserId`
   - ❌ **Fail:** Continue to step 4 anyway (Firebase is optional!)

4. **Create user in SQLite (required):**
   ```java
   int userId = DatabaseService.createUser(phone, pin, name, role, district);
   ```
   - Returns new `userId` (e.g., 5)
   - Returns `-2` if phone already exists
   - Returns `-1` on database error

5. **Save session for one-time login (if Firebase succeeded):**
   ```java
   AuthSessionManager.getInstance().saveSession(
       userId, firebaseUid, idToken, refreshToken, phone, role
   );
   ```
   - ✅ If Firebase auth succeeded: Session cached
   - ❌ If Firebase auth failed: No session cached (will need Firebase or SQLite login next time)

6. **Sync user to Firebase Firestore (background):**
   ```java
   FirebaseService.getInstance().saveUser(userId, userData, ...);
   ```
   - Stores user profile in Firestore for cross-device access
   - Non-blocking, happens in background

7. **Show success message and return to welcome screen**

---

### 🔑 LOGIN FLOW

**File:** `controllers/LoginController.java`

#### Case 1: User has valid cached session (ONE-TIME LOGIN)

**When:** User logged in within last 7 days and hasn't logged out

```java
// In initialize() method
AuthSessionManager.CachedSession cachedSession = sessionManager.getActiveSession();
if (cachedSession != null) {
    autoLoginWithCachedSession(cachedSession);
}
```

**Result:**
- ⚡ **Instant login** - no network call, no Firebase, no SQLite query
- Loads user data from cached session
- Navigates directly to crop feed

**User Experience:**
- App opens → automatically logged in (no login screen)
- Fastest possible login (milliseconds)

---

#### Case 2: No cached session (FIRST-TIME LOGIN or EXPIRED)

**Steps:**

1. **User enters phone, PIN, and selects role**

2. **Attempt Firebase authentication (background thread):**
   ```java
   FirebaseAuthService.AuthResult authResult = firebaseAuth.signIn(phone, pin);
   ```

   **Possible outcomes:**
   
   - ✅ **Success:** Firebase auth passed
     - Get `idToken`, `refreshToken`
     - Set token for API calls: `FirebaseService.getInstance().setIdToken(authResult.getIdToken())`
     - Proceed to step 3 with Firebase session
   
   - ❌ **Fail:** Firebase auth failed (common scenarios)
     - Password login disabled in Firebase Console
     - User registered before Firebase integration
     - Network issues
     - User not registered in Firebase (registered locally only)
     - **Action:** Fall back to SQLite authentication (step 3)

3. **Verify credentials in SQLite:**
   ```java
   SELECT * FROM users WHERE phone = ? AND role = ?
   ```
   - Compare entered PIN with stored PIN
   - ✅ **Match:** Login successful
   - ❌ **No match:** Show "Invalid PIN" error

4. **Save session (if Firebase auth succeeded):**
   ```java
   AuthSessionManager.getInstance().saveSession(
       userId, firebaseUid, idToken, refreshToken, phone, role
   );
   ```
   - Future logins will use one-time login (Case 1)
   - Session valid for 7 days

5. **Set current user and navigate to crop feed**

---

## Firebase Configuration Issues

### Problem: "PASSWORD_LOGIN_DISABLED"

**Error message in logs:**
```
⚠️ Firebase auth failed (will use local auth): PASSWORD_LOGIN_DISABLED
```

**Cause:** Phone authentication is disabled in Firebase Console

**Solution:**

1. Open Firebase Console: https://console.firebase.google.com/
2. Select project: `testfirebase-12671`
3. Navigate to: **Authentication** → **Sign-in method**
4. Find **Phone** provider
5. Click **Enable**
6. Save changes

**Without enabling Phone auth:**
- ✅ Users can still login (SQLite fallback)
- ✅ All features work
- ❌ No Firebase authentication tokens
- ❌ No cross-device sync for auth
- ❌ One-time login only works if user logged in BEFORE Firebase was disabled

---

## Security Notes

### PIN Storage
- PINs are stored in **plain text** in SQLite for simplicity
- ⚠️ **Production recommendation:** Use BCrypt hashing
  ```java
  String hashedPin = BCrypt.hashpw(pin, BCrypt.gensalt());
  boolean pinMatches = BCrypt.checkpw(enteredPin, storedHashedPin);
  ```

### Firebase Tokens
- `idToken` expires after 1 hour
- `refreshToken` can be used to get new `idToken`
- Tokens stored in SQLite session cache (encrypted filesystem recommended for production)

### Session Expiry
- Default: 7 days
- Configurable in `AuthSessionManager.SESSION_EXPIRY_DAYS`
- User must re-authenticate after expiry

---

## Offline Support

### What works offline?
- ✅ Login (if session cached OR SQLite fallback)
- ✅ View crops from local database
- ✅ View profile data
- ✅ Browse all local data

### What requires internet?
- ❌ Firebase authentication (first-time login)
- ❌ Syncing new data to cloud
- ❌ Real-time chat updates from other users
- ❌ Loading data from Firebase Firestore

### Hybrid approach benefits:
- **Best of both worlds:** Fast local access + cloud backup
- **Resilient:** Works even if Firebase is down
- **User-friendly:** One-time login reduces friction

---

## Testing Scenarios

### Test 1: New User Signup (Firebase Enabled)
1. Fill signup form
2. Submit
3. **Expected:**
   - ✅ User created in Firebase Auth
   - ✅ User created in SQLite
   - ✅ Session cached
   - ✅ User synced to Firestore
   - ✅ Success message shown

### Test 2: New User Signup (Firebase Disabled)
1. Fill signup form
2. Submit
3. **Expected:**
   - ⚠️ Firebase registration fails (error logged)
   - ✅ User created in SQLite
   - ❌ Session NOT cached (no Firebase token)
   - ✅ Success message shown
   - **Next login:** Will use SQLite auth

### Test 3: Existing User Login (Session Cached)
1. Open app
2. **Expected:**
   - ⚡ Auto-login immediately
   - ✅ No login screen shown
   - ✅ Navigate to crop feed

### Test 4: Existing User Login (Session Expired)
1. Enter phone, PIN, role
2. Click Login
3. **Expected:**
   - ⏳ Firebase auth attempted
   - ✅ SQLite verification
   - ✅ Login successful
   - ✅ Session cached (if Firebase succeeded)

### Test 5: Wrong PIN
1. Enter phone with wrong PIN
2. Click Login
3. **Expected:**
   - ❌ "Invalid PIN" error
   - 🔄 Can retry

### Test 6: Unregistered Phone
1. Enter phone that doesn't exist
2. Click Login
3. **Expected:**
   - ❌ "Account not found" error
   - 🔗 Suggestion to sign up

---

## Files Modified

### Authentication
- ✅ `controllers/LoginController.java` - Login logic with Firebase + SQLite
- ✅ `controllers/SignupController.java` - Signup with Firebase registration + session saving
- ✅ `services/FirebaseAuthService.java` - Firebase REST API auth
- ✅ `services/AuthSessionManager.java` - Session caching for one-time login
- ✅ `services/DatabaseService.java` - SQLite user management

### Related
- ✅ `services/FirebaseService.java` - Firestore data sync
- ✅ `services/ChatService.java` - Chat with dual-database support
- ✅ `utils/SessionManager.java` - Legacy session management

---

## Common Issues & Solutions

### Issue 1: "Account not found for this role"
**Cause:** User selected wrong role (farmer vs buyer)
**Solution:** Select correct role or sign up as new user

### Issue 2: "Firebase auth failed"
**Cause:** Phone authentication disabled in Firebase
**Solution:** Enable phone auth in Firebase Console (or continue with local auth)

### Issue 3: Auto-login not working
**Cause:** Session expired (> 7 days) or user logged out
**Solution:** Login again to create new session

### Issue 4: Can't login after signup
**Cause:** Phone number mismatch or wrong role selected
**Solution:** Verify phone format (01XXXXXXXXX) and role

---

## Future Enhancements

### Recommended improvements:
1. **BCrypt PIN hashing** - Secure PIN storage
2. **Token refresh logic** - Auto-refresh expired Firebase tokens
3. **Biometric auth** - Fingerprint/Face ID for one-time login
4. **Multi-device session management** - Track active sessions
5. **Remember me checkbox** - Let users opt-out of one-time login
6. **Session revocation** - Admin panel to logout users remotely
7. **2FA support** - SMS OTP for sensitive operations

---

## Summary

| Feature | Status | Notes |
|---------|--------|-------|
| Firebase Auth | ✅ Implemented | Falls back gracefully if disabled |
| SQLite Auth | ✅ Always works | Offline-first approach |
| One-time Login | ✅ Implemented | 7-day session cache |
| Session Management | ✅ Implemented | Auto-login on app start |
| Signup → Firebase | ✅ Attempted | Continues if fails |
| Signup → SQLite | ✅ Always | Required for user data |
| Login → Firebase first | ✅ Implemented | Tries Firebase, falls back to SQLite |
| Login → SQLite fallback | ✅ Implemented | Works offline |
| Cross-device sync | ✅ Implemented | Via Firebase Firestore |
| Offline support | ✅ Full support | SQLite-first architecture |

---

**Last Updated:** January 13, 2026  
**Version:** 1.0
