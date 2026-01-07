package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.FirebaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ChatConversationController - Displays and manages a single chat conversation
 */
public class ChatConversationController {

    @FXML private Button btnBack;
    @FXML private ImageView userAvatar;
    @FXML private Label lblUserName;
    @FXML private Label lblUserStatus;
    @FXML private Button btnCall;
    @FXML private Button btnVideoCall;
    @FXML private Button btnInfo;
    @FXML private HBox hboxCropContext;
    @FXML private ImageView cropImage;
    @FXML private Label lblCropName;
    @FXML private Label lblCropPrice;
    @FXML private Button btnViewCrop;
    @FXML private ScrollPane scrollPane;
    @FXML private VBox vboxMessages;
    @FXML private HBox hboxTypingIndicator;
    @FXML private Label lblTypingUser;
    @FXML private Button btnAttach;
    @FXML private Button btnPhoto;
    @FXML private TextArea txtMessage;
    @FXML private Button btnEmoji;
    @FXML private Button btnSend;

    private User currentUser;
    private int conversationId;
    private int otherUserId;
    private String otherUserName;
    private Integer cropId;
    private final List<MessageItem> loadedMessages = new ArrayList<>();

    private static class MessageItem {
        int id;
        int senderId;
        int receiverId;
        String messageText;
        String messageType;
        String attachmentPath;
        boolean isRead;
        String createdAt;
        boolean isSent; // true if sent by current user
    }

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        if (currentUser == null) {
            showError("Error", "User not logged in");
            return;
        }

        setupEventHandlers();
        
