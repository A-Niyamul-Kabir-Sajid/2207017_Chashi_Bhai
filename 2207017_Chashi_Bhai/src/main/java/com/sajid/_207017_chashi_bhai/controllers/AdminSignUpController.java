package com.sajid._207017_chashi_bhai.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class AdminSignUpController {

    @FXML
    private TextField adminNameField;

    @FXML
    private TextField adminEmailField;

    @FXML
    private TextField adminPasswordField;

    @FXML
    private Button signUpButton;

    @FXML
    public void initialize() {
        signUpButton.setOnAction(event -> handleSignUp());
    }

    private void handleSignUp() {
        String name = adminNameField.getText();
        String email = adminEmailField.getText();
        String password = adminPasswordField.getText();

        // Add validation and sign-up logic here
    }
}