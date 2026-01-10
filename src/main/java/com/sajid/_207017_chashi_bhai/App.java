package com.sajid._207017_chashi_bhai;

import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.FirebaseService;
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
    private static int currentOrderId = -1; // For viewing order details
    private static String currentOrderNumber = ""; // For searching orders by number
    private static String searchQuery = "";
    private static String previousScene = ""; // Track previous scene for back navigation

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        
        // Set implicit exit to true - allows app to exit when last window closes
        javafx.application.Platform.setImplicitExit(true);
        
        // Initialize SQLite database (local storage)
        DatabaseService.initializeDatabase();
        
        // Initialize Firebase (cloud storage - optional)
        try {
            FirebaseService.getInstance().initialize();
            System.out.println("✅ Firebase cloud sync enabled");
        } catch (Exception e) {
            System.out.println("⚠️ Firebase not configured - running in offline mode");
            System.out.println("   To enable cloud sync, see FIREBASE_SETUP.md");
            // Continue without Firebase - app works offline with SQLite
        }
        
        // Set app icon
        try {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("icon.png")));
        } catch (Exception e) {
            // Icon not found, continue without it
        }

        // Load welcome screen
        loadScene("welcome-view.fxml", "Chashi Bhai - কৃষি বাজার");
        
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("[App] Shutting down application...");
            
            // Don't consume the event - let it close naturally
            // But shutdown all background services first
            new Thread(() -> {
                // Shutdown all services in a separate thread to avoid blocking UI
                try {
                    com.sajid._207017_chashi_bhai.utils.DataSyncManager.getInstance().shutdown();
                    System.out.println("[App] DataSyncManager stopped");
                } catch (IllegalAccessError | Exception e) {
                    System.err.println("Error shutting down DataSyncManager: " + e.getMessage());
                }
                
                try {
                    DatabaseService.shutdown();
                    System.out.println("[App] DatabaseService stopped");
                } catch (IllegalAccessError | Exception e) {
                    System.err.println("Error shutting down DatabaseService: " + e.getMessage());
                }
                
                try {
                    FirebaseService.getInstance().shutdown();
                    System.out.println("[App] FirebaseService stopped");
                } catch (IllegalAccessError | Exception e) {
                    System.err.println("Error shutting down FirebaseService: " + e.getMessage());
                }
                
                System.out.println("[App] All services stopped, exiting...");
                
                // Force exit after cleanup
                javafx.application.Platform.exit();
                System.exit(0);
            }, "Shutdown-Thread").start();
        });
        
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // JavaFX stop() method - called when app is closing
        System.out.println("[App] JavaFX stop() called");
        
        // Ensure all services are shut down
        try {
            com.sajid._207017_chashi_bhai.utils.DataSyncManager.getInstance().shutdown();
            System.out.println("[App] DataSyncManager stopped");
        } catch (IllegalAccessError | Exception e) {
            System.err.println("Error in stop() shutting down DataSyncManager: " + e.getMessage());
        }
        
        try {
            DatabaseService.shutdown();
            System.out.println("[App] DatabaseService stopped");
        } catch (IllegalAccessError | Exception e) {
            System.err.println("Error in stop() shutting down DatabaseService: " + e.getMessage());
        }
        
        try {
            FirebaseService.getInstance().shutdown();
            System.out.println("[App] FirebaseService stopped");
        } catch (IllegalAccessError | Exception e) {
            System.err.println("Error in stop() shutting down FirebaseService: " + e.getMessage());
        }
        
        super.stop();
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
     * Get previous scene (for back navigation)
     */
    public static String getPreviousScene() {
        return previousScene;
    }

    /**
     * Set previous scene (call before navigating to detail views)
     */
    public static void setPreviousScene(String sceneName) {
        previousScene = sceneName;
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
     * Get current order ID (for viewing order details)
     */
    public static int getCurrentOrderId() {
        return currentOrderId;
    }

    /**
     * Set current order ID
     */
    public static void setCurrentOrderId(int orderId) {
        currentOrderId = orderId;
    }

    /**
     * Get current order number (for searching)
     */
    public static String getCurrentOrderNumber() {
        return currentOrderNumber;
    }

    /**
     * Set current order number
     */
    public static void setCurrentOrderNumber(String orderNumber) {
        currentOrderNumber = orderNumber;
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
