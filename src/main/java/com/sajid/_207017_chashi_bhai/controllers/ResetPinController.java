package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class ResetPinController {

    @FXML
    private Label phoneLabel;
    
    @FXML
    private TextField otpField;

    @FXML
    private PasswordField newPinField;

    @FXML
    private PasswordField confirmPinField;

    @FXML
    private Label errorLabel;

    private boolean otpVerified = false;

    @FXML
    public void initialize() {
        // Display masked phone number and role
        String phone = SessionManager.getTempPhone();
        String role = SessionManager.getTempRole();
        
        StringBuilder info = new StringBuilder();
        
        if (phone != null && phone.length() >= 11) {
            String masked = "+880 " + phone.substring(0, 4) + "-XXX-" + phone.substring(phone.length() - 3);
            info.append("Phone: ").append(masked);
        }
        
        // Extract role from RESET_PIN_FARMER or RESET_PIN_BUYER
        if (role != null && role.startsWith("RESET_PIN_")) {
            String userRole = role.replace("RESET_PIN_", "");
            String roleEmoji = userRole.equals("FARMER") ? "👨‍🌾" : "🛒";
            info.append(" | Role: ").append(roleEmoji).append(" ").append(userRole);
        }
        
        phoneLabel.setText(info.toString());
        
        // Disable PIN fields until OTP is verified
        newPinField.setDisable(true);
        confirmPinField.setDisable(true);
        
        otpField.requestFocus();
    }

    @FXML
    protected void onVerifyOTPClick() {
        final String otp = otpField.getText().trim();
        final String phone = SessionManager.getTempPhone();
        String tempRole = SessionManager.getTempRole();
        
        // Validate OTP format
        if (!otp.matches("^[0-9]{6}$")) {
            showError("OTP must be 6 digits");
            return;
        }
        
        // Extract role
        String role = "buyer";
        if (tempRole != null && tempRole.startsWith("RESET_PIN_")) {
            role = tempRole.replace("RESET_PIN_", "").toLowerCase();
        }
        final String finalRole = role;
        
        // Verify OTP from Firestore
        new Thread(() -> {
            try {
                com.sajid._207017_chashi_bhai.services.FirebaseService firebaseService = 
                    com.sajid._207017_chashi_bhai.services.FirebaseService.getInstance();
                
                boolean isValid = firebaseService.verifyPinResetOTP(phone, finalRole, otp);
                
                javafx.application.Platform.runLater(() -> {
                    if (isValid) {
                        otpVerified = true;
                        showSuccess("✅ OTP verified! Now set your new PIN.");
                        
                        // Enable PIN fields
                        newPinField.setDisable(false);
                        confirmPinField.setDisable(false);
                        
                        // Disable OTP field
                        otpField.setDisable(true);
                        
                        newPinField.requestFocus();
                    } else {
                        showError("❌ Invalid or expired OTP. Please contact admin.");
                    }
                });
                
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    showError("Failed to verify OTP: " + e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    protected void onResetPinClick() {
        if (!otpVerified) {
            showError("Please verify OTP first");
            return;
        }
        
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

        final String phone = SessionManager.getTempPhone();
        String tempRole = SessionManager.getTempRole();
        
        // Extract role from RESET_PIN_FARMER or RESET_PIN_BUYER
        String role = "buyer";
        if (tempRole != null && tempRole.startsWith("RESET_PIN_")) {
            role = tempRole.replace("RESET_PIN_", "").toLowerCase();
        }
        final String finalRole = role;
        final String finalNewPin = newPin;
        
        System.out.println("================================");
        System.out.println("Resetting PIN for:");
        System.out.println("Phone: " + phone);
        System.out.println("Role: " + finalRole);
        System.out.println("================================");
        
        // Update PIN in BOTH SQLite (backup) AND Firebase Auth (primary)
        String updateSql = "UPDATE users SET pin = ? WHERE phone = ? AND role = ?";
        Object[] params = {finalNewPin, phone, finalRole};
        
        com.sajid._207017_chashi_bhai.services.DatabaseService.executeUpdateAsync(updateSql, params,
            rowsAffected -> {
                if (rowsAffected > 0) {
                    System.out.println("✅ PIN updated in SQLite (backup)");
                } else {
                    System.out.println("⚠️ SQLite PIN update: No matching user found");
                }
            },
            error -> {
                System.err.println("⚠️ SQLite PIN update failed: " + error.getMessage());
            }
        );
        
        // Update password in Firebase Auth (primary)
        new Thread(() -> {
            try {
                com.sajid._207017_chashi_bhai.services.FirebaseAuthService authService = 
                    new com.sajid._207017_chashi_bhai.services.FirebaseAuthService();
                
                // Default display name
                String displayName = "User";
                
                // Update Firebase Auth password via account recreation
                authService.updatePasswordViaRecreate(phone, finalNewPin, displayName);
                
                // Mark OTP as used
                com.sajid._207017_chashi_bhai.services.FirebaseService firebaseService = 
                    com.sajid._207017_chashi_bhai.services.FirebaseService.getInstance();
                firebaseService.markOTPAsUsed(phone, finalRole);
                
                System.out.println("✅ PIN reset completed successfully");
                
                javafx.application.Platform.runLater(() -> {
                    showSuccess("✅ PIN reset successfully! You can now login with your new PIN.");
                    
                    // Clear fields
                    otpField.clear();
                    newPinField.clear();
                    confirmPinField.clear();
                    
                    // Redirect to login after 3 seconds
                    new Thread(() -> {
                        try {
                            Thread.sleep(3000);
                            javafx.application.Platform.runLater(() -> {
                                App.loadScene("login-view.fxml", "Login - Chashi Bhai");
                                SessionManager.clearTempData();
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                });
                
            } catch (Exception e) {
                System.err.println("⚠️ PIN reset failed: " + e.getMessage());
                e.printStackTrace();
                
                javafx.application.Platform.runLater(() -> {
                    showError("Failed to reset PIN. Please try again or contact admin.");
                });
            }
        }).start();
    }

    @FXML
    protected void onBackClick() {
        App.loadScene("login-view.fxml", "Login - Chashi Bhai");
        SessionManager.clearTempData();
    }

    @FXML
    protected void onBackToLoginClick() {
        App.loadScene("login-view.fxml", "Login - Chashi Bhai");
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
}
