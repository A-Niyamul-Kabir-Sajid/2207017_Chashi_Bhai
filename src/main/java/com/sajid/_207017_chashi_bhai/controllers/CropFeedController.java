package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.File;
import java.io.IOException;
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
    @FXML private ComboBox<String> cbSortBy;
    @FXML private CheckBox chkVerifiedOnly;
    @FXML private GridPane gridCropFeed;
    @FXML private VBox vboxEmptyState;

    private User currentUser;
    private String role; // "farmer" or "buyer"
    
    // 64 districts of Bangladesh with Bangla translations
    private static final String[] DISTRICTS = {
        "সব জেলা / All Districts",
        "বাগেরহাট / Bagerhat", "বান্দরবান / Bandarban", "বরগুনা / Barguna", "বরিশাল / Barisal", "ভোলা / Bhola", "বগুড়া / Bogra",
        "ব্রাহ্মণবাড়িয়া / Brahmanbaria", "চাঁদপুর / Chandpur", "চাঁপাইনবাবগঞ্জ / Chapainawabganj", "চট্টগ্রাম / Chittagong", "চুয়াডাঙ্গা / Chuadanga",
        "কুমিল্লা / Comilla", "কক্সবাজার / Cox's Bazar", "ঢাকা / Dhaka", "দিনাজপুর / Dinajpur", "ফরিদপুর / Faridpur", "ফেনী / Feni",
        "গাইবান্ধা / Gaibandha", "গাজীপুর / Gazipur", "গোপালগঞ্জ / Gopalganj", "হবিগঞ্জ / Habiganj", "জামালপুর / Jamalpur", "যশোর / Jessore",
        "ঝালকাঠি / Jhalokati", "ঝিনাইদহ / Jhenaidah", "জয়পুরহাট / Joypurhat", "খাগড়াছড়ি / Khagrachhari", "খুলনা / Khulna", "কিশোরগঞ্জ / Kishoreganj",
        "কুড়িগ্রাম / Kurigram", "কুষ্টিয়া / Kushtia", "লক্ষ্মীপুর / Lakshmipur", "লালমনিরহাট / Lalmonirhat", "মাদারীপুর / Madaripur", "মাগুরা / Magura",
        "মানিকগঞ্জ / Manikganj", "মেহেরপুর / Meherpur", "মৌলভীবাজার / Moulvibazar", "মুন্সিগঞ্জ / Munshiganj", "ময়মনসিংহ / Mymensingh", "নওগাঁ / Naogaon",
        "নড়াইল / Narail", "নারায়ণগঞ্জ / Narayanganj", "নরসিংদী / Narsingdi", "নাটোর / Natore", "নেত্রকোনা / Netrokona", "নীলফামারী / Nilphamari",
        "নোয়াখালী / Noakhali", "পাবনা / Pabna", "পঞ্চগড় / Panchagarh", "পটুয়াখালী / Patuakhali", "পিরোজপুর / Pirojpur", "রাজবাড়ী / Rajbari",
        "রাজশাহী / Rajshahi", "রাঙামাটি / Rangamati", "রংপুর / Rangpur", "সাতক্ষীরা / Satkhira", "শরীয়তপুর / Shariatpur", "শেরপুর / Sherpur",
        "সিরাজগঞ্জ / Sirajganj", "সুনামগঞ্জ / Sunamganj", "সিলেট / Sylhet", "টাঙ্গাইল / Tangail", "ঠাকুরগাঁও / Thakurgaon"
    };
    
    private static final String[] CATEGORIES = {
        "সব শ্রেণী / All Categories",
        "শস্য/ধান (Rice/Grain)",
        "গম/আটা (Wheat)",
        "সবজি (Vegetables)",
        "ফলমূল (Fruits)",
        "মসলা (Spices)",
        "ডাল (Pulses/Lentils)",
        "তেল বীজ (Oil Seeds)",
        "আখ/গুড় (Sugarcane/Molasses)",
        "চা/পান (Tea/Betel)",
        "ফুল (Flowers)",
        "অন্যান্য (Others)"
    };

    // Keep a simple in-memory representation to support quick filtering
    private static class CropItem {
        int id;
        String productCode;
        int farmerId;
        String name;
        String category;
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
            cbFilterDistrict.getSelectionModel().select(0); // Default: All Districts
        }
        if (cbFilterCropType != null) {
            cbFilterCropType.setItems(FXCollections.observableArrayList(CATEGORIES));
            cbFilterCropType.getSelectionModel().select(0); // Default: All Categories
        }
        
        // Initialize sort dropdown with default selection
        if (cbSortBy != null) {
            cbSortBy.getSelectionModel().select(0); // Default: Newest First
            cbSortBy.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    loadCrops(true);
                }
            });
        }

        // Pre-select district for farmer - find matching item in "বাংলা / English" format
        if ("farmer".equals(role) && currentUser.getDistrict() != null && cbFilterDistrict != null) {
            String userDistrict = currentUser.getDistrict();
            for (String districtOption : DISTRICTS) {
                if (districtOption.contains(userDistrict)) {
                    cbFilterDistrict.getSelectionModel().select(districtOption);
                    break;
                }
            }
        }

        // Live search
        if (txtQuickSearch != null) {
            txtQuickSearch.textProperty().addListener((obs, oldV, newV) -> filterLocally(newV));
        }

        // Initial load - don't apply filters yet, just load all crops with default sort
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
    private void onSearchCropId() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Crop ID দিয়ে খুঁজুন");
        dialog.setHeaderText("ফসল খুঁজুন / Search Crop by ID");
        dialog.setContentText("Crop ID লিখুন:");

        dialog.showAndWait().ifPresent(cropIdStr -> {
            try {
                int cropId = Integer.parseInt(cropIdStr.trim());
                searchCropById(cropId);
            } catch (NumberFormatException e) {
                showError("ত্রুটি", "সঠিক Crop ID লিখুন (শুধুমাত্র সংখ্যা)");
            }
        });
    }

    @FXML
    private void onSearchOrder() {
        // Show order ID search dialog
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("অর্ডার ID দিয়ে খুঁজুন");
        dialog.setHeaderText("অর্ডার খুঁজুন / Search Order by ID");
        dialog.setContentText("অর্ডার ID লিখুন (সংখ্যা):");

        dialog.showAndWait().ifPresent(orderIdStr -> {
            try {
                int orderId = Integer.parseInt(orderIdStr.trim());
                searchOrderById(orderId);
            } catch (NumberFormatException e) {
                showError("ত্রুটি", "সঠিক Order ID লিখুন (শুধুমাত্র সংখ্যা)");
            }
        });
    }

    private void searchOrderById(int orderId) {
        String sql = "SELECT o.id, o.order_number, o.status, o.total_amount, o.created_at, " +
                    "c.name as crop_name, f.name as farmer_name, b.name as buyer_name " +
                    "FROM orders o " +
                    "JOIN crops c ON o.crop_id = c.id " +
                    "JOIN users f ON o.farmer_id = f.id " +
                    "JOIN users b ON o.buyer_id = b.id " +
                    "WHERE o.id = ?";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{orderId},
            rs -> {
                // Read ResultSet data BEFORE Platform.runLater
                String orderNum = null;
                String status = null;
                double total = 0.0;
                String cropName = null;
                String farmerName = null;
                String buyerName = null;
                boolean found = false;
                try {
                    if (rs.next()) {
                        orderNum = rs.getString("order_number");
                        status = rs.getString("status");
                        total = rs.getDouble("total_amount");
                        cropName = rs.getString("crop_name");
                        farmerName = rs.getString("farmer_name");
                        buyerName = rs.getString("buyer_name");
                        found = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // Now update UI on JavaFX thread with pre-loaded data
                final String finalOrderNum = orderNum;
                final String finalStatus = status;
                final double finalTotal = total;
                final String finalCropName = cropName;
                final String finalFarmerName = farmerName;
                final String finalBuyerName = buyerName;
                final boolean finalFound = found;
                Platform.runLater(() -> {
                    try {
                        if (finalFound) {
                            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
                            info.setTitle("অর্ডার পাওয়া গেছে / Order Found");
                            info.setHeaderText(finalOrderNum);
                            info.setContentText(
                                "ফসল: " + finalCropName + "\n" +
                                "কৃষক: " + finalFarmerName + "\n" +
                                "ক্রেতা: " + finalBuyerName + "\n" +
                                "মোট: ৳" + String.format("%.2f", finalTotal) + "\n" +
                                "স্ট্যাটাস: " + finalStatus + "\n\n" +
                                "বিস্তারিত দেখতে চান?"
                            );

                            info.showAndWait().ifPresent(response -> {
                                if (response == ButtonType.OK) {
                                    App.setCurrentOrderId(orderId);
                                    App.setCurrentOrderNumber(finalOrderNum);
                                    App.loadScene("order-detail-view.fxml", "অর্ডার বিবরণ");
                                }
                            });
                        } else {
                            showError("পাওয়া যায়নি", "এই ID এর কোনো অর্ডার পাওয়া যায়নি।");
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
                // Read ResultSet data BEFORE Platform.runLater
                String userRole = null;
                String userName = null;
                String phone = null;
                String district = null;
                boolean isVerified = false;
                boolean found = false;
                try {
                    if (rs.next()) {
                        userRole = rs.getString("role");
                        userName = rs.getString("name");
                        phone = rs.getString("phone");
                        district = rs.getString("district");
                        isVerified = rs.getBoolean("is_verified");
                        found = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // Now update UI on JavaFX thread with pre-loaded data
                final String finalUserRole = userRole;
                final String finalUserName = userName;
                final String finalPhone = phone;
                final String finalDistrict = district;
                final boolean finalIsVerified = isVerified;
                final boolean finalFound = found;
                Platform.runLater(() -> {
                    try {
                        if (finalFound) {
                            // Show user info and ask to view profile
                            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
                            info.setTitle("ইউজার পাওয়া গেছে / User Found");
                            info.setHeaderText(finalUserName + (finalIsVerified ? " ✓" : ""));
                            info.setContentText(
                                "Role: " + ("farmer".equals(finalUserRole) ? "কৃষক / Farmer" : "ক্রেতা / Buyer") + "\n" +
                                "Phone: " + finalPhone + "\n" +
                                "District: " + finalDistrict + "\n\n" +
                                "প্রোফাইল দেখতে চান?"
                            );

                            info.showAndWait().ifPresent(response -> {
                                if (response == ButtonType.OK) {
                                    App.setCurrentViewedUserId(userId);
                                    if ("farmer".equals(finalUserRole)) {
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

    private void searchCropById(int cropId) {
        String sql = "SELECT id, name, status FROM crops WHERE id = ?";
        DatabaseService.executeQueryAsync(
            sql,
            new Object[]{cropId},
            rs -> {
                // Read ResultSet data BEFORE Platform.runLater
                String cropName = null;
                String status = null;
                boolean found = false;
                try {
                    if (rs.next()) {
                        cropName = rs.getString("name");
                        status = rs.getString("status");
                        found = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // Now update UI on JavaFX thread with pre-loaded data
                final String finalCropName = cropName;
                final String finalStatus = status;
                final boolean finalFound = found;
                Platform.runLater(() -> {
                    try {
                        if (finalFound) {
                            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
                            info.setTitle("ফসল পাওয়া গেছে / Crop Found");
                            info.setHeaderText(finalCropName + " (ID: " + cropId + ")");
                            info.setContentText("স্ট্যাটাস: " + (finalStatus != null ? finalStatus : "N/A") + "\n\nবিস্তারিত দেখতে চান?");
                            info.showAndWait().ifPresent(response -> {
                                if (response == ButtonType.OK) {
                                    App.setCurrentCropId(cropId);
                                    App.setPreviousScene("crop-feed-view.fxml");
                                    App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
                                }
                            });
                        } else {
                            showError("পাওয়া যায়নি", "এই ID এর কোনো ফসল পাওয়া যায়নি।");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("ত্রুটি", "ফসল খুঁজতে ব্যর্থ হয়েছে।");
                    }
                });
            },
            err -> {
                err.printStackTrace();
                Platform.runLater(() -> showError("ডাটাবেস ত্রুটি", "ফসল সার্চ করতে সমস্যা হয়েছে।"));
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
        if (cbFilterCropType != null) cbFilterCropType.getSelectionModel().select(0); // Reset to All Categories
        if (cbFilterDistrict != null) cbFilterDistrict.getSelectionModel().select(0); // Reset to All Districts
        if (cbSortBy != null) cbSortBy.getSelectionModel().select(0); // Reset to Newest First
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
           .append("c.price_per_kg as price, c.available_quantity_kg as quantity, 'কেজি' as unit, ")
           .append(" (SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as photo")
           .append(" FROM crops c JOIN users u ON c.farmer_id = u.id WHERE c.status = 'active'");

        List<Object> params = new ArrayList<>();

        if (useFilters) {
            String category = cbFilterCropType != null ? cbFilterCropType.getSelectionModel().getSelectedItem() : null;
            String district = cbFilterDistrict != null ? cbFilterDistrict.getSelectionModel().getSelectedItem() : null;

            if (category != null && !category.isEmpty() && !category.contains("সব") && !category.contains("All")) {
                // Category is stored as-is in database (e.g., "শস্য/ধান (Rice/Grain)")
                sql.append(" AND c.category = ?");
                params.add(category);
            }
            if (district != null && !district.isEmpty() && !district.contains("সব") && !district.contains("All")) {
                // DB stores districts like "কুমিল্লা (Comilla)".
                // UI dropdown uses "বাংলা / English". Match flexibly against both parts.
                if (district.contains("/")) {
                    String[] parts = district.split("/");
                    String banglaDistrict = parts[0].trim();
                    String englishDistrict = parts.length > 1 ? parts[1].trim() : "";
                    sql.append(" AND (c.district LIKE ? OR c.district LIKE ?)");
                    params.add("%" + banglaDistrict + "%");
                    params.add("%" + englishDistrict + "%");
                } else {
                    sql.append(" AND c.district LIKE ?");
                    params.add("%" + district.trim() + "%");
                }
            }
        }

        // Build the ORDER BY clause
        StringBuilder orderBy = new StringBuilder(" ORDER BY ");
        List<Object> orderParams = new ArrayList<>();
        boolean hasRoleSort = false;

        // Role-based ordering
        if ("farmer".equals(role)) {
            orderBy.append("CASE WHEN c.farmer_id = ? THEN 0 ELSE 1 END");
            orderParams.add(currentUser.getId());
            hasRoleSort = true;
        } else { // buyer
            String district = currentUser.getDistrict();
            if (district != null && !district.isEmpty()) {
                orderBy.append("CASE WHEN c.district = ? THEN 0 ELSE 1 END");
                orderParams.add(district);
                hasRoleSort = true;
            }
        }
        
        // Apply sorting based on user selection
        String sortOption = cbSortBy != null ? cbSortBy.getSelectionModel().getSelectedItem() : null;
        if (sortOption != null) {
            if (hasRoleSort) orderBy.append(", "); // Add comma if role-based order exists
            if (sortOption.contains("High to Low") || sortOption.contains("বেশি থেকে কম")) {
                orderBy.append("c.price_per_kg DESC");
            } else if (sortOption.contains("Low to High") || sortOption.contains("কম থেকে বেশি")) {
                orderBy.append("c.price_per_kg ASC");
            } else {
                // Default: Newest First (by harvest date, fallback to created_at)
                orderBy.append("COALESCE(c.harvest_date, c.created_at) DESC");
            }
        } else {
            // No sort selected, default to newest by harvest date
            if (hasRoleSort) orderBy.append(", ");
            orderBy.append("COALESCE(c.harvest_date, c.created_at) DESC");
        }

        // Append the ORDER BY clause to the main query
        sql.append(orderBy);
        params.addAll(orderParams);

        System.out.println("[CropFeed] Loading crops with query: " + sql.toString());
        System.out.println("[CropFeed] Params: " + params);

        DatabaseService.executeQueryAsync(sql.toString(), params.toArray(), rs -> {
            // CRITICAL: Read ResultSet data BEFORE Platform.runLater to avoid closed ResultSet
            List<CropItem> items = new ArrayList<>();
            try {
                while (rs.next()) {
                    CropItem item = mapItem(rs);
                    items.add(item);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showError("ত্রুটি", "ফসলের তালিকা লোড করতে ব্যর্থ হয়েছে।"));
                return;
            }
            
            // Now update UI with the loaded data
            Platform.runLater(() -> {
                try {
                    loadedCrops.clear();
                    loadedCrops.addAll(items);
                    
                    int colCount = 3; // Number of columns in the grid
                    int row = 0, col = 0;
                    
                    for (CropItem item : items) {
                        if (gridCropFeed != null) {
                            gridCropFeed.add(buildCropCard(item), col, row);
                            col++;
                            if (col >= colCount) {
                                col = 0;
                                row++;
                            }
                        }
                    }
                    
                    System.out.println("[CropFeed] Loaded " + items.size() + " crops");

                    // Show empty state if no crops found
                    if (items.isEmpty() && vboxEmptyState != null) {
                        vboxEmptyState.setVisible(true);
                        if (gridCropFeed != null) gridCropFeed.setVisible(false);
                    } else {
                        if (vboxEmptyState != null) vboxEmptyState.setVisible(false);
                        if (gridCropFeed != null) gridCropFeed.setVisible(true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showError("ত্রুটি", "ফসলের তালিকা প্রদর্শন করতে ব্যর্থ হয়েছে।");
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
        item.category = safeString(rs, "category");
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

    @SuppressWarnings("unused")
    private void addMyCropPreview(CropItem item, int index) {
        // Note: myCropsGrid is not available in current FXML, skipping preview
        // This method is kept for future use when the FXML is updated
    }

    /**
     * Build crop card using FXML template
     */
    private Pane buildCropCard(CropItem item) {
        try {
            // Load FXML template
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/sajid/_207017_chashi_bhai/item-crop.fxml"));
            VBox cardRoot = loader.load();
            
            // Get controller and set data
            CropItemController controller = loader.getController();
            controller.setCropData(
                item.id,
                item.name,
                "শ্রেণী: " + (item.category != null ? item.category : "") + " , জেলা: " + (item.district != null ? item.district : ""),
                item.farmerName + (item.farmerVerified ? " ✓" : ""),
                item.quantity,
                item.unit,
                item.price,
                item.photoPath
            );
            
            return cardRoot;
        } catch (IOException e) {
            e.printStackTrace();
            // Fallback to programmatic card if FXML fails
            return buildCropCardProgrammatic(item);
        }
    }

    /**
     * Fallback method to build crop card programmatically
     */
    private Pane buildCropCardProgrammatic(CropItem item) {
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
        Label category = new Label("শ্রেণী: " + (item.category != null ? item.category : ""));
        category.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label qty = new Label(item.quantity > 0 ? String.format("পরিমাণ: %.1f", item.quantity) : "পরিমাণ: N/A");
        qty.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label district = new Label("📍 " + item.district);
        district.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label date = new Label("তারিখ: " + (item.availableDate != null ? item.availableDate : "N/A"));
        date.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        details.getChildren().addAll(titleRow, farmerRow, price, category, qty, district, date);

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
        App.setPreviousScene("crop-feed-view.fxml");
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
        App.setPreviousScene("crop-feed-view.fxml");
        App.loadScene("crop-detail-view.fxml", "অর্ডার করুন");
    }

    private void contactFarmer(CropItem item) {
        if (item == null || currentUser == null) {
            return;
        }
        if (item.farmerId <= 0) {
            showError("Error", "Invalid farmer");
            return;
        }
        if (item.farmerId == currentUser.getId()) {
            showInfo("Not Allowed", "You cannot chat with yourself.");
            return;
        }

        // Let ChatConversationController find/create the conversation
        openConversation(0, item.farmerId, item.farmerName, item.id);
    }
    
    @SuppressWarnings("unused")
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
            App.setPreviousScene("crop-feed-view.fxml");
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
