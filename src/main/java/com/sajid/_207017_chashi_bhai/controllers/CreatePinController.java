package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

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
        
        // Save user to database
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
}
