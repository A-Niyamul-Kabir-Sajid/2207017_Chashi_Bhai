package com.sajid._207017_chashi_bhai.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;

import java.io.File;
import java.util.function.Consumer;

/**
 * ChatListItemController - Controller for a single chat person row in chat list.
 */
public class ChatListItemController {

    @FXML private ImageView userAvatar;
    @FXML private Region onlineIndicator;

    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;
    @FXML private Label lblVerified;

    @FXML private Label lblUserId;
    @FXML private Label lblUserPhone;

    @FXML private Label lblLastMessage;
    @FXML private Label lblCropName;

    @FXML private Label lblTime;
    @FXML private javafx.scene.layout.StackPane unreadBadge;
    @FXML private Label lblUnreadCount;
    @FXML private Label lblStatus;

    private Consumer<Void> onClick;

    public void setOnClick(Runnable onClick) {
        this.onClick = v -> { onClick.run(); return; };
    }

    public void setData(
            String otherUserName,
            String otherUserRole,
            boolean otherUserVerified,
            int otherUserId,
            String otherUserPhone,
            String lastMessage,
            String lastMessageTime,
            Integer cropId,
            String cropName,
            int unreadCount,
            boolean isOnline,
            String avatarPath
    ) {
        if (lblUserName != null) lblUserName.setText(otherUserName != null ? otherUserName : "");
        if (lblUserRole != null) {
            String roleText = "farmer".equals(otherUserRole) ? "কৃষক" : "ক্রেতা";
            lblUserRole.setText(roleText);
        }
        if (lblVerified != null) lblVerified.setVisible(otherUserVerified);

        if (lblUserId != null) lblUserId.setText("ID: " + otherUserId);
        if (lblUserPhone != null) lblUserPhone.setText("📱 " + (otherUserPhone != null ? otherUserPhone : ""));

        if (lblLastMessage != null) {
            lblLastMessage.setText(lastMessage != null && !lastMessage.isBlank() ? lastMessage : "Start conversation...");
        }

        if (lblCropName != null) {
            if (cropName != null && !cropName.isBlank()) {
                lblCropName.setText("ফসল: " + cropName);
                lblCropName.setVisible(true);
                lblCropName.setManaged(true);
            } else {
                lblCropName.setVisible(false);
                lblCropName.setManaged(false);
            }
        }

        if (lblTime != null) lblTime.setText(lastMessageTime != null ? lastMessageTime : "");

        if (unreadBadge != null) {
            boolean showUnread = unreadCount > 0;
            unreadBadge.setVisible(showUnread);
            unreadBadge.setManaged(showUnread);
        }
        if (lblUnreadCount != null) lblUnreadCount.setText(String.valueOf(Math.max(unreadCount, 0)));

        if (onlineIndicator != null) {
            onlineIndicator.setVisible(isOnline);
            onlineIndicator.setManaged(isOnline);
        }

        if (lblStatus != null) {
            lblStatus.setVisible(false);
            lblStatus.setManaged(false);
        }

        // Avatar (optional)
        if (userAvatar != null) {
            try {
                if (avatarPath != null && !avatarPath.isBlank()) {
                    File f = new File(avatarPath);
                    if (f.exists()) {
                        userAvatar.setImage(new Image(f.toURI().toString()));
                        return;
                    }
                }
                userAvatar.setImage(new Image(getClass().getResourceAsStream("/image/default-avatar.png")));
            } catch (Exception ignored) {
            }
        }
    }

    @FXML
    private void onChatClick() {
        if (onClick != null) {
            onClick.accept(null);
        }
    }
}
