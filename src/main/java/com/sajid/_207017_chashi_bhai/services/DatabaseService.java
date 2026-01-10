package com.sajid._207017_chashi_bhai.services;

import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * DatabaseService - Centralized database operations using SQLite
 * All write operations run on single-threaded executor for thread safety
 * Read operations can be parallelized but use the same executor for simplicity
 */
public class DatabaseService {

    private static final String DB_URL = "jdbc:sqlite:data/chashi_bhai.db";
    private static DatabaseService instance;
    
    public static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("DatabaseWorker");
        return thread;
    });

    private DatabaseService() {
        // Private constructor for singleton
    }

    public static DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    /**
     * Get a database connection
     */
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Execute a query asynchronously (SELECT operations)
     * 
     * @param sql SQL query string
     * @param params Query parameters
     * @param onSuccess Callback with ResultSet (runs on UI thread via Platform.runLater)
     * @param onError Error handler (runs on UI thread)
     */
    public static void executeQueryAsync(String sql, Object[] params, 
                                        Consumer<ResultSet> onSuccess, 
                                        Consumer<Exception> onError) {
        dbExecutor.submit(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                // Set parameters
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                }

                ResultSet rs = stmt.executeQuery();
                
                // Callback with result - callback must NOT use Platform.runLater to read ResultSet
                // Read all data from ResultSet BEFORE using Platform.runLater
                if (onSuccess != null) {
                    onSuccess.accept(rs);
                }
                
            } catch (Exception e) {
                System.err.println("Database query error: " + e.getMessage());
                e.printStackTrace();
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Execute an update asynchronously (INSERT, UPDATE, DELETE operations)
     * 
     * @param sql SQL update string
     * @param params Update parameters
     * @param onSuccess Callback with number of affected rows (runs on UI thread)
     * @param onError Error handler (runs on UI thread)
     */
    public static void executeUpdateAsync(String sql, Object[] params, 
                                         Consumer<Integer> onSuccess, 
                                         Consumer<Exception> onError) {
        dbExecutor.submit(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                // Set parameters
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                }

                int rowsAffected = stmt.executeUpdate();
                
                // Callback with result
                if (onSuccess != null) {
                    onSuccess.accept(rowsAffected);
                }
                
            } catch (Exception e) {
                System.err.println("Database update error: " + e.getMessage());
                e.printStackTrace();
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Execute a batch update asynchronously (multiple INSERT/UPDATE operations)
     * 
     * @param sql SQL update string
     * @param paramsList List of parameter arrays
     * @param onSuccess Callback with total affected rows
     * @param onError Error handler
     */
    public static void executeBatchAsync(String sql, Object[][] paramsList, 
                                        Consumer<Integer> onSuccess, 
                                        Consumer<Exception> onError) {
        dbExecutor.submit(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                conn.setAutoCommit(false);
                
                int totalAffected = 0;
                for (Object[] params : paramsList) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                    stmt.addBatch();
                }
                
                int[] results = stmt.executeBatch();
                conn.commit();
                
                for (int result : results) {
                    totalAffected += result;
                }
                
                int finalTotal = totalAffected;
                if (onSuccess != null) {
                    onSuccess.accept(finalTotal);
                }
                
            } catch (Exception e) {
                System.err.println("Database batch error: " + e.getMessage());
                e.printStackTrace();
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Initialize database tables (call this on app startup)
     */
    public static void initializeDatabase() {
        dbExecutor.submit(() -> {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // Users table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "phone TEXT UNIQUE NOT NULL, " +
                    "pin TEXT NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "district TEXT, " +
                    "upazila TEXT, " +
                    "village TEXT, " +
                    "nid TEXT, " +
                    "profile_photo TEXT, " +
                    "is_verified INTEGER DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
                );
                
                // Add farmer statistics columns (if not exist)
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN total_accepted_orders INTEGER DEFAULT 0");
                    System.out.println("Added column: total_accepted_orders");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        throw e;
                    }
                }
                
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN most_sold_crop TEXT");
                    System.out.println("Added column: most_sold_crop");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        throw e;
                    }
                }
                
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN total_income REAL DEFAULT 0.0");
                    System.out.println("Added column: total_income");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        throw e;
                    }
                }
                
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN rating REAL DEFAULT 0.0");
                    System.out.println("Added column: rating");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        throw e;
                    }
                }
                
                // Add buyer statistics columns (if not exist)
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN total_buyer_orders INTEGER DEFAULT 0");
                    System.out.println("Added column: total_buyer_orders");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        throw e;
                    }
                }
                
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN most_bought_crop TEXT");
                    System.out.println("Added column: most_bought_crop");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        throw e;
                    }
                }
                
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN total_expense REAL DEFAULT 0.0");
                    System.out.println("Added column: total_expense");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        throw e;
                    }
                }

                // Crops table with proper columns
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS crops (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "product_code TEXT UNIQUE NOT NULL, " +
                    "farmer_id INTEGER NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "category TEXT NOT NULL, " +
                    "initial_quantity_kg REAL NOT NULL, " +
                    "available_quantity_kg REAL NOT NULL, " +
                    "price_per_kg REAL NOT NULL, " +
                    "description TEXT, " +
                    "district TEXT NOT NULL, " +
                    "upazila TEXT, " +
                    "village TEXT, " +
                    "harvest_date DATE, " +
                    "transport_info TEXT, " +
                    "status TEXT DEFAULT 'active' CHECK(status IN ('active', 'sold', 'expired', 'deleted')), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (farmer_id) REFERENCES users(id) ON DELETE CASCADE)"
                );

                // Crop photos table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS crop_photos (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "crop_id INTEGER NOT NULL, " +
                    "photo_path TEXT NOT NULL, " +
                    "photo_order INTEGER DEFAULT 1, " +
                    "FOREIGN KEY (crop_id) REFERENCES crops(id) ON DELETE CASCADE)"
                );

                // Farm photos table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS farm_photos (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "farmer_id INTEGER NOT NULL, " +
                    "photo_path TEXT NOT NULL, " +
                    "FOREIGN KEY (farmer_id) REFERENCES users(id))"
                );

                // Orders table with proper columns
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS orders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "order_number TEXT UNIQUE NOT NULL, " +
                    "crop_id INTEGER NOT NULL, " +
                    "farmer_id INTEGER NOT NULL, " +
                    "buyer_id INTEGER NOT NULL, " +
                    "quantity_kg REAL NOT NULL, " +
                    "price_per_kg REAL NOT NULL, " +
                    "total_amount REAL NOT NULL, " +
                    "delivery_address TEXT, " +
                    "delivery_district TEXT, " +
                    "delivery_upazila TEXT, " +
                    "buyer_phone TEXT NOT NULL, " +
                    "buyer_name TEXT NOT NULL, " +
                    "status TEXT DEFAULT 'new' CHECK(status IN ('new', 'processing', 'accepted', 'shipped', 'in_transit', 'delivered', 'rejected', 'cancelled', 'completed')), " +
                    "payment_status TEXT DEFAULT 'pending' CHECK(payment_status IN ('pending', 'partial', 'paid', 'refunded')), " +
                    "payment_method TEXT CHECK(payment_method IN ('cash', 'bkash', 'nagad', 'rocket', 'bank')), " +
                    "notes TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "accepted_at TIMESTAMP, " +
                    "in_transit_at TIMESTAMP, " +
                    "delivered_at TIMESTAMP, " +
                    "completed_at TIMESTAMP, " +
                    "FOREIGN KEY (crop_id) REFERENCES crops(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (farmer_id) REFERENCES users(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE)"
                );

                // Orders migrations
                try {
                    stmt.execute("ALTER TABLE orders ADD COLUMN in_transit_at TIMESTAMP");
                    System.out.println("Added column: in_transit_at");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        throw e;
                    }
                }

                try {
                    stmt.execute("ALTER TABLE orders ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                    System.out.println("Added column: updated_at");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        throw e;
                    }
                }

                // Reviews table (replaces old ratings table)
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS reviews (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "order_id INTEGER NOT NULL, " +
                    "reviewer_id INTEGER NOT NULL, " +
                    "reviewee_id INTEGER NOT NULL, " +
                    "rating INTEGER NOT NULL CHECK(rating >= 1 AND rating <= 5), " +
                    "review_text TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (reviewee_id) REFERENCES users(id) ON DELETE CASCADE)"
                );

                // Market prices table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS market_prices (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "crop_name TEXT NOT NULL, " +
                    "price REAL NOT NULL, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
                );

                // Conversations table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS conversations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user1_id INTEGER NOT NULL, " +
                    "user2_id INTEGER NOT NULL, " +
                    "crop_id INTEGER, " +
                    "last_message TEXT, " +
                    "last_message_time TIMESTAMP, " +
                    "unread_count_user1 INTEGER DEFAULT 0, " +
                    "unread_count_user2 INTEGER DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (crop_id) REFERENCES crops(id) ON DELETE SET NULL, " +
                    "UNIQUE(user1_id, user2_id, crop_id))"
                );

                // Messages table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "conversation_id INTEGER NOT NULL, " +
                    "sender_id INTEGER NOT NULL, " +
                    "receiver_id INTEGER NOT NULL, " +
                    "message_text TEXT, " +
                    "message_type TEXT DEFAULT 'text' CHECK(message_type IN ('text', 'image', 'file', 'location')), " +
                    "attachment_path TEXT, " +
                    "is_read BOOLEAN DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "read_at TIMESTAMP, " +
                    "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE)"
                );

                // Notifications table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS notifications (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "type TEXT DEFAULT 'info' CHECK(type IN ('info', 'success', 'warning', 'error', 'order', 'message', 'review')), " +
                    "is_read BOOLEAN DEFAULT 0, " +
                    "related_id INTEGER, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)"
                );

                System.out.println("Database initialized successfully");
                
            } catch (Exception e) {
                System.err.println("Database initialization error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Update user profile (name and district/hometown)
     * 
     * @param userId User ID
     * @param fullName New full name
     * @param homeTown New home town/district
     * @return true if update was successful
     */
    public static boolean updateUserProfile(int userId, String fullName, String homeTown) {
        String sql = "UPDATE users SET name = ?, district = ? WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, fullName);
            stmt.setString(2, homeTown);
            stmt.setInt(3, userId);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating user profile: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Create a new user account
     * 
     * @param phone User phone number (unique)
     * @param pin User PIN (should be hashed with BCrypt in production)
     * @param name User full name
     * @param role User role (farmer/buyer)
     * @param district User district
     * @return userId if successful, -1 if failed, -2 if phone already exists
     */
    public static int createUser(String phone, String pin, String name, String role, String district) {
        // First check if phone already exists
        String checkSql = "SELECT id FROM users WHERE phone = ?";
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            
            checkStmt.setString(1, phone);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                System.err.println("Phone number already exists: " + phone);
                return -2; // Phone already exists
            }
        } catch (SQLException e) {
            System.err.println("Error checking phone existence: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
        
        // Insert new user
        String sql = "INSERT INTO users (phone, pin, name, role, district) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, phone);
            stmt.setString(2, pin); // TODO: Hash with BCrypt before storing
            stmt.setString(3, name);
            stmt.setString(4, role.toLowerCase());
            stmt.setString(5, district);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Get the generated user ID
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    int userId = keys.getInt(1);
                    System.out.println("User created successfully with ID: " + userId);
                    return userId;
                }
            }
            
            return -1;
            
        } catch (SQLException e) {
            System.err.println("Error creating user: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Shutdown the database executor (call on app exit)
     */
    public static void shutdown() {
        System.out.println("[DatabaseService] Shutting down executor...");
        dbExecutor.shutdownNow();
        try {
            if (!dbExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                System.out.println("[DatabaseService] Executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            System.err.println("[DatabaseService] Shutdown interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
