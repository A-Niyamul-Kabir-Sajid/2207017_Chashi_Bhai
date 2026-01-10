# Statistics Calculation Implementation Summary

## Overview
Implemented automatic calculation and updating of farmer and buyer statistics in the Chashi Bhai application.

## Statistics Fields

### Farmer Statistics
- **totalAcceptedOrders**: Count of delivered/completed orders
- **mostSoldCrop**: Most frequently sold crop by order count
- **totalIncome**: Sum of all completed order amounts
- **rating**: Average rating from buyer reviews

### Buyer Statistics
- **totalBuyerOrders**: Count of delivered/completed purchases
- **mostBoughtCrop**: Most frequently purchased crop
- **totalExpense**: Total amount spent on completed orders

## Implementation Details

### 1. Database Schema (database_schema.sql)
Added columns to `users` table:
```sql
-- Farmer statistics
total_accepted_orders INTEGER DEFAULT 0
most_sold_crop TEXT
total_income REAL DEFAULT 0.0
rating REAL DEFAULT 0.0

-- Buyer statistics
total_buyer_orders INTEGER DEFAULT 0
most_bought_crop TEXT
total_expense REAL DEFAULT 0.0
```

### 2. User Model (User.java)
Added fields with getters/setters for all 7 statistics fields.

### 3. Database Migration (DatabaseService.java)
Added ALTER TABLE statements in `initializeDatabase()` method with duplicate column error handling. Migration runs automatically on app startup.

### 4. Statistics Calculator (StatisticsCalculator.java)
New utility class with two main methods:
- `updateFarmerStatistics(int farmerId)`: Recalculates all farmer stats
- `updateBuyerStatistics(int buyerId)`: Recalculates all buyer stats

Each method runs multiple async database queries to:
- Count completed orders
- Calculate totals (income/expense)
- Find most sold/bought crop
- Calculate average rating (farmers only)
- Update user record in database

### 5. Automatic Updates

#### Order Status Changes
**FarmerOrdersController.java & BuyerOrdersController.java**
- When order status changes to "delivered" or "completed"
- Automatically calls `StatisticsCalculator.updateFarmerStatistics()`
- Automatically calls `StatisticsCalculator.updateBuyerStatistics()`
- Updates both farmer and buyer statistics in real-time

#### Rating Submissions
**RateOrderDialogController.java**
- After buyer submits rating for farmer
- Automatically calls `StatisticsCalculator.updateFarmerStatistics()`
- Updates farmer's average rating immediately

### 6. Controllers Updated
All profile and history controllers now read statistics from database columns:
- **FarmerProfileController**: Shows totalIncome, rating
- **FarmerHistoryController**: Shows totalIncome, mostSoldCrop, totalAcceptedOrders
- **PublicFarmerProfileController**: Shows totalAcceptedOrders, rating
- **BuyerProfileController**: Shows totalBuyerOrders, totalExpense
- **BuyerHistoryController**: Shows totalExpense, mostBoughtCrop, totalBuyerOrders
- **PublicBuyerProfileController**: Shows totalBuyerOrders, totalExpense

## How Statistics Are Calculated

### Total Orders/Income/Expense
- Queries `orders` table with `status IN ('delivered', 'completed')`
- Uses `SUM()` for monetary values, `COUNT()` for order counts
- Joins with `crops` table to filter by farmer_id

### Most Sold/Bought Crop
- Queries orders grouped by crop name
- Orders by `COUNT(*)` descending
- Takes top 1 result

### Average Rating
- Queries `reviews` table filtered by `reviewee_id`
- Calculates `AVG(rating)`

## Usage

### Automatic Updates (Production)
Statistics are automatically updated when:
1. Farmer/Buyer changes order status to "delivered" or "completed"
2. Buyer submits a rating for farmer

### Manual Recalculation (One-time Setup)
Use `RecalculateAllStatistics.java` utility:
```bash
# Compile
mvnw.cmd compile

# Run (with SQLite JDBC in classpath)
java -cp "target/classes;C:/path/to/sqlite-jdbc.jar" com.sajid._207017_chashi_bhai.utils.RecalculateAllStatistics
```

This recalculates statistics for all existing users based on historical order data.

## Testing
1. Run application: `mvnw.cmd javafx:run`
2. Database migration adds columns automatically on startup
3. Login as farmer, complete an order → statistics update
4. Login as buyer, rate a farmer → farmer rating updates
5. View profile/history pages to see updated statistics

## Benefits
- Real-time statistics without manual calculation
- Accurate data synchronized with orders and reviews
- Performance: Statistics cached in user table (no expensive aggregations on every page load)
- Maintainable: Centralized calculation logic in StatisticsCalculator

## Files Modified/Created
1. User.java - Added 7 statistics fields
2. database_schema.sql - Added 7 database columns
3. DatabaseService.java - Added migration ALTER TABLE statements
4. StatisticsCalculator.java - NEW: Core calculation logic
5. RecalculateAllStatistics.java - NEW: One-time recalculation utility
6. FarmerOrdersController.java - Auto-update on status change
7. BuyerOrdersController.java - Auto-update on status change
8. RateOrderDialogController.java - Auto-update on rating submission
9. FarmerProfileController.java - Display farmer statistics
10. FarmerHistoryController.java - Display sales summary
11. PublicFarmerProfileController.java - Display farmer stats to buyers
12. BuyerProfileController.java - Display buyer statistics
13. BuyerHistoryController.java - Display purchase summary
14. PublicBuyerProfileController.java - Display buyer stats to farmers

Total: 14 files modified/created
Compilation: Successful (46 source files)
