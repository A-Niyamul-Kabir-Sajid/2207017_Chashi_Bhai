-- Add farmer statistics columns to users table
-- Run this with: sqlite3 chashi_bhai.db < add_farmer_stats_columns.sql

-- Add total_accepted_orders column
ALTER TABLE users ADD COLUMN total_accepted_orders INTEGER DEFAULT 0;

-- Add most_sold_crop column
ALTER TABLE users ADD COLUMN most_sold_crop TEXT;

-- Add total_income column
ALTER TABLE users ADD COLUMN total_income REAL DEFAULT 0.0;

-- Add rating column
ALTER TABLE users ADD COLUMN rating REAL DEFAULT 0.0;

-- Display success message
SELECT 'Farmer statistics columns added successfully!' as result;
