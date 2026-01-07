package com.sajid._207017_chashi_bhai;

import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * App - Main application class and singleton for managing app state
 * Handles navigation, current user session, and global app state
 */
public class App extends Application {

    private static Stage primaryStage;
    private static User currentUser;
    private static int currentCropId = -1;
    private static int viewedUserId = -1; // For viewing other users' profiles
    private static String searchQuery = "";

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        
        // Initialize database
        DatabaseService.initializeDatabase();
        
        // Set app icon
        try {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("icon.png")));
        } catch (Exception e) {
            // Icon not found, continue without it
        }

        // Load welcome screen
        loadScene("welcome-view.fxml", "Chashi Bhai - কৃষি বাজার");
        
        primaryStage.setOnCloseRequest(event -> {
            DatabaseService.shutdown();
        });
        
        primaryStage.show();
    }

    /**
     * Load a new scene
     * 
     * @param fxmlFile FXML filename (e.g., "login-view.fxml")
     * @param title Window title
     */
    public static void loadScene(String fxmlFile, String title) {
        System.out.println("[DEBUG] Loading scene: " + fxmlFile);
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxmlFile));
            Scene scene = new Scene(fxmlLoader.load());
            
            // Load complete original CSS file (all styles included)
            scene.getStylesheets().add(App.class.getResource("styles.css").toExternalForm());
            
            // Also load modular CSS files for any additional styling
            scene.getStylesheets().add(App.class.getResource("base.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("components.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("auth.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("dashboard.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("marketplace.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("tables.css").toExternalForm());
            
            primaryStage.setScene(scene);
            primaryStage.setTitle(title);
            primaryStage.centerOnScreen();
            
        } catch (IOException e) {
            System.err.println("Error loading scene: " + fxmlFile);
            e.printStackTrace();
        }
    }

    /**
     * Get the current logged-in user
     */
    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Set the current logged-in user
     */
    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    /**
     * Logout current user
     */
    public static void logout() {
        currentUser = null;
        currentCropId = -1;
        searchQuery = "";
        loadScene("welcome-view.fxml", "Chashi Bhai");
    }

    /**
     * Get current crop ID (for passing between controllers)
     */
    public static int getCurrentCropId() {
        return currentCropId;
    }

    /**
     * Set current crop ID
     */
    public static void setCurrentCropId(int cropId) {
        currentCropId = cropId;
    }

    /**
     * Get search query (for passing from dashboard to crop feed)
     */
    public static String getSearchQuery() {
        return searchQuery;
    }

    /**
     * Set search query
     */
    public static void setSearchQuery(String query) {
        searchQuery = query;
    }

    /**
     * Clear search query
     */
    public static void clearSearchQuery() {
        searchQuery = "";
    }

    /**
     * Get primary stage reference
     */
    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Get viewed user ID (for public profile views)
     */
    public static int getCurrentViewedUserId() {
        return viewedUserId;
    }

    /**
     * Set viewed user ID
     */
    public static void setCurrentViewedUserId(int userId) {
        viewedUserId = userId;
    }

    /**
     * Show a view and pass its controller to a callback
     */
    public static void showView(String fxmlFile, java.util.function.Consumer<Object> controllerCallback) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource(fxmlFile));
        Scene scene = new Scene(loader.load());
        
        // Load CSS
        scene.getStylesheets().add(App.class.getResource("styles.css").toExternalForm());
        scene.getStylesheets().add(App.class.getResource("base.css").toExternalForm());
        scene.getStylesheets().add(App.class.getResource("components.css").toExternalForm());
        scene.getStylesheets().add(App.class.getResource("chat.css").toExternalForm());
        
        primaryStage.setScene(scene);
        
        // Pass controller to callback
        if (controllerCallback != null) {
            controllerCallback.accept(loader.getController());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
