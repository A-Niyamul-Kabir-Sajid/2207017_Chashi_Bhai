# Hardcoded Test Data Fix Summary
**Date:** January 13, 2026  
**Issue:** User reported seeing pre-existing data (12 orders, ৳25,000 spent) after signing in when initial data should be 0 or null

## Root Cause
The FXML UI files contained **hardcoded placeholder values** that were intended for design preview but were showing in the actual application instead of being replaced by database values.

## Investigation Results

### Database Status: ✅ CLEAN
```sql
-- Database query results:
Users with statistics:
1|test|01711111111|farmer|0|0.0|0|0.0
2|Razzak|01722222222|buyer|0|0.0|0|0.0

Total orders: 0
Total crops: 0
Total messages: 0
```
**Conclusion:** Database has NO test data - all statistics are correctly 0.

### Issue Source: FXML Files
The problem was **hardcoded placeholder text** in FXML files:

## Files Fixed

### 1. buyer-profile-view.fxml
**Before:**
```xml
<Label fx:id="lblTotalOrders" text="12" styleClass="stat-value"/>
<Label fx:id="lblTotalSpent" text="৳ 25,000" styleClass="stat-value"/>
<Label fx:id="lblMemberSince" text="2024" styleClass="stat-value"/>
```

**After:**
```xml
<Label fx:id="lblTotalOrders" text="0" styleClass="stat-value"/>
<Label fx:id="lblTotalSpent" text="৳0.00" styleClass="stat-value"/>
<Label fx:id="lblMemberSince" text="--" styleClass="stat-value"/>
```

### 2. farmer-profile-view.fxml
**Before:**
```xml
<Label fx:id="lblYearsFarming" text="5" styleClass="stat-value"/>
<Label fx:id="lblTotalSales" text="৳ 50,000" styleClass="stat-value"/>
<Label fx:id="lblRating" text="4.8" styleClass="stat-value"/>
```

**After:**
```xml
<Label fx:id="lblYearsFarming" text="0" styleClass="stat-value"/>
<Label fx:id="lblTotalSales" text="৳0.00" styleClass="stat-value"/>
<Label fx:id="lblRating" text="0.0" styleClass="stat-value"/>
```

### 3. buyer-history-view.fxml
**Before:**
```xml
<Label fx:id="lblTotalAcceptedOrders" text="32" styleClass="summary-value"/>
<Label fx:id="lblTotalExpense" text="৳ 50,000" styleClass="summary-value"/>
```

**After:**
```xml
<Label fx:id="lblTotalAcceptedOrders" text="0" styleClass="summary-value"/>
<Label fx:id="lblTotalExpense" text="৳0.00" styleClass="summary-value"/>
```

### 4. farmer-history-view.fxml
**Before:**
```xml
<Label fx:id="lblTotalAcceptedOrders" text="45" styleClass="summary-value"/>
<Label fx:id="lblTotalIncome" text="৳ 1,50,000" styleClass="summary-value"/>
```

**After:**
```xml
<Label fx:id="lblTotalAcceptedOrders" text="0" styleClass="summary-value"/>
<Label fx:id="lblTotalIncome" text="৳0.00" styleClass="summary-value"/>
```

### 5. crop-detail-view.fxml
**Before:**
```xml
<Label fx:id="lblCropPrice" text="৳ 45" styleClass="detail-price"/>
<Label fx:id="lblFarmerSales" text="120" styleClass="farmer-stat-value"/>
```

**After:**
```xml
<Label fx:id="lblCropPrice" text="৳0.00" styleClass="detail-price"/>
<Label fx:id="lblFarmerSales" text="0" styleClass="farmer-stat-value"/>
```

### 6. order-detail-view.fxml
**Before:**
```xml
<Label fx:id="lblTotalAmount" text="৳ 5,000.00" styleClass="detail-value"/>
<Label fx:id="lblPricePerKg" text="৳ 45.00" styleClass="detail-value"/>
<Label fx:id="lblBuyerId" text="2002" styleClass="detail-value"/>
```

**After:**
```xml
<Label fx:id="lblTotalAmount" text="৳0.00" styleClass="detail-value"/>
<Label fx:id="lblPricePerKg" text="৳0.00" styleClass="detail-value"/>
<Label fx:id="lblBuyerId" text="0" styleClass="detail-value"/>
```

