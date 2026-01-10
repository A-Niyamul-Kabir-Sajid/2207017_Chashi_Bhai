package com.sajid._207017_chashi_bhai.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Database Migration Utility
 * Adds farmer statistics columns to the users table
 */
public class AddFarmerStatsColumns {
    
    public static void main(String[] args) {
        String dbPath = "chashi_bhai.db";
        String url = "jdbc:sqlite:" + dbPath;
        
        System.out.println("🔧 Starting database migration...");
        System.out.println("📁 Database: " + dbPath);
        
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            
            // Add total_accepted_orders column
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN total_accepted_orders INTEGER DEFAULT 0");
                System.out.println("✅ Added total_accepted_orders column");
            } catch (Exception e) {
                if (e.getMessage().contains("duplicate column name")) {
                    System.out.println("ℹ️  Column total_accepted_orders already exists");
                } else {
                    throw e;
                }
            }
            
            // Add most_sold_crop column
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN most_sold_crop TEXT");
                System.out.println("✅ Added most_sold_crop column");
            } catch (Exception e) {
                if (e.getMessage().contains("duplicate column name")) {
                    System.out.println("ℹ️  Column most_sold_crop already exists");
                } else {
                    throw e;
                }
            }
            
            // Add total_income column
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN total_income REAL DEFAULT 0.0");
                System.out.println("✅ Added total_income column");
            } catch (Exception e) {
                if (e.getMessage().contains("duplicate column name")) {
                    System.out.println("ℹ️  Column total_income already exists");
                } else {
                    throw e;
                }
            }
            
            // Add rating column
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN rating REAL DEFAULT 0.0");
                System.out.println("✅ Added rating column");
            } catch (Exception e) {
                if (e.getMessage().contains("duplicate column name")) {
                    System.out.println("ℹ️  Column rating already exists");
                } else {
                    throw e;
                }
            }
            
            System.out.println("\n🎉 Database migration completed successfully!");
            System.out.println("📝 Columns added:");
            System.out.println("   - total_accepted_orders (INTEGER, default 0)");
            System.out.println("   - most_sold_crop (TEXT)");
            System.out.println("   - total_income (REAL, default 0.0)");
            System.out.println("   - rating (REAL, default 0.0)");
            
        } catch (Exception e) {
            System.err.println("❌ Migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
