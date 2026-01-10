# Farmer Statistics Initialization and Calculation

## Default Values (Initialization)

When a new farmer account is created, the database automatically initializes farmer statistics with default values:

```sql
-- From database_schema.sql
total_accepted_orders INTEGER DEFAULT 0      -- Initialized to 0
most_sold_crop TEXT                           -- Initialized to NULL
total_income REAL DEFAULT 0.0                 -- Initialized to 0.0
rating REAL DEFAULT 0.0                       -- Initialized to 0.0
```

## Where Farmer Statistics Are Calculated

### 1. Order Status Changes (Automatic)

**Location:** [FarmerOrdersController.java](src/main/java/com/sajid/_207017_chashi_bhai/controllers/FarmerOrdersController.java)
- **Trigger:** When farmer changes order status to "delivered" or "completed"
- **What updates:** 
  - `totalAcceptedOrders` - Count of delivered/completed orders
  - `totalIncome` - Sum of all completed order amounts
  - `mostSoldCrop` - Crop with most completed orders
- **Code location:** `updateOrderStatus()` method, lines ~375-420

**Location:** [BuyerOrdersController.java](src/main/java/com/sajid/_207017_chashi_bhai/controllers/BuyerOrdersController.java)
- **Trigger:** When buyer confirms delivery (status → "delivered" or "completed")
- **What updates:** Same as above (farmer statistics)
- **Code location:** `updateOrderStatus()` method, lines ~437-480

### 2. Rating Submissions (Automatic)

**Location:** [RateOrderDialogController.java](src/main/java/com/sajid/_207017_chashi_bhai/controllers/RateOrderDialogController.java)
- **Trigger:** When buyer submits rating for farmer
- **What updates:** 
  - `rating` - Recalculates average rating from all reviews
- **Code location:** `insertReview()` method, lines ~130-160

### 3. Manual Recalculation (One-time)

**Location:** [RecalculateAllStatistics.java](src/main/java/com/sajid/_207017_chashi_bhai/utils/RecalculateAllStatistics.java)
- **Trigger:** Run manually to populate statistics for existing users
- **What updates:** All farmer statistics for all farmers in database
- **Usage:**
  ```bash
  java -cp "target/classes;path/to/sqlite-jdbc.jar" com.sajid._207017_chashi_bhai.utils.RecalculateAllStatistics
  ```

## Statistics Calculation Logic

### Core Calculation Engine

**Location:** [StatisticsCalculator.java](src/main/java/com/sajid/_207017_chashi_bhai/utils/StatisticsCalculator.java)

#### Method: `updateFarmerStatistics(int farmerId)`

**1. Total Accepted Orders & Total Income** (Combined Query)
```sql
SELECT 
  COUNT(*) as total_orders, 
  COALESCE(SUM(o.total_amount), 0.0) as total_income 
FROM orders o 
JOIN crops c ON o.crop_id = c.id 
WHERE c.farmer_id = ? AND o.status IN ('delivered', 'completed')
```
- Returns `0` if no completed orders
- Returns `0.0` for income if no completed orders

**2. Most Sold Crop**
```sql
SELECT c.name 
FROM orders o JOIN crops c ON o.crop_id = c.id 
WHERE c.farmer_id = ? AND o.status IN ('delivered', 'completed') 
GROUP BY c.name ORDER BY COUNT(*) DESC LIMIT 1
```
- Returns `NULL` if no completed orders
- Returns crop name with most completed orders

**3. Average Rating**
```sql
SELECT COALESCE(AVG(rating), 0.0) as avg_rating 
FROM reviews 
WHERE reviewee_id = ?
```
- Returns `0.0` if no reviews
- Calculates average from all reviews for the farmer

## Flow Diagrams

### Order Completion Flow
```
Farmer/Buyer clicks "Mark as Delivered"
           ↓
Order status updated to "delivered" in database
           ↓
StatisticsCalculator.updateFarmerStatistics(farmerId) called
           ↓
Three async queries execute:
  1. Count orders + Sum income → Update total_accepted_orders, total_income
  2. Find most sold crop → Update most_sold_crop (or NULL)
  3. Calculate avg rating → Update rating
           ↓
Farmer's profile/history views now show updated statistics
```

### Rating Submission Flow
```
Buyer submits rating via RateOrderDialogController
           ↓
Rating inserted into reviews table
           ↓
StatisticsCalculator.updateFarmerStatistics(farmerId) called
           ↓
Rating query executes:
  - AVG(rating) from reviews → Update rating
           ↓
Farmer's rating updated on profile
```

## Where Statistics Are Displayed

### Farmer's Own Views
1. **FarmerProfileController** - Shows `totalIncome` and `rating`
2. **FarmerHistoryController** - Shows `totalIncome`, `mostSoldCrop`, `totalAcceptedOrders`

### Public Views (Buyers See)
1. **PublicFarmerProfileController** - Shows `totalAcceptedOrders` and `rating`
2. **CropDetailController** - Shows farmer info including ratings

## Key Features

### ✅ Proper Initialization
- All numeric fields default to `0` or `0.0`
- Text fields (mostSoldCrop) default to `NULL`
- Database handles defaults automatically on INSERT

### ✅ Automatic Updates
- Statistics recalculate on every order completion
- Ratings update immediately after review submission
- No manual intervention required

### ✅ Performance Optimized
- Combined queries reduce database calls
- Statistics cached in users table (not calculated on every view)
- Async operations don't block UI

### ✅ NULL Handling
- `COALESCE()` ensures numeric fields never return NULL
- `mostSoldCrop` properly handles NULL when no orders exist
- Display logic handles NULL values gracefully ("N/A" shown)

## Testing Checklist

- [ ] New farmer account created → all statistics are 0/NULL
- [ ] Farmer completes first order → statistics update correctly
- [ ] Multiple orders completed → mostSoldCrop shows correct crop
- [ ] Buyer rates farmer → rating updates and displays correctly
- [ ] No orders yet → profile shows 0 orders, ৳0.00 income, NULL crop
- [ ] Profile views display statistics without errors
- [ ] Statistics persist after app restart

## Common Issues & Solutions

**Issue:** Statistics show 0 after completing orders
- **Solution:** Check order status is "delivered" or "completed" (not "pending")

**Issue:** mostSoldCrop shows NULL with orders
- **Solution:** Verify orders have status IN ('delivered', 'completed')

**Issue:** Rating doesn't update
- **Solution:** Check reviews table has entries with correct reviewee_id

**Issue:** Statistics not initialized on signup
- **Solution:** Database DEFAULT values should handle this automatically
