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
import javafx.stage.FileChooser;

import java.io.File;
import java.sql.*;
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
    private String previousScene;

    private static final class ConversationResolution {
        final int conversationId;
        final int user1Id;
        final int user2Id;

        private ConversationResolution(int conversationId, int user1Id, int user2Id) {
            this.conversationId = conversationId;
            this.user1Id = user1Id;
            this.user2Id = user2Id;
        }
    }
    private int user1Id;
    private int user2Id;
    private final List<MessageItem> loadedMessages = new ArrayList<>();

    private static class MessageItem {
        @SuppressWarnings("unused")
        int id;
        int senderId;
        @SuppressWarnings("unused")
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
        this.cropId = (crop != null && crop > 0) ? crop : null;

        this.previousScene = App.getPreviousScene();

        validateAndLoadConversation();
    }

    private void validateAndLoadConversation() {
        if (currentUser == null) {
            Platform.runLater(() -> showError("Error", "User not logged in"));
            return;
        }

        if (otherUserId <= 0) {
            Platform.runLater(() -> {
                showError("Error", "Invalid chat user");
                onBack();
            });
            return;
        }

        // Self-chat is not allowed
        if (otherUserId == currentUser.getId()) {
            Platform.runLater(() -> {
                showInfo("Not Allowed", "You cannot chat with yourself.");
                onBack();
            });
            return;
        }

        // If no conversation ID provided, find existing or create a new one
        if (conversationId <= 0) {
            findOrCreateConversation();
            return;
        }

        String sql = "SELECT id, user1_id, user2_id, crop_id FROM conversations WHERE id = ?";
        DatabaseService.executeQueryAsync(
            sql,
            new Object[]{conversationId},
            rs -> {
                try {
                    if (!rs.next()) {
                        Platform.runLater(() -> {
                            showError("Error", "Conversation not found");
                            onBack();
                        });
                        return;
                    }

                    user1Id = rs.getInt("user1_id");
                    user2Id = rs.getInt("user2_id");

                    if (currentUser.getId() != user1Id && currentUser.getId() != user2Id) {
                        Platform.runLater(() -> {
                            showError("Error", "You are not a participant of this chat.");
                            onBack();
                        });
                        return;
                    }

                    if (cropId == null && rs.getObject("crop_id") != null) {
                        cropId = rs.getInt("crop_id");
                    }

                    // Derive other user from conversation participants to avoid mixing users
                    otherUserId = (currentUser.getId() == user1Id) ? user2Id : user1Id;

                    Platform.runLater(() -> {
                        lblUserName.setText(otherUserName != null && !otherUserName.isBlank() ? otherUserName : ("User " + otherUserId));
                        lblUserStatus.setText("অফলাইন");

                        loadOtherUserDetails();
                        if (cropId != null) {
                            loadCropContext();
                        } else {
                            hboxCropContext.setVisible(false);
                        }
                        loadMessages();
                        markMessagesAsRead();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        showError("Error", "Failed to open conversation.");
                        onBack();
                    });
                }
            },
            err -> Platform.runLater(() -> {
                showError("Database Error", "Could not load conversation.");
                onBack();
            })
        );
    }

    private void findOrCreateConversation() {
        final int currentUserId = currentUser.getId();
        final int u1 = Math.min(currentUserId, otherUserId);
        final int u2 = Math.max(currentUserId, otherUserId);
        final Integer crop = cropId;

        DatabaseService.executeTransactionAsync(conn -> {
            // 1) Try to find existing conversation
            try (PreparedStatement stmt = conn.prepareStatement(
                    crop != null
                            ? "SELECT id FROM conversations WHERE user1_id = ? AND user2_id = ? AND crop_id = ? LIMIT 1"
                            : "SELECT id FROM conversations WHERE user1_id = ? AND user2_id = ? AND crop_id IS NULL ORDER BY updated_at DESC LIMIT 1")) {
                stmt.setInt(1, u1);
                stmt.setInt(2, u2);
                if (crop != null) {
                    stmt.setInt(3, crop);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new ConversationResolution(rs.getInt("id"), u1, u2);
                    }
                }
            }

            // 2) Create new conversation
            String insertSql = "INSERT INTO conversations (user1_id, user2_id, crop_id, created_at, updated_at) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            try (PreparedStatement insert = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, u1);
                insert.setInt(2, u2);
                if (crop != null) {
                    insert.setInt(3, crop);
                } else {
                    insert.setNull(3, Types.INTEGER);
                }

                insert.executeUpdate();

                int newId = -1;
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (keys.next()) {
                        newId = keys.getInt(1);
                    }
                }
                if (newId <= 0) {
                    try (Statement st = conn.createStatement();
                         ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                        if (rs.next()) {
                            newId = rs.getInt(1);
                        }
                    }
                }

                return new ConversationResolution(newId, u1, u2);
            }
        },
        resolved -> {
            if (resolved == null || resolved.conversationId <= 0) {
                showError("Error", "Failed to create conversation");
                onBack();
                return;
            }
            this.conversationId = resolved.conversationId;
            this.user1Id = resolved.user1Id;
            this.user2Id = resolved.user2Id;
            // Now validate and load UI using the conversation row
            validateAndLoadConversation();
        },
        err -> {
            showError("Database Error", "Could not create conversation");
            onBack();
        });
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
        String sql = "SELECT name, role, is_verified FROM users WHERE id = ?";
        DatabaseService.executeQueryAsync(sql, new Object[]{otherUserId},
            rs -> {
                try {
                    if (rs.next()) {
                        String name = rs.getString("name");
                        String role = rs.getString("role");
                        boolean verified = rs.getBoolean("is_verified");
                        
                        Platform.runLater(() -> {
                            if ((otherUserName == null || otherUserName.isBlank()) && name != null && !name.isBlank()) {
                                otherUserName = name;
                                lblUserName.setText(name);
                            }
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
        
        String sql = "SELECT * FROM messages WHERE conversation_id = ? " +
                     "AND sender_id IN (?, ?) AND receiver_id IN (?, ?) " +
                     "ORDER BY created_at ASC";
        DatabaseService.executeQueryAsync(sql, new Object[]{conversationId, user1Id, user2Id, user1Id, user2Id},
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
                // Update unread count in conversations for the correct side
                String updateConv = (currentUser.getId() == user1Id) ?
                    "UPDATE conversations SET unread_count_user1 = 0 WHERE id = ?" :
                    "UPDATE conversations SET unread_count_user2 = 0 WHERE id = ?";
                DatabaseService.executeUpdateAsync(updateConv, new Object[]{conversationId},
                    r -> {}, e -> {});
            },
            err -> err.printStackTrace()
        );
    }

    @FXML
    private void onBack() {
        try {
            if (previousScene != null && !previousScene.isBlank()) {
                App.loadScene(previousScene, "Chashi Bhai");
            } else {
                App.loadScene("chat-list-view.fxml", "Chats");
            }
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
                                new ProcessBuilder("cmd", "/c", "start", "tel:" + phone).start();
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
