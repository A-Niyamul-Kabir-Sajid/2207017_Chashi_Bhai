package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.File;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * CropFeedController - Shared crop feed for both buyers and farmers
 * Role-aware behavior and actions, with filters and search
 */
public class CropFeedController {

    // FXML fields matching crop-feed-view.fxml
    @FXML private TextField txtQuickSearch;
    @FXML private ComboBox<String> cbFilterCropType;
    @FXML private ComboBox<String> cbFilterDistrict;
    @FXML private Slider sliderPriceMin;
    @FXML private Label lblPriceRange;
    @FXML private CheckBox chkVerifiedOnly;
    @FXML private GridPane gridCropFeed;
    @FXML private VBox vboxEmptyState;

    private User currentUser;
    private String role; // "farmer" or "buyer"
    
    // 64 districts of Bangladesh sorted alphabetically
    private static final String[] DISTRICTS = {
        "Bagerhat", "Bandarban", "Barguna", "Barisal", "Bhola", "Bogra",
        "Brahmanbaria", "Chandpur", "Chapainawabganj", "Chittagong", "Chuadanga",
        "Comilla", "Cox's Bazar", "Dhaka", "Dinajpur", "Faridpur", "Feni",
        "Gaibandha", "Gazipur", "Gopalganj", "Habiganj", "Jamalpur", "Jessore",
        "Jhalokati", "Jhenaidah", "Joypurhat", "Khagrachhari", "Khulna", "Kishoreganj",
        "Kurigram", "Kushtia", "Lakshmipur", "Lalmonirhat", "Madaripur", "Magura",
        "Manikganj", "Meherpur", "Moulvibazar", "Munshiganj", "Mymensingh", "Naogaon",
        "Narail", "Narayanganj", "Narsingdi", "Natore", "Netrokona", "Nilphamari",
        "Noakhali", "Pabna", "Panchagarh", "Patuakhali", "Pirojpur", "Rajbari",
        "Rajshahi", "Rangamati", "Rangpur", "Satkhira", "Shariatpur", "Sherpur",
        "Sirajganj", "Sunamganj", "Sylhet", "Tangail", "Thakurgaon"
    };
    
    private static final String[] CATEGORIES = {
        "Rice", "Wheat", "Vegetables", "Fruits", "Spices", "Pulses", "Others"
    };

    // Keep a simple in-memory representation to support quick filtering
    private static class CropItem {
        int id;
        String productCode;
        int farmerId;
        String name;
        String farmerName;
        boolean farmerVerified;
        String farmerPhone; // Add this field
        double price;
        String unit;
        double quantity;
        String district;
        String availableDate; // created_at or date string
        String photoPath;
    }

