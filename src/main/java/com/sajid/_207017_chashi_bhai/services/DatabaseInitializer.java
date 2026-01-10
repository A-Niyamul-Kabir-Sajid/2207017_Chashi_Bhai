package com.sajid._207017_chashi_bhai.services;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * DatabaseInitializer - Initializes the SQLite database with proper schema
 * Runs the database_schema.sql file to create all tables, triggers, and views
 */
public class DatabaseInitializer {

    private static final String DB_URL = "jdbc:sqlite:data/chashi_bhai.db";
    private static final String SCHEMA_FILE = "database_schema.sql";

    /**
     * Initialize the database with the complete schema
     * This should be called once at app startup
     */
    public static void initialize() {
        try {
            // Ensure data directory exists
            Files.createDirectories(Paths.get("data"));
            
            System.out.println("Initializing database...");
            
            // Read and execute the schema file
            String schemaSQL = readSchemaFile();
            
            if (schemaSQL == null || schemaSQL.trim().isEmpty()) {
                System.out.println("Schema file is empty or not found! Using basic schema...");
                // Fall back to basic initialization
                initializeBasicSchema();
                System.out.println("Database initialized successfully!");
                return;
            }
            
            System.out.println("Using schema from SQL file...");
            
            // Execute the schema
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {
                
                // Split by semicolon and execute each statement
                String[] statements = schemaSQL.split(";");
                int executedCount = 0;
                
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty() && 
                        !trimmed.startsWith("--") && 
                        !trimmed.startsWith("/*")) {
                        try {
                            stmt.execute(trimmed);
                            executedCount++;
                        } catch (Exception e) {
                            // Some statements might fail if they already exist, that's OK
                            if (!e.getMessage().contains("already exists")) {
                                System.err.println("Error executing SQL: " + trimmed.substring(0, Math.min(50, trimmed.length())));
                                System.err.println("Error: " + e.getMessage());
                            }
                        }
                    }
                }
                
                System.out.println("Database initialized successfully!");
                System.out.println("Executed " + executedCount + " SQL statements.");
                
                // Insert sample data if needed
                insertSampleData(conn);
                
            }
            
        } catch (Exception e) {
            System.err.println("Database initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Read the schema file from resources or file system
     * Note: Currently disabled to use basic schema from DatabaseService
     */
    private static String readSchemaFile() {
        // Return null to let DatabaseService handle schema initialization
        // This can be re-enabled if a valid database_schema.sql file is needed
        return null;
    }

    /**
     * Fall back to basic schema if SQL file is not available
     */
    private static void initializeBasicSchema() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            System.out.println("Using basic schema initialization...");
            
            // Users table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "phone TEXT UNIQUE NOT NULL, " +
                "pin TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "role TEXT NOT NULL CHECK(role IN ('farmer', 'buyer')), " +
                "district TEXT, " +
                "upazila TEXT, " +
                "village TEXT, " +
                "nid TEXT, " +
                "is_verified BOOLEAN DEFAULT 0, " +
                "profile_photo TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // Crops table
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
                "photo_order INTEGER DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (crop_id) REFERENCES crops(id) ON DELETE CASCADE)"
            );

            // Farm photos table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS farm_photos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "farmer_id INTEGER NOT NULL, " +
                "photo_path TEXT NOT NULL, " +
                "photo_order INTEGER DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (farmer_id) REFERENCES users(id) ON DELETE CASCADE)"
            );

            // Orders table
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

            // Reviews table
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

            System.out.println("Basic schema created successfully");
            
        } catch (Exception e) {
            System.err.println("Error creating basic schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Insert sample data for testing
     */
    private static void insertSampleData(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            
            // Check if data already exists
            var rs = stmt.executeQuery("SELECT COUNT(*) as count FROM users");
            if (rs.next() && rs.getInt("count") > 0) {
                System.out.println("Sample data already exists, skipping insertion.");
                return;
            }
            
            System.out.println("Inserting sample data...");
            
            // Insert sample users (password: 1234)
            stmt.execute(
                "INSERT INTO users (phone, pin, name, role, district, is_verified) VALUES " +
                "('01711111111', '1234', 'রহিম মিয়া', 'farmer', 'ঢাকা', 1), " +
                "('01722222222', '1234', 'করিম সাহেব', 'buyer', 'চট্টগ্রাম', 1), " +
                "('01733333333', '1234', 'সালমা বেগম', 'farmer', 'রাজশাহী', 0), " +
                "('01744444444', '1234', 'জামাল উদ্দিন', 'buyer', 'খুলনা', 1)"
            );
            
            // Insert sample crops with product codes
            stmt.execute(
                "INSERT INTO crops (product_code, farmer_id, name, category, initial_quantity_kg, available_quantity_kg, price_per_kg, description, district, status) VALUES " +
                "('CRP-20260110-0001', 1, 'ধান (Rice)', 'শস্য', 100.0, 100.0, 45.0, 'উচ্চ মানের ধান', 'ঢাকা', 'active'), " +
                "('CRP-20260110-0002', 1, 'আলু (Potato)', 'সবজি', 50.0, 50.0, 30.0, 'তাজা আলু', 'ঢাকা', 'active'), " +
                "('CRP-20260110-0003', 3, 'টমেটো (Tomato)', 'সবজি', 30.0, 30.0, 60.0, 'টাটকা টমেটো', 'রাজশাহী', 'active')"
            );
            
            System.out.println("Sample data inserted successfully");
            
        } catch (Exception e) {
            System.err.println("Error inserting sample data: " + e.getMessage());
        }
    }

    /**
     * Test database connection
     */
    public static boolean testConnection() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            System.out.println("Database connection test: SUCCESS");
            return true;
        } catch (Exception e) {
            System.err.println("Database connection test: FAILED");
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }
}
