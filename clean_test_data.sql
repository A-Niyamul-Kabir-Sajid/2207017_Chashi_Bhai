-- Clean Test Data Script
-- Run this to reset all statistics to 0 for fresh start
-- Date: January 13, 2026

-- Reset buyer statistics
UPDATE users 
SET total_buyer_orders = 0,
    most_bought_crop = NULL,
    total_expense = 0.0
WHERE role = 'buyer';

-- Reset farmer statistics  
UPDATE users
SET total_accepted_orders = 0,
    most_sold_crop = NULL,
    total_income = 0.0,
    rating = 0.0
WHERE role = 'farmer';

-- Optional: Delete all test orders (CAUTION: This will delete order history)
-- DELETE FROM orders;

-- Optional: Delete all test crops (CAUTION: This will delete crop listings)
-- DELETE FROM crops;

-- Optional: Delete all test messages (CAUTION: This will delete chat history)
-- DELETE FROM messages;
-- DELETE FROM conversations;

-- Verify the cleanup
SELECT 'Buyer Stats After Cleanup:' as info;
SELECT id, name, phone, role, total_buyer_orders, total_expense 
FROM users WHERE role = 'buyer';

SELECT 'Farmer Stats After Cleanup:' as info;
SELECT id, name, phone, role, total_accepted_orders, total_income 
FROM users WHERE role = 'farmer';

SELECT 'Total Orders Remaining:' as info;
SELECT COUNT(*) as order_count FROM orders;

SELECT 'Total Crops Remaining:' as info;
SELECT COUNT(*) as crop_count FROM crops;
