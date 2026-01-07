package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;

public class LoginController {

    @FXML
    private TextField phoneField;

    @FXML
    private PasswordField pinField;

    @FXML
    private Label errorLabel;

    @FXML
    private Button farmerButton;

    @FXML
    private Button buyerButton;

    private String selectedRole = null;

    @FXML
    protected void onBackClick() {
        App.loadScene("welcome-view.fxml", "Welcome - Chashi Bhai");
    }

    @FXML
    protected void onFarmerSelect() {
        selectedRole = "FARMER";
        // Highlight farmer button
        farmerButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-background-radius: 8; -fx-border-color: #4CAF50; -fx-border-width: 1.5; -fx-border-radius: 8; -fx-cursor: hand; -fx-padding: 12 20;");
        // Reset buyer button
        buyerButton.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #b0b0b0; -fx-background-radius: 8; -fx-border-color: #444444; -fx-border-width: 1.5; -fx-border-radius: 8; -fx-cursor: hand; -fx-padding: 12 20;");
        errorLabel.setVisible(false);
    }

    @FXML
    protected void onBuyerSelect() {
        selectedRole = "BUYER";
        // Highlight buyer button
        buyerButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-background-radius: 8; -fx-border-color: #2196F3; -fx-border-width: 1.5; -fx-border-radius: 8; -fx-cursor: hand; -fx-padding: 12 20;");
        // Reset farmer button
        farmerButton.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #b0b0b0; -fx-background-radius: 8; -fx-border-color: #444444; -fx-border-width: 1.5; -fx-border-radius: 8; -fx-cursor: hand; -fx-padding: 12 20;");
        errorLabel.setVisible(false);
    }

    @FXML
    protected void onLoginClick() {
        String phone = phoneField.getText().trim();
        String pin = pinField.getText().trim();

        // Validate role selection
        if (selectedRole == null) {
            showError("Please select your role (Farmer or Buyer)");
            return;
        }

        // Validate phone number
        if (phone.isEmpty()) {
            showError("Please enter your phone number");
            return;
        }

        if (!phone.matches("^01[0-9]{9}$")) {
            showError("Invalid phone number. Use format: 01XXXXXXXXX");
            return;
        }

        // Validate PIN
        if (pin.isEmpty()) {
            showError("Please enter your PIN");
            return;
        }

        if (!pin.matches("^[0-9]{4,6}$")) {
            showError("PIN must be 4-6 digits");
            return;
        }

        // Disable login button during authentication
        Button loginBtn = (Button) phoneField.getScene().lookup(".button-primary");
        if (loginBtn != null) loginBtn.setDisable(true);

        // Check credentials in database
        String sql = "SELECT * FROM users WHERE phone = ? AND role = ?";
        Object[] params = {phone, selectedRole.toLowerCase()};

        DatabaseService.executeQueryAsync(sql, params,
            rs -> {
                try {
                    if (rs.next()) {
                        String storedPin = rs.getString("pin");
                        
                        // TODO: Use BCrypt for hashing - for now comparing plain text
                        if (pin.equals(storedPin)) {
                            // Create User object
                            User user = new User();
                            user.setId(rs.getInt("id"));
                            user.setName(rs.getString("name"));
                            user.setPhone(rs.getString("phone"));
                            user.setRole(rs.getString("role"));
                            user.setDistrict(rs.getString("district"));
                            user.setUpazila(rs.getString("upazila"));
                            user.setVerified(rs.getBoolean("is_verified"));
                            user.setProfilePhoto(rs.getString("profile_photo"));
                            user.setCreatedAt(rs.getString("created_at"));
                            
                            Platform.runLater(() -> {
                                // Set current user in App
                                App.setCurrentUser(user);
                                
                                // Set session data (for backward compatibility)
                                SessionManager.setCurrentUserId(user.getId());
                                SessionManager.setCurrentUserName(user.getName());
                                SessionManager.setCurrentUserRole(user.getRole().toUpperCase());
                                SessionManager.setCurrentUserPhone(user.getPhone());
                                
                                System.out.println("✅ Login successful - User: " + user.getName() + ", Role: " + user.getRole());
                                
                                // Navigate to crop feed (marketplace) - default landing page
                                App.loadScene("crop-feed-view.fxml", "সকল ফসল / Browse Crops - Chashi Bhai");
                                
                                if (loginBtn != null) loginBtn.setDisable(false);
                            });
                        } else {
                            Platform.runLater(() -> {
                                showError("❌ Invalid PIN. Please try again.");
                                System.out.println("Login failed - Wrong PIN for phone: " + phone);
                                if (loginBtn != null) loginBtn.setDisable(false);
                            });
                        }
                    } else {
                        Platform.runLater(() -> {
                            showError("❌ Account not found for this role.\n\n" +
                                     "Please check:\n" +
                                     "• Phone number is correct (01XXXXXXXXX)\n" +
                                     "• Selected correct role (Farmer/Buyer)\n" +
                                     "• Account exists (Sign up if new user)");
                            System.out.println("Login failed - No account found for phone: " + phone + ", Role: " + selectedRole);
                            if (loginBtn != null) loginBtn.setDisable(false);
                        });
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showError("❌ Database error occurred. Please try again.\n" +
                                 "If problem persists, contact support.");
                        System.err.println("Database error during login: " + e.getMessage());
                        e.printStackTrace();
                        if (loginBtn != null) loginBtn.setDisable(false);
                    });
                }
            },
            err -> {
                Platform.runLater(() -> {
                    showError("❌ Connection error. Please check:\n" +
                             "• Database is accessible\n" +
                             "• Network connection is stable");
                    System.err.println("Login connection error: " + err.getMessage());
                    err.printStackTrace();
                    if (loginBtn != null) loginBtn.setDisable(false);
                });
            }
        );
    }

    @FXML
    protected void onForgotPinClick() {
        String phone = phoneField.getText().trim();

        // Validate role selection
        if (selectedRole == null) {
            showError("Please select your role (Farmer or Buyer) first");
            return;
        }

        if (phone.isEmpty()) {
            showError("Please enter your phone number first");
            return;
        }

        if (!phone.matches("^01[0-9]{9}$")) {
            showError("Invalid phone number. Use format: 01XXXXXXXXX");
            return;
        }

        // TODO: Check if phone exists in database for the selected role
        // For now, proceed to OTP for password reset
        
        SessionManager.setTempPhone(phone);
        SessionManager.setLoginMode(false); // This is password reset mode
        SessionManager.setTempName(""); // Clear other fields
        SessionManager.setTempDistrict("");
        SessionManager.setTempRole("RESET_PIN_" + selectedRole); // Store role for reset

        System.out.println("Forgot PIN - Phone: " + phone + ", Role: " + selectedRole);

        App.loadScene("otp-verification-view.fxml", "OTP Verification");
    }

    @FXML
    protected void onSignupLinkClick() {
        App.loadScene("signup-view.fxml", "Sign Up - Chashi Bhai");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
