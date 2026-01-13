package com.sajid._207017_chashi_bhai.services;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for Firebase settings
 * Loads configuration from firebase.properties file
 */
public class FirebaseConfig {
    private static final Properties properties = new Properties();
    private static boolean loaded = false;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        try {
            // Try to load from file
            InputStream input = new FileInputStream("firebase.properties");
            properties.load(input);
            loaded = true;
            System.out.println("✓ Firebase configuration loaded from firebase.properties");
        } catch (IOException e) {
            System.err.println("⚠️  Could not load firebase.properties: " + e.getMessage());
            System.err.println("Using system properties or default values instead.");
        }
    }

    /**
     * Get Firebase Web API Key
     * Tries in order: 1) firebase.properties, 2) System property, 3) Default value
     */
    public static String getWebApiKey() {
        String apiKey = properties.getProperty("FIREBASE_WEB_API_KEY");
        
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("YOUR_WEB_API_KEY_HERE")) {
            // Try system property
            apiKey = System.getProperty("FIREBASE_WEB_API_KEY");
        }
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = "YOUR_WEB_API_KEY_HERE";
        }
        
        return apiKey;
    }

    /**
     * Get Firebase Project ID
     */
    public static String getProjectId() {
        String projectId = properties.getProperty("FIREBASE_PROJECT_ID");
        
        if (projectId == null || projectId.trim().isEmpty()) {
            // Try system property
            projectId = System.getProperty("FIREBASE_PROJECT_ID");
        }
        
        if (projectId == null || projectId.trim().isEmpty()) {
            projectId = "testfirebase-12671"; // Default project ID
        }
        
        return projectId;
    }

    /**
     * Check if configuration is properly set up
     */
    public static boolean isConfigured() {
        String apiKey = getWebApiKey();
        return !apiKey.equals("YOUR_WEB_API_KEY_HERE") && !apiKey.trim().isEmpty();
    }

    /**
     * Print configuration status
     */
    public static void printStatus() {
        System.out.println("\n=== Firebase Configuration Status ===");
        System.out.println("Config file loaded: " + (loaded ? "✓ Yes" : "✗ No"));
        System.out.println("API Key configured: " + (isConfigured() ? "✓ Yes" : "✗ No"));
        System.out.println("Project ID: " + getProjectId());
        
        if (!isConfigured()) {
            System.err.println("\n⚠️  Firebase Web API Key NOT configured!");
            System.err.println("To fix this:");
            System.err.println("1. Go to: https://console.firebase.google.com/project/" + getProjectId() + "/settings/general");
            System.err.println("2. Scroll to 'Web API Key'");
            System.err.println("3. Copy the key");
            System.err.println("4. Edit firebase.properties and replace YOUR_WEB_API_KEY_HERE with your key");
            System.err.println("5. Or set system property: -DFIREBASE_WEB_API_KEY=your_key_here\n");
        }
        System.out.println("=====================================\n");
    }
}