### 7. public-buyer-profile-view.fxml
**Before:**
```xml
<Label fx:id="lblMemberSince" text="2024" styleClass="stat-value"/>
```

**After:**
```xml
<Label fx:id="lblMemberSince" text="--" styleClass="stat-value"/>
```

### 8. public-farmer-profile-view.fxml
**Before:**
```xml
<Label fx:id="lblTotalProducts" text="12" styleClass="stat-value"/>
<Label fx:id="lblTotalSales" text="45" styleClass="stat-value"/>
```

**After:**
```xml
<Label fx:id="lblTotalProducts" text="0" styleClass="stat-value"/>
<Label fx:id="lblTotalSales" text="0" styleClass="stat-value"/>
```

## Additional Fix: BuyerProfileController.java

### Field Name Mismatch
**Issue:** Controller was looking for `lblTotalPurchases` but FXML had `lblTotalOrders`

**Fix Applied:**
```java
// Changed field declaration
@FXML private Label lblTotalOrders;  // was: lblTotalPurchases

// Updated usage
lblTotalOrders.setText(String.valueOf(totalPurchases));

// Added null checks for optional fields
if (lblDistrict != null) lblDistrict.setText(district != null ? district : "N/A");
if (lblUpazila != null) lblUpazila.setText(upazila != null ? upazila : "N/A");
```

## Testing Results

### Build Status: ✅ SUCCESS
```
[INFO] BUILD SUCCESS
[INFO] Compiling 52 source files
```

### Runtime Status: ✅ NO ERRORS
```
✅ Firebase REST API ready
✅ Database initialized successfully
✅ Auto-login successful - User: Razzak, Role: buyer
[CropFeed] Loaded 0 crops
[DataSync] Started polling for: profile_2 every 30s
```

**No NullPointerException errors**
**Application running smoothly**

## Impact on User Experience

### Before Fix:
- New users saw misleading statistics (12 orders, ৳25,000 spent)
- Created confusion about data integrity
- Made system appear to have pre-existing test data

### After Fix:
- New users see correct initial values (0 orders, ৳0.00 spent)
- Statistics accurately reflect actual database values
- Clean slate for all new accounts

## Files Not Modified (Intentional)

### buyer-dashboard-view.fxml & my-crops-view.fxml
These files contain **intentional placeholder prices** for UI examples:
```xml
<!-- Market price ticker - informational only -->
<Label text="৳ 40-50/কেজি" styleClass="ticker-price"/>
<Label text="৳ 45 / কেজি" styleClass="featured-price"/>
```
**Reason:** These are static UI elements showing price ranges, not dynamic user statistics.

## Prevention Measures

### Best Practice for Future FXML Development:
1. ✅ Use `text=""` or `text="0"` for dynamic labels
2. ✅ Use `text="--"` for date/string placeholders
3. ✅ Never use realistic-looking test values (e.g., "12", "25000")
4. ✅ Add comments in FXML: `<!-- Populated by controller -->`

### Example Template:
```xml
<!-- Statistics - Values populated from database -->
<Label fx:id="lblTotalOrders" text="0" styleClass="stat-value"/>
<Label fx:id="lblTotalSpent" text="৳0.00" styleClass="stat-value"/>
<Label fx:id="lblMemberSince" text="--" styleClass="stat-value"/>
```

## Verification Steps Completed

1. ✅ Checked database - confirmed all statistics are 0
2. ✅ Fixed 8 FXML files with hardcoded values
3. ✅ Fixed controller field name mismatches
4. ✅ Added null-safety checks
5. ✅ Compiled successfully
6. ✅ Tested application - no errors
7. ✅ Created clean_test_data.sql script for future use

## Related Files

### Utility Script Created:
- **clean_test_data.sql** - SQL script to reset all user statistics to 0 (for future use if needed)

## Conclusion

**Issue Status:** ✅ RESOLVED

The "pre-existing data" was **not actual database data** but **hardcoded FXML placeholder values** used during UI design. All FXML files have been corrected to show proper default values (0 or --) that will be replaced by actual database values when the controllers load.

**Database is clean** - No test data exists.
**UI now correctly displays** - All statistics start at 0 for new users.
**Controllers working properly** - All field names match FXML fx:id attributes.
