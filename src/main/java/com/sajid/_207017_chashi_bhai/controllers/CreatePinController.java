package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.ChashiBhaiApp;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

import java.io.IOException;

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

        // TODO: Save user account to database with PIN
        String name = SessionManager.getTempName();
        String phone = SessionManager.getTempPhone();
        String district = SessionManager.getTempDistrict();
        String role = SessionManager.getTempRole();

        System.out.println("================================");
        System.out.println("Account Creation Successful!");
        System.out.println("Name: " + name);
        System.out.println("Phone: " + phone);
        System.out.println("District: " + district);
        System.out.println("Role: " + role);
        System.out.println("PIN: " + newPin);
        System.out.println("================================");

        // Show success message
        showSuccess("✅ Account created successfully! Redirecting to login...");

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
            ChashiBhaiApp.showWelcomeView();
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
