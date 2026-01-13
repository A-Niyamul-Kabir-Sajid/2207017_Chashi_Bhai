package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.FirebaseAuthService;
import com.sajid._207017_chashi_bhai.services.FirebaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.HashMap;
import java.util.Map;

public class SignupController {

    @FXML
    private TextField nameField;

    @FXML
    private TextField phoneField;

    @FXML
    private ComboBox<String> districtCombo;

    @FXML
    private Button farmerButton;

    @FXML
    private Button buyerButton;

    @FXML
    private PasswordField pinField;

    @FXML
    private PasswordField confirmPinField;

    @FXML
    private Label errorLabel;

    private String selectedRole = "";
    private FirebaseAuthService firebaseAuth;
    
    // 64 districts of Bangladesh sorted alphabetically
    private static final String[] DISTRICTS = {
        "Bagerhat", "Bandarban", "Barguna", "Barisal", "Bhola", "Bogra",
        "Brahmanbaria", "Chandpur", "Chapainawabganj", "Chittagong", "Chuadanga",
        "Comilla", "Cox's Bazar", "Dhaka", "Dinajpur", "Faridpur", "Feni",
        "Gaibandha", "Gazipur", "Gopalganj", "Habiganj", "Jamalpur", "Jessore",
        "Jhalokati", "Jhenaidah", "Joypurhat", "Khagrachhari", "Khulna", "Kishoreganj",
        "Kurigram", "Kushtia", "Lakshmipur", "Lalmonirhat", "Madaripur", "Magura",
        "Manikganj", "Meherpur", "Moulvibazar", "Munshiganj", "Mymensingh", "Naogaon",
        "Narail", "Narayanganj", "Narsingdi", "Natore", "Netrokona", "Nilphamari",
        "Noakhali", "Pabna", "Panchagarh", "Patuakhali", "Pirojpur", "Rajbari",
        "Rajshahi", "Rangamati", "Rangpur", "Satkhira", "Shariatpur", "Sherpur",
        "Sirajganj", "Sunamganj", "Sylhet", "Tangail", "Thakurgaon"
    };

    @FXML
    public void initialize() {
        // Populate districts
        districtCombo.getItems().addAll(DISTRICTS);
        
        // Initialize Firebase Auth
        firebaseAuth = new FirebaseAuthService();
    }

    @FXML
    protected void onBackClick() {
        App.loadScene("welcome-view.fxml", "Welcome - Chashi Bhai");
    }

    @FXML
    protected void onFarmerSelect() {
        selectedRole = "FARMER";
        farmerButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
        buyerButton.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 5;");
    }

    @FXML
    protected void onBuyerSelect() {
        selectedRole = "BUYER";
        buyerButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
        farmerButton.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 5;");
    }

