package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

public class ResetPinController {

    @FXML
    private Label phoneLabel;

    @FXML
    private PasswordField newPinField;

    @FXML
    private PasswordField confirmPinField;

    @FXML
    private Label errorLabel;

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
        newPinField.requestFocus();
    }

    @FXML
    protected void onResetPinClick() {
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

        // Update PIN in database for user with phone number
        String phone = SessionManager.getTempPhone();
        String tempRole = SessionManager.getTempRole();
        
        // Extract role from RESET_PIN_FARMER or RESET_PIN_BUYER
        String role = "buyer"; // default
        if (tempRole != null && tempRole.startsWith("RESET_PIN_")) {
            role = tempRole.replace("RESET_PIN_", "").toLowerCase();
        }
        
        System.out.println("================================");
        System.out.println("Resetting PIN for:");
        System.out.println("Phone: " + phone);
        System.out.println("Role: " + role);
        System.out.println("================================");
        
        // Update PIN in database
        String updateSql = "UPDATE users SET pin = ? WHERE phone = ? AND role = ?";
        Object[] params = {newPin, phone, role};
        
        com.sajid._207017_chashi_bhai.services.DatabaseService.executeUpdateAsync(updateSql, params,
            rowsAffected -> {
                if (rowsAffected > 0) {
                    System.out.println("✅ PIN updated successfully in database");
                    
                    javafx.application.Platform.runLater(() -> {
                        showSuccess("✅ PIN reset successfully! Redirecting to login...");
                        
                        // Clear fields
                        newPinField.clear();
                        confirmPinField.clear();
                        
                        // Redirect to login after 2 seconds
                        new Thread(() -> {
                            try {
                                Thread.sleep(2000);
                                javafx.application.Platform.runLater(() -> {
                                    App.loadScene("login-view.fxml", "Login - Chashi Bhai");
                                    SessionManager.clearTempData();
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    });
                } else {
                    javafx.application.Platform.runLater(() -> {
                        showError("❌ User not found. Please check phone number and role.");
                    });
                }
            },
            error -> {
                javafx.application.Platform.runLater(() -> {
                    showError("❌ Failed to update PIN. Please try again.");
                    error.printStackTrace();
                });
            }
        );
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
