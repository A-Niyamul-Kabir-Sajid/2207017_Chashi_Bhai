package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
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
        HBox container = new HBox(15);
        container.getStyleClass().add("chat-list-item");
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(12, 20, 12, 20));
        container.setCursor(javafx.scene.Cursor.HAND);
        
        // Avatar with online indicator
        StackPane avatarStack = new StackPane();
        ImageView avatar = new ImageView();
        avatar.setFitWidth(55);
        avatar.setFitHeight(55);
        avatar.setPreserveRatio(true);
        avatar.getStyleClass().add("avatar-medium");
        
        // Default avatar
        try {
            avatar.setImage(new Image(getClass().getResourceAsStream("/image/default-avatar.png")));
        } catch (Exception e) {
            // Use placeholder if image not found
        }
        
        avatarStack.getChildren().add(avatar);
        
        // Online indicator
        if (item.isOnline) {
            Region onlineIndicator = new Region();
            onlineIndicator.setPrefSize(12, 12);
            onlineIndicator.getStyleClass().add("online-indicator");
            StackPane.setAlignment(onlineIndicator, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(onlineIndicator, new Insets(0, 2, 2, 0));
            avatarStack.getChildren().add(onlineIndicator);
        }
        
        // Chat info
        VBox infoBox = new VBox(4);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        
        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        
        Label lblName = new Label(item.otherUserName);
        lblName.getStyleClass().add("chat-user-name");
        nameRow.getChildren().add(lblName);
        
        Label lblRole = new Label(item.otherUserRole.equals("farmer") ? "কৃষক" : "ক্রেতা");
        lblRole.getStyleClass().add("chat-user-role");
        nameRow.getChildren().add(lblRole);
        
        if (item.otherUserVerified) {
            Label lblVerified = new Label("✓");
            lblVerified.getStyleClass().add("verified-badge");
            nameRow.getChildren().add(lblVerified);
        }
        
        infoBox.getChildren().add(nameRow);
        
        Label lblLastMessage = new Label(item.lastMessage != null ? item.lastMessage : "Start conversation...");
        lblLastMessage.getStyleClass().add("chat-last-message");
        lblLastMessage.setMaxWidth(400);
        lblLastMessage.setWrapText(false);
        infoBox.getChildren().add(lblLastMessage);
        
        if (item.cropName != null) {
            Label lblCrop = new Label("ফসল: " + item.cropName);
            lblCrop.getStyleClass().add("chat-crop-name");
            infoBox.getChildren().add(lblCrop);
        }
        
        // Right side - time and unread
        VBox rightBox = new VBox(8);
        rightBox.setAlignment(Pos.TOP_RIGHT);
        rightBox.setMinWidth(80);
        
        Label lblTime = new Label(item.lastMessageTime);
        lblTime.getStyleClass().add("chat-time");
        rightBox.getChildren().add(lblTime);
        
        if (item.unreadCount > 0) {
            StackPane badgeStack = new StackPane();
            badgeStack.getStyleClass().add("unread-badge");
            badgeStack.setMaxSize(24, 24);
            
            Label lblUnread = new Label(String.valueOf(item.unreadCount));
            lblUnread.getStyleClass().add("unread-count");
            badgeStack.getChildren().add(lblUnread);
            
            rightBox.getChildren().add(badgeStack);
        }
        
        container.getChildren().addAll(avatarStack, infoBox, rightBox);
        
        // Click handler
        container.setOnMouseClicked(e -> openConversation(item));
        
        return container;
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
                App.showView("farmer-dashboard-view.fxml");
            } else {
                App.showView("buyer-dashboard-view.fxml");
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
