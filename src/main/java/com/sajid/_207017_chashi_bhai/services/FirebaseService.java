package com.sajid._207017_chashi_bhai.services;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * FirebaseService - Cloud database service using Firestore
 * Mirrors the SQLite schema for cloud synchronization
 * 
 * Collections structure:
 * - users
 * - crops
 * - crop_photos
 * - farm_photos
 * - orders
 * - reviews
 * - conversations
 * - messages
 * - notifications
 * - market_prices
 */
public class FirebaseService {
    private static FirebaseService instance;
    private Firestore firestore;
    private FirebaseAuth auth;
    private boolean initialized = false;
    
    // Executor for async operations
    private static final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("FirebaseWorker");
        return thread;
    });

    // Collection names matching SQLite tables
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_CROPS = "crops";
    public static final String COLLECTION_CROP_PHOTOS = "crop_photos";
    public static final String COLLECTION_FARM_PHOTOS = "farm_photos";
    public static final String COLLECTION_ORDERS = "orders";
    public static final String COLLECTION_REVIEWS = "reviews";
    public static final String COLLECTION_CONVERSATIONS = "conversations";
    public static final String COLLECTION_MESSAGES = "messages";
    public static final String COLLECTION_NOTIFICATIONS = "notifications";
    public static final String COLLECTION_MARKET_PRICES = "market_prices";

    private FirebaseService() {
        // Private constructor for singleton
    }

    public static FirebaseService getInstance() {
        if (instance == null) {
            instance = new FirebaseService();
        }
        return instance;
    }

    /**
     * Initialize Firebase with credentials file
     * 
     * @param credentialsPath Path to firebase-credentials.json
     * @throws IOException if credentials file not found or invalid
     */
    public void initialize(String credentialsPath) throws IOException {
        if (initialized) {
            System.out.println("✅ Firebase already initialized.");
            return;
        }

        try {
            InputStream serviceAccount;
            
            // Try to load from file system first
            try {
                serviceAccount = new FileInputStream(credentialsPath);
                System.out.println("📂 Loading Firebase credentials from: " + credentialsPath);
            } catch (IOException e) {
                // Try to load from resources
                serviceAccount = getClass().getClassLoader().getResourceAsStream(credentialsPath);
                if (serviceAccount == null) {
                    throw new IOException("❌ Firebase credentials file not found: " + credentialsPath);
                }
                System.out.println("📂 Loading Firebase credentials from resources");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            
            this.firestore = FirestoreClient.getFirestore();
            this.auth = FirebaseAuth.getInstance();
            this.initialized = true;
            
            System.out.println("✅ Firebase initialized successfully with Firestore!");
            
            // Initialize indexes (one-time setup)
            initializeIndexes();
            
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Initialize with default credentials path
     */
    public void initialize() throws IOException {
        initialize("firebase-credentials.json");
    }

    /**
     * Create composite indexes for efficient queries
     * Note: These will be created automatically on first query
     */
    private void initializeIndexes() {
        System.out.println("📊 Firestore indexes will be created automatically on first query");
        // Firestore creates indexes automatically based on query patterns
        // No manual setup needed for single-field queries
    }

    // ==================== USER OPERATIONS ====================

    /**
     * Create a new user in Firestore
     * 
     * @param userId User ID (matches SQLite)
     * @param userData User data map (name, phone, pin, role, district, etc.)
     * @param onSuccess Success callback
     * @param onError Error callback
     */
    public void createUser(String userId, Map<String, Object> userData, 
                          Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                // Add timestamps
                userData.put("created_at", FieldValue.serverTimestamp());
                userData.put("updated_at", FieldValue.serverTimestamp());
                userData.put("is_verified", false);
                
                firestore.collection(COLLECTION_USERS)
                        .document(userId)
                        .set(userData)
                        .get(); // Wait for completion
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error creating user: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Get user by ID
     */
    public void getUser(String userId, Consumer<DocumentSnapshot> onSuccess, 
                       Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                DocumentSnapshot doc = firestore.collection(COLLECTION_USERS)
                        .document(userId)
                        .get()
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.accept(doc);
                }
            } catch (Exception e) {
                System.err.println("❌ Error getting user: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Get user by phone number
     */
    public void getUserByPhone(String phone, Consumer<QuerySnapshot> onSuccess, 
                               Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                QuerySnapshot querySnapshot = firestore.collection(COLLECTION_USERS)
                        .whereEqualTo("phone", phone)
                        .limit(1)
                        .get()
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.accept(querySnapshot);
                }
            } catch (Exception e) {
                System.err.println("❌ Error getting user by phone: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Update user profile
     */
    public void updateUser(String userId, Map<String, Object> updates, 
                          Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                updates.put("updated_at", FieldValue.serverTimestamp());
                
                firestore.collection(COLLECTION_USERS)
                        .document(userId)
                        .update(updates)
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error updating user: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    // ==================== CROP OPERATIONS ====================

    /**
     * Create a new crop listing
     */
    public void createCrop(String cropId, Map<String, Object> cropData, 
                          Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                cropData.put("created_at", FieldValue.serverTimestamp());
                cropData.put("updated_at", FieldValue.serverTimestamp());
                cropData.put("status", "active");
                
                firestore.collection(COLLECTION_CROPS)
                        .document(cropId)
                        .set(cropData)
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error creating crop: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Get crops by farmer ID
     */
    public void getCropsByFarmer(String farmerId, Consumer<QuerySnapshot> onSuccess, 
                                 Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                QuerySnapshot querySnapshot = firestore.collection(COLLECTION_CROPS)
                        .whereEqualTo("farmer_id", farmerId)
                        .whereEqualTo("status", "active")
                        .orderBy("created_at", Query.Direction.DESCENDING)
                        .get()
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.accept(querySnapshot);
                }
            } catch (Exception e) {
                System.err.println("❌ Error getting crops: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Search crops by name or category
     */
    public void searchCrops(String searchQuery, Consumer<QuerySnapshot> onSuccess, 
                           Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                // Firestore doesn't support full-text search natively
                // This is a basic implementation - consider using Algolia for production
                QuerySnapshot querySnapshot = firestore.collection(COLLECTION_CROPS)
                        .whereEqualTo("status", "active")
                        .orderBy("created_at", Query.Direction.DESCENDING)
                        .limit(100)
                        .get()
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.accept(querySnapshot);
                }
            } catch (Exception e) {
                System.err.println("❌ Error searching crops: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Update crop details
     */
    public void updateCrop(String cropId, Map<String, Object> updates, 
                          Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                updates.put("updated_at", FieldValue.serverTimestamp());
                
                firestore.collection(COLLECTION_CROPS)
                        .document(cropId)
                        .update(updates)
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error updating crop: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    // ==================== ORDER OPERATIONS ====================

    /**
     * Get a single order document
     */
    public void getOrder(String orderId, Consumer<DocumentSnapshot> onSuccess,
                         Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                DocumentSnapshot doc = firestore.collection(COLLECTION_ORDERS)
                        .document(orderId)
                        .get()
                        .get();

                if (onSuccess != null) {
                    onSuccess.accept(doc);
                }
            } catch (Exception e) {
                System.err.println("❌ Error getting order: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Upsert (merge) order fields without touching created_at
     */
    public void upsertOrder(String orderId, Map<String, Object> orderData,
                            Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                orderData.put("updated_at", FieldValue.serverTimestamp());

                firestore.collection(COLLECTION_ORDERS)
                        .document(orderId)
                        .set(orderData, SetOptions.merge())
                        .get();

                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error upserting order: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Create a new order
     */
    public void createOrder(String orderId, Map<String, Object> orderData, 
                           Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                orderData.putIfAbsent("created_at", FieldValue.serverTimestamp());
                orderData.putIfAbsent("status", "new");
                orderData.putIfAbsent("payment_status", "pending");
                orderData.put("updated_at", FieldValue.serverTimestamp());
                
                firestore.collection(COLLECTION_ORDERS)
                        .document(orderId)
                        .set(orderData)
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error creating order: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Get orders by farmer ID
     */
    public void getOrdersByFarmer(int farmerId, Consumer<QuerySnapshot> onSuccess,
                                  Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                QuerySnapshot querySnapshot = firestore.collection(COLLECTION_ORDERS)
                        .whereEqualTo("farmer_id", farmerId)
                        .orderBy("created_at", Query.Direction.DESCENDING)
                        .get()
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.accept(querySnapshot);
                }
            } catch (Exception e) {
                System.err.println("❌ Error getting farmer orders: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Get orders by buyer ID
     */
    public void getOrdersByBuyer(int buyerId, Consumer<QuerySnapshot> onSuccess,
                                 Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                QuerySnapshot querySnapshot = firestore.collection(COLLECTION_ORDERS)
                        .whereEqualTo("buyer_id", buyerId)
                        .orderBy("created_at", Query.Direction.DESCENDING)
                        .get()
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.accept(querySnapshot);
                }
            } catch (Exception e) {
                System.err.println("❌ Error getting buyer orders: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Update order status
     */
    public void updateOrderStatus(String orderId, String status, 
                                  Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", status);
                updates.put("updated_at", FieldValue.serverTimestamp());
                
                // Add timestamp for specific statuses
                switch (status) {
                    case "accepted":
                        updates.put("accepted_at", FieldValue.serverTimestamp());
                        break;
                    case "in_transit":
                        updates.put("in_transit_at", FieldValue.serverTimestamp());
                        break;
                    case "delivered":
                        updates.put("delivered_at", FieldValue.serverTimestamp());
                        break;
                    case "completed":
                        updates.put("completed_at", FieldValue.serverTimestamp());
                        break;
                }
                
                firestore.collection(COLLECTION_ORDERS)
                        .document(orderId)
                        .update(updates)
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error updating order status: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    // ==================== MESSAGING OPERATIONS ====================

    /**
     * Create or get conversation between two users
     */
    public void getOrCreateConversation(String user1Id, String user2Id, String cropId,
                                       Consumer<DocumentSnapshot> onSuccess,
                                       Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                // Search for existing conversation
                QuerySnapshot existing = firestore.collection(COLLECTION_CONVERSATIONS)
                        .whereEqualTo("user1_id", user1Id)
                        .whereEqualTo("user2_id", user2Id)
                        .whereEqualTo("crop_id", cropId)
                        .limit(1)
                        .get()
                        .get();
                
                if (!existing.isEmpty()) {
                    if (onSuccess != null) {
                        onSuccess.accept(existing.getDocuments().get(0));
                    }
                    return;
                }
                
                // Create new conversation
                Map<String, Object> conversationData = new HashMap<>();
                conversationData.put("user1_id", user1Id);
                conversationData.put("user2_id", user2Id);
                conversationData.put("crop_id", cropId);
                conversationData.put("unread_count_user1", 0);
                conversationData.put("unread_count_user2", 0);
                conversationData.put("created_at", FieldValue.serverTimestamp());
                conversationData.put("updated_at", FieldValue.serverTimestamp());
                
                DocumentReference docRef = firestore.collection(COLLECTION_CONVERSATIONS)
                        .document();
                docRef.set(conversationData).get();
                
                if (onSuccess != null) {
                    onSuccess.accept(docRef.get().get());
                }
            } catch (Exception e) {
                System.err.println("❌ Error creating conversation: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Send a message
     */
    public void sendMessage(String conversationId, Map<String, Object> messageData,
                           Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                messageData.put("created_at", FieldValue.serverTimestamp());
                messageData.put("is_read", false);
                
                firestore.collection(COLLECTION_MESSAGES)
                        .add(messageData)
                        .get();
                
                // Update conversation last message
                Map<String, Object> conversationUpdate = new HashMap<>();
                conversationUpdate.put("last_message", messageData.get("message_text"));
                conversationUpdate.put("last_message_time", FieldValue.serverTimestamp());
                conversationUpdate.put("updated_at", FieldValue.serverTimestamp());
                
                firestore.collection(COLLECTION_CONVERSATIONS)
                        .document(conversationId)
                        .update(conversationUpdate)
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error sending message: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    // ==================== PHOTO OPERATIONS ====================

    /**
     * Add crop photo reference
     */
    public void addCropPhoto(String cropId, String photoPath, int photoOrder,
                            Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                Map<String, Object> photoData = new HashMap<>();
                photoData.put("crop_id", cropId);
                photoData.put("photo_path", photoPath);
                photoData.put("photo_order", photoOrder);
                photoData.put("created_at", FieldValue.serverTimestamp());
                
                firestore.collection(COLLECTION_CROP_PHOTOS)
                        .add(photoData)
                        .get();
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error adding crop photo: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Check if Firebase is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get Firestore instance
     */
    public Firestore getFirestore() {
        if (!initialized) {
            throw new IllegalStateException("❌ Firebase not initialized. Call initialize() first.");
        }
        return firestore;
    }

    /**
     * Get Firebase Auth instance
     */
    public FirebaseAuth getAuth() {
        if (!initialized) {
            throw new IllegalStateException("❌ Firebase not initialized. Call initialize() first.");
        }
        return auth;
    }

    /**
     * Batch write operation for bulk updates
     */
    public void executeBatch(List<Map<String, Object>> operations,
                            Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                WriteBatch batch = firestore.batch();
                
                for (Map<String, Object> op : operations) {
                    String collection = (String) op.get("collection");
                    String docId = (String) op.get("docId");
                    String action = (String) op.get("action");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) op.get("data");
                    
                    DocumentReference docRef = firestore.collection(collection).document(docId);
                    
                    if ("set".equals(action)) {
                        batch.set(docRef, data);
                    } else if ("update".equals(action)) {
                        batch.update(docRef, data);
                    } else if ("delete".equals(action)) {
                        batch.delete(docRef);
                    }
                }
                
                batch.commit().get();
                
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                System.err.println("❌ Error executing batch: " + e.getMessage());
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Shutdown Firebase service
     */
    public void shutdown() {
        if (initialized) {
            try {
                System.out.println("🔄 Closing Firestore connection...");
                if (firestore != null) {
                    firestore.close();
                }
                executor.shutdown();
                initialized = false;
                System.out.println("✅ Firebase connection closed successfully.");
            } catch (Exception e) {
                System.err.println("❌ Error closing Firebase: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
