package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.File;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/*
 * ChatListController - Displays list of conversations for current user
 */
public class ChatListController {

    @FXML private Button btnBack;
    @FXML private Button btnRefresh;
    @FXML private TextField txtSearch;
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterUnread;
    @FXML private Button btnFilterFarmers;
    @FXML private Button btnFilterBuyers;
    @FXML private VBox vboxChatList;
    @FXML private VBox vboxEmptyState;
    @FXML private VBox vboxLoading;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;
    private String currentFilter = "all";
    private final List<ConversationItem> loadedConversations = new ArrayList<>();

    private static class ConversationItem {
        int id;
        int otherUserId;
        String otherUserName;
        String otherUserRole;
        boolean otherUserVerified;
        String otherUserPhone;
        Integer cropId;
        String cropName;
        String lastMessage;
        String lastMessageTime;
        int unreadCount;
        boolean isOnline;
    }

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        if (currentUser == null) {
            showError("Error", "User not logged in");
            return;
        }

        setupEventHandlers();
        loadConversations();
    }

    private void setupEventHandlers() {
        btnBack.setOnAction(e -> onBack());
        btnRefresh.setOnAction(e -> onRefresh());
        txtSearch.textProperty().addListener((obs, old, newVal) -> filterConversations());
        
        btnFilterAll.setOnAction(e -> setFilter("all"));
        btnFilterUnread.setOnAction(e -> setFilter("unread"));
        btnFilterFarmers.setOnAction(e -> setFilter("farmers"));
        btnFilterBuyers.setOnAction(e -> setFilter("buyers"));
    }

    private void loadConversations() {
        showLoading(true);
        loadedConversations.clear();

        String sql = "SELECT c.*, " +
                    "CASE WHEN c.user1_id = ? THEN c.user2_id ELSE c.user1_id END as other_user_id, " +
                    "CASE WHEN c.user1_id = ? THEN u2.name ELSE u1.name END as other_user_name, " +
                    "CASE WHEN c.user1_id = ? THEN u2.role ELSE u1.role END as other_user_role, " +
                    "CASE WHEN c.user1_id = ? THEN u2.phone ELSE u1.phone END as other_user_phone, " +
                    "CASE WHEN c.user1_id = ? THEN u2.is_verified ELSE u1.is_verified END as other_user_verified, " +
                    "CASE WHEN c.user1_id = ? THEN c.unread_count_user1 ELSE c.unread_count_user2 END as unread, " +
                    "cr.name as crop_name " +
                    "FROM conversations c " +
                    "LEFT JOIN users u1 ON c.user1_id = u1.id " +
                    "LEFT JOIN users u2 ON c.user2_id = u2.id " +
                    "LEFT JOIN crops cr ON c.crop_id = cr.id " +
                    "WHERE c.user1_id = ? OR c.user2_id = ? " +
                    "ORDER BY c.last_message_time DESC";

        Object[] params = {currentUser.getId(), currentUser.getId(), currentUser.getId(), 
                          currentUser.getId(), currentUser.getId(), currentUser.getId(),
                          currentUser.getId(), currentUser.getId()};

        DatabaseService.executeQueryAsync(sql, params, 
            rs -> {
                try {
                    while (rs.next()) {
                        ConversationItem item = mapConversation(rs);
                        loadedConversations.add(item);
                    }
                    Platform.runLater(() -> {
                        displayConversations();
                        showLoading(false);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        showError("Error", "Failed to load conversations");
                        showLoading(false);
                    });
                }
            },
            err -> {
                Platform.runLater(() -> {
                    showError("Error", "Database error: " + err.getMessage());
                    showLoading(false);
                });
            }
        );
    }

    private ConversationItem mapConversation(ResultSet rs) throws Exception {
        ConversationItem item = new ConversationItem();
        item.id = rs.getInt("id");
        item.otherUserId = rs.getInt("other_user_id");
        item.otherUserName = rs.getString("other_user_name");
        item.otherUserRole = rs.getString("other_user_role");
        item.otherUserVerified = rs.getBoolean("other_user_verified");
        item.otherUserPhone = rs.getString("other_user_phone");
        item.cropId = rs.getObject("crop_id") != null ? rs.getInt("crop_id") : null;
        item.cropName = rs.getString("crop_name");
        item.lastMessage = rs.getString("last_message");
        
        String timestamp = rs.getString("last_message_time");
        item.lastMessageTime = timestamp != null ? formatTime(timestamp) : "";
        
        item.unreadCount = rs.getInt("unread");
        item.isOnline = false; // Will be updated from Firebase
        
        return item;
    }

    private void displayConversations() {
        vboxChatList.getChildren().clear();
        
        List<ConversationItem> filtered = getFilteredConversations();
        
        if (filtered.isEmpty()) {
            vboxEmptyState.setVisible(true);
            vboxChatList.setVisible(false);
        } else {
            vboxEmptyState.setVisible(false);
            vboxChatList.setVisible(true);
            
            for (ConversationItem conv : filtered) {
                HBox chatItem = buildChatItem(conv);
                vboxChatList.getChildren().add(chatItem);
            }
        }
    }

    private HBox buildChatItem(ConversationItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/sajid/_207017_chashi_bhai/item-chat-list.fxml"));
            HBox row = loader.load();

            ChatListItemController controller = loader.getController();
            controller.setData(
                    item.otherUserName,
                    item.otherUserRole,
                    item.otherUserVerified,
                    item.otherUserId,
                    item.otherUserPhone,
                    item.lastMessage,
                    item.lastMessageTime,
                    item.cropId,
                    item.cropName,
                    item.unreadCount,
                    item.isOnline,
                    null
            );
            controller.setOnClick(() -> openConversation(item));

            return row;
        } catch (Exception e) {
            // Fallback: if FXML fails, keep old behavior minimal
            HBox container = new HBox(15);
            container.getStyleClass().add("chat-list-item");
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(12, 20, 12, 20));
            container.setCursor(javafx.scene.Cursor.HAND);

            Label lblName = new Label(item.otherUserName + " (ID: " + item.otherUserId + ")");
            lblName.getStyleClass().add("chat-user-name");
            container.getChildren().add(lblName);

            container.setOnMouseClicked(ev -> openConversation(item));
            return container;
        }
    }

    private void openConversation(ConversationItem item) {
        try {
            App.showView("chat-conversation-view.fxml", controller -> {
                if (controller instanceof ChatConversationController) {
                    ChatConversationController chatController = (ChatConversationController) controller;
                    chatController.loadConversation(item.id, item.otherUserId, 
                        item.otherUserName, item.cropId);
                }
            });
        } catch (Exception e) {
            showError("Error", "Failed to open conversation");
            e.printStackTrace();
        }
    }

    private List<ConversationItem> getFilteredConversations() {
        String search = txtSearch.getText().toLowerCase().trim();
        
        return loadedConversations.stream()
            .filter(item -> {
                // Apply search filter
                if (!search.isEmpty()) {
                    boolean matchName = item.otherUserName.toLowerCase().contains(search);
                    boolean matchMessage = item.lastMessage != null && 
                                          item.lastMessage.toLowerCase().contains(search);
                    boolean matchCrop = item.cropName != null && 
                                       item.cropName.toLowerCase().contains(search);
                    if (!matchName && !matchMessage && !matchCrop) return false;
                }
                
                // Apply type filter
                switch (currentFilter) {
                    case "unread":
                        return item.unreadCount > 0;
                    case "farmers":
                        return item.otherUserRole.equals("farmer");
                    case "buyers":
                        return item.otherUserRole.equals("buyer");
                    default:
                        return true;
                }
            })
            .toList();
    }

    private void filterConversations() {
        displayConversations();
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        
        // Update button styles
        btnFilterAll.getStyleClass().remove("filter-active");
        btnFilterUnread.getStyleClass().remove("filter-active");
        btnFilterFarmers.getStyleClass().remove("filter-active");
        btnFilterBuyers.getStyleClass().remove("filter-active");
        
        switch (filter) {
            case "all" -> btnFilterAll.getStyleClass().add("filter-active");
            case "unread" -> btnFilterUnread.getStyleClass().add("filter-active");
            case "farmers" -> btnFilterFarmers.getStyleClass().add("filter-active");
            case "buyers" -> btnFilterBuyers.getStyleClass().add("filter-active");
        }
        
        displayConversations();
    }

    @FXML
    private void onBack() {
        try {
            String role = currentUser.getRole();
            if ("farmer".equals(role)) {
                App.loadScene("farmer-dashboard-view.fxml", "Dashboard");
            } else {
                App.loadScene("buyer-dashboard-view.fxml", "Dashboard");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onRefresh() {
        loadConversations();
    }

    @FXML
    private void onFilterAll() {
        setFilter("all");
    }

    @FXML
    private void onFilterUnread() {
        setFilter("unread");
    }

    @FXML
    private void onFilterFarmers() {
        setFilter("farmers");
    }

    @FXML
    private void onFilterBuyers() {
        setFilter("buyers");
    }

    @FXML
    private void onSearchKeyUp() {
        filterConversations();
    }

    private void showLoading(boolean show) {
        vboxLoading.setVisible(show);
        vboxChatList.setVisible(!show);
        vboxEmptyState.setVisible(false);
    }

    private String formatTime(String timestamp) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timestamp.replace(" ", "T"));
            LocalDateTime now = LocalDateTime.now();
            
            if (dateTime.toLocalDate().equals(now.toLocalDate())) {
                return dateTime.format(DateTimeFormatter.ofPattern("hh:mm a"));
            } else if (dateTime.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
                return "গতকাল";
            } else {
                return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
            return timestamp;
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
