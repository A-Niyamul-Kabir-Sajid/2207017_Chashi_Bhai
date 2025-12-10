package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.ChashiBhaiApp;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;

import java.io.IOException;

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
        try {
            ChashiBhaiApp.showWelcomeView();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

        // TODO: Check phone, PIN and role in database
        // For now, simulate login failure for demo
        showError("Account not found or incorrect credentials. Please sign up first.");
        
        System.out.println("Login attempt - Phone: " + phone + ", Role: " + selectedRole);
        
        // TODO: On successful login:
        // SessionManager.setCurrentUserId(userId);
        // SessionManager.setCurrentUserName(name);
        // SessionManager.setCurrentUserRole(selectedRole);
        // SessionManager.setCurrentUserPhone(phone);
        // Navigate to appropriate dashboard based on role
        // if (selectedRole.equals("FARMER")) {
        //     ChashiBhaiApp.showFarmerDashboard();
        // } else {
        //     ChashiBhaiApp.showBuyerDashboard();
        // }
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

        try {
            ChashiBhaiApp.showOtpView();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to load OTP screen");
        }
    }

    @FXML
    protected void onSignupLinkClick() {
        try {
            ChashiBhaiApp.showSignupView();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
