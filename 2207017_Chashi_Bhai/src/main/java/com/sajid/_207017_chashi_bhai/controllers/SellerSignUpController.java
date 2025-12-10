package com.sajid._207017_chashi_bhai.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class SellerSignUpController {

    @FXML
    private TextField sellerNameField;

    @FXML
    private TextField sellerEmailField;

    @FXML
    private TextField sellerPasswordField;

    @FXML
    private Button signUpButton;

    @FXML
    public void initialize() {
        signUpButton.setOnAction(event -> handleSignUp());
    }

    private void handleSignUp() {
        String name = sellerNameField.getText();
        String email = sellerEmailField.getText();
        String password = sellerPasswordField.getText();

        if (validateInput(name, email, password)) {
            // Logic to handle sign-up (e.g., call a service to save the seller)
            showAlert("Success", "Seller signed up successfully!", AlertType.INFORMATION);
        } else {
            showAlert("Error", "Please fill in all fields correctly.", AlertType.ERROR);
        }
    }

    private boolean validateInput(String name, String email, String password) {
        return !name.isEmpty() && !email.isEmpty() && !password.isEmpty();
    }

    private void showAlert(String title, String message, AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}