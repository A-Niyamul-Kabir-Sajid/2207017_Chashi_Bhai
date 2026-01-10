# 🚀 Firebase Integration - Developer Guide

## Quick Start

Your app now has **FirebaseService** (Firestore) integrated alongside SQLite. Here's how to use it in your controllers.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────┐
│                Your Controllers                  │
│  (LoginController, CropController, etc.)         │
└───────────────┬─────────────────┬───────────────┘
                │                 │
       ┌────────▼─────────┐      ┌▼────────────────┐
       │ DatabaseService  │      │ FirebaseService │
       │   (SQLite)       │      │  (Firestore)    │
       └────────┬─────────┘      └┬────────────────┘
                │                 │
       ┌────────▼─────────┐      ┌▼────────────────┐
       │  Local Database  │      │  Cloud Database │
       │ data/chashi.db   │      │   Firebase       │
       └──────────────────┘      └──────────────────┘
```

**Strategy:**
1. **Write to SQLite first** (fast, always works)
2. **Sync to Firebase** (optional, background)
3. **Read from SQLite** (fast, local)
4. **Fetch from Firebase** (when needed for real-time)

---

## Basic Usage Examples

### 1. Check if Firebase is Available

```java
FirebaseService firebase = FirebaseService.getInstance();

if (firebase.isInitialized()) {
    System.out.println("✅ Cloud sync available");
    // Use Firebase features
} else {
    System.out.println("⚠️ Offline mode");
    // App still works with SQLite
}
```

### 2. Create User (With Sync)

```java
// Method 1: SQLite only (existing code)
int userId = DatabaseService.createUser(phone, pin, name, role, district);

// Method 2: SQLite + Firebase sync
int userId = DatabaseService.createUser(phone, pin, name, role, district);
if (userId > 0) {
    // Sync to Firebase in background
    FirebaseSyncService.getInstance().syncUserToFirebase(userId);
}

// Method 3: Firebase directly (if building cloud-first features)
Map<String, Object> userData = new HashMap<>();
userData.put("id", userId);
userData.put("name", name);
userData.put("phone", phone);
userData.put("pin", pin);
userData.put("role", role);
userData.put("district", district);

FirebaseService.getInstance().createUser(
    String.valueOf(userId),
    userData,
    () -> Platform.runLater(() -> {
        System.out.println("✅ User created in cloud");
    }),
    e -> System.err.println("❌ Cloud sync failed: " + e.getMessage())
);
```

### 3. Create Crop Listing

```java
// In PostCropController or similar

// Step 1: Insert into SQLite (fast, local)
DatabaseService.executeUpdateAsync(
    "INSERT INTO crops (product_code, farmer_id, name, category, ...) VALUES (?, ?, ?, ?, ...)",
    new Object[]{productCode, farmerId, name, category, ...},
    cropId -> {
        System.out.println("✅ Crop saved locally: " + cropId);
        
        // Step 2: Sync to Firebase (optional, background)
        if (FirebaseService.getInstance().isInitialized()) {
            FirebaseSyncService.getInstance().syncCropToFirebase(cropId);
        }
    },
    e -> System.err.println("❌ Error: " + e.getMessage())
);
```

### 4. Fetch Farmer's Crops (Cloud)

```java
// Get crops from Firebase (real-time, cross-device)
FirebaseService.getInstance().getCropsByFarmer(
    String.valueOf(farmerId),
    querySnapshot -> {
        Platform.runLater(() -> {
            cropsList.clear();
            
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Map<String, Object> data = doc.getData();
                
                Crop crop = new Crop();
                crop.setId(((Long) data.get("id")).intValue());
                crop.setName((String) data.get("name"));
                crop.setPrice(((Number) data.get("price_per_kg")).doubleValue());
                // ... set other fields
                
                cropsList.add(crop);
            }
            
            System.out.println("✅ Loaded " + cropsList.size() + " crops from cloud");
        });
    },
    e -> {
        System.err.println("❌ Failed to fetch crops: " + e.getMessage());
        // Fallback: load from SQLite
        loadCropsFromSQLite();
    }
);
```

### 5. Update Order Status (Both Databases)

```java
// Use FirebaseSyncService to update both SQLite and Firebase
FirebaseSyncService syncService = FirebaseSyncService.getInstance();

