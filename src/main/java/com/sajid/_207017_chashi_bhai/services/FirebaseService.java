package com.sajid._207017_chashi_bhai.services;

import java.sql.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.mindrot.jbcrypt.BCrypt;

import com.sajid._207017_chashi_bhai.models.User;

/**
 * Service for initializing and managing database connections (Firebase-style API with SQLite backend).
 * Provides access to Authentication and Database operations.
 * This is a singleton service that mimics Firebase SDK patterns while using SQLite for storage.
 */
public class FirebaseService {
    private static FirebaseService instance;
    private Connection connection;
    private boolean initialized = false;
    private String webApiKey; // For password verification (future use)
    
    private static final String DB_DIR = "data";
    private static final String DB_URL = "jdbc:sqlite:data/chashi_bhai.db";
    
    // Single-threaded executor for thread-safe database operations
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("FirebaseServiceWorker");
        return thread;
    });

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
     * Initialize Firebase-style service with credentials/configuration.
     * The credentials file can be placed in the resources folder or a specific path.
     * 
     * @param credentialsPath Path to the configuration JSON file (for future Firebase integration)
     * @throws IOException If credentials file is not found or invalid
     */
    public void initialize(String credentialsPath) throws IOException {
        if (initialized) {
            System.out.println("FirebaseService already initialized.");
            return;
        }

        try {
            // Ensure data directory exists
            ensureDataDirectoryExists();
            
            // Initialize SQLite connection
            connection = DriverManager.getConnection(DB_URL);
            
            // Initialize database tables
            initializeTables();
            
            this.initialized = true;
            
            // Set Web API Key for password verification (from Firebase Console)
            // Get this from: Firebase Console > Project Settings > General > Web API Key
            this.webApiKey = ""; // Update with your actual key when using Firebase
            
            System.out.println("FirebaseService initialized successfully with SQLite backend.");
        } catch (SQLException e) {
            System.err.println("Failed to initialize FirebaseService: " + e.getMessage());
            throw new IOException("Database initialization failed", e);
        } catch (Exception e) {
            System.err.println("Failed to initialize FirebaseService: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Initialize FirebaseService with default credentials path.
     * Looks for 'firebase-credentials.json' in the resources folder.
     */
    public void initialize() throws IOException {
        initialize("firebase-credentials.json");
    }

    /**
     * Ensure the data directory exists for SQLite database
     */
    private void ensureDataDirectoryExists() {
        File dataDir = new File(DB_DIR);
        if (!dataDir.exists()) {
            boolean created = dataDir.mkdirs();
            if (created) {
                System.out.println("Created data directory: " + dataDir.getAbsolutePath());
            } else {
                System.err.println("Failed to create data directory: " + dataDir.getAbsolutePath());
            }
        }
    }

    /**
     * Initialize database tables
     */
    private void initializeTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
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
                "farm_type TEXT, " +
                "profile_photo TEXT, " +
                "is_verified INTEGER DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // Crops table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS crops (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "farmer_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "price REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "quantity REAL NOT NULL, " +
                "harvest_date TEXT, " +
                "district TEXT, " +
                "transport_info TEXT, " +
                "description TEXT, " +
                "status TEXT DEFAULT 'active', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (farmer_id) REFERENCES users(id))"
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

            // Orders table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS orders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "crop_id INTEGER NOT NULL, " +
                "buyer_id INTEGER NOT NULL, " +
                "quantity REAL NOT NULL, " +
                "total_price REAL, " +
                "status TEXT DEFAULT 'pending', " +
                "payment_status TEXT DEFAULT 'pending', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (crop_id) REFERENCES crops(id), " +
                "FOREIGN KEY (buyer_id) REFERENCES users(id))"
            );

            // Ratings table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS ratings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "order_id INTEGER NOT NULL, " +
                "buyer_id INTEGER NOT NULL, " +
                "farmer_id INTEGER NOT NULL, " +
                "rating INTEGER NOT NULL CHECK(rating >= 1 AND rating <= 5), " +
                "comment TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (order_id) REFERENCES orders(id), " +
                "FOREIGN KEY (buyer_id) REFERENCES users(id), " +
                "FOREIGN KEY (farmer_id) REFERENCES users(id))"
            );

            // Messages table (for chat)
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender_id INTEGER NOT NULL, " +
                "receiver_id INTEGER NOT NULL, " +
                "message TEXT NOT NULL, " +
                "is_read INTEGER DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (sender_id) REFERENCES users(id), " +
                "FOREIGN KEY (receiver_id) REFERENCES users(id))"
            );

            // Market prices table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS market_prices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "crop_name TEXT NOT NULL, " +
                "price REAL NOT NULL, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // Transactions table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "order_id INTEGER NOT NULL, " +
                "amount REAL NOT NULL, " +
                "payment_method TEXT, " +
                "transaction_id TEXT, " +
                "status TEXT DEFAULT 'pending', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (order_id) REFERENCES orders(id))"
            );

            System.out.println("✅ All database tables initialized successfully.");
        }
    }

    /**
     * Get a new database connection
     * @return Connection instance
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Get the main database connection.
     * Similar to Firestore's getFirestore() method.
     * @return Connection instance
     */
    public Connection getDatabase() {
        if (!initialized) {
            throw new IllegalStateException("FirebaseService not initialized. Call initialize() first.");
        }
        return connection;
    }

    /**
     * Check if FirebaseService has been initialized.
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get Web API Key for password verification.
     * @return Web API Key
     */
    public String getWebApiKey() {
        return webApiKey;
    }

    // ==================== AUTHENTICATION METHODS ====================

    /**
     * Login user with phone number, PIN, and role.
     * 
     * @param phone User's phone number
     * @param pin User's PIN
     * @param role User's role (FARMER or BUYER)
     * @param onSuccess Callback with User object on successful login
     * @param onError Callback with error message on failure
     */
    public void login(String phone, String pin, String role, 
                      Consumer<User> onSuccess, Consumer<String> onError) {
        if (!initialized) {
            if (onError != null) onError.accept("FirebaseService not initialized.");
            return;
        }

        dbExecutor.submit(() -> {
            String sql = "SELECT * FROM users WHERE phone = ? AND role = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, phone);
                stmt.setString(2, role.toLowerCase());
                
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String storedPin = rs.getString("pin");
                    
                    // Use BCrypt.checkpw for hashed password verification
                    if (BCrypt.checkpw(pin, storedPin)) {
                        // Create User object from database result
                        User user = new User();
                        user.setId(rs.getInt("id"));
                        user.setName(rs.getString("name"));
                        user.setPhone(rs.getString("phone"));
                        user.setRole(rs.getString("role"));
                        user.setDistrict(rs.getString("district"));
                        user.setUpazila(rs.getString("upazila"));
                        user.setVerified(rs.getBoolean("is_verified"));
                        user.setProfilePhoto(rs.getString("profile_photo"));
                        user.setCreatedAt(rs.getString("created_at"));
                        
                        System.out.println("✅ Login successful - User: " + user.getName() + ", Role: " + user.getRole());
                        
                        if (onSuccess != null) onSuccess.accept(user);
                    } else {
                        System.out.println("Login failed - Wrong PIN for phone: " + phone);
                        if (onError != null) onError.accept("Invalid PIN. Please try again.");
                    }
                } else {
                    System.out.println("Login failed - No account found for phone: " + phone + ", Role: " + role);
                    if (onError != null) onError.accept("Account not found for this role.");
                }
                
            } catch (SQLException e) {
                System.err.println("Login error: " + e.getMessage());
                e.printStackTrace();
                if (onError != null) onError.accept("Database error: " + e.getMessage());
            }
        });
    }

    /**
     * Login user with phone and PIN only (any role).
     * 
     * @param phone User's phone number
     * @param pin User's PIN
     * @param onSuccess Callback with User object on successful login
     * @param onError Callback with error message on failure
     */
    public void login(String phone, String pin, 
                      Consumer<User> onSuccess, Consumer<String> onError) {
        if (!initialized) {
            if (onError != null) onError.accept("FirebaseService not initialized.");
            return;
        }

        dbExecutor.submit(() -> {
            String sql = "SELECT * FROM users WHERE phone = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, phone);
                
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String storedPin = rs.getString("pin");
                    
                    // Use BCrypt.checkpw for hashed password verification
                    if (BCrypt.checkpw(pin, storedPin)) {
                        // Create User object from database result
                        User user = new User();
                        user.setId(rs.getInt("id"));
                        user.setName(rs.getString("name"));
                        user.setPhone(rs.getString("phone"));
                        user.setRole(rs.getString("role"));
                        user.setDistrict(rs.getString("district"));
                        user.setUpazila(rs.getString("upazila"));
                        user.setVerified(rs.getBoolean("is_verified"));
                        user.setProfilePhoto(rs.getString("profile_photo"));
                        user.setCreatedAt(rs.getString("created_at"));
                        
                        System.out.println("✅ Login successful - User: " + user.getName() + ", Role: " + user.getRole());
                        
                        if (onSuccess != null) onSuccess.accept(user);
                    } else {
                        System.out.println("Login failed - Wrong PIN for phone: " + phone);
                        if (onError != null) onError.accept("Invalid PIN. Please try again.");
                    }
                } else {
                    System.out.println("Login failed - No account found for phone: " + phone);
                    if (onError != null) onError.accept("Account not found.");
                }
                
            } catch (SQLException e) {
                System.err.println("Login error: " + e.getMessage());
                e.printStackTrace();
                if (onError != null) onError.accept("Database error: " + e.getMessage());
            }
        });
    }

    /**
     * Register a new user.
     * 
     * @param name User's name
     * @param phone User's phone number
     * @param pin User's PIN (will be hashed)
     * @param role User's role (FARMER or BUYER)
     * @param district User's district
     * @param upazila User's upazila
     * @param onSuccess Callback with User object on successful registration
     * @param onError Callback with error message on failure
     */
    public void register(String name, String phone, String pin, String role,
                         String district, String upazila,
                         Consumer<User> onSuccess, Consumer<String> onError) {
        if (!initialized) {
            if (onError != null) onError.accept("FirebaseService not initialized.");
            return;
        }

        dbExecutor.submit(() -> {
            // Check if phone already exists
            String checkSql = "SELECT id FROM users WHERE phone = ?";
            String insertSql = "INSERT INTO users (name, phone, pin, role, district, upazila, is_verified) VALUES (?, ?, ?, ?, ?, ?, 1)";
            
            try (Connection conn = getConnection()) {
                // Check existing user
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, phone);
                    ResultSet rs = checkStmt.executeQuery();
                    if (rs.next()) {
                        if (onError != null) onError.accept("Phone number already registered.");
                        return;
                    }
                }
                
                // Insert new user
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    // Hash PIN with BCrypt before storing
                    String hashedPin = BCrypt.hashpw(pin, BCrypt.gensalt());
                    insertStmt.setString(1, name);
                    insertStmt.setString(2, phone);
                    insertStmt.setString(3, hashedPin);
                    insertStmt.setString(4, role.toLowerCase());
                    insertStmt.setString(5, district);
                    insertStmt.setString(6, upazila);
                    
                    int rowsAffected = insertStmt.executeUpdate();
                    
                    if (rowsAffected > 0) {
                        ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            User user = new User();
                            user.setId(generatedKeys.getInt(1));
                            user.setName(name);
                            user.setPhone(phone);
                            user.setRole(role.toLowerCase());
                            user.setDistrict(district);
                            user.setUpazila(upazila);
                            user.setVerified(true);
                            
                            System.out.println("✅ Registration successful - User: " + name);
                            
                            if (onSuccess != null) onSuccess.accept(user);
                        }
                    } else {
                        if (onError != null) onError.accept("Registration failed.");
                    }
                }
                
            } catch (SQLException e) {
                System.err.println("Registration error: " + e.getMessage());
                e.printStackTrace();
                if (onError != null) onError.accept("Database error: " + e.getMessage());
            }
        });
    }

    /**
     * Get user by phone number.
     * 
     * @param phone User's phone number
     * @param onSuccess Callback with User object if found
     * @param onError Callback with error message on failure
     */
    public void getUserByPhone(String phone, Consumer<User> onSuccess, Consumer<String> onError) {
        if (!initialized) {
            if (onError != null) onError.accept("FirebaseService not initialized.");
            return;
        }

        dbExecutor.submit(() -> {
            String sql = "SELECT * FROM users WHERE phone = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, phone);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setName(rs.getString("name"));
                    user.setPhone(rs.getString("phone"));
                    user.setRole(rs.getString("role"));
                    user.setDistrict(rs.getString("district"));
                    user.setUpazila(rs.getString("upazila"));
                    user.setVerified(rs.getBoolean("is_verified"));
                    user.setProfilePhoto(rs.getString("profile_photo"));
                    user.setCreatedAt(rs.getString("created_at"));
                    
                    if (onSuccess != null) onSuccess.accept(user);
                } else {
                    if (onError != null) onError.accept("User not found.");
                }
                
            } catch (SQLException e) {
                System.err.println("Get user error: " + e.getMessage());
                if (onError != null) onError.accept("Database error: " + e.getMessage());
            }
        });
    }

    /**
     * Get user by ID.
     * 
     * @param userId User's ID
     * @param onSuccess Callback with User object if found
     * @param onError Callback with error message on failure
     */
    public void getUserById(int userId, Consumer<User> onSuccess, Consumer<String> onError) {
        if (!initialized) {
            if (onError != null) onError.accept("FirebaseService not initialized.");
            return;
        }

        dbExecutor.submit(() -> {
            String sql = "SELECT * FROM users WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setName(rs.getString("name"));
                    user.setPhone(rs.getString("phone"));
                    user.setRole(rs.getString("role"));
                    user.setDistrict(rs.getString("district"));
                    user.setUpazila(rs.getString("upazila"));
                    user.setVerified(rs.getBoolean("is_verified"));
                    user.setProfilePhoto(rs.getString("profile_photo"));
                    user.setCreatedAt(rs.getString("created_at"));
                    
                    if (onSuccess != null) onSuccess.accept(user);
                } else {
                    if (onError != null) onError.accept("User not found.");
                }
                
            } catch (SQLException e) {
                System.err.println("Get user error: " + e.getMessage());
                if (onError != null) onError.accept("Database error: " + e.getMessage());
            }
        });
    }

    /**
     * Update user's PIN.
     * 
     * @param phone User's phone number
     * @param newPin New PIN
     * @param onSuccess Callback on successful update
     * @param onError Callback with error message on failure
     */
    public void updatePin(String phone, String newPin, Runnable onSuccess, Consumer<String> onError) {
        if (!initialized) {
            if (onError != null) onError.accept("FirebaseService not initialized.");
            return;
        }

        dbExecutor.submit(() -> {
            String sql = "UPDATE users SET pin = ? WHERE phone = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                // Hash new PIN with BCrypt before storing
                String hashedPin = BCrypt.hashpw(newPin, BCrypt.gensalt());
                stmt.setString(1, hashedPin);
                stmt.setString(2, phone);
                
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    System.out.println("✅ PIN updated successfully for: " + phone);
                    if (onSuccess != null) onSuccess.run();
                } else {
                    if (onError != null) onError.accept("User not found.");
                }
                
            } catch (SQLException e) {
                System.err.println("Update PIN error: " + e.getMessage());
                if (onError != null) onError.accept("Database error: " + e.getMessage());
            }
        });
    }

    /**
     * Update user profile.
     * 
     * @param userId User's ID
     * @param name New name
     * @param district New district
     * @param upazila New upazila
     * @param profilePhoto Profile photo path
     * @param onSuccess Callback on successful update
     * @param onError Callback with error message on failure
     */
    public void updateProfile(int userId, String name, String district, String upazila, 
                              String profilePhoto, Runnable onSuccess, Consumer<String> onError) {
        if (!initialized) {
            if (onError != null) onError.accept("FirebaseService not initialized.");
            return;
        }

        dbExecutor.submit(() -> {
            String sql = "UPDATE users SET name = ?, district = ?, upazila = ?, profile_photo = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, name);
                stmt.setString(2, district);
                stmt.setString(3, upazila);
                stmt.setString(4, profilePhoto);
                stmt.setInt(5, userId);
                
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    System.out.println("✅ Profile updated successfully for user ID: " + userId);
                    if (onSuccess != null) onSuccess.run();
                } else {
                    if (onError != null) onError.accept("User not found.");
                }
                
            } catch (SQLException e) {
                System.err.println("Update profile error: " + e.getMessage());
                if (onError != null) onError.accept("Database error: " + e.getMessage());
            }
        });
    }

    // ==================== DATABASE QUERY METHODS ====================

    /**
     * Execute a query asynchronously (SELECT operations)
     * 
     * @param sql SQL query string
     * @param params Query parameters
     * @param onSuccess Callback with ResultSet
     * @param onError Error handler
     */
    public void executeQueryAsync(String sql, Object[] params, 
                                  Consumer<ResultSet> onSuccess, 
                                  Consumer<Exception> onError) {
        dbExecutor.submit(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                }

                ResultSet rs = stmt.executeQuery();
                if (onSuccess != null) onSuccess.accept(rs);
                
            } catch (Exception e) {
                System.err.println("Database query error: " + e.getMessage());
                e.printStackTrace();
                if (onError != null) onError.accept(e);
            }
        });
    }

    /**
     * Execute an update asynchronously (INSERT, UPDATE, DELETE operations)
     * 
     * @param sql SQL update string
     * @param params Update parameters
     * @param onSuccess Callback with number of affected rows
     * @param onError Error handler
     */
    public void executeUpdateAsync(String sql, Object[] params, 
                                   Consumer<Integer> onSuccess, 
                                   Consumer<Exception> onError) {
        dbExecutor.submit(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                }

                int rowsAffected = stmt.executeUpdate();
                if (onSuccess != null) onSuccess.accept(rowsAffected);
                
            } catch (Exception e) {
                System.err.println("Database update error: " + e.getMessage());
                e.printStackTrace();
                if (onError != null) onError.accept(e);
            }
        });
    }

    /**
     * Shutdown Firebase/database connection.
     */
    public void shutdown() {
        if (initialized) {
            try {
                System.out.println("Closing database connection...");
                
                // Shutdown executor
                dbExecutor.shutdown();
                
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
                
                initialized = false;
                System.out.println("FirebaseService connection closed successfully.");
            } catch (Exception e) {
                System.err.println("Error closing FirebaseService: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
