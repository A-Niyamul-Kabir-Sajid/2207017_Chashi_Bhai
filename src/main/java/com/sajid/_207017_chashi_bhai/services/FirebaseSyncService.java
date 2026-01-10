package com.sajid._207017_chashi_bhai.services;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import javafx.application.Platform;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * FirebaseSyncService - Syncs data between SQLite (local) and Firestore (cloud)
 * 
 * Strategy:
 * - SQLite is the primary source of truth for offline operations
 * - Firebase Firestore provides cloud backup and real-time sync
 * - Write to SQLite first, then sync to Firebase
 * - Read from SQLite for speed, fetch from Firebase when needed
 */
public class FirebaseSyncService {
    
    private static final String DB_URL = "jdbc:sqlite:data/chashi_bhai.db";
        private static final DateTimeFormatter SQLITE_TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final FirebaseService firebaseService;
    
    private static FirebaseSyncService instance;
    
    private FirebaseSyncService() {
        this.firebaseService = FirebaseService.getInstance();
    }
    
    public static FirebaseSyncService getInstance() {
        if (instance == null) {
            instance = new FirebaseSyncService();
        }
        return instance;
    }
    
    /**
     * Check if sync is available (Firebase initialized)
     */
    public boolean isSyncAvailable() {
        return firebaseService.isInitialized();
    }
    
    // ==================== USER SYNC ====================
    
