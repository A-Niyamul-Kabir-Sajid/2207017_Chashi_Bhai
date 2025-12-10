package com.sajid._207017_chashi_bhai.services;

import com.sajid._207017_chashi_bhai.models.Crop;
import java.util.ArrayList;
import java.util.List;

public class CropService {
    private List<Crop> crops;

    public CropService() {
        this.crops = new ArrayList<>();
        // Initialize with some sample crops
        crops.add(new Crop("Wheat", "Cereal", true));
        crops.add(new Crop("Rice", "Cereal", true));
        crops.add(new Crop("Corn", "Cereal", true));
    }

    public List<Crop> getAvailableCrops() {
        return crops;
    }

    public void addCrop(Crop crop) {
        crops.add(crop);
    }

    public void removeCrop(Crop crop) {
        crops.remove(crop);
    }
}