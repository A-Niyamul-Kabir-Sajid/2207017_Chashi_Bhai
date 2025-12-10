package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.ChashiBhaiApp;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Random;

public class OtpVerificationController {

    @FXML
    private Label phoneLabel;

    @FXML
    private TextField otp1, otp2, otp3, otp4, otp5, otp6;

    @FXML
    private Label errorLabel;

    @FXML
    private Label timerLabel;

    @FXML
    private Label debugOtpLabel;

    private String generatedOtp;
    private int countdown = 60;
    private Timeline timer;
    private boolean isPinResetMode = false;

    @FXML
    public void initialize() {
        // Check if this is PIN reset mode (supports RESET_PIN, RESET_PIN_FARMER, RESET_PIN_BUYER)
        String role = SessionManager.getTempRole();
        isPinResetMode = role != null && role.startsWith("RESET_PIN");
        
        // Display masked phone number
        String phone = SessionManager.getTempPhone();
        if (phone != null && phone.length() >= 11) {
            String masked = "+880 " + phone.substring(0, 4) + "-XXX-" + phone.substring(phone.length() - 3);
            phoneLabel.setText(masked);
        }

        // Generate OTP
        generateOtp();

        // Start countdown timer
        startTimer();

        // Setup auto-focus for OTP fields
        setupOtpFields();
    }

    private void generateOtp() {
        Random random = new Random();
        generatedOtp = String.format("%06d", random.nextInt(1000000));
        
        // Display OTP in debug label (for development)
        debugOtpLabel.setText("Your OTP: " + generatedOtp);
        
        // In production, this would be sent via SMS
        System.out.println("=============================");
        System.out.println("OTP for " + SessionManager.getTempPhone() + ": " + generatedOtp);
        System.out.println("=============================");
    }

    private void startTimer() {
        countdown = 60;
        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            countdown--;
            timerLabel.setText("(" + countdown + "s)");
            if (countdown <= 0) {
                timer.stop();
                timerLabel.setText("(Expired)");
            }
        }));
        timer.setCycleCount(60);
        timer.play();
    }

    private void setupOtpFields() {
        TextField[] fields = {otp1, otp2, otp3, otp4, otp5, otp6};
        
        for (int i = 0; i < fields.length; i++) {
            final int index = i;
            TextField field = fields[i];
            
            // Limit to single digit
            field.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue.length() > 1) {
                    field.setText(newValue.substring(0, 1));
                }
                if (!newValue.matches("\\d*")) {
                    field.setText(oldValue);
                }
            });

            // Auto-focus next field
            field.setOnKeyReleased(event -> {
                if (event.getCode() == KeyCode.BACK_SPACE && field.getText().isEmpty() && index > 0) {
                    fields[index - 1].requestFocus();
                } else if (!field.getText().isEmpty() && index < fields.length - 1) {
                    fields[index + 1].requestFocus();
                }
            });
        }

        otp1.requestFocus();
    }

    @FXML
    protected void onBackClick() {
        if (timer != null) {
            timer.stop();
        }
        
        if (SessionManager.isLoginMode()) {
            try {
                ChashiBhaiApp.showLoginView();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                ChashiBhaiApp.showSignupView();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    protected void onVerifyClick() {
        String enteredOtp = otp1.getText() + otp2.getText() + otp3.getText() + 
                           otp4.getText() + otp5.getText() + otp6.getText();

        if (enteredOtp.length() != 6) {
            showError("Please enter complete OTP");
            return;
        }

        if (countdown <= 0) {
            showError("OTP expired. Please resend.");
            return;
        }

        if (!enteredOtp.equals(generatedOtp)) {
            showError("Invalid OTP. Please try again.");
            clearOtpFields();
            return;
        }

        // OTP verified successfully
        if (timer != null) {
            timer.stop();
        }

        if (isPinResetMode) {
            // Show success and navigate to Reset PIN screen
            if (timer != null) {
                timer.stop();
            }
            
            showSuccess("✅ OTP Verified! Setting up new PIN...");
            
            // Navigate to reset PIN screen after 1 second
            try {
                Thread.sleep(1000);
                ChashiBhaiApp.showResetPinView();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        } else if (SessionManager.isLoginMode()) {
            // This shouldn't happen with new login flow, but keep for safety
            System.out.println("Login successful for: " + SessionManager.getTempPhone());
            showSuccess("✅ Login successful! (Dashboard not yet implemented)");
            
        } else {
            // New signup - navigate to Create PIN screen
            if (timer != null) {
                timer.stop();
            }
            
            showSuccess("✅ OTP Verified! Now create your PIN...");
            
            // Navigate to create PIN screen after 1 second
            try {
                Thread.sleep(1000);
                ChashiBhaiApp.showCreatePinView();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    protected void onResendOtpClick() {
        if (countdown > 0) {
            showError("Please wait " + countdown + " seconds before resending");
            return;
        }

        // Generate new OTP
        generateOtp();
        
        // Clear fields
        clearOtpFields();
        
        // Restart timer
        if (timer != null) {
            timer.stop();
        }
        startTimer();

        errorLabel.setText("New OTP sent!");
        errorLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setVisible(true);
    }

    @FXML
    protected void onOtpKeyReleased(KeyEvent event) {
        // Auto-submit when all fields are filled
        if (!otp1.getText().isEmpty() && !otp2.getText().isEmpty() && 
            !otp3.getText().isEmpty() && !otp4.getText().isEmpty() && 
            !otp5.getText().isEmpty() && !otp6.getText().isEmpty()) {
            // Optional: Auto-verify
            // onVerifyClick();
        }
    }

    private void clearOtpFields() {
        otp1.clear();
        otp2.clear();
        otp3.clear();
        otp4.clear();
        otp5.clear();
        otp6.clear();
        otp1.requestFocus();
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