        // Auto-scroll to bottom when new messages arrive
        vboxMessages.heightProperty().addListener((obs, old, newVal) -> {
            scrollPane.setVvalue(1.0);
        });
    }

    public void loadConversation(int convId, int otherUser, String userName, Integer crop) {
        this.conversationId = convId;
        this.otherUserId = otherUser;
        this.otherUserName = userName;
        this.cropId = crop;
        
        // Update header
        lblUserName.setText(userName);
        lblUserStatus.setText("অফলাইন");
        
        // Load user details
        loadOtherUserDetails();
        
        // Load crop context if available
        if (cropId != null) {
            loadCropContext();
        } else {
            hboxCropContext.setVisible(false);
        }
        
        // Load messages
        loadMessages();
        
        // Mark messages as read
        markMessagesAsRead();
        
        // Setup Firebase listeners
        if (FirebaseService.isInitialized()) {
            setupRealtimeListeners();
        }
    }

    private void setupEventHandlers() {
        btnBack.setOnAction(e -> onBack());
        btnCall.setOnAction(e -> onCall());
        btnVideoCall.setOnAction(e -> onVideoCall());
        btnInfo.setOnAction(e -> onInfo());
        btnViewCrop.setOnAction(e -> onViewCrop());
        btnAttach.setOnAction(e -> onAttach());
        btnPhoto.setOnAction(e -> onPhoto());
        btnEmoji.setOnAction(e -> onEmoji());
        btnSend.setOnAction(e -> onSendMessage());
        
        txtMessage.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                onSendMessage();
            }
        });
    }

    private void loadOtherUserDetails() {
        String sql = "SELECT * FROM users WHERE id = ?";
        DatabaseService.executeQueryAsync(sql, new Object[]{otherUserId},
            rs -> {
                try {
                    if (rs.next()) {
                        String role = rs.getString("role");
                        boolean verified = rs.getBoolean("is_verified");
                        
                        Platform.runLater(() -> {
                            String roleText = role.equals("farmer") ? "কৃষক" : "ক্রেতা";
                            if (verified) roleText += " ✓";
                            lblUserStatus.setText(roleText);
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            err -> err.printStackTrace()
        );
        
        // Check online status from Firebase
        if (FirebaseService.isInitialized()) {
            FirebaseService.listenToUserStatus(String.valueOf(otherUserId), online -> {
                Platform.runLater(() -> {
                    if (online) {
                        lblUserStatus.setText("অনলাইন");
                        lblUserStatus.setStyle("-fx-text-fill: #4CAF50;");
                    }
                });
            });
        }
    }

    private void loadCropContext() {
        String sql = "SELECT * FROM crops WHERE id = ?";
        DatabaseService.executeQueryAsync(sql, new Object[]{cropId},
            rs -> {
                try {
                    if (rs.next()) {
                        String name = rs.getString("name");
                        double price = rs.getDouble("price_per_kg");
                        
                        Platform.runLater(() -> {
                            hboxCropContext.setVisible(true);
                            lblCropName.setText(name);
                            lblCropPrice.setText("৳" + price + " / কেজি");
                        });
                        
                        // Load crop photo
                        String photoSql = "SELECT photo_path FROM crop_photos WHERE crop_id = ? ORDER BY photo_order LIMIT 1";
                        DatabaseService.executeQueryAsync(photoSql, new Object[]{cropId},
                            photoRs -> {
                                try {
                                    if (photoRs.next()) {
                                        String photoPath = photoRs.getString("photo_path");
                                        Platform.runLater(() -> {
                                            File imgFile = new File(photoPath);
                                            if (imgFile.exists()) {
                                                cropImage.setImage(new Image(imgFile.toURI().toString()));
                                            }
                                        });
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            },
                            err -> err.printStackTrace()
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            err -> err.printStackTrace()
        );
    }

    private void loadMessages() {
        loadedMessages.clear();
        
        String sql = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC";
        DatabaseService.executeQueryAsync(sql, new Object[]{conversationId},
            rs -> {
                try {
                    while (rs.next()) {
                        MessageItem item = mapMessage(rs);
                        loadedMessages.add(item);
                    }
                    Platform.runLater(this::displayMessages);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            err -> {
                Platform.runLater(() -> showError("Error", "Failed to load messages"));
                err.printStackTrace();
            }
        );
    }

    private MessageItem mapMessage(ResultSet rs) throws Exception {
        MessageItem item = new MessageItem();
        item.id = rs.getInt("id");
        item.senderId = rs.getInt("sender_id");
        item.receiverId = rs.getInt("receiver_id");
        item.messageText = rs.getString("message_text");
        item.messageType = rs.getString("message_type");
        item.attachmentPath = rs.getString("attachment_path");
        item.isRead = rs.getBoolean("is_read");
        item.createdAt = rs.getString("created_at");
        item.isSent = (item.senderId == currentUser.getId());
        return item;
    }

    private void displayMessages() {
        vboxMessages.getChildren().clear();
        
        String currentDate = "";
        
        for (MessageItem msg : loadedMessages) {
            // Add date separator if date changed
            String msgDate = getDateFromTimestamp(msg.createdAt);
            if (!msgDate.equals(currentDate)) {
                currentDate = msgDate;
                vboxMessages.getChildren().add(createDateSeparator(msgDate));
            }
            
            // Add message bubble
            HBox messageBubble = createMessageBubble(msg);
            vboxMessages.getChildren().add(messageBubble);
        }
        
        // Scroll to bottom
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private HBox createDateSeparator(String date) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(10, 0, 10, 0));
        
        Label lblDate = new Label(date);
        lblDate.getStyleClass().add("date-separator");
        container.getChildren().add(lblDate);
        
        return container;
    }

    private HBox createMessageBubble(MessageItem msg) {
        HBox container = new HBox(10);
        container.setPadding(new Insets(5, 0, 5, 0));
        
        if (msg.isSent) {
            // Sent message - align right
            container.setAlignment(Pos.CENTER_RIGHT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            container.getChildren().add(spacer);
        } else {
            // Received message - align left
            container.setAlignment(Pos.CENTER_LEFT);
        }
        
        VBox bubble = new VBox(5);
        bubble.getStyleClass().add("message-bubble");
        if (msg.isSent) {
            bubble.getStyleClass().add("message-sent");
        } else {
            bubble.getStyleClass().add("message-received");
        }
        bubble.setPadding(new Insets(10, 15, 10, 15));
        bubble.setMaxWidth(600);
        
        // Message text
        Label lblText = new Label(msg.messageText);
        lblText.getStyleClass().add("message-text");
        lblText.setWrapText(true);
        bubble.getChildren().add(lblText);
        
        // Image attachment
        if ("image".equals(msg.messageType) && msg.attachmentPath != null) {
            ImageView imgView = new ImageView();
            imgView.setFitWidth(300);
            imgView.setFitHeight(200);
            imgView.setPreserveRatio(true);
            imgView.getStyleClass().add("message-image");
            
            File imgFile = new File(msg.attachmentPath);
            if (imgFile.exists()) {
                imgView.setImage(new Image(imgFile.toURI().toString()));
            }
            bubble.getChildren().add(imgView);
        }
        
        // Time and status
        HBox timeBox = new HBox(8);
        timeBox.setAlignment(Pos.BASELINE_RIGHT);
        
        String time = formatMessageTime(msg.createdAt);
        Label lblTime = new Label(time);
        lblTime.getStyleClass().add("message-time");
        timeBox.getChildren().add(lblTime);
        
        // Status for sent messages
        if (msg.isSent) {
            Label lblStatus = new Label(msg.isRead ? "✓✓" : "✓");
            lblStatus.getStyleClass().add("message-status");
            if (msg.isRead) {
                lblStatus.setStyle("-fx-text-fill: #4CAF50;");
            }
            timeBox.getChildren().add(lblStatus);
        }
        
        bubble.getChildren().add(timeBox);
        container.getChildren().add(bubble);
        
        return container;
    }

    @FXML
    private void onSendMessage() {
        String text = txtMessage.getText().trim();
        if (text.isEmpty()) return;
        
        // Insert into database
        String sql = "INSERT INTO messages (conversation_id, sender_id, receiver_id, message_text, message_type) " +
                    "VALUES (?, ?, ?, ?, 'text')";
        Object[] params = {conversationId, currentUser.getId(), otherUserId, text};
        
        DatabaseService.executeUpdateAsync(sql, params,
            rows -> {
                Platform.runLater(() -> {
                    txtMessage.clear();
                    loadMessages(); // Reload to show new message
                });
                
                // Send to Firebase for real-time delivery
                if (FirebaseService.isInitialized()) {
                    FirebaseService.sendMessage(
                        String.valueOf(conversationId),
                        String.valueOf(currentUser.getId()),
                        String.valueOf(otherUserId),
                        text,
                        "text",
                        messageId -> {
                            // Message sent successfully
                        },
                        error -> {
                            System.err.println("Firebase send error: " + error.getMessage());
                        }
                    );
                }
            },
            err -> {
                Platform.runLater(() -> showError("Error", "Failed to send message"));
                err.printStackTrace();
            }
        );
    }

    private void markMessagesAsRead() {
        String sql = "UPDATE messages SET is_read = 1, read_at = CURRENT_TIMESTAMP " +
                    "WHERE conversation_id = ? AND receiver_id = ? AND is_read = 0";
        Object[] params = {conversationId, currentUser.getId()};
        
        DatabaseService.executeUpdateAsync(sql, params,
            rows -> {
                // Update unread count in conversations
                String updateConv = "UPDATE conversations SET " +
                                  (currentUser.getId() == conversationId ? 
                                   "unread_count_user1 = 0" : "unread_count_user2 = 0") +
                                  " WHERE id = ?";
                DatabaseService.executeUpdateAsync(updateConv, new Object[]{conversationId},
                    r -> {}, e -> {});
            },
            err -> err.printStackTrace()
        );
    }

    private void setupRealtimeListeners() {
        // Listen for new messages
        FirebaseService.listenToMessages(String.valueOf(conversationId), snapshot -> {
            Platform.runLater(() -> {
                loadMessages(); // Reload messages when new message arrives
            });
        });
        
        // Listen for typing status
        // (Implementation optional - requires additional Firebase structure)
    }

    @FXML
    private void onBack() {
        try {
            App.showView("chat-list-view.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onCall() {
        // Get phone number and open dialer
        String sql = "SELECT phone FROM users WHERE id = ?";
        DatabaseService.executeQueryAsync(sql, new Object[]{otherUserId},
            rs -> {
                try {
                    if (rs.next()) {
                        String phone = rs.getString("phone");
                        Platform.runLater(() -> {
                            try {
                                String cmd = "cmd /c start tel:" + phone;
                                Runtime.getRuntime().exec(cmd);
                            } catch (Exception e) {
                                showError("Error", "Could not open phone dialer");
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            err -> err.printStackTrace()
        );
    }

    @FXML
    private void onVideoCall() {
        showInfo("Coming Soon", "Video call feature will be available soon!");
    }

    @FXML
    private void onInfo() {
        // Show user profile info
        showInfo("User Info", "Name: " + otherUserName + "\nUser ID: " + otherUserId);
    }

    @FXML
    private void onViewCrop() {
        if (cropId != null) {
            try {
                App.showView("crop-detail-view.fxml", controller -> {
                    // Pass crop ID to detail view
                    // ((CropDetailController) controller).loadCrop(cropId);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void onAttach() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File");
        File file = fileChooser.showOpenDialog(btnAttach.getScene().getWindow());
        
        if (file != null) {
            sendFileMessage(file);
        }
    }

    @FXML
    private void onPhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Photo");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File file = fileChooser.showOpenDialog(btnPhoto.getScene().getWindow());
        
        if (file != null) {
            sendImageMessage(file);
        }
    }

    private void sendImageMessage(File imageFile) {
        String sql = "INSERT INTO messages (conversation_id, sender_id, receiver_id, message_text, message_type, attachment_path) " +
                    "VALUES (?, ?, ?, ?, 'image', ?)";
        Object[] params = {conversationId, currentUser.getId(), otherUserId, 
                          "[Image]", imageFile.getAbsolutePath()};
        
        DatabaseService.executeUpdateAsync(sql, params,
            rows -> Platform.runLater(this::loadMessages),
            err -> Platform.runLater(() -> showError("Error", "Failed to send image"))
        );
    }

    private void sendFileMessage(File file) {
        String sql = "INSERT INTO messages (conversation_id, sender_id, receiver_id, message_text, message_type, attachment_path) " +
                    "VALUES (?, ?, ?, ?, 'file', ?)";
        Object[] params = {conversationId, currentUser.getId(), otherUserId, 
                          "[File: " + file.getName() + "]", file.getAbsolutePath()};
        
        DatabaseService.executeUpdateAsync(sql, params,
            rows -> Platform.runLater(this::loadMessages),
            err -> Platform.runLater(() -> showError("Error", "Failed to send file"))
        );
    }

    @FXML
    private void onEmoji() {
        // Simple emoji picker - can be enhanced
        String[] emojis = {"😊", "👍", "❤️", "😂", "🙏", "👏", "🎉", "🌾", "🍅", "🥔"};
        
        ChoiceDialog<String> dialog = new ChoiceDialog<>(emojis[0], emojis);
        dialog.setTitle("Select Emoji");
        dialog.setHeaderText("Choose an emoji");
        dialog.showAndWait().ifPresent(emoji -> {
            txtMessage.appendText(emoji);
        });
    }

    @FXML
    private void onMessageKeyPressed(javafx.scene.input.KeyEvent event) {
        if (event.getCode() == javafx.scene.input.KeyCode.ENTER && !event.isShiftDown()) {
            event.consume();
            onSendMessage();
        }
    }

    private String getDateFromTimestamp(String timestamp) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timestamp.replace(" ", "T"));
            LocalDateTime now = LocalDateTime.now();
            
            if (dateTime.toLocalDate().equals(now.toLocalDate())) {
                return "আজ";
            } else if (dateTime.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
                return "গতকাল";
            } else {
                return dateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            }
        } catch (Exception e) {
            return timestamp;
        }
    }

    private String formatMessageTime(String timestamp) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timestamp.replace(" ", "T"));
            return dateTime.format(DateTimeFormatter.ofPattern("hh:mm a"));
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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
