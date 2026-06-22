package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.FirebaseService;
import com.sajid._207017_chashi_bhai.utils.ImageBase64Util;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * EditCropController - Edit existing crop listing
 */
public class EditCropController {

    @FXML private TextField txtCropName;
    @FXML private ComboBox<String> cbCategory;
    @FXML private TextField txtPrice;
    @FXML private ComboBox<String> cbUnit;
    @FXML private TextField txtQuantity;
    @FXML private DatePicker dpAvailableDate;
    @FXML private ComboBox<String> cbDistrict;
    @FXML private ComboBox<String> cbTransport;
    @FXML private TextArea txtDescription;
    @FXML private Label lblError;
    @FXML private Button btnUpdate;
    @FXML private Button btnCancel;
    
    @FXML private ImageView imgPhoto1;
    @FXML private ImageView imgPhoto2;
    @FXML private ImageView imgPhoto3;
    @FXML private ImageView imgPhoto4;
    @FXML private ImageView imgPhoto5;

    private User currentUser;
    private int cropId;
    private List<String> existingPhotoPaths = new ArrayList<>();
    private List<File> newPhotos = new ArrayList<>();

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        cropId = App.getCurrentCropId();
        
        if (currentUser == null || !"farmer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র কৃষকরা ফসল সম্পাদনা করতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        // Initialize ComboBoxes
        cbCategory.getItems().addAll("শস্য (Crops)", "সবজি (Vegetables)", "ফল (Fruits)", "মসলা (Spices)", "ডাল (Pulses)");
        cbUnit.getItems().addAll("কেজি (kg)", "মণ (maund)", "টন (ton)", "পিস (piece)");
        cbDistrict.getItems().addAll("ঢাকা", "চট্টগ্রাম", "রাজশাহী", "খুলনা", "বরিশাল", "সিলেট", "রংপুর", "ময়মনসিংহ");
        cbTransport.getItems().addAll("বিক্রেতা সরবরাহ করবেন", "ক্রেতা নিয়ে যাবেন", "আলোচনা সাপেক্ষ");

        // Initialize photo lists
        for (int i = 0; i < 5; i++) {
            existingPhotoPaths.add(null);
            newPhotos.add(null);
        }

        // Load crop data
        loadCropData();
    }

