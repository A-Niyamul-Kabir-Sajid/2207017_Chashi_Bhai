package com.sajid._207017_chashi_bhai.utils;

import javafx.scene.image.Image;

import java.io.*;
import java.nio.file.Files;
import java.util.Base64;

/**
 * ImageBase64Util - Utility class for Base64 image handling
 * 
 * Provides methods to:
 * - Convert image files to Base64 strings
 * - Convert Base64 strings back to JavaFX Image objects
 * - Handle image compression for Firebase storage
 */
public class ImageBase64Util {

    /**
     * Convert an image file to Base64 string
     * 
     * @param file The image file to convert
     * @return Base64 encoded string of the image
     * @throws IOException if file cannot be read
     */
    public static String fileToBase64(File file) throws IOException {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(fileContent);
    }

    /**
     * Convert a file path to Base64 string
     * 
     * @param filePath Path to the image file
     * @return Base64 encoded string of the image
     * @throws IOException if file cannot be read
     */
    public static String pathToBase64(String filePath) throws IOException {
        return fileToBase64(new File(filePath));
    }

    /**
     * Convert Base64 string to JavaFX Image
     * 
     * @param base64String Base64 encoded image string
     * @return JavaFX Image object
     */
    public static Image base64ToImage(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return null;
        }
        
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64String);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
            return new Image(inputStream);
        } catch (Exception e) {
            System.err.println("Error converting Base64 to Image: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert Base64 string to byte array
     * 
     * @param base64String Base64 encoded string
     * @return Decoded byte array
     */
    public static byte[] base64ToBytes(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(base64String);
    }

    /**
     * Convert byte array to Base64 string
     * 
     * @param bytes Byte array to encode
     * @return Base64 encoded string
     */
    public static String bytesToBase64(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Save Base64 string to a file
     * 
     * @param base64String Base64 encoded image
     * @param outputPath Path where to save the file
     * @throws IOException if file cannot be written
     */
    public static void base64ToFile(String base64String, String outputPath) throws IOException {
        byte[] imageBytes = Base64.getDecoder().decode(base64String);
        Files.write(new File(outputPath).toPath(), imageBytes);
    }

    /**
     * Check if a string is valid Base64
     * 
     * @param str String to check
     * @return true if valid Base64
     */
    public static boolean isValidBase64(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get the approximate size of Base64 string in bytes
     * 
     * @param base64String Base64 encoded string
     * @return Size in bytes
     */
    public static long getBase64Size(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return 0;
        }
        // Base64 encoding increases size by ~33%
        return (long) (base64String.length() * 0.75);
    }

    /**
     * Get human-readable size string
     * 
     * @param bytes Size in bytes
     * @return Formatted size string (e.g., "1.5 MB")
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
