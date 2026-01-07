package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * EditProfileController - Allows users to edit their profile information
 * Can edit: Full Name, Home Town
 * Cannot edit: Phone, User ID, Role
 */
public class EditProfileController {

    @FXML private TextField txtFullName;
    @FXML private TextField txtHomeTown;
    @FXML private TextField txtPhone;
    @FXML private TextField txtUserId;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        if (currentUser == null) {
            showError("দয়া করে প্রথমে লগইন করুন / Please login first");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        loadUserData();
    }

    private void loadUserData() {
        txtFullName.setText(currentUser.getName());
        txtHomeTown.setText(currentUser.getDistrict());
        txtPhone.setText(currentUser.getPhone());
        txtUserId.setText(currentUser.getUserId());
    }

    @FXML
    private void onSave() {
        String fullName = txtFullName.getText().trim();
        String homeTown = txtHomeTown.getText().trim();

        // Validation
        if (fullName.isEmpty()) {
            showError("পূর্ণ নাম খালি রাখা যাবে না / Full name cannot be empty");
            return;
        }

        if (homeTown.isEmpty()) {
            showError("শহর/জেলা খালি রাখা যাবে না / Home town cannot be empty");
            return;
        }

        // Show loading
        progressIndicator.setVisible(true);
        btnSave.setDisable(true);

        // Update database
        boolean success = DatabaseService.updateUserProfile(
            currentUser.getId(),
            fullName,
            homeTown
        );

        progressIndicator.setVisible(false);
        btnSave.setDisable(false);

        if (success) {
            // Update current user object
            currentUser.setName(fullName);
            currentUser.setDistrict(homeTown);

            showSuccess("প্রোফাইল সফলভাবে আপডেট হয়েছে / Profile updated successfully");

            // Navigate back to profile view
            navigateBackToProfile();
        } else {
            showError("প্রোফাইল আপডেট ব্যর্থ হয়েছে / Failed to update profile");
        }
    }

    @FXML
    private void onCancel() {
        navigateBackToProfile();
    }

    private void navigateBackToProfile() {
        if (currentUser.getRole().equalsIgnoreCase("FARMER")) {
            App.loadScene("farmer-profile-view.fxml", "Profile");
        } else {
            App.loadScene("buyer-profile-view.fxml", "Profile");
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
