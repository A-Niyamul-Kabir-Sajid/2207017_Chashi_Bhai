package com.sajid._207017_chashi_bhai.utils;

/**
 * SessionManager - Manages temporary session data during authentication
 */
public class SessionManager {
    private static String tempPhone;
    private static String tempName;
    private static String tempDistrict;
    private static String tempRole;
    private static String tempFirebaseUid;
    private static boolean isLoginMode;

    // Logged in user data
    private static Integer currentUserId;
    private static String currentUserName;
    private static String currentUserRole;
    private static String currentUserPhone;

    // Temp data methods (for signup/login flow)
    public static String getTempPhone() {
        return tempPhone;
    }

    public static void setTempPhone(String phone) {
        tempPhone = phone;
    }

    public static String getTempName() {
        return tempName;
    }

    public static void setTempName(String name) {
        tempName = name;
    }

    public static String getTempDistrict() {
        return tempDistrict;
    }

    public static void setTempDistrict(String district) {
        tempDistrict = district;
    }

    public static String getTempRole() {
        return tempRole;
    }

    public static void setTempRole(String role) {
        tempRole = role;
    }

    public static String getTempFirebaseUid() {
        return tempFirebaseUid;
    }

    public static void setTempFirebaseUid(String firebaseUid) {
        tempFirebaseUid = firebaseUid;
    }

    public static boolean isLoginMode() {
        return isLoginMode;
    }

    public static void setLoginMode(boolean loginMode) {
        isLoginMode = loginMode;
    }

    // Current user methods
    public static Integer getCurrentUserId() {
        return currentUserId;
    }

    public static void setCurrentUserId(Integer userId) {
        currentUserId = userId;
    }

    public static String getCurrentUserName() {
        return currentUserName;
    }

    public static void setCurrentUserName(String userName) {
        currentUserName = userName;
    }

    public static String getCurrentUserRole() {
        return currentUserRole;
    }

    public static void setCurrentUserRole(String userRole) {
        currentUserRole = userRole;
    }

    public static String getCurrentUserPhone() {
        return currentUserPhone;
    }

    public static void setCurrentUserPhone(String userPhone) {
        currentUserPhone = userPhone;
    }

    public static boolean isLoggedIn() {
        return currentUserId != null;
    }

    // Clear all session data
    public static void clear() {
        tempPhone = null;
        tempName = null;
        tempDistrict = null;
        tempRole = null;
        tempFirebaseUid = null;
        isLoginMode = false;
        currentUserId = null;
        currentUserName = null;
        currentUserRole = null;
        currentUserPhone = null;
    }

    // Clear only temp data
    public static void clearTempData() {
        tempPhone = null;
        tempName = null;
        tempDistrict = null;
        tempRole = null;
        tempFirebaseUid = null;
        isLoginMode = false;
    }

    // Logout
    public static void logout() {
        clear();
    }
}
