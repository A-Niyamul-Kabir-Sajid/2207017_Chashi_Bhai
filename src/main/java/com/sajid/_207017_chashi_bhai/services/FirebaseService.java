package com.sajid._207017_chashi_bhai.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * FirebaseService - Firestore REST API Service
 * Handles storing and retrieving data from Firestore using REST API
 * 
 * This is a lightweight approach that:
 * - Uses Java's built-in HttpClient (no external dependencies)
 * - Stores images as Base64 strings
 * - Syncs with local SQLite database
 * 
 * Collections:
 * - users
 * - crops
 * - crop_photos (with Base64 images)
 * - farm_photos (with Base64 images)
 * - orders
 * - reviews
 * - conversations
 * - messages
 * - notifications
 */
public class FirebaseService {
    private static FirebaseService instance;
    private static final String PROJECT_ID = FirebaseConfig.getProjectId();
    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents";
    
    private final HttpClient httpClient;
    private final Gson gson;
    
    // Current auth token (set after login)
    private String currentIdToken;
    
    // Executor for async operations
    private static final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("FirebaseWorker");
        return thread;
    });

    // Collection names
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_CROPS = "crops";
    public static final String COLLECTION_CROP_PHOTOS = "crop_photos";
    public static final String COLLECTION_FARM_PHOTOS = "farm_photos";
    public static final String COLLECTION_ORDERS = "orders";
    public static final String COLLECTION_REVIEWS = "reviews";
    public static final String COLLECTION_CONVERSATIONS = "conversations";
    public static final String COLLECTION_MESSAGES = "messages";
    public static final String COLLECTION_NOTIFICATIONS = "notifications";
    public static final String COLLECTION_PASSWORD_RESET_OTPS = "password_reset_otps";

    private FirebaseService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.gson = new Gson();
    }

    public static FirebaseService getInstance() {
        if (instance == null) {
            instance = new FirebaseService();
        }
        return instance;
    }

    /**
     * Set authentication token for API calls
     */
    public void setIdToken(String idToken) {
        this.currentIdToken = idToken;
    }

    /**
     * Check if Firebase is authenticated
     */
    public boolean isAuthenticated() {
        return currentIdToken != null && !currentIdToken.isEmpty();
    }

    // ==================== USER OPERATIONS ====================

    /**
     * Save user data to Firestore
     * @param userId User ID (matches SQLite id)
     * @param userData Map containing user data
     */
    public void saveUser(String userId, Map<String, Object> userData, 
                        Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                String url = BASE_URL + "/" + COLLECTION_USERS + "?documentId=" + userId;
                
                Map<String, Object> document = new HashMap<>();
                document.put("fields", convertToFirestoreFields(userData));
                
                String jsonBody = gson.toJson(document);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                
                if (currentIdToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + currentIdToken);
                }
                
                HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), 
                    HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    System.out.println("✓ User data saved to Firestore: " + userId);
                    if (onSuccess != null) onSuccess.run();
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error saving user to Firestore: " + e.getMessage());
                if (onError != null) onError.accept(e);
            }
        });
    }

    /**
     * Load user data from Firestore
     */
    public void loadUser(String userId, Consumer<Map<String, Object>> onSuccess, 
                        Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                String url = BASE_URL + "/" + COLLECTION_USERS + "/" + userId;
                
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();
                
                if (currentIdToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + currentIdToken);
                }
                
                HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), 
                    HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() == 200) {
                    JsonObject document = JsonParser.parseString(response.body()).getAsJsonObject();
                    Map<String, Object> userData = parseFirestoreDocument(document);
                    
                    System.out.println("✓ User data loaded from Firestore: " + userId);
                    if (onSuccess != null) onSuccess.accept(userData);
                } else if (response.statusCode() == 404) {
                    if (onSuccess != null) onSuccess.accept(null); // User not found
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode());
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error loading user from Firestore: " + e.getMessage());
                if (onError != null) onError.accept(e);
            }
        });
    }

    // ==================== CROP OPERATIONS ====================

    /**
     * Save crop data to Firestore
     */
    public void saveCrop(String cropId, Map<String, Object> cropData,
                        Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                String url = BASE_URL + "/" + COLLECTION_CROPS + "?documentId=" + cropId;
                
                Map<String, Object> document = new HashMap<>();
                document.put("fields", convertToFirestoreFields(cropData));
                
                String jsonBody = gson.toJson(document);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                
                if (currentIdToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + currentIdToken);
                }
                
                HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), 
                    HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    System.out.println("✓ Crop saved to Firestore: " + cropId);
                    if (onSuccess != null) onSuccess.run();
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error saving crop to Firestore: " + e.getMessage());
                if (onError != null) onError.accept(e);
            }
        });
    }

    /**
     * Save crop photo with Base64 image data
     * @param cropId Crop ID
     * @param photoOrder Photo order (1-5)
     * @param imageBase64 Base64 encoded image data
     */
    public void saveCropPhoto(String cropId, int photoOrder, String imageBase64,
                             Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                String photoId = cropId + "_" + photoOrder;
                String url = BASE_URL + "/" + COLLECTION_CROP_PHOTOS + "?documentId=" + photoId;
                
                Map<String, Object> photoData = new HashMap<>();
                photoData.put("crop_id", cropId);
                photoData.put("photo_order", photoOrder);
                photoData.put("image_base64", imageBase64);
                photoData.put("created_at", System.currentTimeMillis());
                
                Map<String, Object> document = new HashMap<>();
                document.put("fields", convertToFirestoreFields(photoData));
                
                String jsonBody = gson.toJson(document);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                
                if (currentIdToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + currentIdToken);
                }
                
                HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), 
                    HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    System.out.println("✓ Crop photo saved to Firestore: " + photoId);
                    if (onSuccess != null) onSuccess.run();
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error saving crop photo: " + e.getMessage());
                if (onError != null) onError.accept(e);
            }
        });
    }

    /**
     * Load crop photo Base64 from Firestore
     */
    public void loadCropPhoto(String cropId, int photoOrder,
                             Consumer<String> onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                String photoId = cropId + "_" + photoOrder;
                String url = BASE_URL + "/" + COLLECTION_CROP_PHOTOS + "/" + photoId;
                
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();
                
                if (currentIdToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + currentIdToken);
                }
                
                HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), 
                    HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() == 200) {
                    JsonObject document = JsonParser.parseString(response.body()).getAsJsonObject();
                    Map<String, Object> photoData = parseFirestoreDocument(document);
                    
                    String imageBase64 = (String) photoData.get("image_base64");
                    if (onSuccess != null) onSuccess.accept(imageBase64);
                } else if (response.statusCode() == 404) {
                    if (onSuccess != null) onSuccess.accept(null);
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode());
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error loading crop photo: " + e.getMessage());
                if (onError != null) onError.accept(e);
            }
        });
    }

    /**
     * Load all crop photos for a crop
     */
    public void loadAllCropPhotos(String cropId, Consumer<List<String>> onSuccess, 
                                  Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                List<String> photoBase64List = new ArrayList<>();
                
                // Load photos 1-5
                for (int i = 1; i <= 5; i++) {
                    String photoId = cropId + "_" + i;
                    String url = BASE_URL + "/" + COLLECTION_CROP_PHOTOS + "/" + photoId;
                    
                    try {
                        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET();
                        
                        if (currentIdToken != null) {
                            requestBuilder.header("Authorization", "Bearer " + currentIdToken);
                        }
                        
                        HttpResponse<String> response = httpClient.send(
                            requestBuilder.build(), 
                            HttpResponse.BodyHandlers.ofString()
                        );
                        
                        if (response.statusCode() == 200) {
                            JsonObject document = JsonParser.parseString(response.body()).getAsJsonObject();
                            Map<String, Object> photoData = parseFirestoreDocument(document);
                            
                            String imageBase64 = (String) photoData.get("image_base64");
                            if (imageBase64 != null && !imageBase64.isEmpty()) {
                                photoBase64List.add(imageBase64);
                            }
                        }
                    } catch (Exception e) {
                        // Photo doesn't exist, continue
                    }
                }
                
                if (onSuccess != null) onSuccess.accept(photoBase64List);
                
            } catch (Exception e) {
                if (onError != null) onError.accept(e);
            }
        });
    }

    // ==================== PROFILE PHOTO OPERATIONS ====================

    /**
     * Save user profile photo as Base64
     */
    public void saveProfilePhoto(String userId, String imageBase64,
                                Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                String url = BASE_URL + "/" + COLLECTION_USERS + "/" + userId 
                    + "?updateMask.fieldPaths=profile_photo_base64&updateMask.fieldPaths=updated_at";
                
                // Update user document with profile photo
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("profile_photo_base64", imageBase64);
                updateData.put("updated_at", System.currentTimeMillis());
                
                Map<String, Object> document = new HashMap<>();
                document.put("fields", convertToFirestoreFields(updateData));
                
                String jsonBody = gson.toJson(document);

                // Use PATCH to update specific fields
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody));
                
                if (currentIdToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + currentIdToken);
                }
                
                HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), 
                    HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    System.out.println("✓ Profile photo saved to Firestore: " + userId);
                    if (onSuccess != null) onSuccess.run();
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error saving profile photo: " + e.getMessage());
                if (onError != null) onError.accept(e);
            }
        });
    }

    // ==================== ORDER OPERATIONS ====================

    /**
     * Save order to Firestore
     */
    public void saveOrder(String orderId, Map<String, Object> orderData,
                         Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                String url = BASE_URL + "/" + COLLECTION_ORDERS + "?documentId=" + orderId;
                
                Map<String, Object> document = new HashMap<>();
                document.put("fields", convertToFirestoreFields(orderData));
                
                String jsonBody = gson.toJson(document);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                
                if (currentIdToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + currentIdToken);
                }
                
                HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), 
                    HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    System.out.println("✓ Order saved to Firestore: " + orderId);
                    if (onSuccess != null) onSuccess.run();
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error saving order: " + e.getMessage());
                if (onError != null) onError.accept(e);
            }
        });
    }

    // ==================== HELPER METHODS ====================

    /**
     * Convert Java map to Firestore fields format
     */
    private Map<String, Object> convertToFirestoreFields(Map<String, Object> data) {
        Map<String, Object> fields = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            Map<String, Object> fieldValue = new HashMap<>();
            
            if (value == null) {
                fieldValue.put("nullValue", null);
            } else if (value instanceof String) {
                fieldValue.put("stringValue", value);
            } else if (value instanceof Integer) {
                fieldValue.put("integerValue", String.valueOf(value));
            } else if (value instanceof Long) {
                fieldValue.put("integerValue", String.valueOf(value));
            } else if (value instanceof Double) {
                fieldValue.put("doubleValue", value);
            } else if (value instanceof Float) {
                fieldValue.put("doubleValue", ((Float) value).doubleValue());
            } else if (value instanceof Boolean) {
                fieldValue.put("booleanValue", value);
            } else {
                // Convert other types to string
                fieldValue.put("stringValue", value.toString());
            }
            
            fields.put(key, fieldValue);
        }
        
        return fields;
    }

    /**
     * Parse Firestore document to Java map
     */
    private Map<String, Object> parseFirestoreDocument(JsonObject document) {
        Map<String, Object> result = new HashMap<>();
        
        if (!document.has("fields")) {
            return result;
        }
        
        JsonObject fields = document.getAsJsonObject("fields");
        
        for (String key : fields.keySet()) {
            JsonObject field = fields.getAsJsonObject(key);
            Object value = parseFirestoreValue(field);
            result.put(key, value);
        }
        
        return result;
    }

    /**
     * Parse a single Firestore field value
     */
    private Object parseFirestoreValue(JsonObject field) {
        if (field.has("stringValue")) {
            return field.get("stringValue").getAsString();
        } else if (field.has("integerValue")) {
            return Long.parseLong(field.get("integerValue").getAsString());
        } else if (field.has("doubleValue")) {
            return field.get("doubleValue").getAsDouble();
        } else if (field.has("booleanValue")) {
            return field.get("booleanValue").getAsBoolean();
        } else if (field.has("nullValue")) {
            return null;
        } else if (field.has("arrayValue")) {
            JsonArray array = field.getAsJsonObject("arrayValue").getAsJsonArray("values");
            List<Object> list = new ArrayList<>();
            if (array != null) {
                for (JsonElement element : array) {
                    list.add(parseFirestoreValue(element.getAsJsonObject()));
                }
            }
            return list;
        }
        return null;
    }

    /**
     * Sync local SQLite data to Firestore
     * Call this after any local database changes
     */
    public void syncToFirestore() {
        if (!isAuthenticated()) {
            System.out.println("⚠️ Cannot sync - not authenticated with Firebase");
            return;
        }
        
        System.out.println("🔄 Syncing local data to Firestore...");
        // Sync is handled by individual save operations
    }

    // ==================== PASSWORD RESET OTP OPERATIONS ====================

    /**
     * Generate a 6-digit OTP
     */
    private String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // 6 digits
        return String.valueOf(otp);
    }

    /**
     * Request PIN reset - Generate and store OTP in Firestore
     * Admin can view OTP in Firebase Console and manually provide to user
     * 
     * @param phone User's phone number
     * @param role User's role (farmer/buyer)
     * @return Generated OTP (for testing/logging - in production, admin views in Firebase Console)
     */
    public String requestPinReset(String phone, String role) throws Exception {
        String otp = generateOTP();
        long now = System.currentTimeMillis();
        long expiresAt = now + (15 * 60 * 1000); // 15 minutes
        
        // Create OTP document
        Map<String, Object> fields = new HashMap<>();
        fields.put("phone", createStringValue(phone));
        fields.put("role", createStringValue(role));
        fields.put("otp", createStringValue(otp));
        fields.put("createdAt", createIntegerValue(now));
        fields.put("expiresAt", createIntegerValue(expiresAt));
        fields.put("used", createBooleanValue(false));
        
        Map<String, Object> document = new HashMap<>();
        document.put("fields", fields);
        
        // Use phone+role as document ID for easy lookup
        String documentId = phone + "_" + role;
        String url = BASE_URL + "/" + COLLECTION_PASSWORD_RESET_OTPS + "?documentId=" + documentId;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(document)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("✅ OTP stored in Firestore for " + phone + " (" + role + ")");
            System.out.println("🔑 OTP: " + otp + " (expires in 15 minutes)");
            System.out.println("📋 View in Firebase Console: Firestore > password_reset_otps > " + documentId);
            return otp;
        } else {
            System.err.println("❌ Failed to store OTP: " + response.body());
            throw new Exception("Failed to store OTP in Firestore");
        }
    }

    /**
     * Verify OTP from Firestore for PIN reset
     * 
     * @param phone User's phone number
     * @param role User's role (farmer/buyer)
     * @param otp OTP entered by user
     * @return true if OTP is valid and not expired
     */
    public boolean verifyPinResetOTP(String phone, String role, String otp) throws Exception {
        String documentId = phone + "_" + role;
        String url = BASE_URL + "/" + COLLECTION_PASSWORD_RESET_OTPS + "/" + documentId;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 404) {
            System.out.println("❌ No OTP request found for " + phone + " (" + role + ")");
            return false;
        }
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonObject doc = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject fields = doc.getAsJsonObject("fields");
            
            String storedOtp = getStringValue(fields, "otp");
            long expiresAt = getIntegerValue(fields, "expiresAt");
            boolean used = getBooleanValue(fields, "used");
            
            long now = System.currentTimeMillis();
            
            // Validate OTP
            if (used) {
                System.out.println("❌ OTP already used");
                return false;
            }
            
            if (now > expiresAt) {
                System.out.println("❌ OTP expired");
                return false;
            }
            
            if (!otp.equals(storedOtp)) {
                System.out.println("❌ Invalid OTP");
                return false;
            }
            
            System.out.println("✅ OTP verified successfully");
            return true;
        } else {
            System.err.println("❌ Failed to verify OTP: " + response.body());
            throw new Exception("Failed to verify OTP");
        }
    }

    /**
     * Mark OTP as used after successful PIN reset
     * 
     * @param phone User's phone number
     * @param role User's role (farmer/buyer)
     */
    public void markOTPAsUsed(String phone, String role) throws Exception {
        String documentId = phone + "_" + role;
        String url = BASE_URL + "/" + COLLECTION_PASSWORD_RESET_OTPS + "/" + documentId + "?updateMask.fieldPaths=used";
        
        Map<String, Object> fields = new HashMap<>();
        fields.put("used", createBooleanValue(true));
        
        Map<String, Object> document = new HashMap<>();
        document.put("fields", fields);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(document)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("✅ OTP marked as used");
        } else {
            System.err.println("⚠️ Failed to mark OTP as used: " + response.body());
        }
    }

    /**
     * Helper method to create string value for Firestore
     */
    private Map<String, Object> createStringValue(String value) {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("stringValue", value);
        return valueMap;
    }

    /**
     * Helper method to create integer value for Firestore
     */
    private Map<String, Object> createIntegerValue(long value) {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("integerValue", String.valueOf(value));
        return valueMap;
    }

    /**
     * Helper method to create boolean value for Firestore
     */
    private Map<String, Object> createBooleanValue(boolean value) {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("booleanValue", value);
        return valueMap;
    }

    /**
     * Helper method to get string value from Firestore field
     */
    private String getStringValue(JsonObject fields, String fieldName) {
        if (fields.has(fieldName)) {
            JsonObject field = fields.getAsJsonObject(fieldName);
            if (field.has("stringValue")) {
                return field.get("stringValue").getAsString();
            }
        }
        return "";
    }

    /**
     * Helper method to get integer value from Firestore field
     */
    private long getIntegerValue(JsonObject fields, String fieldName) {
        if (fields.has(fieldName)) {
            JsonObject field = fields.getAsJsonObject(fieldName);
            if (field.has("integerValue")) {
                return Long.parseLong(field.get("integerValue").getAsString());
            }
        }
        return 0;
    }

    /**
     * Helper method to get boolean value from Firestore field
     */
    private boolean getBooleanValue(JsonObject fields, String fieldName) {
        if (fields.has(fieldName)) {
            JsonObject field = fields.getAsJsonObject(fieldName);
            if (field.has("booleanValue")) {
                return field.get("booleanValue").getAsBoolean();
            }
        }
        return false;
    }
}

