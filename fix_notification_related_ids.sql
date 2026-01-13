-- Migration to fix existing notifications without related_id
-- Option 1: Delete all existing notifications (they'll be recreated on next order action)
DELETE FROM notifications WHERE related_id IS NULL;

-- Option 2: Or if you want to try to match them to orders (uncomment the lines below)
-- This attempts to set related_id for notifications about new orders
-- UPDATE notifications 
-- SET related_id = (
--     SELECT o.id 
--     FROM orders o 
--     WHERE o.farmer_id = notifications.user_id 
--     AND o.status = 'new' 
--     ORDER BY o.created_at DESC 
--     LIMIT 1
-- )
-- WHERE notifications.type = 'order' AND notifications.related_id IS NULL;

-- This attempts to set related_id for notifications about accepted/rejected orders
-- UPDATE notifications 
-- SET related_id = (
--     SELECT o.id 
--     FROM orders o 
--     WHERE o.buyer_id = notifications.user_id 
--     AND o.status IN ('accepted', 'rejected', 'in_transit', 'completed')
--     ORDER BY o.updated_at DESC 
--     LIMIT 1
-- )
-- WHERE notifications.type IN ('success', 'error', 'warning') AND notifications.related_id IS NULL;
