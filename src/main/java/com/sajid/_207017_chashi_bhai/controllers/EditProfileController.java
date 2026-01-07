package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * EditProfileController - Allows users to edit their profile information
 * Can edit: Full Name, District
 * Cannot edit: Phone, User ID, Role
 */
public class EditProfileController {

    @FXML private TextField txtFullName;
    @FXML private ComboBox<String> cmbDistrict;
    @FXML private TextField txtPhone;
    @FXML private TextField txtUserId;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;
    
    // 64 districts of Bangladesh sorted alphabetically
    private static final String[] DISTRICTS = {
        "Bagerhat", "Bandarban", "Barguna", "Barisal", "Bhola", "Bogra",
        "Brahmanbaria", "Chandpur", "Chapainawabganj", "Chittagong", "Chuadanga",
        "Comilla", "Cox's Bazar", "Dhaka", "Dinajpur", "Faridpur", "Feni",
        "Gaibandha", "Gazipur", "Gopalganj", "Habiganj", "Jamalpur", "Jessore",
        "Jhalokati", "Jhenaidah", "Joypurhat", "Khagrachhari", "Khulna", "Kishoreganj",
        "Kurigram", "Kushtia", "Lakshmipur", "Lalmonirhat", "Madaripur", "Magura",
        "Manikganj", "Meherpur", "Moulvibazar", "Munshiganj", "Mymensingh", "Naogaon",
        "Narail", "Narayanganj", "Narsingdi", "Natore", "Netrokona", "Nilphamari",
        "Noakhali", "Pabna", "Panchagarh", "Patuakhali", "Pirojpur", "Rajbari",
        "Rajshahi", "Rangamati", "Rangpur", "Satkhira", "Shariatpur", "Sherpur",
        "Sirajganj", "Sunamganj", "Sylhet", "Tangail", "Thakurgaon"
    };

    @FXML
    public void initialize() {
        // Populate districts combobox
        cmbDistrict.setItems(FXCollections.observableArrayList(DISTRICTS));
        
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
        
        // Set district if it exists in the list
        String district = currentUser.getDistrict();
        if (district != null && !district.isEmpty()) {
            cmbDistrict.setValue(district);
        }
        
        txtPhone.setText(currentUser.getPhone());
        txtUserId.setText(currentUser.getUserId());
    }

    @FXML
    private void onSave() {
        // Re-check if user is still logged in
        currentUser = App.getCurrentUser();
        if (currentUser == null) {
            showError("সেশন মেয়াদ শেষ / Session expired. Please login again");
            App.loadScene("login-view.fxml", "Login");
            return;
        }
        
        String fullName = txtFullName.getText().trim();
        String district = cmbDistrict.getValue();

        // Validation
        if (fullName.isEmpty()) {
            showError("পূর্ণ নাম খালি রাখা যাবে না / Full name cannot be empty");
            return;
        }

        if (district == null || district.isEmpty()) {
            showError("জেলা নির্বাচন করুন / Please select a district");
            return;
        }

        // Show loading
        progressIndicator.setVisible(true);
        btnSave.setDisable(true);

        // Update database
        boolean success = DatabaseService.updateUserProfile(
            currentUser.getId(),
            fullName,
            district
        );

        progressIndicator.setVisible(false);
        btnSave.setDisable(false);

        if (success) {
            // Update current user object
            currentUser.setName(fullName);
            currentUser.setDistrict(district);

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
        // Re-check current user in case session expired
        User user = App.getCurrentUser();
        if (user == null) {
            App.loadScene("login-view.fxml", "Login");
            return;
        }
        
        if (user.getRole().equalsIgnoreCase("FARMER")) {
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
