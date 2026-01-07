package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
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
    private PasswordField pinField;

    @FXML
    private PasswordField confirmPinField;

    @FXML
    private Label errorLabel;

    private String selectedRole = "";
    
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
        // Populate districts
        districtCombo.getItems().addAll(DISTRICTS);
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
        String pin = pinField.getText().trim();
        String confirmPin = confirmPinField.getText().trim();

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

        // Validate PIN
        if (pin.isEmpty() || confirmPin.isEmpty()) {
            showError("Please enter PIN in both fields");
            return;
        }

        if (!pin.matches("^[0-9]{4,6}$")) {
            showError("PIN must be 4-6 digits only");
            return;
        }

        if (!pin.equals(confirmPin)) {
            showError("PINs do not match. Please try again.");
            confirmPinField.clear();
            confirmPinField.requestFocus();
            return;
        }

        // Check if phone already exists and create user
        errorLabel.setText("Creating your account...");
        errorLabel.setVisible(true);
        
        // Create user in database with user-provided PIN
        new Thread(() -> {
            int userId = DatabaseService.createUser(phone, pin, name, selectedRole.toLowerCase(), district);
            
            Platform.runLater(() -> {
                if (userId == -2) {
                    showError("❌ Phone number already registered. Please login instead.");
                } else if (userId == -1) {
                    showError("❌ Failed to create account. Please try again.");
                } else {
                    // Success - show popup and go to welcome
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Signup Successful");
                    alert.setHeaderText("✅ Account Created Successfully!");
                    alert.setContentText(
                        "Your account has been created successfully!\n\n" +
                        "User ID: USR" + String.format("%06d", userId) + "\n" +
                        "Phone: " + phone + "\n" +
                        "Role: " + selectedRole + "\n\n" +
                        "You can now login with your phone number and PIN."
                    );
                    
                    alert.showAndWait();
                    
                    // Clear form
                    nameField.clear();
                    phoneField.clear();
                    pinField.clear();
                    confirmPinField.clear();
                    districtCombo.setValue(null);
                    selectedRole = "";
                    farmerButton.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 5;");
                    buyerButton.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 5;");
                    errorLabel.setVisible(false);
                    
                    // Go back to welcome page
                    App.loadScene("welcome-view.fxml", "Welcome - Chashi Bhai");
                }
            });
        }).start();
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
