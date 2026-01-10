package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
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
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            txtCropName.setText(resultSet.getString("name"));
                            cbCategory.setValue(resultSet.getString("category"));
                            txtPrice.setText(String.valueOf(resultSet.getDouble("price_per_kg")));
                            cbUnit.setValue("কেজি (kg)");
                            txtQuantity.setText(String.valueOf(resultSet.getDouble("available_quantity_kg")));
                            
                            String harvestDateStr = resultSet.getString("harvest_date");
                            if (harvestDateStr != null) {
                                dpAvailableDate.setValue(LocalDate.parse(harvestDateStr));
                            }
                            
                            cbDistrict.setValue(resultSet.getString("district"));
                            cbTransport.setValue(resultSet.getString("transport_info"));
                            txtDescription.setText(resultSet.getString("description"));
                        } else {
                            showError("ত্রুটি", "ফসল খুঁজে পাওয়া যায়নি।");
                            onCancel();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("ত্রুটি", "ফসল লোড করতে ব্যর্থ হয়েছে।");
                    }
                });
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
                Platform.runLater(() -> {
                    try {
                        ImageView[] imageViews = {imgPhoto1, imgPhoto2, imgPhoto3, imgPhoto4, imgPhoto5};
                        int index = 0;
                        while (resultSet.next() && index < 5) {
                            String photoPath = resultSet.getString("photo_path");
                            existingPhotoPaths.set(index, photoPath);
                            
                            File photoFile = new File(photoPath);
                            if (photoFile.exists()) {
                                imageViews[index].setImage(new Image(photoFile.toURI().toString()));
                            }
                            index++;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> error.printStackTrace()
        );
    }

    @FXML
    private void onChangePhoto1() { changePhoto(0, imgPhoto1); }
    
    @FXML
    private void onChangePhoto2() { changePhoto(1, imgPhoto2); }
    
    @FXML
    private void onChangePhoto3() { changePhoto(2, imgPhoto3); }
    
    @FXML
    private void onChangePhoto4() { changePhoto(3, imgPhoto4); }
    
    @FXML
    private void onChangePhoto5() { changePhoto(4, imgPhoto5); }

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
            "UPDATE crops SET name = ?, category = ?, price = ?, unit = ?, quantity = ?, harvest_date = ?, district = ?, transport_info = ?, description = ? " +
            "WHERE id = ? AND farmer_id = ?",
            new Object[]{cropName, category, price, unit, quantity, harvestDate.toString(), district, transport, description, cropId, currentUser.getId()},
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
                    String fileName = "photo_" + (i + 1) + "_" + System.currentTimeMillis() + getFileExtension(newPhoto);
                    Path destination = photosDir.resolve(fileName);
                    Files.copy(newPhoto.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
                    
                    String existingPath = existingPhotoPaths.get(i);
                    int finalI = i;
                    if (existingPath != null) {
                        // Update existing photo
                        DatabaseService.executeUpdateAsync(
                            "UPDATE crop_photos SET photo_path = ? WHERE crop_id = ? AND photo_order = ?",
                            new Object[]{destination.toString(), cropId, finalI + 1},
                            rows -> {},
                            error -> error.printStackTrace()
                        );
                    } else {
                        // Insert new photo
                        DatabaseService.executeUpdateAsync(
                            "INSERT INTO crop_photos (crop_id, photo_path, photo_order) VALUES (?, ?, ?)",
                            new Object[]{cropId, destination.toString(), finalI + 1},
                            rows -> {},
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
