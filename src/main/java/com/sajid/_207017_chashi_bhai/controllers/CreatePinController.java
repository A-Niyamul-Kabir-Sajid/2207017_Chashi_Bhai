package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.FirebaseService;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import java.util.HashMap;
import java.util.Map;

public class CreatePinController {

    @FXML
    private PasswordField newPinField;

    @FXML
    private PasswordField confirmPinField;

    @FXML
    private Label errorLabel;

    @FXML
    public void initialize() {
        newPinField.requestFocus();
    }

    @FXML
    protected void onCreatePinClick() {
        String newPin = newPinField.getText().trim();
        String confirmPin = confirmPinField.getText().trim();

        // Validate PIN fields are not empty
        if (newPin.isEmpty() || confirmPin.isEmpty()) {
            showError("Please enter PIN in both fields");
            return;
        }

        // Validate PIN format (4-6 digits)
        if (!newPin.matches("^[0-9]{4,6}$")) {
            showError("PIN must be 4-6 digits only");
            return;
        }

        // Validate PINs match
        if (!newPin.equals(confirmPin)) {
            showError("PINs do not match. Please try again.");
            confirmPinField.clear();
            confirmPinField.requestFocus();
            return;
        }

        // Get user data from session
        String name = SessionManager.getTempName();
        String phone = SessionManager.getTempPhone();
        String district = SessionManager.getTempDistrict();
        String role = SessionManager.getTempRole();

        // Show creating account message
        showSuccess("Creating your account...");
        
        // Save user to database (PIN stored as backup for fallback auth)
        // Primary auth is Firebase, SQLite is fallback if Firebase fails
        new Thread(() -> {
            int userId = DatabaseService.createUser(phone, newPin, name, role, district);
            
            Platform.runLater(() -> {
                if (userId == -2) {
                    showError("❌ Phone number already registered. Please login instead.");
                } else if (userId == -1) {
                    showError("❌ Failed to create account. Please try again.");
                } else {
                    // Success
                    System.out.println("================================");
                    System.out.println("Account Creation Successful!");
                    System.out.println("User ID: USR" + String.format("%06d", userId));
                    System.out.println("Name: " + name);
                    System.out.println("Phone: " + phone);
                    System.out.println("District: " + district);
                    System.out.println("Role: " + role);
                    System.out.println("================================");

                    // Save user data to Firestore (if not already present)
                    saveUserToFirestore(userId, phone, name, district, role);

                    showSuccess("✅ Account created successfully! Redirecting to login...");

                    // Clear fields
                    newPinField.clear();
                    confirmPinField.clear();

                    // Redirect to login after 2 seconds
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            Platform.runLater(() -> {
                                App.loadScene("login-view.fxml", "Login - Chashi Bhai");
                                SessionManager.clearTempData();
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            });
        }).start();
    }

    @FXML
    protected void onBackClick() {
        App.loadScene("welcome-view.fxml", "Welcome - Chashi Bhai");
        SessionManager.clearTempData();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setVisible(true);
    }

    private void showSuccess(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setVisible(true);
    }

    /**
     * Save user data to Firestore
     * Only saves if the document doesn't already exist
     */
    private void saveUserToFirestore(int localId, String phone, String name, String district, String role) {
        FirebaseService firebaseService = FirebaseService.getInstance();
        
        // Use local_id as the document ID (matching the Firestore structure)
        String documentId = String.valueOf(localId);
        
        // Check if user already exists in Firestore
        firebaseService.loadUser(documentId, 
            existingData -> {
                if (existingData == null) {
                    // User doesn't exist in Firestore, create it
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("local_id", localId);
                    userData.put("phone", phone);
                    userData.put("name", name);
                    userData.put("district", district);
                    userData.put("role", role);
                    userData.put("created_at", System.currentTimeMillis());
                    
                    // Try to get firebase_uid from session if available
                    // (This will be null for OTP-based signup, which is okay)
                    String firebaseUid = SessionManager.getTempFirebaseUid();
                    if (firebaseUid != null && !firebaseUid.isEmpty()) {
                        userData.put("firebase_uid", firebaseUid);
                    }
                    
                    firebaseService.saveUser(documentId, userData,
                        () -> {
                            System.out.println("✅ User saved to Firestore (users collection):");
                            System.out.println("   Document ID: " + documentId);
                            System.out.println("   local_id: " + localId);
                            System.out.println("   phone: " + phone);
                            System.out.println("   name: " + name);
                            System.out.println("   district: " + district);
                            System.out.println("   role: " + role);
                            System.out.println("   created_at: " + userData.get("created_at"));
                            if (firebaseUid != null) {
                                System.out.println("   firebase_uid: " + firebaseUid);
                            }
                        },
                        error -> System.err.println("❌ Failed to save user to Firestore: " + error.getMessage())
                    );
                } else {
                    System.out.println("ℹ️ User already exists in Firestore: " + documentId);
                }
            },
            error -> {
                // If error checking, still try to save (user might not exist, just error accessing)
                System.err.println("⚠️ Error checking Firestore user existence, attempting to save anyway...");
                Map<String, Object> userData = new HashMap<>();
                userData.put("local_id", localId);
                userData.put("phone", phone);
                userData.put("name", name);
                userData.put("district", district);
                userData.put("role", role);
                userData.put("created_at", System.currentTimeMillis());
                
                String firebaseUid = SessionManager.getTempFirebaseUid();
                if (firebaseUid != null && !firebaseUid.isEmpty()) {
                    userData.put("firebase_uid", firebaseUid);
                }
                
                firebaseService.saveUser(documentId, userData,
                    () -> System.out.println("✅ User saved to Firestore: " + documentId),
                    err -> System.err.println("❌ Failed to save user to Firestore: " + err.getMessage())
                );
            }
        );
    }
}
