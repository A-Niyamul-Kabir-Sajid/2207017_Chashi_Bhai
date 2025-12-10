package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.ChashiBhaiApp;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField phoneField;

    @FXML
    private Label errorLabel;

    @FXML
    protected void onBackClick() {
        try {
            ChashiBhaiApp.showWelcomeView();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onSendOtpClick() {
        String phone = phoneField.getText().trim();

        // Validate phone number
        if (phone.isEmpty()) {
            showError("Please enter your phone number");
            return;
        }

        if (!phone.matches("^01[0-9]{9}$")) {
            showError("Invalid phone number. Use format: 01XXXXXXXXX");
            return;
        }

        // TODO: Check if phone exists in database
        // For now, simulate OTP sending
        
        // Save phone to session for OTP verification
        SessionManager.setTempPhone(phone);
        SessionManager.setLoginMode(true);

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