syncService.updateOrderStatus(
    orderId,
    "completed",
    () -> {
        System.out.println("✅ Order status updated everywhere");
        // Refresh UI
        loadOrders();
    }
);
```

### 6. Real-Time Messaging

```java
// Create or get conversation
FirebaseService.getInstance().getOrCreateConversation(
    String.valueOf(user1Id),
    String.valueOf(user2Id),
    String.valueOf(cropId),
    conversationDoc -> {
        String conversationId = conversationDoc.getId();
        
        // Send message
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("conversation_id", conversationId);
        messageData.put("sender_id", senderId);
        messageData.put("receiver_id", receiverId);
        messageData.put("message_text", "Hello!");
        messageData.put("message_type", "text");
        
        FirebaseService.getInstance().sendMessage(
            conversationId,
            messageData,
            () -> System.out.println("✅ Message sent"),
            e -> System.err.println("❌ Failed: " + e.getMessage())
        );
    },
    e -> System.err.println("❌ Failed to create conversation")
);
```

---

## Using FirebaseSyncService (Recommended)

The `FirebaseSyncService` handles syncing between SQLite and Firebase automatically.

### Sync New User
```java
FirebaseSyncService.getInstance().syncUserToFirebase(userId);
```

### Sync New Crop
```java
FirebaseSyncService.getInstance().syncCropToFirebase(cropId);
```

### Sync New Order
```java
FirebaseSyncService.getInstance().syncOrderToFirebase(orderId);
```

### Update Order Status (Both DBs)
```java
FirebaseSyncService.getInstance().updateOrderStatus(
    orderId, 
    "completed", 
    () -> {
        // Success callback
    }
);
```

### Full Sync (Initial Setup)
```java
// Sync all local data to Firebase
FirebaseSyncService.getInstance().performFullSync(() -> {
    System.out.println("✅ Full sync complete");
});
```

---

## Integration Patterns

### Pattern 1: Offline-First (Recommended)

**Best for:** Reliable operation, poor connectivity

```java
// 1. Write to SQLite immediately
DatabaseService.executeUpdateAsync(sql, params, 
    result -> {
        showSuccessMessage();
        
        // 2. Sync to Firebase in background (best effort)
        if (FirebaseService.getInstance().isInitialized()) {
            FirebaseSyncService.getInstance().syncToFirebase(id);
        }
    },
    error -> showErrorMessage()
);
```

**Pros:**
- Always works (offline capable)
- Fast user experience
- No waiting for network

**Cons:**
- May have sync delays
- Need to handle conflicts

### Pattern 2: Cloud-First

**Best for:** Real-time features, multi-device sync

```java
// 1. Write to Firebase first
FirebaseService.getInstance().createData(data,
    () -> {
        // 2. Then update SQLite cache
        DatabaseService.executeUpdateAsync(sql, params, 
            result -> showSuccessMessage(),
            error -> showErrorMessage()
        );
    },
    error -> {
        // Firebase failed, fallback to SQLite only
        DatabaseService.executeUpdateAsync(sql, params, 
            result -> showSuccessMessage(),
            error -> showErrorMessage()
        );
    }
);
```

**Pros:**
- Immediate cloud sync
- Real-time across devices
- Centralized data

**Cons:**
- Requires internet
- Slower response time
- Fails if offline

### Pattern 3: Hybrid (Best of Both)

**Best for:** Production apps

```java
// Write to both simultaneously
AtomicInteger completed = new AtomicInteger(0);

// SQLite write
DatabaseService.executeUpdateAsync(sql, params,
    result -> {
        if (completed.incrementAndGet() == 2) {
            showSuccessMessage();
        }
    },
    error -> showErrorMessage()
);

// Firebase write (if available)
if (FirebaseService.getInstance().isInitialized()) {
    FirebaseService.getInstance().createData(data,
        () -> {
            if (completed.incrementAndGet() == 2) {
                showSuccessMessage();
            }
        },
        error -> {
            // Firebase failed but SQLite succeeded
            System.err.println("Cloud sync failed, data saved locally");
        }
    );
} else {
    completed.incrementAndGet(); // Skip Firebase
}
```

---

## Example: Complete Crop Creation Flow

```java
public class PostCropController {
    