    @FXML
    protected void onContinueClick() {
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String district = districtCombo.getValue();
        String pin = pinField.getText().trim();
        String confirmPin = confirmPinField.getText().trim();

        // Validate inputs
        if (name.isEmpty()) {
            showError("Please enter your name");
            return;
        }

        if (phone.isEmpty()) {
            showError("Please enter your phone number");
            return;
        }

        if (!phone.matches("^01[0-9]{9}$")) {
            showError("Invalid phone number. Use format: 01XXXXXXXXX");
            return;
        }

        if (district == null || district.isEmpty()) {
            showError("Please select your district");
            return;
        }

        if (selectedRole.isEmpty()) {
            showError("Please select your role (Farmer or Buyer)");
            return;
        }

        // Validate PIN
        if (pin.isEmpty() || confirmPin.isEmpty()) {
            showError("Please enter PIN in both fields");
            return;
        }

        if (!pin.matches("^[0-9]{4,6}$")) {
            showError("PIN must be 4-6 digits only");
            return;
        }

        if (!pin.equals(confirmPin)) {
            showError("PINs do not match. Please try again.");
            confirmPinField.clear();
            confirmPinField.requestFocus();
            return;
        }

        // Check if phone already exists and create user
        errorLabel.setText("Creating your account...");
        errorLabel.setVisible(true);
        
        // Final variables for lambda
        final String finalName = name;
        final String finalPhone = phone;
        final String finalDistrict = district;
        final String finalPin = pin;
        
        // First register with Firebase (in background), then create local user
        new Thread(() -> {
            // Try to register with Firebase first
            String firebaseUid = null;
            FirebaseAuthService.AuthResult firebaseAuthResult = null;
            try {
                firebaseAuthResult = firebaseAuth.signUp(finalPhone, finalPin, finalName);
                firebaseUid = firebaseAuthResult.getFirebaseUserId();
                System.out.println("✅ Firebase registration successful for: " + finalPhone);
                
                // Set Firebase token
                FirebaseService.getInstance().setIdToken(firebaseAuthResult.getIdToken());
            } catch (Exception e) {
                System.err.println("⚠️ Firebase registration failed: " + e.getMessage());
                System.err.println("   Continuing with local registration only...");
                System.err.println("   Note: Phone authentication may be disabled in Firebase Console.");
                System.err.println("   To enable: Firebase Console > Authentication > Sign-in method > Phone");
                // Continue with local registration even if Firebase fails
            }
            
            // Create user in local SQLite database (PIN stored as backup for fallback auth)
            // Primary auth is Firebase, SQLite is fallback if Firebase fails
            int userId = DatabaseService.createUser(finalPhone, finalPin, finalName, selectedRole.toLowerCase(), finalDistrict);
            
            final String finalFirebaseUid = firebaseUid;
            final FirebaseAuthService.AuthResult finalAuthResult = firebaseAuthResult;
            
            Platform.runLater(() -> {
                if (userId == -2) {
                    showError("❌ Phone number already registered. Please login instead.");
                } else if (userId == -1) {
                    showError("❌ Failed to create account. Please try again.");
                } else {
                    // Save session for one-time login (if Firebase auth succeeded)
                    if (finalAuthResult != null) {
                        com.sajid._207017_chashi_bhai.services.AuthSessionManager.getInstance().saveSession(
                            userId,
                            finalAuthResult.getFirebaseUserId(),
                            finalAuthResult.getIdToken(),
                            finalAuthResult.getRefreshToken(),
                            finalPhone,
                            selectedRole.toLowerCase()
                        );
                        System.out.println("✅ Session saved for one-time login");
                    }
                    
                    // Also sync user to Firebase Firestore
                    if (finalFirebaseUid != null) {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("name", finalName);
                        userData.put("phone", finalPhone);
                        userData.put("district", finalDistrict);
                        userData.put("role", selectedRole.toLowerCase());
                        userData.put("local_id", userId);
                        userData.put("firebase_uid", finalFirebaseUid);
                        userData.put("created_at", System.currentTimeMillis());
                        
                        FirebaseService.getInstance().saveUser(
                            String.valueOf(userId),
                            userData,
                            () -> System.out.println("✓ User synced to Firestore"),
                            err -> System.err.println("❌ Firestore sync failed: " + err.getMessage())
                        );
                    }
                    
                    // Success - show popup and go to welcome
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Signup Successful");
                    alert.setHeaderText("✅ Account Created Successfully!");
                    alert.setContentText(
                        "Your account has been created successfully!\n\n" +
                        "User ID: USR" + String.format("%06d", userId) + "\n" +
                        "Phone: " + finalPhone + "\n" +
                        "Role: " + selectedRole + "\n\n" +
                        "You can now login with your phone number and PIN."
                    );
                    
                    alert.showAndWait();
                    
                    // Clear form
                    nameField.clear();
                    phoneField.clear();
                    pinField.clear();
                    confirmPinField.clear();
                    districtCombo.setValue(null);
                    selectedRole = "";
                    farmerButton.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 5;");
                    buyerButton.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 5;");
                    errorLabel.setVisible(false);
                    
                    // Go back to welcome page
                    App.loadScene("welcome-view.fxml", "Welcome - Chashi Bhai");
                }
            });
        }).start();
    }

    @FXML
    protected void onLoginLinkClick() {
        App.loadScene("login-view.fxml", "Login - Chashi Bhai");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
