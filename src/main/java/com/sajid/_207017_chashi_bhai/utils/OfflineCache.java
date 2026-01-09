package com.sajid._207017_chashi_bhai.utils;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;

/**
 * OfflineCache - Local caching for offline support in poor connectivity areas
 * Features:
 * - File-based persistent cache
 * - In-memory cache for fast access
 * - Compression for large data
 * - TTL (Time-to-Live) support
 * - Cache invalidation
 */
public class OfflineCache {

    private static OfflineCache instance;
    private final ConcurrentHashMap<String, CacheEntry> memoryCache;
    private final Path cacheDir;
    
    // Default TTL: 1 hour
    private static final long DEFAULT_TTL_MS = 60 * 60 * 1000;
    // Max memory cache entries
    private static final int MAX_MEMORY_ENTRIES = 100;

    private OfflineCache() {
        memoryCache = new ConcurrentHashMap<>();
        cacheDir = Paths.get("data", "cache");
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            System.err.println("Failed to create cache directory: " + e.getMessage());
        }
    }

    public static synchronized OfflineCache getInstance() {
        if (instance == null) {
            instance = new OfflineCache();
        }
        return instance;
    }

    /**
     * Store data in cache (memory + disk)
     */
    public void put(String key, String data) {
        put(key, data, DEFAULT_TTL_MS);
    }

    /**
     * Store data with custom TTL
     */
    public void put(String key, String data, long ttlMs) {
        long expiry = System.currentTimeMillis() + ttlMs;
        CacheEntry entry = new CacheEntry(data, expiry);
        
        // Memory cache (with size limit)
        if (memoryCache.size() >= MAX_MEMORY_ENTRIES) {
            // Remove oldest entry
            String oldestKey = memoryCache.entrySet().stream()
                .min((a, b) -> Long.compare(a.getValue().expiry, b.getValue().expiry))
                .map(e -> e.getKey())
                .orElse(null);
            if (oldestKey != null) {
                memoryCache.remove(oldestKey);
            }
        }
        memoryCache.put(key, entry);
        
        // Disk cache (async)
        saveToDisk(key, entry);
    }

    /**
     * Get data from cache
     * @return cached data or null if not found/expired
     */
    public String get(String key) {
        // Try memory cache first
        CacheEntry entry = memoryCache.get(key);
        if (entry != null) {
            if (entry.isExpired()) {
                memoryCache.remove(key);
                deleteDiskCache(key);
                return null;
            }
            return entry.data;
        }
        
        // Try disk cache
        entry = loadFromDisk(key);
        if (entry != null) {
            if (entry.isExpired()) {
                deleteDiskCache(key);
                return null;
            }
            // Load back into memory
            memoryCache.put(key, entry);
            return entry.data;
        }
        
        return null;
    }

    /**
     * Check if cached data exists and is not expired
     */
    public boolean has(String key) {
        return get(key) != null;
    }

    /**
     * Get cached data even if expired (for offline fallback)
     */
    public CachedResult getWithMetadata(String key) {
        CacheEntry entry = memoryCache.get(key);
        if (entry == null) {
            entry = loadFromDisk(key);
        }
        
        if (entry != null) {
            boolean isStale = entry.isExpired();
            long age = System.currentTimeMillis() - (entry.expiry - DEFAULT_TTL_MS);
            return new CachedResult(entry.data, isStale, age);
        }
        return null;
    }

    /**
     * Remove from cache
     */
    public void remove(String key) {
        memoryCache.remove(key);
        deleteDiskCache(key);
    }

    /**
     * Clear all cache
     */
    public void clear() {
        memoryCache.clear();
        try {
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to clear disk cache: " + e.getMessage());
        }
    }

    /**
     * Clear expired entries only
     */
    public void clearExpired() {
        // Memory
        memoryCache.entrySet().removeIf(e -> e.getValue().isExpired());
        
        // Disk
        try {
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        CacheEntry entry = loadFromDiskPath(path);
                        if (entry != null && entry.isExpired()) {
                            Files.delete(path);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                });
        } catch (IOException e) {
            // Ignore
        }
    }

    // ==========================================
    // Private methods
    // ==========================================

    private void saveToDisk(String key, CacheEntry entry) {
        try {
            Path file = getCacheFile(key);
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(file)))) {
                oos.writeObject(entry);
            }
        } catch (IOException e) {
            System.err.println("Failed to save cache to disk: " + e.getMessage());
        }
    }

    private CacheEntry loadFromDisk(String key) {
        return loadFromDiskPath(getCacheFile(key));
    }

    private CacheEntry loadFromDiskPath(Path path) {
        if (!Files.exists(path)) return null;
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new GZIPInputStream(Files.newInputStream(path)))) {
            return (CacheEntry) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            // Corrupted cache, delete it
            try {
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                // Ignore
            }
            return null;
        }
    }

    private void deleteDiskCache(String key) {
        try {
            Files.deleteIfExists(getCacheFile(key));
        } catch (IOException e) {
            // Ignore
        }
    }

    private Path getCacheFile(String key) {
        // Sanitize key for filename
        String filename = key.replaceAll("[^a-zA-Z0-9_-]", "_") + ".cache";
        return cacheDir.resolve(filename);
    }

    // ==========================================
    // Inner classes
    // ==========================================

    /**
     * Internal cache entry
     */
    private static class CacheEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        final String data;
        final long expiry;

        CacheEntry(String data, long expiry) {
            this.data = data;
            this.expiry = expiry;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiry;
        }
    }

    /**
     * Result with metadata
     */
    public static class CachedResult {
        public final String data;
        public final boolean isStale;
        public final long ageMs;

        CachedResult(String data, boolean isStale, long ageMs) {
            this.data = data;
            this.isStale = isStale;
            this.ageMs = ageMs;
        }

        public String getAgeFormatted() {
            long seconds = ageMs / 1000;
            if (seconds < 60) return seconds + " সেকেন্ড আগে";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + " মিনিট আগে";
            long hours = minutes / 60;
            if (hours < 24) return hours + " ঘণ্টা আগে";
            long days = hours / 24;
            return days + " দিন আগে";
        }
    }

    // ==========================================
    // Cache key builders for common data types
    // ==========================================

    public static String profileKey(int userId) {
        return "profile_" + userId;
    }

    public static String ordersKey(int userId) {
        return "orders_" + userId;
    }

    public static String cropKey(int cropId) {
        return "crop_" + cropId;
    }

    public static String cropFeedKey() {
        return "crop_feed";
    }

    public static String chatKey(int userId1, int userId2) {
        int min = Math.min(userId1, userId2);
        int max = Math.max(userId1, userId2);
        return "chat_" + min + "_" + max;
    }
}
