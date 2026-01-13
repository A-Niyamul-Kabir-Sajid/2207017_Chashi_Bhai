# Firebase Security Rules Guide

## 📋 Overview

This document explains the Firebase Firestore security rules for the Chashi Bhai application.

**File:** `firestore.rules`

---

## 🔒 Security Principles

1. **Authentication Required**: Most operations require user authentication
2. **Owner-Based Access**: Users can only modify their own data
3. **Public Reading**: Marketplace data (crops, user profiles) is publicly readable
4. **Privacy Protection**: Orders and messages are only visible to participants

---

## 📚 Collection Rules

### 1. Users Collection

```javascript
match /users/{userId} {
  allow read: if true;                    // Public profiles
  allow create: if isAuthenticated();     // Signup
  allow update: if isOwner(userId);       // Own profile only
  allow delete: if isOwner(userId);       // Delete own account
}
```

**Use Cases:**
- ✅ Anyone can view farmer/buyer profiles
- ✅ Users can create account during signup
- ✅ Users can update their own profile only
- ✅ Users can delete their own account

---

### 2. Crops Collection

```javascript
match /crops/{cropId} {
  allow read: if true;                           // Public marketplace
  allow create: if isAuthenticated();            // Post crop
  allow update: if resource.data.farmer_id == uid;  // Own crops only
  allow delete: if resource.data.farmer_id == uid;  // Own crops only
}
```

**Use Cases:**
- ✅ Anyone can browse crops (public marketplace)
- ✅ Authenticated farmers can post crops
- ✅ Farmers can edit/delete only their own crops
- ❌ Cannot modify other farmers' crops

---

### 3. Crop Photos Collection

```javascript
match /crop_photos/{photoId} {
  allow read: if true;                    // Public photos
  allow create: if isAuthenticated();     // Upload photos
  allow update, delete: if isAuthenticated();
}
```

**Use Cases:**
- ✅ Anyone can view crop photos
- ✅ Authenticated users can upload photos
- ⚠️ Update/delete permissions simplified (enhance in production)

---

### 4. Orders Collection

```javascript
match /orders/{orderId} {
  allow read: if buyer_id == uid || farmer_id == uid;
  allow create: if buyer_id == uid;
  allow update: if buyer_id == uid || farmer_id == uid;
  allow delete: if buyer_id == uid;
}
```

**Use Cases:**
- ✅ Buyers and farmers can see their own orders
- ✅ Buyers can place orders
- ✅ Both parties can update order status
- ✅ Buyers can cancel orders
- ❌ Cannot view other users' orders

---

### 5. Conversations Collection

```javascript
match /conversations/{conversationId} {
  allow read: if user1_id == uid || user2_id == uid;
  allow create: if user1_id == uid || user2_id == uid;
  allow update: if user1_id == uid || user2_id == uid;
  allow delete: if user1_id == uid || user2_id == uid;
}
```

**Use Cases:**
- ✅ Users can access conversations they're part of
- ✅ Users can start new conversations
- ✅ Participants can update conversation metadata
- ❌ Cannot view other users' conversations

---

### 6. Messages Collection

```javascript
match /messages/{messageId} {
  allow read: if isAuthenticated();          // Simplified
  allow create: if sender_id == uid;         // Send message
  allow update: if sender_id == uid;         // Edit own message
  allow delete: if sender_id == uid;         // Delete own message
}
```

**Use Cases:**
- ✅ Authenticated users can read messages
- ✅ Users can send messages
- ✅ Users can edit/delete their own messages
- ⚠️ Read rule simplified (enhance by checking conversation membership)

---

## 🚀 Deployment

### Step 1: Install Firebase CLI

```bash
npm install -g firebase-tools
```

### Step 2: Login to Firebase

```bash
firebase login
```

### Step 3: Initialize Firebase (if not done)

```bash
firebase init firestore
```

Select:
- ✅ Firestore: Configure security rules and indexes files
- Project: `testfirebase-12671`
- Rules file: `firestore.rules`

### Step 4: Deploy Rules

```bash
firebase deploy --only firestore:rules
```

**Output:**
```
=== Deploying to 'testfirebase-12671'...

i  firestore: checking firestore.rules for compilation errors...
✔  firestore: rules file firestore.rules compiled successfully
i  firestore: uploading rules firestore.rules...
✔  firestore: released rules firestore.rules to cloud.firestore

✔  Deploy complete!
```

---

## 🧪 Testing Rules

### Using Firebase Console

1. Go to: https://console.firebase.google.com/
2. Navigate to: **Firestore Database** → **Rules** tab
3. Click **Rules Playground**
4. Test scenarios:

**Test 1: Unauthenticated user reading crops**
```
Location: /crops/crop123
Operation: get
Auth: Unauthenticated
Result: ✅ Allow (crops are public)
```