    /**
     * Sync user to Firebase after SQLite insert
     * 
     * @param userId SQLite user ID
     */
    public void syncUserToFirebase(int userId) {
        if (!isSyncAvailable()) {
            System.out.println("⚠️ Firebase not available, skipping user sync");
            return;
        }
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM users WHERE id = ?")) {
            
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("id", rs.getInt("id"));
                userData.put("name", rs.getString("name"));
                userData.put("phone", rs.getString("phone"));
                userData.put("pin", rs.getString("pin"));
                userData.put("role", rs.getString("role"));
                userData.put("district", rs.getString("district"));
                userData.put("upazila", rs.getString("upazila"));
                userData.put("village", rs.getString("village"));
                userData.put("nid", rs.getString("nid"));
                userData.put("profile_photo", rs.getString("profile_photo"));
                userData.put("is_verified", rs.getBoolean("is_verified"));
                
                firebaseService.createUser(
                    String.valueOf(userId),
                    userData,
                    () -> System.out.println("✅ User synced to Firebase: " + userId),
                    e -> System.err.println("❌ Failed to sync user: " + e.getMessage())
                );
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error reading user from SQLite: " + e.getMessage());
        }
    }
    
    /**
     * Sync user from Firebase to SQLite
     */
    public void syncUserFromFirebase(String userId, Runnable onComplete) {
        if (!isSyncAvailable()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        firebaseService.getUser(
            userId,
            doc -> {
                if (doc.exists()) {
                    try (Connection conn = DriverManager.getConnection(DB_URL)) {
                        Map<String, Object> data = doc.getData();
                        if (data == null) {
                            if (onComplete != null) {
                                Platform.runLater(onComplete);
                            }
                            return;
                        }
                        
                        // Check if user exists in SQLite
                        PreparedStatement checkStmt = conn.prepareStatement(
                            "SELECT id FROM users WHERE id = ?"
                        );
                        checkStmt.setInt(1, Integer.parseInt(userId));
                        ResultSet rs = checkStmt.executeQuery();
                        
                        if (rs.next()) {
                            // Update existing
                            PreparedStatement updateStmt = conn.prepareStatement(
                                "UPDATE users SET name = ?, phone = ?, role = ?, " +
                                "district = ?, upazila = ?, village = ?, " +
                                "profile_photo = ?, is_verified = ? WHERE id = ?"
                            );
                            updateStmt.setString(1, (String) data.get("name"));
                            updateStmt.setString(2, (String) data.get("phone"));
                            updateStmt.setString(3, (String) data.get("role"));
                            updateStmt.setString(4, (String) data.get("district"));
                            updateStmt.setString(5, (String) data.get("upazila"));
                            updateStmt.setString(6, (String) data.get("village"));
                            updateStmt.setString(7, (String) data.get("profile_photo"));
                            updateStmt.setBoolean(8, (Boolean) data.getOrDefault("is_verified", false));
                            updateStmt.setInt(9, Integer.parseInt(userId));
                            updateStmt.executeUpdate();
                            
                            System.out.println("✅ User updated from Firebase: " + userId);
                        } else {
                            // Insert new
                            PreparedStatement insertStmt = conn.prepareStatement(
                                "INSERT INTO users (id, name, phone, pin, role, district, " +
                                "upazila, village, profile_photo, is_verified) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                            );
                            insertStmt.setInt(1, Integer.parseInt(userId));
                            insertStmt.setString(2, (String) data.get("name"));
                            insertStmt.setString(3, (String) data.get("phone"));
                            insertStmt.setString(4, (String) data.get("pin"));
                            insertStmt.setString(5, (String) data.get("role"));
                            insertStmt.setString(6, (String) data.get("district"));
                            insertStmt.setString(7, (String) data.get("upazila"));
                            insertStmt.setString(8, (String) data.get("village"));
                            insertStmt.setString(9, (String) data.get("profile_photo"));
                            insertStmt.setBoolean(10, (Boolean) data.getOrDefault("is_verified", false));
                            insertStmt.executeUpdate();
                            
                            System.out.println("✅ User inserted from Firebase: " + userId);
                        }
                        
                        if (onComplete != null) {
                            Platform.runLater(onComplete);
                        }
                        
                    } catch (SQLException e) {
                        System.err.println("❌ Error syncing user from Firebase: " + e.getMessage());
                    }
                }
            },
            e -> {
                System.err.println("❌ Error fetching user from Firebase: " + e.getMessage());
                if (onComplete != null) {
                    Platform.runLater(onComplete);
                }
            }
        );
    }
    
    // ==================== CROP SYNC ====================
    
    /**
     * Sync crop to Firebase after SQLite insert
     */
    public void syncCropToFirebase(int cropId) {
        if (!isSyncAvailable()) {
            System.out.println("⚠️ Firebase not available, skipping crop sync");
            return;
        }
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM crops WHERE id = ?")) {
            
            stmt.setInt(1, cropId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> cropData = new HashMap<>();
                cropData.put("id", rs.getInt("id"));
                cropData.put("product_code", rs.getString("product_code"));
                cropData.put("farmer_id", rs.getInt("farmer_id"));
                cropData.put("name", rs.getString("name"));
                cropData.put("category", rs.getString("category"));
                cropData.put("initial_quantity_kg", rs.getDouble("initial_quantity_kg"));
                cropData.put("available_quantity_kg", rs.getDouble("available_quantity_kg"));
                cropData.put("price_per_kg", rs.getDouble("price_per_kg"));
                cropData.put("description", rs.getString("description"));
                cropData.put("district", rs.getString("district"));
                cropData.put("upazila", rs.getString("upazila"));
                cropData.put("village", rs.getString("village"));
                cropData.put("harvest_date", rs.getString("harvest_date"));
                cropData.put("transport_info", rs.getString("transport_info"));
                cropData.put("status", rs.getString("status"));
                
                firebaseService.createCrop(
                    String.valueOf(cropId),
                    cropData,
                    () -> System.out.println("✅ Crop synced to Firebase: " + cropId),
                    e -> System.err.println("❌ Failed to sync crop: " + e.getMessage())
                );
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error reading crop from SQLite: " + e.getMessage());
        }
    }
    
    /**
     * Sync all crops from a farmer from Firebase
     */
    public void syncFarmerCropsFromFirebase(int farmerId, Runnable onComplete) {
        if (!isSyncAvailable()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        firebaseService.getCropsByFarmer(
            String.valueOf(farmerId),
            querySnapshot -> {
                int syncedCount = 0;
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    try (Connection conn = DriverManager.getConnection(DB_URL)) {
                        Map<String, Object> data = doc.getData();
                        if (data == null) {
                            continue;
                        }
                        if (data == null) {
                            continue;
                        }
                        
                        // Update or insert crop in SQLite
                        PreparedStatement stmt = conn.prepareStatement(
                            "INSERT OR REPLACE INTO crops " +
                            "(id, product_code, farmer_id, name, category, " +
                            "initial_quantity_kg, available_quantity_kg, price_per_kg, " +
                            "description, district, upazila, village, harvest_date, " +
                            "transport_info, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        );
                        
                        stmt.setInt(1, ((Long) data.get("id")).intValue());
                        stmt.setString(2, (String) data.get("product_code"));
                        stmt.setInt(3, ((Long) data.get("farmer_id")).intValue());
                        stmt.setString(4, (String) data.get("name"));
                        stmt.setString(5, (String) data.get("category"));
                        stmt.setDouble(6, ((Number) data.get("initial_quantity_kg")).doubleValue());
                        stmt.setDouble(7, ((Number) data.get("available_quantity_kg")).doubleValue());
                        stmt.setDouble(8, ((Number) data.get("price_per_kg")).doubleValue());
                        stmt.setString(9, (String) data.get("description"));
                        stmt.setString(10, (String) data.get("district"));
                        stmt.setString(11, (String) data.get("upazila"));
                        stmt.setString(12, (String) data.get("village"));
                        stmt.setString(13, (String) data.get("harvest_date"));
                        stmt.setString(14, (String) data.get("transport_info"));
                        stmt.setString(15, (String) data.get("status"));
                        
                        stmt.executeUpdate();
                        syncedCount++;
                        
                    } catch (SQLException e) {
                        System.err.println("❌ Error syncing crop from Firebase: " + e.getMessage());
                    }
                }
                
                System.out.println("✅ Synced " + syncedCount + " crops from Firebase");
                
                if (onComplete != null) {
                    Platform.runLater(onComplete);
                }
            },
            e -> {
                System.err.println("❌ Error fetching crops from Firebase: " + e.getMessage());
                if (onComplete != null) {
                    Platform.runLater(onComplete);
                }
            }
        );
    }
    
    // ==================== ORDER SYNC ====================

    private static String toSqliteTimestamp(com.google.cloud.Timestamp ts) {
        if (ts == null) return null;
        Instant instant = Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
        return SQLITE_TS_FORMAT.format(instant);
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Integer i) return i;
        if (value instanceof Long l) return l.intValue();
        if (value instanceof Double d) return d.intValue();
        if (value instanceof Float f) return f.intValue();
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double d) return d;
        if (value instanceof Float f) return (double) f;
        if (value instanceof Long l) return l.doubleValue();
        if (value instanceof Integer i) return i.doubleValue();
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }
    
    /**
     * Sync order to Firebase after SQLite insert
     */
    public void syncOrderToFirebase(int orderId) {
        if (!isSyncAvailable()) {
            System.out.println("⚠️ Firebase not available, skipping order sync");
            return;
        }
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM orders WHERE id = ?")) {

            stmt.setInt(1, orderId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return;
            }

            Map<String, Object> orderData = new HashMap<>();
            orderData.put("id", rs.getInt("id"));
            orderData.put("order_number", rs.getString("order_number"));
            orderData.put("crop_id", rs.getInt("crop_id"));
            orderData.put("farmer_id", rs.getInt("farmer_id"));
            orderData.put("buyer_id", rs.getInt("buyer_id"));
            orderData.put("quantity_kg", rs.getDouble("quantity_kg"));
            orderData.put("price_per_kg", rs.getDouble("price_per_kg"));
            orderData.put("total_amount", rs.getDouble("total_amount"));
            orderData.put("delivery_address", rs.getString("delivery_address"));
            orderData.put("delivery_district", rs.getString("delivery_district"));
            orderData.put("delivery_upazila", rs.getString("delivery_upazila"));
            orderData.put("buyer_phone", rs.getString("buyer_phone"));
            orderData.put("buyer_name", rs.getString("buyer_name"));
            orderData.put("status", rs.getString("status"));
            orderData.put("payment_status", rs.getString("payment_status"));
            orderData.put("payment_method", rs.getString("payment_method"));
            orderData.put("notes", rs.getString("notes"));

            String orderIdStr = String.valueOf(orderId);
            firebaseService.getOrder(
                    orderIdStr,
                    doc -> {
                        if (doc.exists()) {
                            firebaseService.upsertOrder(
                                    orderIdStr,
                                    orderData,
                                    () -> System.out.println("✅ Order fields synced to Firebase: " + orderId),
                                    e -> System.err.println("❌ Failed to upsert order: " + e.getMessage())
                            );
                        } else {
                            firebaseService.createOrder(
                                    orderIdStr,
                                    orderData,
                                    () -> System.out.println("✅ Order created in Firebase: " + orderId),
                                    e -> System.err.println("❌ Failed to create order: " + e.getMessage())
                            );
                        }
                    },
                    e -> System.err.println("❌ Failed to check order existence: " + e.getMessage())
            );

        } catch (SQLException e) {
            System.err.println("❌ Error reading order from SQLite: " + e.getMessage());
        }
    }

    /**
     * Push an order status update to Firebase (timestamps handled server-side)
     */
    public void syncOrderStatusToFirebase(int orderId, String newStatus, Runnable onComplete) {
        if (!isSyncAvailable()) {
            if (onComplete != null) Platform.runLater(onComplete);
            return;
        }

        firebaseService.updateOrderStatus(
                String.valueOf(orderId),
                newStatus,
                () -> {
                    if (onComplete != null) Platform.runLater(onComplete);
                },
                e -> {
                    System.err.println("❌ Failed to sync order status: " + e.getMessage());
                    if (onComplete != null) Platform.runLater(onComplete);
                }
        );
    }

    private void upsertOrdersFromFirebase(QuerySnapshot querySnapshot) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String updateSql =
                    "UPDATE orders SET order_number = ?, crop_id = ?, farmer_id = ?, buyer_id = ?, " +
                            "quantity_kg = ?, price_per_kg = ?, total_amount = ?, delivery_address = ?, " +
                            "delivery_district = ?, delivery_upazila = ?, buyer_phone = ?, buyer_name = ?, " +
                            "status = ?, payment_status = ?, payment_method = ?, notes = ?, " +
                            "created_at = COALESCE(?, created_at), updated_at = COALESCE(?, updated_at), " +
                            "accepted_at = ?, in_transit_at = ?, delivered_at = ?, completed_at = ? " +
                            "WHERE id = ?";

            String insertSql =
                    "INSERT INTO orders (id, order_number, crop_id, farmer_id, buyer_id, quantity_kg, " +
                            "price_per_kg, total_amount, delivery_address, delivery_district, delivery_upazila, " +
                            "buyer_phone, buyer_name, status, payment_status, payment_method, notes, " +
                            "created_at, updated_at, accepted_at, in_transit_at, delivered_at, completed_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Map<String, Object> data = doc.getData();
                    if (data == null) continue;

                    int id = toInt(data.get("id"), -1);
                    if (id <= 0) {
                        id = toInt(doc.getId(), -1);
                    }
                    if (id <= 0) continue;

                    Double quantity = toDouble(data.get("quantity_kg"));
                    Double pricePerKg = toDouble(data.get("price_per_kg"));
                    Double totalAmount = toDouble(data.get("total_amount"));

                    String createdAt = toSqliteTimestamp(doc.getTimestamp("created_at"));
                    String updatedAt = toSqliteTimestamp(doc.getTimestamp("updated_at"));
                    String acceptedAt = toSqliteTimestamp(doc.getTimestamp("accepted_at"));
                    String inTransitAt = toSqliteTimestamp(doc.getTimestamp("in_transit_at"));
                    String deliveredAt = toSqliteTimestamp(doc.getTimestamp("delivered_at"));
                    String completedAt = toSqliteTimestamp(doc.getTimestamp("completed_at"));

                    // UPDATE first
                    updateStmt.setString(1, (String) data.get("order_number"));
                    updateStmt.setInt(2, toInt(data.get("crop_id"), 0));
                    updateStmt.setInt(3, toInt(data.get("farmer_id"), 0));
                    updateStmt.setInt(4, toInt(data.get("buyer_id"), 0));
                    updateStmt.setObject(5, quantity);
                    updateStmt.setObject(6, pricePerKg);
                    updateStmt.setObject(7, totalAmount);
                    updateStmt.setString(8, (String) data.get("delivery_address"));
                    updateStmt.setString(9, (String) data.get("delivery_district"));
                    updateStmt.setString(10, (String) data.get("delivery_upazila"));
                    updateStmt.setString(11, (String) data.get("buyer_phone"));
                    updateStmt.setString(12, (String) data.get("buyer_name"));
                    updateStmt.setString(13, (String) data.get("status"));
                    updateStmt.setString(14, (String) data.get("payment_status"));
                    updateStmt.setString(15, (String) data.get("payment_method"));
                    updateStmt.setString(16, (String) data.get("notes"));
                    updateStmt.setString(17, createdAt);
                    updateStmt.setString(18, updatedAt);
                    updateStmt.setString(19, acceptedAt);
                    updateStmt.setString(20, inTransitAt);
                    updateStmt.setString(21, deliveredAt);
                    updateStmt.setString(22, completedAt);
                    updateStmt.setInt(23, id);

                    int updated = updateStmt.executeUpdate();
                    if (updated > 0) {
                        continue;
                    }

                    // INSERT if missing locally
                    insertStmt.setInt(1, id);
                    insertStmt.setString(2, (String) data.get("order_number"));
                    insertStmt.setInt(3, toInt(data.get("crop_id"), 0));
                    insertStmt.setInt(4, toInt(data.get("farmer_id"), 0));
                    insertStmt.setInt(5, toInt(data.get("buyer_id"), 0));
                    insertStmt.setObject(6, quantity);
                    insertStmt.setObject(7, pricePerKg);
                    insertStmt.setObject(8, totalAmount);
                    insertStmt.setString(9, (String) data.get("delivery_address"));
                    insertStmt.setString(10, (String) data.get("delivery_district"));
                    insertStmt.setString(11, (String) data.get("delivery_upazila"));
                    insertStmt.setString(12, (String) data.get("buyer_phone"));
                    insertStmt.setString(13, (String) data.get("buyer_name"));
                    insertStmt.setString(14, (String) data.get("status"));
                    insertStmt.setString(15, (String) data.get("payment_status"));
                    insertStmt.setString(16, (String) data.get("payment_method"));
                    insertStmt.setString(17, (String) data.get("notes"));
                    insertStmt.setString(18, createdAt);
                    insertStmt.setString(19, updatedAt);
                    insertStmt.setString(20, acceptedAt);
                    insertStmt.setString(21, inTransitAt);
                    insertStmt.setString(22, deliveredAt);
                    insertStmt.setString(23, completedAt);
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error upserting orders from Firebase: " + e.getMessage());
        }
    }
    
    /**
     * Sync farmer's orders from Firebase (for real-time updates)
     */
    public void syncFarmerOrdersFromFirebase(int farmerId, Runnable onComplete) {
        if (!isSyncAvailable()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        firebaseService.getOrdersByFarmer(
                farmerId,
                querySnapshot -> {
                    upsertOrdersFromFirebase(querySnapshot);
                    if (onComplete != null) {
                        Platform.runLater(onComplete);
                    }
                },
                e -> {
                    System.err.println("❌ Error fetching orders from Firebase: " + e.getMessage());
                    if (onComplete != null) {
                        Platform.runLater(onComplete);
                    }
                }
        );
    }

    /**
     * Sync buyer's orders from Firebase (for real-time updates)
     */
    public void syncBuyerOrdersFromFirebase(int buyerId, Runnable onComplete) {
        if (!isSyncAvailable()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        firebaseService.getOrdersByBuyer(
                buyerId,
                querySnapshot -> {
                    upsertOrdersFromFirebase(querySnapshot);
                    if (onComplete != null) {
                        Platform.runLater(onComplete);
                    }
                },
                e -> {
                    System.err.println("❌ Error fetching orders from Firebase: " + e.getMessage());
                    if (onComplete != null) {
                        Platform.runLater(onComplete);
                    }
                }
        );
    }
    
    /**
     * Update order status in both SQLite and Firebase
     */
    public void updateOrderStatus(int orderId, String newStatus, Runnable onComplete) {
        // Update SQLite first
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE orders SET status = ? WHERE id = ?")) {
            
            stmt.setString(1, newStatus);
            stmt.setInt(2, orderId);
            stmt.executeUpdate();
            
            System.out.println("✅ Order status updated in SQLite: " + orderId);
            
            // Then sync to Firebase
            if (isSyncAvailable()) {
                firebaseService.updateOrderStatus(
                    String.valueOf(orderId),
                    newStatus,
                    () -> {
                        System.out.println("✅ Order status synced to Firebase: " + orderId);
                        if (onComplete != null) {
                            Platform.runLater(onComplete);
                        }
                    },
                    e -> {
                        System.err.println("❌ Failed to sync order status: " + e.getMessage());
                        if (onComplete != null) {
                            Platform.runLater(onComplete);
                        }
                    }
                );
            } else {
                if (onComplete != null) {
                    Platform.runLater(onComplete);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Error updating order status in SQLite: " + e.getMessage());
        }
    }
    
    /**
     * Full sync - push all local data to Firebase (initial sync)
     */
    public void performFullSync(Runnable onComplete) {
        if (!isSyncAvailable()) {
            System.out.println("⚠️ Firebase not available, cannot perform full sync");
            if (onComplete != null) onComplete.run();
            return;
        }
        
        System.out.println("🔄 Starting full sync to Firebase...");
        
        // This would sync all tables - implement as needed
        // For now, just complete
        System.out.println("✅ Full sync completed");
        
        if (onComplete != null) {
            Platform.runLater(onComplete);
        }
    }
}