    private final List<CropItem> loadedCrops = new ArrayList<>();

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        if (currentUser == null) {
            showError("অ্যাক্সেস অস্বীকার", "দয়া করে লগইন করুন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }
        role = currentUser.getRole();
        
        // Initialize filter dropdowns if they exist
        if (cbFilterDistrict != null) {
            cbFilterDistrict.setItems(FXCollections.observableArrayList(DISTRICTS));
        }
        if (cbFilterCropType != null) {
            cbFilterCropType.setItems(FXCollections.observableArrayList(CATEGORIES));
        }

        // Pre-select district for farmer
        if ("farmer".equals(role) && currentUser.getDistrict() != null && cbFilterDistrict != null) {
            cbFilterDistrict.getSelectionModel().select(currentUser.getDistrict());
        }

        // Live search
        if (txtQuickSearch != null) {
            txtQuickSearch.textProperty().addListener((obs, oldV, newV) -> filterLocally(newV));
        }

        // Initial load
        loadCrops(false);
    }

    @FXML
    private void onBack() {
        // Navigate to dashboard based on role
        if ("farmer".equals(role)) {
            App.loadScene("farmer-dashboard-view.fxml", "কৃষক ড্যাশবোর্ড");
        } else {
            App.loadScene("buyer-dashboard-view.fxml", "ক্রেতা ড্যাশবোর্ড");
        }
    }

    @FXML
    private void onDashboard() {
        // Navigate to dashboard based on role
        if ("farmer".equals(role)) {
            App.loadScene("farmer-dashboard-view.fxml", "কৃষক ড্যাশবোর্ড");
        } else {
            App.loadScene("buyer-dashboard-view.fxml", "ক্রেতা ড্যাশবোর্ড");
        }
    }

    @FXML
    private void onProfile() {
        // Navigate to profile based on role
        if ("farmer".equals(role)) {
            App.loadScene("farmer-profile-view.fxml", "প্রোফাইল");
        } else {
            App.loadScene("buyer-profile-view.fxml", "প্রোফাইল");
        }
    }

    @FXML
    private void onSearchUser() {
        // Show user ID search dialog
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("User ID দিয়ে খুঁজুন");
        dialog.setHeaderText("ইউজার খুঁজুন / Search User by ID");
        dialog.setContentText("User ID লিখুন:");

        dialog.showAndWait().ifPresent(userIdStr -> {
            try {
                int userId = Integer.parseInt(userIdStr.trim());
                searchUserById(userId);
            } catch (NumberFormatException e) {
                showError("ত্রুটি", "সঠিক User ID লিখুন (শুধুমাত্র সংখ্যা)");
            }
        });
    }

    @FXML
    private void onSearchOrder() {
        // Show order number search dialog
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("অর্ডার নম্বর দিয়ে খুঁজুন");
        dialog.setHeaderText("অর্ডার খুঁজুন / Search Order by Number");
        dialog.setContentText("অর্ডার নম্বর লিখুন (যেমন: ORD-20260108-0001):");

        dialog.showAndWait().ifPresent(orderNum -> {
            String trimmed = orderNum.trim();
            if (!trimmed.isEmpty()) {
                searchOrderByNumber(trimmed);
            }
        });
    }

    private void searchOrderByNumber(String orderNumber) {
        String sql = "SELECT o.id, o.order_number, o.status, o.total_amount, o.created_at, " +
                    "c.name as crop_name, f.name as farmer_name, b.name as buyer_name " +
                    "FROM orders o " +
                    "JOIN crops c ON o.crop_id = c.id " +
                    "JOIN users f ON o.farmer_id = f.id " +
                    "JOIN users b ON o.buyer_id = b.id " +
                    "WHERE o.order_number LIKE ? OR o.id = ?";
        
        int orderId = -1;
        try { orderId = Integer.parseInt(orderNumber); } catch (Exception e) {}
        
        final int finalOrderId = orderId;
        DatabaseService.executeQueryAsync(sql, new Object[]{"%" + orderNumber + "%", finalOrderId},
            rs -> {
                Platform.runLater(() -> {
                    try {
                        if (rs.next()) {
                            int id = rs.getInt("id");
                            String orderNum = rs.getString("order_number");
                            String status = rs.getString("status");
                            double total = rs.getDouble("total_amount");
                            String cropName = rs.getString("crop_name");
                            String farmerName = rs.getString("farmer_name");
                            String buyerName = rs.getString("buyer_name");

                            // Show order info and ask to view details
                            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
                            info.setTitle("অর্ডার পাওয়া গেছে / Order Found");
                            info.setHeaderText(orderNum);
                            info.setContentText(
                                "ফসল: " + cropName + "\n" +
                                "কৃষক: " + farmerName + "\n" +
                                "ক্রেতা: " + buyerName + "\n" +
                                "মোট: ৳" + String.format("%.2f", total) + "\n" +
                                "স্ট্যাটাস: " + status + "\n\n" +
                                "বিস্তারিত দেখতে চান?"
                            );

                            info.showAndWait().ifPresent(response -> {
                                if (response == ButtonType.OK) {
                                    App.setCurrentOrderId(id);
                                    App.setCurrentOrderNumber(orderNum);
                                    App.loadScene("order-detail-view.fxml", "অর্ডার বিবরণ");
                                }
                            });
                        } else {
                            showError("পাওয়া যায়নি", "এই নম্বরের কোনো অর্ডার পাওয়া যায়নি।");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("ত্রুটি", "অর্ডার খুঁজতে ব্যর্থ হয়েছে।");
                    }
                });
            },
            err -> {
                Platform.runLater(() -> showError("ডাটাবেস ত্রুটি", "অর্ডার সার্চ করতে সমস্যা হয়েছে।"));
                err.printStackTrace();
            }
        );
    }

    private void searchUserById(int userId) {
        String sql = "SELECT id, role, name, phone, district, is_verified FROM users WHERE id = ?";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{userId},
            rs -> {
                Platform.runLater(() -> {
                    try {
                        if (rs.next()) {
                            String userRole = rs.getString("role");
                            String userName = rs.getString("name");
                            String phone = rs.getString("phone");
                            String district = rs.getString("district");
                            boolean isVerified = rs.getBoolean("is_verified");

                            // Show user info and ask to view profile
                            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
                            info.setTitle("ইউজার পাওয়া গেছে / User Found");
                            info.setHeaderText(userName + (isVerified ? " ✓" : ""));
                            info.setContentText(
                                "Role: " + (userRole.equals("farmer") ? "কৃষক / Farmer" : "ক্রেতা / Buyer") + "\n" +
                                "Phone: " + phone + "\n" +
                                "District: " + district + "\n\n" +
                                "প্রোফাইল দেখতে চান?"
                            );

                            info.showAndWait().ifPresent(response -> {
                                if (response == ButtonType.OK) {
                                    App.setCurrentViewedUserId(userId);
                                    if (userRole.equals("farmer")) {
                                        App.loadScene("public-farmer-profile-view.fxml", "কৃষকের প্রোফাইল");
                                    } else {
                                        App.loadScene("public-buyer-profile-view.fxml", "ক্রেতার প্রোফাইল");
                                    }
                                }
                            });
                        } else {
                            showError("পাওয়া যায়নি", "এই ID এর কোনো ইউজার পাওয়া যায়নি।");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("ত্রুটি", "ইউজার খুঁজতে ব্যর্থ হয়েছে।");
                    }
                });
            },
            err -> {
                Platform.runLater(() -> showError("ডাটাবেস ত্রুটি", "ইউজার সার্চ করতে সমস্যা হয়েছে।"));
                err.printStackTrace();
            }
        );
    }

    @FXML
    private void onToggleFilter() {
        // Filter pane toggle - not in current FXML
    }

    @FXML
    private void onSearchKeyUp() {
        if (txtQuickSearch != null) {
            filterLocally(txtQuickSearch.getText().trim());
        }
    }

    @FXML
    private void onApplyFilter() {
        loadCrops(true);
    }

    @FXML
    private void onResetFilter() {
        if (cbFilterCropType != null) cbFilterCropType.getSelectionModel().clearSelection();
        if (cbFilterDistrict != null) cbFilterDistrict.getSelectionModel().clearSelection();
        if (chkVerifiedOnly != null) chkVerifiedOnly.setSelected(false);
        if (txtQuickSearch != null) txtQuickSearch.clear();
        loadCrops(false);
    }

    /**
     * Load crops from DB with optional filters.
     */
    private void loadCrops(boolean useFilters) {
        if (gridCropFeed != null) gridCropFeed.getChildren().clear();
        if (vboxEmptyState != null) vboxEmptyState.setVisible(false);
        loadedCrops.clear();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.*, u.name as farmer_name, u.phone as farmer_phone, u.is_verified, ")
           .append(" (SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as photo")
           .append(" FROM crops c JOIN users u ON c.farmer_id = u.id WHERE c.status = 'active'");

        List<Object> params = new ArrayList<>();

        if (useFilters) {
            String category = cbFilterCropType != null ? cbFilterCropType.getSelectionModel().getSelectedItem() : null;
            String district = cbFilterDistrict != null ? cbFilterDistrict.getSelectionModel().getSelectedItem() : null;
            boolean verifiedOnly = chkVerifiedOnly != null && chkVerifiedOnly.isSelected();

            if (category != null && !category.isEmpty() && !category.contains("সব") && !category.contains("All")) {
                sql.append(" AND c.category = ?");
                params.add(category);
            }
            if (district != null && !district.isEmpty() && !district.contains("সব") && !district.contains("All")) {
                sql.append(" AND c.district = ?");
                params.add(district);
            }
            if (verifiedOnly) {
                sql.append(" AND u.is_verified = 1");
            }
        }

        // Ordering by role
        if ("farmer".equals(role)) {
            sql.append(" ORDER BY CASE WHEN c.farmer_id = ? THEN 0 ELSE 1 END, c.created_at DESC");
            params.add(currentUser.getId());
        } else { // buyer
            String district = currentUser.getDistrict();
            if (district != null && !district.isEmpty()) {
                sql.append(" ORDER BY CASE WHEN c.district = ? THEN 0 ELSE 1 END, c.created_at DESC");
                params.add(district);
            } else {
                sql.append(" ORDER BY c.created_at DESC");
            }
        }

        DatabaseService.executeQueryAsync(sql.toString(), params.toArray(), rs -> {
            Platform.runLater(() -> {
                try {
                    int colCount = 3; // Number of columns in the grid
                    int row = 0, col = 0;
                    
                    while (rs.next()) {
                        CropItem item = mapItem(rs);
                        loadedCrops.add(item);

                        if (gridCropFeed != null) {
                            gridCropFeed.add(buildCropCard(item), col, row);
                            col++;
                            if (col >= colCount) {
                                col = 0;
                                row++;
                            }
                        }
                    }

                    // Show empty state if no crops found
                    if (loadedCrops.isEmpty() && vboxEmptyState != null) {
                        vboxEmptyState.setVisible(true);
                        if (gridCropFeed != null) gridCropFeed.setVisible(false);
                    } else {
                        if (vboxEmptyState != null) vboxEmptyState.setVisible(false);
                        if (gridCropFeed != null) gridCropFeed.setVisible(true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showError("ত্রুটি", "ফসলের তালিকা লোড করতে ব্যর্থ হয়েছে।");
                }
            });
        }, err -> {
            Platform.runLater(() -> {
                showError("ডাটাবেস ত্রুটি", "ফসল লোডে সমস্যা হয়েছে।");
                err.printStackTrace();
            });
        });
    }

    private CropItem mapItem(ResultSet rs) throws Exception {
        CropItem item = new CropItem();
        item.id = rs.getInt("id");
        item.productCode = safeString(rs, "product_code");
        item.farmerId = rs.getInt("farmer_id");
        item.name = rs.getString("name");
        item.farmerName = rs.getString("farmer_name");
        item.farmerPhone = safeString(rs, "farmer_phone");
        item.farmerVerified = rs.getBoolean("is_verified");
        item.price = rs.getDouble("price");
        item.unit = rs.getString("unit");
        try { item.quantity = rs.getDouble("quantity"); } catch (Exception ignored) { item.quantity = 0.0; }
        item.district = safeString(rs, "district");
        item.availableDate = safeString(rs, "created_at");
        item.photoPath = safeString(rs, "photo");
        return item;
    }

    private String safeString(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (Exception e) { return ""; }
    }

    private void addMyCropPreview(CropItem item, int index) {
        // Note: myCropsGrid is not available in current FXML, skipping preview
        // This method is kept for future use when the FXML is updated
    }

    private Pane buildCropCard(CropItem item) {
        HBox card = new HBox(12);
        card.getStyleClass().add("crop-card");
        card.setPadding(new Insets(12));
        card.setOnMouseClicked(e -> openDetails(item.id));

        // Photo
        ImageView imageView = new ImageView();
        imageView.setFitWidth(120);
        imageView.setFitHeight(100);
        imageView.setPreserveRatio(true);
        if (item.photoPath != null && !item.photoPath.isEmpty()) {
            File photoFile = new File(item.photoPath);
            if (photoFile.exists()) {
                imageView.setImage(new Image(photoFile.toURI().toString()));
            }
        }

        // Details
        VBox details = new VBox(6);
        details.setPrefWidth(480);

        HBox titleRow = new HBox(6);
        Label name = new Label(item.name);
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label myBadge = new Label("আমার ফসল");
        myBadge.getStyleClass().add("my-crop-badge");
        myBadge.setVisible(item.farmerId == currentUser.getId());
        titleRow.getChildren().addAll(name, myBadge);

        HBox farmerRow = new HBox(6);
        Label farmerName = new Label(item.farmerName);
        farmerName.setStyle("-fx-font-size: 12px;");
        Label verified = new Label("✓");
        verified.getStyleClass().add("verified-badge");
        verified.setVisible(item.farmerVerified);
        farmerRow.getChildren().addAll(farmerName, verified);

        Label price = new Label(String.format("৳%.2f/%s", item.price, item.unit));
        price.setStyle("-fx-font-size: 16px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        Label qty = new Label(item.quantity > 0 ? String.format("পরিমাণ: %.1f", item.quantity) : "পরিমাণ: N/A");
        qty.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label district = new Label("📍 " + item.district);
        district.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label date = new Label("তারিখ: " + (item.availableDate != null ? item.availableDate : "N/A"));
        date.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        details.getChildren().addAll(titleRow, farmerRow, price, qty, district, date);

        // Actions by role
        VBox actionsBox = new VBox(8);
        actionsBox.setPrefWidth(200);
        actionsBox.setId("actionsHBox");

        if (item.farmerId == currentUser.getId()) {
            // Farmer own crop
            Button edit = new Button("সম্পাদনা");
            edit.getStyleClass().add("button-secondary");
            edit.setMaxWidth(Double.MAX_VALUE);
            edit.setOnAction(e -> editCrop(item.id));

            Button del = new Button("মুছুন");
            del.getStyleClass().add("button-danger");
            del.setMaxWidth(Double.MAX_VALUE);
            del.setOnAction(e -> deleteCrop(item.id));

            actionsBox.getChildren().addAll(edit, del);
        } else if ("buyer".equals(role)) {
            // Buyer actions
            Button contact = new Button("যোগাযোগ করুন");
            contact.getStyleClass().add("button-secondary");
            contact.setMaxWidth(Double.MAX_VALUE);
            contact.setOnAction(e -> contactFarmer(item));

            Button order = new Button("অর্ডার করুন");
            order.getStyleClass().add("button-primary");
            order.setMaxWidth(Double.MAX_VALUE);
            order.setOnAction(e -> orderCrop(item.id));

            Button whatsapp = new Button("WhatsApp");
            whatsapp.getStyleClass().add("button-transparent");
            whatsapp.setMaxWidth(Double.MAX_VALUE);
            whatsapp.setOnAction(e -> openWhatsApp(item.farmerPhone));

            Button call = new Button("Call");
            call.getStyleClass().add("button-transparent");
            call.setMaxWidth(Double.MAX_VALUE);
            call.setOnAction(e -> openPhone(item.farmerPhone));

            actionsBox.getChildren().addAll(contact, order, whatsapp, call);
        } else {
            // Farmer viewing others' crops - can only view details and copy code
            Button view = new Button("বিস্তারিত দেখুন");
            view.getStyleClass().add("button-secondary");
            view.setMaxWidth(Double.MAX_VALUE);
            view.setOnAction(e -> openDetails(item.id));
            
            Button copyCode = new Button("📋 কোড কপি করুন");
            copyCode.getStyleClass().add("button-transparent");
            copyCode.setMaxWidth(Double.MAX_VALUE);
            copyCode.setOnAction(e -> copyProductCode(item.productCode));
            
            actionsBox.getChildren().addAll(view, copyCode);
        }

        card.getChildren().addAll(imageView, details, actionsBox);
        return card;
    }

    private void filterLocally(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (gridCropFeed != null) gridCropFeed.getChildren().clear();
        int count = 0;
        int colCount = 3;
        int row = 0, col = 0;
        
        for (CropItem item : loadedCrops) {
            if (q.isEmpty() || 
                (item.name != null && item.name.toLowerCase().contains(q)) ||
                (item.district != null && item.district.toLowerCase().contains(q)) ||
                (item.farmerName != null && item.farmerName.toLowerCase().contains(q)) ||
                (item.productCode != null && item.productCode.toLowerCase().contains(q))) {
                if (gridCropFeed != null) {
                    gridCropFeed.add(buildCropCard(item), col, row);
                    col++;
                    if (col >= colCount) {
                        col = 0;
                        row++;
                    }
                }
                count++;
            }
        }
        
        // Show/hide empty state
        if (count == 0 && vboxEmptyState != null) {
            vboxEmptyState.setVisible(true);
            if (gridCropFeed != null) gridCropFeed.setVisible(false);
        } else {
            if (vboxEmptyState != null) vboxEmptyState.setVisible(false);
            if (gridCropFeed != null) gridCropFeed.setVisible(true);
        }
    }

    private void openDetails(int cropId) {
        App.setCurrentCropId(cropId);
        App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
    }

    private void editCrop(int cropId) {
        App.setCurrentCropId(cropId);
        App.loadScene("edit-crop-view.fxml", "ফসল সম্পাদনা");
    }

    private void deleteCrop(int cropId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("আপনি কি এই ফসলটি মুছতে চান?");
        confirm.setContentText("এই কাজটি পূর্বাবস্থায় ফেরত নেওয়া যাবে না।");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                DatabaseService.executeUpdateAsync(
                    "UPDATE crops SET status = 'deleted' WHERE id = ?",
                    new Object[]{cropId},
                    rowsAffected -> {
                        Platform.runLater(() -> {
                            showSuccess("সফল", "ফসল মুছে ফেলা হয়েছে।");
                            loadCrops(false);
                        });
                    },
                    err -> {
                        Platform.runLater(() -> showError("ত্রুটি", "ফসল মুছতে ব্যর্থ হয়েছে।"));
                        err.printStackTrace();
                    }
                );
            }
        });
    }

    private void orderCrop(int cropId) {
        App.setCurrentCropId(cropId);
        App.loadScene("crop-detail-view.fxml", "অর্ডার করুন");
    }

    private void contactFarmer(CropItem item) {
        // Get or create conversation with farmer
        String sql = "SELECT id FROM conversations WHERE " +
                    "(user1_id = ? AND user2_id = ? AND (crop_id = ? OR crop_id IS NULL)) OR " +
                    "(user1_id = ? AND user2_id = ? AND (crop_id = ? OR crop_id IS NULL))";
        Object[] params = {currentUser.getId(), item.farmerId, item.id, 
                          item.farmerId, currentUser.getId(), item.id};
        
        DatabaseService.executeQueryAsync(sql, params,
            rs -> {
                try {
                    if (rs.next()) {
                        // Conversation exists
                        int convId = rs.getInt("id");
                        Platform.runLater(() -> openConversation(convId, item.farmerId, item.farmerName, item.id));
                    } else {
                        // Create new conversation
                        createAndOpenConversation(item);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> showError("Error", "Failed to open chat"));
                }
            },
            err -> {
                Platform.runLater(() -> showError("Error", "Database error: " + err.getMessage()));
            }
        );
    }
    
    private void createAndOpenConversation(CropItem item) {
        String insertSql = "INSERT INTO conversations (user1_id, user2_id, crop_id) VALUES (?, ?, ?)";
        Object[] params = {currentUser.getId(), item.farmerId, item.id};
        
        DatabaseService.executeUpdateAsync(insertSql, params,
            rows -> {
                // Get the newly created conversation ID
                String selectSql = "SELECT id FROM conversations WHERE user1_id = ? AND user2_id = ? AND crop_id = ?";
                DatabaseService.executeQueryAsync(selectSql, params,
                    rs -> {
                        try {
                            if (rs.next()) {
                                int convId = rs.getInt("id");
                                Platform.runLater(() -> openConversation(convId, item.farmerId, item.farmerName, item.id));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    },
                    err -> err.printStackTrace()
                );
            },
            err -> {
                Platform.runLater(() -> showError("Error", "Failed to create conversation"));
            }
        );
    }
    
    private void openConversation(int convId, int userId, String userName, int cropId) {
        try {
            App.showView("chat-conversation-view.fxml", controller -> {
                if (controller instanceof ChatConversationController) {
                    ChatConversationController chatController = (ChatConversationController) controller;
                    chatController.loadConversation(convId, userId, userName, cropId);
                }
            });
        } catch (Exception e) {
            showError("Error", "Failed to open chat");
            e.printStackTrace();
        }
    }

    private void openPhone(String phone) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("tel:" + (phone == null ? "" : phone)));
        } catch (Exception e) {
            showInfo("Phone", "ফোন নম্বর: " + (phone == null ? "N/A" : phone));
        }
    }

    private void openWhatsApp(String phone) {
        try {
            String cleanPhone = phone == null ? "" : phone.replaceAll("[^0-9]", "");
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://wa.me/" + cleanPhone));
        } catch (Exception e) {
            showInfo("WhatsApp", "WhatsApp: " + (phone == null ? "N/A" : phone));
        }
    }

    private void copyProductCode(String productCode) {
        if (productCode == null || productCode.isEmpty()) {
            showInfo("কোড নেই", "এই পণ্যের কোড পাওয়া যায়নি।");
            return;
        }
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(productCode);
            clipboard.setContent(content);
            showInfo("কপি সফল", "পণ্য কোড কপি হয়েছে: " + productCode);
        } catch (Exception e) {
            showError("ত্রুটি", "কোড কপি করতে ব্যর্থ হয়েছে।");
            e.printStackTrace();
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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
