package com.sajid._207017_chashi_bhai.utils;

import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;

import java.sql.ResultSet;
import java.util.function.Consumer;

/**
 * StatisticsUpdater - Updates farmer and buyer statistics in the database
 * Recalculates totalAcceptedOrders, mostSoldCrop, totalIncome, rating for farmers
 * Recalculates totalBuyerOrders, mostBoughtCrop, totalExpense for buyers
 */
public class StatisticsCalculator {

    /**
     * Update all farmer statistics for a specific farmer
     * Called after order status changes or rating is given
     * Initializes with 0 for counts/totals, NULL for most_sold_crop if no data
     */
    public static void updateFarmerStatistics(int farmerId) {
        // Calculate total accepted orders and total income in one query
        DatabaseService.executeQueryAsync(
            "SELECT " +
            "  COUNT(*) as total_orders, " +
            "  COALESCE(SUM(o.total_amount), 0.0) as total_income " +
            "FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "WHERE c.farmer_id = ? AND o.status IN ('delivered', 'completed')",
            new Object[]{farmerId},
            resultSet -> {
                try {
                    if (resultSet.next()) {
                        int totalOrders = resultSet.getInt("total_orders");
                        double totalIncome = resultSet.getDouble("total_income");
                        
                        // Update total orders and income
                        DatabaseService.executeUpdateAsync(
                            "UPDATE users SET total_accepted_orders = ?, total_income = ? WHERE id = ?",
                            new Object[]{totalOrders, totalIncome, farmerId},
                            rowsAffected -> {
                                System.out.println("Updated farmer " + farmerId + " stats: Orders=" + totalOrders + ", Income=" + totalIncome);
                            },
                            error -> error.printStackTrace()
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
        
        // Update most sold crop (NULL if no orders)
        DatabaseService.executeQueryAsync(
            "SELECT c.name " +
            "FROM orders o JOIN crops c ON o.crop_id = c.id " +
            "WHERE c.farmer_id = ? AND o.status IN ('delivered', 'completed') " +
            "GROUP BY c.name ORDER BY COUNT(*) DESC LIMIT 1",
            new Object[]{farmerId},
            resultSet -> {
                try {
                    final String mostSold = resultSet.next() ? resultSet.getString("name") : null;
                    
                    DatabaseService.executeUpdateAsync(
                        "UPDATE users SET most_sold_crop = ? WHERE id = ?",
                        new Object[]{mostSold, farmerId},
                        rowsAffected -> {
                            System.out.println("Updated farmer " + farmerId + " most sold crop: " + (mostSold != null ? mostSold : "NULL"));
                        },
                        error -> error.printStackTrace()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
        
        // Update rating (0.0 if no reviews)
        DatabaseService.executeQueryAsync(
            "SELECT COALESCE(AVG(rating), 0.0) as avg_rating FROM reviews WHERE reviewee_id = ?",
            new Object[]{farmerId},
            rs -> {
                try {
                    if (rs.next()) {
                        double avgRating = rs.getDouble("avg_rating");
                        
                        DatabaseService.executeUpdateAsync(
                            "UPDATE users SET rating = ? WHERE id = ?",
                            new Object[]{avgRating, farmerId},
                            rowsAffected -> {
                                System.out.println("Updated farmer " + farmerId + " rating: " + avgRating);
                            },
                            error -> error.printStackTrace()
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
    }
    
    /**
     * Update buyer statistics (total orders, most bought crop, total expense)
     * Initializes with 0 for counts/totals, NULL for most_bought_crop if no data
     */
    public static void updateBuyerStatistics(int buyerId) {
        // Calculate total orders and total expense in one query
        DatabaseService.executeQueryAsync(
            "SELECT " +
            "  COUNT(*) as total_orders, " +
            "  COALESCE(SUM(total_amount), 0.0) as total_expense " +
            "FROM orders " +
            "WHERE buyer_id = ? AND status IN ('delivered', 'completed')",
            new Object[]{buyerId},
            rs -> {
                try {
                    if (rs.next()) {
                        int totalOrders = rs.getInt("total_orders");
                        double totalExpense = rs.getDouble("total_expense");
                        
                        // Update total orders and expense
                        DatabaseService.executeUpdateAsync(
                            "UPDATE users SET total_buyer_orders = ?, total_expense = ? WHERE id = ?",
                            new Object[]{totalOrders, totalExpense, buyerId},
                            rowsAffected -> {
                                System.out.println("Updated buyer " + buyerId + " stats: Orders=" + totalOrders + ", Expense=" + totalExpense);
                            },
                            error -> error.printStackTrace()
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
        
        // Update most bought crop (NULL if no orders)
        DatabaseService.executeQueryAsync(
            "SELECT c.name " +
            "FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "WHERE o.buyer_id = ? AND o.status IN ('delivered', 'completed') " +
            "GROUP BY c.name ORDER BY COUNT(*) DESC LIMIT 1",
            new Object[]{buyerId},
            rs -> {
                try {
                    final String mostBought = rs.next() ? rs.getString("name") : null;
                    
                    DatabaseService.executeUpdateAsync(
                        "UPDATE users SET most_bought_crop = ? WHERE id = ?",
                        new Object[]{mostBought, buyerId},
                        rowsAffected -> {
                            System.out.println("Updated buyer " + buyerId + " most bought crop: " + (mostBought != null ? mostBought : "NULL"));
                        },
                        error -> error.printStackTrace()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
    }
}
