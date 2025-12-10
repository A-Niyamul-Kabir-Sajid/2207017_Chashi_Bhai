package com.sajid._207017_chashi_bhai;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ChashiBhaiApp extends Application {
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        primaryStage.setTitle("Chashi Bhai - Agricultural Marketplace");
        
        // Load welcome view
        showWelcomeView();
        
        primaryStage.show();
    }

    public static void showWelcomeView() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ChashiBhaiApp.class.getResource("welcome-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        primaryStage.setScene(scene);
    }

    public static void showLoginView() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ChashiBhaiApp.class.getResource("login-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        primaryStage.setScene(scene);
    }

    public static void showSignupView() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ChashiBhaiApp.class.getResource("signup-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        primaryStage.setScene(scene);
    }

    public static void showOtpView() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ChashiBhaiApp.class.getResource("otp-verification-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        primaryStage.setScene(scene);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch();
    }
}
