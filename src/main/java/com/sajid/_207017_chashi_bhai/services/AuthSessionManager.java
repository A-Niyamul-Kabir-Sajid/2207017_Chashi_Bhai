package com.sajid._207017_chashi_bhai.services;

import java.sql.*;

/**
 * AuthSessionManager - Manages Firebase authentication session in SQLite
 * Provides one-time login functionality by caching auth tokens locally
 * 
 * This allows users to stay logged in even after app restart:
 * 1. On first login, stores Firebase tokens in SQLite
 * 2. On app restart, checks for valid cached session
 * 3. If cached session exists, auto-login without re-authentication
 */
public class AuthSessionManager {
    private static final String DB_URL = "jdbc:sqlite:data/chashi_bhai.db";
    private static AuthSessionManager instance;
    
    private AuthSessionManager() {
        initializeSessionTable();
    }
    
    public static AuthSessionManager getInstance() {
        if (instance == null) {
            instance = new AuthSessionManager();
        }
        return instance;
    }
    
    /**
     * Create auth_sessions table if not exists
     */
    private void initializeSessionTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS auth_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                firebase_uid TEXT,
                id_token TEXT,
                refresh_token TEXT,
                phone TEXT NOT NULL,
                role TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP,
                is_active BOOLEAN DEFAULT 1,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ Auth sessions table initialized");
        } catch (SQLException e) {
            System.err.println("Error creating auth_sessions table: " + e.getMessage());
        }
    }
    
    /**
     * Save authentication session after successful login
     * 
     * @param userId Local SQLite user ID
     * @param firebaseUid Firebase user ID
     * @param idToken Firebase ID token
     * @param refreshToken Firebase refresh token
     * @param phone User phone number
     * @param role User role (farmer/buyer)
     */
    public void saveSession(int userId, String firebaseUid, String idToken, 
                           String refreshToken, String phone, String role) {
        // First, invalidate any existing sessions for this user
        invalidateUserSessions(userId);
        
        String sql = """
            INSERT INTO auth_sessions (user_id, firebase_uid, id_token, refresh_token, phone, role, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now', '+7 days'))
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, firebaseUid);
            stmt.setString(3, idToken);
            stmt.setString(4, refreshToken);
            stmt.setString(5, phone);
            stmt.setString(6, role);
            
            stmt.executeUpdate();
            System.out.println("✓ Auth session saved for user: " + userId);
            
        } catch (SQLException e) {
            System.err.println("Error saving auth session: " + e.getMessage());
        }
    }
    
    /**
     * Get active session for a user
     * Returns null if no valid session exists
     */
    public CachedSession getActiveSession() {
        String sql = """
            SELECT s.*, u.name, u.district, u.upazila, u.is_verified, u.profile_photo
            FROM auth_sessions s
            JOIN users u ON s.user_id = u.id
            WHERE s.is_active = 1 
            AND datetime(s.expires_at) > datetime('now')
            ORDER BY s.created_at DESC
            LIMIT 1
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                CachedSession session = new CachedSession();
                session.userId = rs.getInt("user_id");
                session.firebaseUid = rs.getString("firebase_uid");
                session.idToken = rs.getString("id_token");
                session.refreshToken = rs.getString("refresh_token");
                session.phone = rs.getString("phone");
                session.role = rs.getString("role");
                session.name = rs.getString("name");
                session.district = rs.getString("district");
                session.upazila = rs.getString("upazila");
                session.isVerified = rs.getBoolean("is_verified");
                session.profilePhoto = rs.getString("profile_photo");
                
                System.out.println("✓ Found active session for user: " + session.name);
                return session;
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting active session: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Check if there's a valid cached session
     */
    public boolean hasValidSession() {
        return getActiveSession() != null;
    }
    
    /**
     * Invalidate all sessions for a user (for logout)
     */
    public void invalidateUserSessions(int userId) {
        String sql = "UPDATE auth_sessions SET is_active = 0 WHERE user_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("Error invalidating sessions: " + e.getMessage());
        }
    }
    
    /**
     * Invalidate all sessions (for complete logout)
     */
    public void logout() {
        String sql = "UPDATE auth_sessions SET is_active = 0 WHERE is_active = 1";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate(sql);
            System.out.println("✓ All sessions invalidated (logged out)");
            
        } catch (SQLException e) {
            System.err.println("Error during logout: " + e.getMessage());
        }
    }
    
    /**
     * Update the ID token (after refresh)
     */
    public void updateIdToken(int userId, String newIdToken) {
        String sql = "UPDATE auth_sessions SET id_token = ?, expires_at = datetime('now', '+7 days') WHERE user_id = ? AND is_active = 1";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, newIdToken);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("Error updating ID token: " + e.getMessage());
        }
    }
    
    /**
     * Clean up expired sessions
     */
    public void cleanupExpiredSessions() {
        String sql = "DELETE FROM auth_sessions WHERE datetime(expires_at) < datetime('now')";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) {
                System.out.println("✓ Cleaned up " + deleted + " expired sessions");
            }
            
        } catch (SQLException e) {
            System.err.println("Error cleaning up sessions: " + e.getMessage());
        }
    }
    
    /**
     * Cached session data class
     */
    public static class CachedSession {
        public int userId;
        public String firebaseUid;
        public String idToken;
        public String refreshToken;
        public String phone;
        public String role;
        public String name;
        public String district;
        public String upazila;
        public boolean isVerified;
        public String profilePhoto;
    }
}