    private void loadCropData() {
        DatabaseService.executeQueryAsync(
            "SELECT * FROM crops WHERE id = ? AND farmer_id = ?",
            new Object[]{cropId, currentUser.getId()},
            resultSet -> {
                try {
                    // Read ALL data from ResultSet FIRST (before Platform.runLater)
                    if (resultSet.next()) {
                        String name = resultSet.getString("name");
                        String category = resultSet.getString("category");
                        double pricePerKg = resultSet.getDouble("price_per_kg");
                        double quantityKg = resultSet.getDouble("available_quantity_kg");
                        String harvestDateStr = resultSet.getString("harvest_date");
                        String district = resultSet.getString("district");
                        String transport = resultSet.getString("transport_info");
                        String description = resultSet.getString("description");
                        
                        // NOW use Platform.runLater with the data we've already read
                        Platform.runLater(() -> {
                            try {
                                txtCropName.setText(name);
                                cbCategory.setValue(category);
                                txtPrice.setText(String.valueOf(pricePerKg));
                                cbUnit.setValue("কেজি (kg)");
                                txtQuantity.setText(String.valueOf(quantityKg));
                                
                                if (harvestDateStr != null) {
                                    dpAvailableDate.setValue(LocalDate.parse(harvestDateStr));
                                }
                                
                                cbDistrict.setValue(district);
                                cbTransport.setValue(transport);
                                txtDescription.setText(description);
                            } catch (Exception e) {
                                e.printStackTrace();
                                showError("ত্রুটি", "ফসল লোড করতে ব্যর্থ হয়েছে।");
                            }
                        });
                    } else {
                        Platform.runLater(() -> {
                            showError("ত্রুটি", "ফসল খুঁজে পাওয়া যায়নি।");
                            onCancel();
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        showError("ত্রুটি", "ফসল লোড করতে ব্যর্থ হয়েছে।");
                    });
                }
            },
            error -> {
                Platform.runLater(() -> {
                    showError("ডাটাবেস ত্রুটি", "ফসল লোড করতে সমস্যা হয়েছে।");
                    error.printStackTrace();
                });
            }
        );

        // Load photos
        loadPhotos();
    }

    private void loadPhotos() {
        DatabaseService.executeQueryAsync(
            "SELECT photo_path, photo_order FROM crop_photos WHERE crop_id = ? ORDER BY photo_order",
            new Object[]{cropId},
            resultSet -> {
                try {
                    // Read ALL photo data from ResultSet FIRST
                    java.util.List<String> photoPaths = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        photoPaths.add(resultSet.getString("photo_path"));
                    }
                    
                    // NOW use Platform.runLater with the data we've already read
                    Platform.runLater(() -> {
                        try {
                            ImageView[] imageViews = {imgPhoto1, imgPhoto2, imgPhoto3, imgPhoto4, imgPhoto5};
                            for (int i = 0; i < photoPaths.size() && i < 5; i++) {
                                String photoPath = photoPaths.get(i);
                                existingPhotoPaths.set(i, photoPath);
                                
                                File photoFile = new File(photoPath);
                                if (photoFile.exists()) {
                                    imageViews[i].setImage(new Image(photoFile.toURI().toString()));
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
    }

    @FXML
    private void onEditPhoto1() { changePhoto(0, imgPhoto1); }
    
    @FXML
    private void onEditPhoto2() { changePhoto(1, imgPhoto2); }
    
    @FXML
    private void onEditPhoto3() { changePhoto(2, imgPhoto3); }
    
    @FXML
    private void onEditPhoto4() { changePhoto(3, imgPhoto4); }
    
    @FXML
    private void onEditPhoto5() { changePhoto(4, imgPhoto5); }

    private void changePhoto(int index, ImageView imageView) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("নতুন ছবি নির্বাচন করুন");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        
        File file = fileChooser.showOpenDialog(btnUpdate.getScene().getWindow());
        if (file != null) {
            newPhotos.set(index, file);
            imageView.setImage(new Image(file.toURI().toString()));
        }
    }

    @FXML
    private void onUpdate() {
        lblError.setText("");
        
        if (!validateFields()) {
            return;
        }

        btnUpdate.setDisable(true);
        lblError.setText("আপডেট হচ্ছে...");

        String cropName = txtCropName.getText().trim();
        String category = cbCategory.getValue();
        double price = Double.parseDouble(txtPrice.getText().trim());
        String unit = cbUnit.getValue();
        double quantity = Double.parseDouble(txtQuantity.getText().trim());
        LocalDate harvestDate = dpAvailableDate.getValue();
        String district = cbDistrict.getValue();
        String transport = cbTransport.getValue();
        String description = txtDescription.getText().trim();

        DatabaseService.executeUpdateAsync(
            "UPDATE crops SET name = ?, category = ?, price_per_kg = ?, available_quantity_kg = ?, harvest_date = ?, district = ?, transport_info = ?, description = ? " +
            "WHERE id = ? AND farmer_id = ?",
            new Object[]{cropName, category, price, quantity, harvestDate.toString(), district, transport, description, cropId, currentUser.getId()},
            rowsAffected -> {
                if (rowsAffected > 0) {
                    updatePhotos();
                } else {
                    Platform.runLater(() -> {
                        btnUpdate.setDisable(false);
                        lblError.setText("আপডেট করতে ব্যর্থ হয়েছে।");
                    });
                }
            },
            error -> {
                Platform.runLater(() -> {
                    btnUpdate.setDisable(false);
                    showError("ডাটাবেস ত্রুটি", "ফসল আপডেট করতে সমস্যা হয়েছে।");
                    error.printStackTrace();
                });
            }
        );
    }

    private void updatePhotos() {
        Path photosDir = Paths.get("data/crop_photos/" + cropId);
        try {
            Files.createDirectories(photosDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < 5; i++) {
            File newPhoto = newPhotos.get(i);
            if (newPhoto != null) {
                try {
                    // Convert image to Base64
                    String imageBase64 = ImageBase64Util.fileToBase64(newPhoto);
                    
                    // Copy file to app directory (backward compatibility)
                    String fileName = "photo_" + (i + 1) + "_" + System.currentTimeMillis() + getFileExtension(newPhoto);
                    Path destination = photosDir.resolve(fileName);
                    Files.copy(newPhoto.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
                    
                    String existingPath = existingPhotoPaths.get(i);
                    int finalI = i;
                    final String cropIdStr = String.valueOf(cropId);
                    final int photoOrder = finalI + 1;
                    
                    if (existingPath != null) {
                        // Update existing photo with Base64
                        DatabaseService.executeUpdateAsync(
                            "UPDATE crop_photos SET photo_path = ?, image_base64 = ? WHERE crop_id = ? AND photo_order = ?",
                            new Object[]{destination.toString(), imageBase64, cropId, photoOrder},
                            rows -> {
                                System.out.println("✓ Photo " + photoOrder + " updated with Base64 for crop " + cropIdStr);
                                
                                // Also sync to Firebase
                                FirebaseService.getInstance().saveCropPhoto(
                                    cropIdStr,
                                    photoOrder,
                                    imageBase64,
                                    () -> System.out.println("✓ Photo " + photoOrder + " synced to Firebase"),
                                    err -> System.err.println("❌ Firebase sync error for photo " + photoOrder + ": " + err.getMessage())
                                );
                            },
                            error -> error.printStackTrace()
                        );
                    } else {
                        // Insert new photo with Base64
                        DatabaseService.executeUpdateAsync(
                            "INSERT INTO crop_photos (crop_id, photo_path, image_base64, photo_order) VALUES (?, ?, ?, ?)",
                            new Object[]{cropId, destination.toString(), imageBase64, photoOrder},
                            rows -> {
                                System.out.println("✓ Photo " + photoOrder + " inserted with Base64 for crop " + cropIdStr);
                                
                                // Also sync to Firebase
                                FirebaseService.getInstance().saveCropPhoto(
                                    cropIdStr,
                                    photoOrder,
                                    imageBase64,
                                    () -> System.out.println("✓ Photo " + photoOrder + " synced to Firebase"),
                                    err -> System.err.println("❌ Firebase sync error for photo " + photoOrder + ": " + err.getMessage())
                                );
                            },
                            error -> error.printStackTrace()
                        );
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        Platform.runLater(() -> {
            showSuccess("সফল!", "ফসল সফলভাবে আপডেট করা হয়েছে।");
            App.loadScene("my-crops-view.fxml", "আমার ফসলসমূহ");
        });
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot) : "";
    }

    private boolean validateFields() {
        if (txtCropName.getText().trim().isEmpty()) {
            lblError.setText("ফসলের নাম লিখুন");
            return false;
        }
        if (cbCategory.getValue() == null) {
            lblError.setText("ক্যাটাগরি নির্বাচন করুন");
            return false;
        }
        try {
            double price = Double.parseDouble(txtPrice.getText().trim());
            if (price <= 0) {
                lblError.setText("সঠিক দাম লিখুন");
                return false;
            }
        } catch (NumberFormatException e) {
            lblError.setText("সঠিক দাম লিখুন");
            return false;
        }
        return true;
    }

    @FXML
    private void onBack() {
        App.loadScene("my-crops-view.fxml", "আমার ফসলসমূহ");
    }

    @FXML
    private void onCancel() {
        App.loadScene("my-crops-view.fxml", "আমার ফসলসমূহ");
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
}