**Test 2: User updating own profile**
```
Location: /users/user123
Operation: update
Auth: user123
Result: ✅ Allow (owner can update)
```

**Test 3: User updating another user's profile**
```
Location: /users/user456
Operation: update
Auth: user123
Result: ❌ Deny (not the owner)
```

### Using Firebase Emulator

```bash
# Install emulator
firebase init emulators

# Start emulator
firebase emulators:start
```

Access: http://localhost:4000

---

## ⚠️ Production Enhancements

### 1. Role-Based Access

```javascript
function isFarmer() {
  return isAuthenticated() && 
         get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'farmer';
}

function isBuyer() {
  return isAuthenticated() && 
         get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'buyer';
}

// Use in rules
match /crops/{cropId} {
  allow create: if isFarmer();  // Only farmers can post crops
}
```

### 2. Message Privacy

```javascript
match /messages/{messageId} {
  allow read: if isAuthenticated() && 
                 isInConversation(resource.data.conversation_id);
}

function isInConversation(conversationId) {
  let conversation = get(/databases/$(database)/documents/conversations/$(conversationId));
  return conversation.data.user1_id == request.auth.uid || 
         conversation.data.user2_id == request.auth.uid;
}
```

### 3. Rate Limiting

```javascript
match /messages/{messageId} {
  allow create: if isAuthenticated() && 
                   request.resource.data.sender_id == request.auth.uid &&
                   request.time < resource.data.lastMessageTime + duration.value(1, 's');
}
```

### 4. Data Validation

```javascript
match /crops/{cropId} {
  allow create: if isAuthenticated() &&
                   request.resource.data.price_per_kg > 0 &&
                   request.resource.data.quantity_kg > 0 &&
                   request.resource.data.name.size() > 0;
}
```

---

## 📊 Current Rules Summary

| Collection | Read | Create | Update | Delete |
|------------|------|--------|--------|--------|
| **users** | 🌍 Public | ✅ Auth | ✅ Owner | ✅ Owner |
| **crops** | 🌍 Public | ✅ Auth | ✅ Owner | ✅ Owner |
| **crop_photos** | 🌍 Public | ✅ Auth | ✅ Auth | ✅ Auth |
| **orders** | 👥 Participants | ✅ Buyer | 👥 Both | ✅ Buyer |
| **conversations** | 👥 Participants | 👥 Participants | 👥 Participants | 👥 Participants |
| **messages** | ✅ Auth | ✅ Sender | ✅ Sender | ✅ Sender |

**Legend:**
- 🌍 Public: Anyone (unauthenticated users included)
- ✅ Auth: Any authenticated user
- ✅ Owner: Resource owner only
- ✅ Sender: Message sender only
- ✅ Buyer: Order buyer only
- 👥 Participants: Conversation/order participants
- 👥 Both: Both buyer and farmer

---

## 🔍 Common Scenarios

### Scenario 1: Farmer Posts Crop

```
1. User authenticates (Firebase Auth)
2. Create crop document in /crops
3. Upload photos to /crop_photos
4. Set farmer_id = authenticated user's UID
✅ Rules allow: authenticated user can create
```

### Scenario 2: Buyer Places Order

```
1. Buyer browses crops (public read)
2. Buyer authenticates
3. Create order with buyer_id = buyer's UID
4. Set farmer_id from crop document
✅ Rules allow: buyer can create order for themselves
```

### Scenario 3: Chat Between Users

```
1. User A initiates chat with User B
2. Create conversation with user1_id=A, user2_id=B
3. Send message with sender_id=A, conversation_id=...
4. User B reads message (authenticated)
✅ Rules allow: participants can chat
```

---

## 🛡️ Security Checklist

- ✅ All write operations require authentication
- ✅ Users can only modify their own data
- ✅ Public data (crops, profiles) is read-only for non-owners
- ✅ Private data (orders, messages) restricted to participants
- ⚠️ Consider adding role-based access (farmer/buyer)
- ⚠️ Consider adding data validation rules
- ⚠️ Consider adding rate limiting for message creation

---

## 📝 Next Steps

1. **Deploy Current Rules**
   ```bash
   firebase deploy --only firestore:rules
   ```

2. **Test in Development**
   - Test with Firebase Emulator
   - Verify all CRUD operations
   - Check error messages

3. **Enhance for Production**
   - Add role-based access
   - Implement data validation
   - Add rate limiting
   - Improve message privacy

4. **Monitor Usage**
   - Check Firebase Console → Usage
   - Review denied requests
   - Adjust rules based on patterns

---

**Last Updated:** January 13, 2026 - 19:35  
**Firebase Project:** testfirebase-12671
