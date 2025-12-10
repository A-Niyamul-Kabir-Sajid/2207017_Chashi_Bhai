package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.ChashiBhaiApp;
import javafx.fxml.FXML;

import java.io.IOException;

public class WelcomeController {

    @FXML
    protected void onLoginClick() {
        try {
            ChashiBhaiApp.showLoginView();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onSignupClick() {
        try {
            ChashiBhaiApp.showSignupView();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
