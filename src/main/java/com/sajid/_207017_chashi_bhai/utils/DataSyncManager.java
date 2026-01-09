package com.sajid._207017_chashi_bhai.utils;

import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;

import java.sql.ResultSet;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * DataSyncManager - Handles real-time sync, caching, and offline support
 * Features:
 * - Auto-refresh polling for profiles, orders, and crops
 * - Retry logic for failed database queries
 * - Offline detection and graceful degradation
 * - Data cache with staleness indicators
 */
public class DataSyncManager {

    private static DataSyncManager instance;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, CachedData<?>> cache;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pollingTasks;
    
    // Polling intervals (in seconds)
    private static final int PROFILE_POLL_INTERVAL = 30;
    private static final int ORDERS_POLL_INTERVAL = 15;
    private static final int CROPS_POLL_INTERVAL = 60;
    
    // Retry settings
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    
    // Connection status
    private volatile boolean isOnline = true;
    private volatile long lastSuccessfulQuery = System.currentTimeMillis();

    private DataSyncManager() {
        scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "DataSyncManager-Thread");
            t.setDaemon(true);
            return t;
        });
        cache = new ConcurrentHashMap<>();
        pollingTasks = new ConcurrentHashMap<>();
    }

    public static synchronized DataSyncManager getInstance() {
        if (instance == null) {
            instance = new DataSyncManager();
        }
        return instance;
    }

    /**
     * Execute a query with retry logic and caching
     */
    public void executeWithRetry(String cacheKey, String sql, Object[] params,
                                  Consumer<ResultSet> onSuccess,
                                  Consumer<Exception> onError,
                                  int maxRetries) {
        executeWithRetryInternal(cacheKey, sql, params, onSuccess, onError, maxRetries, 0);
    }

    private void executeWithRetryInternal(String cacheKey, String sql, Object[] params,
                                           Consumer<ResultSet> onSuccess,
                                           Consumer<Exception> onError,
                                           int maxRetries, int currentAttempt) {
        
        DatabaseService.executeQueryAsync(sql, params,
            rs -> {
                isOnline = true;
                lastSuccessfulQuery = System.currentTimeMillis();
                
                // Cache the result indicator (not the ResultSet itself)
                cache.put(cacheKey, new CachedData<>(System.currentTimeMillis(), true));
                
                onSuccess.accept(rs);
            },
            error -> {
                if (currentAttempt < maxRetries) {
                    // Schedule retry
                    scheduler.schedule(() -> {
                        executeWithRetryInternal(cacheKey, sql, params, onSuccess, onError, 
                                                  maxRetries, currentAttempt + 1);
                    }, RETRY_DELAY_MS * (currentAttempt + 1), TimeUnit.MILLISECONDS);
                } else {
                    // Mark as offline after max retries
                    isOnline = false;
                    onError.accept(error);
                }
            }
        );
    }

    /**
     * Start polling for a specific data type
     */
    public void startPolling(String taskId, Runnable refreshTask, int intervalSeconds) {
        stopPolling(taskId); // Stop existing task if any
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                Platform.runLater(refreshTask);
            } catch (Exception e) {
                System.err.println("Polling task error for " + taskId + ": " + e.getMessage());
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        pollingTasks.put(taskId, future);
        System.out.println("[DataSync] Started polling for: " + taskId + " every " + intervalSeconds + "s");
    }

    /**
     * Stop polling for a specific task
     */
    public void stopPolling(String taskId) {
        ScheduledFuture<?> existingTask = pollingTasks.remove(taskId);
        if (existingTask != null) {
            existingTask.cancel(false);
            System.out.println("[DataSync] Stopped polling for: " + taskId);
        }
    }

    /**
     * Stop all polling tasks
     */
    public void stopAllPolling() {
        pollingTasks.forEach((id, task) -> task.cancel(false));
        pollingTasks.clear();
        System.out.println("[DataSync] Stopped all polling tasks");
    }

    /**
     * Start profile sync polling
     */
    public void startProfileSync(int userId, Runnable onRefresh) {
        startPolling("profile_" + userId, onRefresh, PROFILE_POLL_INTERVAL);
    }

    /**
     * Start orders sync polling
     */
    public void startOrdersSync(int userId, Runnable onRefresh) {
        startPolling("orders_" + userId, onRefresh, ORDERS_POLL_INTERVAL);
    }

    /**
     * Start crop feed sync polling
     */
    public void startCropFeedSync(Runnable onRefresh) {
        startPolling("crop_feed", onRefresh, CROPS_POLL_INTERVAL);
    }

    /**
     * Check if data is stale (older than threshold)
     */
    public boolean isDataStale(String cacheKey, long maxAgeMs) {
        CachedData<?> data = cache.get(cacheKey);
        if (data == null) return true;
        return (System.currentTimeMillis() - data.timestamp) > maxAgeMs;
    }

    /**
     * Get last update time for a cache key
     */
    public long getLastUpdateTime(String cacheKey) {
        CachedData<?> data = cache.get(cacheKey);
        return data != null ? data.timestamp : 0;
    }

    /**
     * Check online status
     */
    public boolean isOnline() {
        return isOnline;
    }

    /**
     * Get time since last successful query
     */
    public long getTimeSinceLastSuccess() {
        return System.currentTimeMillis() - lastSuccessfulQuery;
    }

    /**
     * Force mark as online (after successful manual refresh)
     */
    public void markOnline() {
        isOnline = true;
        lastSuccessfulQuery = System.currentTimeMillis();
    }

    /**
     * Shutdown the sync manager
     */
    public void shutdown() {
        stopAllPolling();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        cache.clear();
        System.out.println("[DataSync] Shutdown complete");
    }

    /**
     * Internal class for cached data
     */
    private static class CachedData<T> {
        final long timestamp;
        final T data;

        CachedData(long timestamp, T data) {
            this.timestamp = timestamp;
            this.data = data;
        }
    }

    // ===========================================
    // Convenience methods for common sync patterns
    // ===========================================

    /**
     * Get polling interval for profile data
     */
    public static int getProfilePollInterval() {
        return PROFILE_POLL_INTERVAL;
    }

    /**
     * Get polling interval for orders data
     */
    public static int getOrdersPollInterval() {
        return ORDERS_POLL_INTERVAL;
    }

    /**
     * Get polling interval for crops data
     */
    public static int getCropsPollInterval() {
        return CROPS_POLL_INTERVAL;
    }
}
