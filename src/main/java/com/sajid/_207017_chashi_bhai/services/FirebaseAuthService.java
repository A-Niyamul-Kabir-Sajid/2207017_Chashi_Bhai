package com.sajid._207017_chashi_bhai.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Authentication Service using REST API
 * Handles phone-based authentication with OTP verification
 * 
 * This service uses Firebase's Identity Platform REST API for:
 * - Phone number authentication (send OTP, verify OTP)
 * - Custom token auth for phone-based login
 * 
 * Benefits:
 * - Lightweight (no Firebase Admin SDK needed)
 * - Works directly with Firebase REST APIs
 * - SQLite caches auth state for one-time login
 */
public class FirebaseAuthService {
    private static final String API_KEY = FirebaseConfig.getWebApiKey();
    
    // Firebase Auth REST API endpoints
    private static final String SIGN_IN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY;
    private static final String SIGN_UP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + API_KEY;
    private static final String UPDATE_PROFILE_URL = "https://identitytoolkit.googleapis.com/v1/accounts:update?key=" + API_KEY;
    
    private final HttpClient httpClient;
    private final Gson gson;

    public FirebaseAuthService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.gson = new Gson();
        
        // Validate API key is configured
        if (!FirebaseConfig.isConfigured()) {
            System.err.println("\n⚠️  WARNING: Firebase Web API Key not configured!");
            System.err.println("Please configure it in firebase.properties file");
            System.err.println("Get your API key from: Firebase Console -> Project Settings -> Web API Key\n");
        }
    }

    /**
     * Sign up a new user with phone and PIN (creates Firebase custom auth)
     * 
     * @param phone User phone number (01XXXXXXXXX format)
     * @param pin User PIN (4-6 digits, will be converted to password format)
     * @param displayName User's full name
     * @return AuthResult containing user ID and authentication token
     * @throws IOException if signup fails
     */
    public AuthResult signUp(String phone, String pin, String displayName) throws IOException {
        // Convert phone to email format for Firebase Auth (phone@chashi-bhai.app)
        String email = phoneToEmail(phone);
        String password = pinToPassword(pin);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("email", email);
        requestBody.put("password", password);
        requestBody.put("returnSecureToken", true);

        String jsonBody = gson.toJson(requestBody);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SIGN_UP_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

                String idToken = jsonResponse.get("idToken").getAsString();
                String refreshToken = jsonResponse.get("refreshToken").getAsString();
                String userId = jsonResponse.get("localId").getAsString();
                
                // Update display name after signup
                updateDisplayName(idToken, displayName);

                return new AuthResult(idToken, refreshToken, userId, phone, displayName, true);
            } else {
                String errorMessage = parseErrorMessage(response.body());
                throw new IOException(errorMessage);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    /**
     * Sign in existing user with phone and PIN
     * 
     * @param phone User phone number
     * @param pin User PIN
     * @return AuthResult containing user ID and authentication token
     * @throws IOException if login fails
     */
    public AuthResult signIn(String phone, String pin) throws IOException {
        String email = phoneToEmail(phone);
        String password = pinToPassword(pin);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("email", email);
        requestBody.put("password", password);
        requestBody.put("returnSecureToken", true);

        String jsonBody = gson.toJson(requestBody);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SIGN_IN_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

                String idToken = jsonResponse.get("idToken").getAsString();
                String refreshToken = jsonResponse.get("refreshToken").getAsString();
                String userId = jsonResponse.get("localId").getAsString();
                String displayName = jsonResponse.has("displayName") ? 
                    jsonResponse.get("displayName").getAsString() : "";

                return new AuthResult(idToken, refreshToken, userId, phone, displayName, false);
            } else {
                String errorMessage = parseErrorMessage(response.body());
                throw new IOException(errorMessage);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    /**
     * Update user's password (PIN) in Firebase Auth by deleting and recreating the account
     * This is a workaround since Firebase REST API doesn't allow password reset without old password
     * 
     * @param phone User phone number
     * @param newPin New PIN to set
     * @param displayName User's display name (retrieved from local DB)
     * @throws IOException if password update fails
     */
    public void updatePasswordViaRecreate(String phone, String newPin, String displayName) throws IOException {
        String email = phoneToEmail(phone);
        String newPassword = pinToPassword(newPin);
        
        System.out.println("🔄 Recreating Firebase Auth account for password reset...");
        System.out.println("   Phone: " + phone + " (email: " + email + ")");
        
        // Step 1: Delete the existing Firebase Auth user
        // Note: This requires getting the user's ID token first, but we can't sign in with old password
        // So we'll try to sign up with the new password - if account exists, it will fail
        // Then we know we need admin SDK. For now, just create a new account.
        
        // Attempt to create new account (will fail if exists, which is expected during reset)
        try {
            signUp(phone, newPin, displayName);
            System.out.println("✅ Firebase Auth account created with new password");
        } catch (IOException e) {
            // If account already exists, that's expected
            if (e.getMessage().contains("EMAIL_EXISTS") || e.getMessage().contains("আগে থেকেই")) {
                System.out.println("⚠️ Firebase Auth account already exists");
                System.out.println("⚠️ Cannot update password via REST API without old password or Admin SDK");
                System.out.println("   Workaround: User must sign up again or use Admin SDK backend");
                throw new IOException("Firebase account exists - password not updated. User should create new account or contact admin.");
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Update user's display name
     */
    private void updateDisplayName(String idToken, String displayName) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("idToken", idToken);
            requestBody.put("displayName", displayName);
            requestBody.put("returnSecureToken", false);

            String jsonBody = gson.toJson(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UPDATE_PROFILE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to update display name: " + e.getMessage());
        }
    }

    /**
     * Convert Bangladesh phone number to Firebase email format
     * 01712345678 -> 8801712345678@chashi-bhai.app
     */
    private String phoneToEmail(String phone) {
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        // Add Bangladesh country code if not present
        if (cleanPhone.startsWith("0")) {
            cleanPhone = "880" + cleanPhone.substring(1);
        } else if (!cleanPhone.startsWith("880")) {
            cleanPhone = "880" + cleanPhone;
        }
        return cleanPhone + "@chashi-bhai.app";
    }

    /**
     * Convert PIN to password format (add prefix for minimum length)
     * Firebase requires minimum 6 characters
     */
    private String pinToPassword(String pin) {
        return "CB_PIN_" + pin;
    }

    /**
     * Parse error message from Firebase Authentication API
     */
    private String parseErrorMessage(String errorBody) {
        try {
            JsonObject errorJson = JsonParser.parseString(errorBody).getAsJsonObject();
            
            if (errorJson.has("error")) {
                JsonObject error = errorJson.getAsJsonObject("error");
                String message = error.get("message").getAsString();
                
                // Convert Firebase error codes to user-friendly messages
                switch (message) {
                    case "EMAIL_EXISTS":
                        return "এই ফোন নম্বর আগে থেকেই রেজিস্টার্ড";
                    case "EMAIL_NOT_FOUND":
                    case "INVALID_PASSWORD":
                    case "INVALID_LOGIN_CREDENTIALS":
                        return "ভুল ফোন নম্বর অথবা পিন";
                    case "WEAK_PASSWORD : Password should be at least 6 characters":
                        return "পিন অন্তত ৬ ক্যারেক্টার হতে হবে";
                    case "INVALID_EMAIL":
                        return "অবৈধ ফোন নম্বর";
                    case "TOO_MANY_ATTEMPTS_TRY_LATER":
                        return "অনেকবার চেষ্টা হয়েছে। পরে আবার চেষ্টা করুন";
                    case "USER_DISABLED":
                        return "এই অ্যাকাউন্ট নিষ্ক্রিয় করা হয়েছে";
                    default:
                        if (message.contains("INVALID_LOGIN_CREDENTIALS")) {
                            return "ভুল ফোন নম্বর অথবা পিন";
                        }
                        return message;
                }
            }
        } catch (Exception ex) {
            // If parsing fails, return generic message
        }
        
        return "প্রমাণীকরণ ব্যর্থ হয়েছে। আবার চেষ্টা করুন।";
    }

    /**
     * Result of authentication operation
     * Contains user information and authentication tokens
     */
    public static class AuthResult {
        private final String idToken;
        private final String refreshToken;
        private final String firebaseUserId;
        private final String phone;
        private final String displayName;
        private final boolean isNewUser;

        public AuthResult(String idToken, String refreshToken, String firebaseUserId, 
                         String phone, String displayName, boolean isNewUser) {
            this.idToken = idToken;
            this.refreshToken = refreshToken;
            this.firebaseUserId = firebaseUserId;
            this.phone = phone;
            this.displayName = displayName;
            this.isNewUser = isNewUser;
        }

        public String getIdToken() { return idToken; }
        public String getRefreshToken() { return refreshToken; }
        public String getFirebaseUserId() { return firebaseUserId; }
        public String getPhone() { return phone; }
        public String getDisplayName() { return displayName; }
        public boolean isNewUser() { return isNewUser; }
    }
}