    @FXML
    private void handleSubmit() {
        // Gather data
        String productCode = generateProductCode();
        int farmerId = App.getCurrentUser().getId();
        String name = nameField.getText();
        String category = categoryComboBox.getValue();
        double quantity = Double.parseDouble(quantityField.getText());
        double price = Double.parseDouble(priceField.getText());
        String description = descriptionArea.getText();
        String district = districtComboBox.getValue();
        
        // Step 1: Save to SQLite (primary database)
        String sql = "INSERT INTO crops (product_code, farmer_id, name, category, " +
                    "initial_quantity_kg, available_quantity_kg, price_per_kg, " +
                    "description, district, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')";
        
        Object[] params = {productCode, farmerId, name, category, 
                          quantity, quantity, price, description, district};
        
        DatabaseService.executeUpdateAsync(sql, params,
            cropId -> Platform.runLater(() -> {
                System.out.println("✅ Crop saved to local database: " + cropId);
                
                // Step 2: Sync to Firebase (optional, background)
                FirebaseSyncService syncService = FirebaseSyncService.getInstance();
                if (syncService.isSyncAvailable()) {
                    syncService.syncCropToFirebase(cropId);
                    System.out.println("🔄 Syncing crop to cloud...");
                } else {
                    System.out.println("📴 Running in offline mode");
                }
                
                // Step 3: Show success and navigate
                showAlert("সফল", "ফসল সফলভাবে যুক্ত হয়েছে!");
                App.loadScene("farmer-dashboard-view.fxml", "Farmer Dashboard");
            }),
            error -> Platform.runLater(() -> {
                System.err.println("❌ Error saving crop: " + error.getMessage());
                showAlert("ত্রুটি", "ফসল যুক্ত করতে ব্যর্থ");
            })
        );
    }
}
```

---

## Error Handling

### Handle Firebase Not Initialized

```java
try {
    FirebaseService firebase = FirebaseService.getInstance();
    
    if (!firebase.isInitialized()) {
        System.out.println("Firebase not available, using offline mode");
        // Use SQLite only
        loadDataFromSQLite();
        return;
    }
    
    // Firebase is available
    loadDataFromFirebase();
    
} catch (Exception e) {
    System.err.println("Firebase error: " + e.getMessage());
    // Fallback to SQLite
    loadDataFromSQLite();
}
```

### Handle Network Failures

```java
FirebaseService.getInstance().getCrops(
    querySnapshot -> {
        // Success - use cloud data
        updateUIWithCloudData(querySnapshot);
    },
    error -> {
        // Failed - fallback to local data
        System.err.println("Cloud fetch failed: " + error.getMessage());
        loadDataFromSQLite();
    }
);
```

---

## Performance Tips

### 1. Batch Operations
```java
// Instead of multiple individual writes
for (Crop crop : crops) {
    FirebaseService.getInstance().createCrop(...); // ❌ Slow
}

// Use batch writes
List<Map<String, Object>> operations = new ArrayList<>();
for (Crop crop : crops) {
    Map<String, Object> op = new HashMap<>();
    op.put("collection", "crops");
    op.put("docId", String.valueOf(crop.getId()));
    op.put("action", "set");
    op.put("data", cropToMap(crop));
    operations.add(op);
}

FirebaseService.getInstance().executeBatch(operations,
    () -> System.out.println("✅ Batch complete"),
    e -> System.err.println("❌ Batch failed")
);
```

### 2. Limit Query Results
```java
// Don't fetch everything
firestore.collection("crops")
    .whereEqualTo("status", "active")
    .limit(50)  // ✅ Limit results
    .orderBy("created_at", Query.Direction.DESCENDING)
    .get();
```

### 3. Use Caching
```java
// Cache frequently accessed data
private Map<String, User> userCache = new HashMap<>();

public void getUser(String userId, Consumer<User> callback) {
    // Check cache first
    if (userCache.containsKey(userId)) {
        callback.accept(userCache.get(userId));
        return;
    }
    
    // Fetch from Firebase
    FirebaseService.getInstance().getUser(userId, doc -> {
        User user = documentToUser(doc);
        userCache.put(userId, user);
        callback.accept(user);
    }, error -> {
        // Fallback to SQLite
    });
}
```

---

## Testing Firebase Integration

### Test 1: Connection Test
```bash
# Run your app and check console output
✅ Firebase initialized successfully with Firestore!
✅ Firebase cloud sync enabled
```

### Test 2: Create Data
```java
// Create a test user in Firebase console:
// Collections → users → Add document
{
  id: 999,
  name: "Test User",
  phone: "01700000000",
  role: "farmer"
}

// Then fetch in your app to verify
```

### Test 3: Sync Test
```java
// Create user in SQLite
int userId = DatabaseService.createUser(...);

// Verify it appears in Firebase Console
// Collections → users → check for user with id=userId
```

---

## When to Use Firebase vs SQLite

### Use SQLite When:
- ✅ Offline operation needed
- ✅ Fast response required
- ✅ Privacy-sensitive data
- ✅ Simple CRUD operations
- ✅ No multi-device sync needed

### Use Firebase When:
- ✅ Real-time updates needed
- ✅ Multi-device synchronization
- ✅ Cloud backup required
- ✅ Collaborative features
- ✅ Analytics/monitoring needed

### Use Both When:
- ✅ **Recommended for most features**
- ✅ Best reliability (offline + online)
- ✅ Fast local access + cloud backup
- ✅ Gradual sync in background

---

## Summary

### ✅ What You Have Now:
1. **FirebaseService** - Full Firestore integration
2. **FirebaseSyncService** - Automatic sync between SQLite and Firebase
3. **App.java** - Auto-initialization on startup
4. **Offline-first** - Works without internet
5. **Optional cloud** - Sync when available

### 📝 What You Need to Do:
1. **Get Firebase credentials** (see FIREBASE_COMPLETE_SETUP.md)
2. **Choose sync pattern** for each feature:
   - Offline-first (recommended)
   - Cloud-first
   - Hybrid
3. **Update controllers** to use FirebaseSyncService

### 🎯 Recommended Approach:
- Keep existing SQLite code (already works)
- Add Firebase sync calls after successful SQLite operations
- App works offline by default, syncs when online

---

**Your app is now ready for cloud deployment! 🚀**
