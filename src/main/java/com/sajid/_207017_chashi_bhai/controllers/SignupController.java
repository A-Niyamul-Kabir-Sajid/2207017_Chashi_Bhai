package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class SignupController {

    @FXML
    private TextField nameField;

    @FXML
    private TextField phoneField;

    @FXML
    private ComboBox<String> districtCombo;

    @FXML
    private Button farmerButton;

    @FXML
    private Button buyerButton;

    @FXML
    private Label errorLabel;

    private String selectedRole = "";

    @FXML
    public void initialize() {
        // Populate districts (Bangladesh)
        districtCombo.getItems().addAll(
            "Dhaka", "Chittagong", "Rajshahi", "Khulna", "Barisal", "Sylhet", "Rangpur", "Mymensingh",
            "Comilla", "Gazipur", "Narayanganj", "Tangail", "Jamalpur", "Bogra", "Dinajpur", "Jessore",
            "Kushtia", "Pabna", "Faridpur", "Madaripur", "Narsingdi", "Brahmanbaria", "Noakhali", "Feni",
            "Cox's Bazar", "Chandpur", "Lakshmipur", "Rangamati", "Bandarban", "Khagrachari", "Patuakhali",
            "Bhola", "Jhalokati", "Pirojpur", "Barguna", "Satkhira", "Bagerhat", "Magura", "Chuadanga",
            "Meherpur", "Jhenaidah", "Narail", "Sirajganj", "Natore", "Chapainawabganj", "Naogaon",
            "Joypurhat", "Panchagarh", "Thakurgaon", "Nilphamari", "Lalmonirhat", "Kurigram", "Gaibandha",
            "Sherpur", "Netrokona", "Kishoreganj", "Munshiganj", "Gopalganj", "Shariatpur", "Rajbari",
            "Manikganj", "Habiganj", "Moulvibazar", "Sunamganj"
        );
    }

    @FXML
    protected void onBackClick() {
        App.loadScene("welcome-view.fxml", "Welcome - Chashi Bhai");
    }

    @FXML
    protected void onFarmerSelect() {
        selectedRole = "FARMER";
        farmerButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
        buyerButton.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 5;");
    }

    @FXML
    protected void onBuyerSelect() {
        selectedRole = "BUYER";
        buyerButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
        farmerButton.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 5;");
    }

    @FXML
    protected void onContinueClick() {
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String district = districtCombo.getValue();

        // Validate inputs
        if (name.isEmpty()) {
            showError("Please enter your name");
            return;
        }

        if (phone.isEmpty()) {
            showError("Please enter your phone number");
            return;
        }

        if (!phone.matches("^01[0-9]{9}$")) {
            showError("Invalid phone number. Use format: 01XXXXXXXXX");
            return;
        }

        if (district == null || district.isEmpty()) {
            showError("Please select your district");
            return;
        }

        if (selectedRole.isEmpty()) {
            showError("Please select your role (Farmer or Buyer)");
            return;
        }

        // TODO: Check if phone already exists in database

        // Save signup data to session
        SessionManager.setTempPhone(phone);
        SessionManager.setTempName(name);
        SessionManager.setTempDistrict(district);
        SessionManager.setTempRole(selectedRole);
        SessionManager.setLoginMode(false);

        App.loadScene("otp-verification-view.fxml", "OTP Verification");
    }

    @FXML
    protected void onLoginLinkClick() {
        App.loadScene("login-view.fxml", "Login - Chashi Bhai");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
