package com.sajid._207017_chashi_bhai.services;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test class for FirebaseService
 * Run this to verify FirebaseService is working correctly with SQLite backend
 */
public class FirebaseServiceTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        FirebaseService Test Suite                        ║");
        System.out.println("║        Testing SQLite Backend Implementation             ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        try {
            // Test 1: Initialization
            testInitialization();
            
            // Test 2: Registration with BCrypt
            testRegistration();
            
            // Test 3: Login with role
            testLoginWithRole();
            
            // Test 4: Login without role
            testLoginWithoutRole();
            
            // Test 5: Get user by phone
            testGetUserByPhone();
            
            // Test 6: Get user by ID
            testGetUserById();
            
            // Test 7: Update PIN
            testUpdatePin();
            
            // Test 8: Login with new PIN
            testLoginWithNewPin();
            
            // Test 9: Duplicate registration
            testDuplicateRegistration();
            
            // Test 10: Invalid login
            testInvalidLogin();

            // Test 11: Database tables exist
            testDatabaseTables();
            
            // Cleanup
            testShutdown();
            
        } catch (Exception e) {
            System.err.println("❌ Test suite failed with exception: " + e.getMessage());
            e.printStackTrace();
        }

        // Summary
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║                    TEST SUMMARY                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  ✅ Passed: %-44d ║%n", testsPassed);
        System.out.printf("║  ❌ Failed: %-44d ║%n", testsFailed);
        System.out.printf("║  📊 Total:  %-44d ║%n", testsPassed + testsFailed);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        
        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void testInitialization() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 1: FirebaseService Initialization");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        try {
            FirebaseService firebase = FirebaseService.getInstance();
            firebase.initialize();
            
            if (firebase.isInitialized()) {
                System.out.println("✅ PASSED: FirebaseService initialized successfully");
                testsPassed++;
            } else {
                System.out.println("❌ FAILED: FirebaseService not initialized");
                testsFailed++;
            }
        } catch (IOException e) {
            System.out.println("❌ FAILED: " + e.getMessage());
            testsFailed++;
        }
        System.out.println();
    }

    private static void testRegistration() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 2: User Registration with BCrypt Hashing");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");
        
        // Use unique phone for test
        String testPhone = "01700000001";
        
        // First, try to delete existing test user if any
        firebase.executeUpdateAsync(
            "DELETE FROM users WHERE phone = ?",
            new Object[]{testPhone},
            rows -> {
                // Now register
                firebase.register(
                    "Test Farmer",
                    testPhone,
                    "1234",
                    "FARMER",
                    "Dhaka",
                    "Savar",
                    user -> {
                        success.set(true);
                        System.out.println("  → Registered user: " + user.getName() + " (ID: " + user.getId() + ")");
                        latch.countDown();
                    },
                    error -> {
                        errorMsg.set(error);
                        latch.countDown();
                    }
                );
            },
            e -> {
                errorMsg.set(e.getMessage());
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (success.get()) {
            System.out.println("✅ PASSED: User registered with BCrypt hashed PIN");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: " + errorMsg.get());
            testsFailed++;
        }
        System.out.println();
    }

    private static void testLoginWithRole() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 3: Login with Phone, PIN, and Role");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");
        
        firebase.login(
            "01700000001",
            "1234",
            "FARMER",
            user -> {
                success.set(true);
                System.out.println("  → Logged in as: " + user.getName() + " (" + user.getRole() + ")");
                latch.countDown();
            },
            error -> {
                errorMsg.set(error);
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (success.get()) {
            System.out.println("✅ PASSED: Login with role successful (BCrypt verification works)");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: " + errorMsg.get());
            testsFailed++;
        }
        System.out.println();
    }

    private static void testLoginWithoutRole() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 4: Login with Phone and PIN Only");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");
        
        firebase.login(
            "01700000001",
            "1234",
            user -> {
                success.set(true);
                System.out.println("  → Logged in as: " + user.getName());
                latch.countDown();
            },
            error -> {
                errorMsg.set(error);
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (success.get()) {
            System.out.println("✅ PASSED: Login without role successful");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: " + errorMsg.get());
            testsFailed++;
        }
        System.out.println();
    }

    private static void testGetUserByPhone() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 5: Get User by Phone Number");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");
        
        firebase.getUserByPhone(
            "01700000001",
            user -> {
                success.set(true);
                System.out.println("  → Found user: " + user.getName());
                System.out.println("  → District: " + user.getDistrict());
                System.out.println("  → Upazila: " + user.getUpazila());
                latch.countDown();
            },
            error -> {
                errorMsg.set(error);
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (success.get()) {
            System.out.println("✅ PASSED: Get user by phone successful");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: " + errorMsg.get());
            testsFailed++;
        }
        System.out.println();
    }

    private static void testGetUserById() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 6: Get User by ID");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");
        
        // First get the user ID
        firebase.getUserByPhone(
            "01700000001",
            user -> {
                // Now get by ID
                firebase.getUserById(
                    user.getId(),
                    foundUser -> {
                        success.set(true);
                        System.out.println("  → Found user by ID " + foundUser.getId() + ": " + foundUser.getName());
                        latch.countDown();
                    },
                    error -> {
                        errorMsg.set(error);
                        latch.countDown();
                    }
                );
            },
            error -> {
                errorMsg.set(error);
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (success.get()) {
            System.out.println("✅ PASSED: Get user by ID successful");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: " + errorMsg.get());
            testsFailed++;
        }
        System.out.println();
    }

    private static void testUpdatePin() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 7: Update PIN (BCrypt Hashing)");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");
        
        firebase.updatePin(
            "01700000001",
            "5678",
            () -> {
                success.set(true);
                System.out.println("  → PIN updated successfully");
                latch.countDown();
            },
            error -> {
                errorMsg.set(error);
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (success.get()) {
            System.out.println("✅ PASSED: PIN updated with BCrypt hashing");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: " + errorMsg.get());
            testsFailed++;
        }
        System.out.println();
    }

    private static void testLoginWithNewPin() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 8: Login with New PIN");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");
        
        firebase.login(
            "01700000001",
            "5678", // New PIN
            user -> {
                success.set(true);
                System.out.println("  → Login with new PIN successful: " + user.getName());
                latch.countDown();
            },
            error -> {
                errorMsg.set(error);
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (success.get()) {
            System.out.println("✅ PASSED: Login with new BCrypt hashed PIN works");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: " + errorMsg.get());
            testsFailed++;
        }
        System.out.println();
    }

    private static void testDuplicateRegistration() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 9: Duplicate Registration Prevention");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean gotError = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");
        
        // Try to register with same phone
        firebase.register(
            "Duplicate User",
            "01700000001", // Same phone as Test 2
            "9999",
            "BUYER",
            "Chittagong",
            "Pahartali",
            user -> {
                // Should NOT succeed
                latch.countDown();
            },
            error -> {
                gotError.set(true);
                errorMsg.set(error);
                System.out.println("  → Expected error received: " + error);
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (gotError.get() && errorMsg.get().contains("already registered")) {
            System.out.println("✅ PASSED: Duplicate registration prevented");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: Duplicate registration was allowed");
            testsFailed++;
        }
        System.out.println();
    }

    private static void testInvalidLogin() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 10: Invalid Login Rejection");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean gotError = new AtomicBoolean(false);
        
        firebase.login(
            "01700000001",
            "wrongpin",
            user -> {
                // Should NOT succeed
                latch.countDown();
            },
            error -> {
                gotError.set(true);
                System.out.println("  → Expected error received: " + error);
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (gotError.get()) {
            System.out.println("✅ PASSED: Invalid PIN rejected");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: Invalid PIN was accepted");
            testsFailed++;
        }
        System.out.println();
    }

    private static void testDatabaseTables() throws InterruptedException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 11: Database Tables Existence");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        
        String[] expectedTables = {"users", "crops", "crop_photos", "farm_photos", 
                                   "orders", "ratings", "messages", "market_prices", "transactions"};
        
        firebase.executeQueryAsync(
            "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
            null,
            rs -> {
                try {
                    java.util.Set<String> foundTables = new java.util.HashSet<>();
                    while (rs.next()) {
                        foundTables.add(rs.getString("name"));
                    }
                    
                    System.out.println("  → Found tables: " + foundTables);
                    
                    boolean allFound = true;
                    for (String table : expectedTables) {
                        if (!foundTables.contains(table)) {
                            System.out.println("  ⚠ Missing table: " + table);
                            allFound = false;
                        }
                    }
                    
                    success.set(allFound);
                } catch (Exception e) {
                    System.out.println("  → Error: " + e.getMessage());
                }
                latch.countDown();
            },
            e -> {
                System.out.println("  → Error: " + e.getMessage());
                latch.countDown();
            }
        );
        
        latch.await(5, TimeUnit.SECONDS);
        
        if (success.get()) {
            System.out.println("✅ PASSED: All database tables exist");
            testsPassed++;
        } else {
            System.out.println("❌ FAILED: Some tables are missing");
            testsFailed++;
        }
        System.out.println();
    }

    private static void testShutdown() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("CLEANUP: Shutdown FirebaseService");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        FirebaseService firebase = FirebaseService.getInstance();
        firebase.shutdown();
        
        if (!firebase.isInitialized()) {
            System.out.println("✅ FirebaseService shutdown successfully");
        } else {
            System.out.println("⚠ FirebaseService may not have shutdown properly");
        }
        System.out.println();
    }
}