package com.sajid._207017_chashi_bhai.utils;

import com.sajid._207017_chashi_bhai.services.DatabaseService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * RecalculateAllStatistics - One-time utility to recalculate all user statistics
 * Run this after adding the statistics columns to populate them with correct values
 */
public class RecalculateAllStatistics {
    
    private static final String DB_URL = "jdbc:sqlite:data/chashi_bhai.db";
    
    public static void main(String[] args) {
        System.out.println("Starting statistics recalculation...");
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            // Get all farmer IDs
            ResultSet farmers = stmt.executeQuery("SELECT id FROM users WHERE role = 'farmer'");
            int farmerCount = 0;
            
            while (farmers.next()) {
                int farmerId = farmers.getInt("id");
                recalculateFarmerStats(conn, farmerId);
                farmerCount++;
            }
            
            System.out.println("Updated statistics for " + farmerCount + " farmers.");
            
            // Get all buyer IDs
            ResultSet buyers = stmt.executeQuery("SELECT id FROM users WHERE role = 'buyer'");
            int buyerCount = 0;
            
            while (buyers.next()) {
                int buyerId = buyers.getInt("id");
                recalculateBuyerStats(conn, buyerId);
                buyerCount++;
            }
            
            System.out.println("Updated statistics for " + buyerCount + " buyers.");
            System.out.println("Statistics recalculation complete!");
            
        } catch (Exception e) {
            System.err.println("Error recalculating statistics: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void recalculateFarmerStats(Connection conn, int farmerId) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            
            // Calculate total accepted orders
            ResultSet rs1 = stmt.executeQuery(
                "SELECT COUNT(*) as total FROM orders o " +
                "JOIN crops c ON o.crop_id = c.id " +
                "WHERE c.farmer_id = " + farmerId + " AND o.status IN ('delivered', 'completed')"
            );
            int totalOrders = rs1.next() ? rs1.getInt("total") : 0;
            
            // Calculate total income
            ResultSet rs2 = stmt.executeQuery(
                "SELECT COALESCE(SUM(o.total_amount), 0.0) as total_income " +
                "FROM orders o JOIN crops c ON o.crop_id = c.id " +
                "WHERE c.farmer_id = " + farmerId + " AND o.status IN ('delivered', 'completed')"
            );
            double totalIncome = rs2.next() ? rs2.getDouble("total_income") : 0.0;
            
            // Find most sold crop
            ResultSet rs3 = stmt.executeQuery(
                "SELECT c.name FROM orders o JOIN crops c ON o.crop_id = c.id " +
                "WHERE c.farmer_id = " + farmerId + " AND o.status IN ('delivered', 'completed') " +
                "GROUP BY c.name ORDER BY COUNT(*) DESC LIMIT 1"
            );
            String mostSold = rs3.next() ? rs3.getString("name") : null;
            
            // Calculate average rating
            ResultSet rs4 = stmt.executeQuery(
                "SELECT AVG(rating) as avg_rating FROM reviews WHERE reviewee_id = " + farmerId
            );
            double avgRating = rs4.next() ? rs4.getDouble("avg_rating") : 0.0;
            
            // Update farmer statistics
            stmt.executeUpdate(
                "UPDATE users SET " +
                "total_accepted_orders = " + totalOrders + ", " +
                "total_income = " + totalIncome + ", " +
                "most_sold_crop = " + (mostSold != null ? "'" + mostSold + "'" : "NULL") + ", " +
                "rating = " + avgRating + " " +
                "WHERE id = " + farmerId
            );
            
            System.out.println("Farmer " + farmerId + ": Orders=" + totalOrders + 
                             ", Income=" + totalIncome + ", Rating=" + avgRating);
        }
    }
    
    private static void recalculateBuyerStats(Connection conn, int buyerId) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            
            // Calculate total buyer orders
            ResultSet rs1 = stmt.executeQuery(
                "SELECT COUNT(*) as total_orders FROM orders " +
                "WHERE buyer_id = " + buyerId + " AND status IN ('delivered', 'completed')"
            );
            int totalOrders = rs1.next() ? rs1.getInt("total_orders") : 0;
            
            // Calculate total expense
            ResultSet rs2 = stmt.executeQuery(
                "SELECT COALESCE(SUM(total_amount), 0.0) as total_expense FROM orders " +
                "WHERE buyer_id = " + buyerId + " AND status IN ('delivered', 'completed')"
            );
            double totalExpense = rs2.next() ? rs2.getDouble("total_expense") : 0.0;
            
            // Find most bought crop
            ResultSet rs3 = stmt.executeQuery(
                "SELECT c.name FROM orders o JOIN crops c ON o.crop_id = c.id " +
                "WHERE o.buyer_id = " + buyerId + " AND o.status IN ('delivered', 'completed') " +
                "GROUP BY c.name ORDER BY COUNT(*) DESC LIMIT 1"
            );
            String mostBought = rs3.next() ? rs3.getString("name") : null;
            
            // Update buyer statistics
            stmt.executeUpdate(
                "UPDATE users SET " +
                "total_buyer_orders = " + totalOrders + ", " +
                "total_expense = " + totalExpense + ", " +
                "most_bought_crop = " + (mostBought != null ? "'" + mostBought + "'" : "NULL") + " " +
                "WHERE id = " + buyerId
            );
            
            System.out.println("Buyer " + buyerId + ": Orders=" + totalOrders + 
                             ", Expense=" + totalExpense);
        }
    }
}
