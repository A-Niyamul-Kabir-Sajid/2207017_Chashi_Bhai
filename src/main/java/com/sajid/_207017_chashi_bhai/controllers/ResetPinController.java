package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.ChashiBhaiApp;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

import java.io.IOException;

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

        // TODO: Update PIN in database for user with phone number
        String phone = SessionManager.getTempPhone();
        System.out.println("================================");
        System.out.println("PIN Reset Successful!");
        System.out.println("Phone: " + phone);
        System.out.println("New PIN: " + newPin);
        System.out.println("================================");

        // Show success message
        showSuccess("✅ PIN reset successfully! Redirecting to login...");

        // Clear fields
        newPinField.clear();
        confirmPinField.clear();

        // Redirect to login after 2 seconds
        try {
            Thread.sleep(2000);
            ChashiBhaiApp.showLoginView();
            SessionManager.clearTempData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onBackClick() {
        try {
            ChashiBhaiApp.showLoginView();
            SessionManager.clearTempData();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onBackToLoginClick() {
        try {
            ChashiBhaiApp.showLoginView();
            SessionManager.clearTempData();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
