package com.sajid._207017_chashi_bhai.utils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * BangladeshData - Contains all 64 districts and transport options for Bangladesh
 */
public class BangladeshData {

    /**
     * All 64 districts of Bangladesh in Bangla
     */
    public static final String[] DISTRICTS_BN = {
        "বাগেরহাট", "বান্দরবান", "বরগুনা", "বরিশাল", "ভোলা", "বগুড়া",
        "ব্রাহ্মণবাড়িয়া", "চাঁদপুর", "চাঁপাইনবাবগঞ্জ", "চট্টগ্রাম", "চুয়াডাঙ্গা",
        "কুমিল্লা", "কক্সবাজার", "ঢাকা", "দিনাজপুর", "ফরিদপুর", "ফেনী",
        "গাইবান্ধা", "গাজীপুর", "গোপালগঞ্জ", "হবিগঞ্জ", "জামালপুর", "যশোর",
        "ঝালকাঠি", "ঝিনাইদহ", "জয়পুরহাট", "খাগড়াছড়ি", "খুলনা", "কিশোরগঞ্জ",
        "কুড়িগ্রাম", "কুষ্টিয়া", "লক্ষ্মীপুর", "লালমনিরহাট", "মাদারীপুর", "মাগুরা",
        "মানিকগঞ্জ", "মেহেরপুর", "মৌলভীবাজার", "মুন্সীগঞ্জ", "ময়মনসিংহ", "নওগাঁ",
        "নড়াইল", "নারায়ণগঞ্জ", "নরসিংদী", "নাটোর", "নেত্রকোণা", "নীলফামারী",
        "নোয়াখালী", "পাবনা", "পঞ্চগড়", "পটুয়াখালী", "পিরোজপুর", "রাজবাড়ী",
        "রাজশাহী", "রাঙ্গামাটি", "রংপুর", "সাতক্ষীরা", "শরীয়তপুর", "শেরপুর",
        "সিরাজগঞ্জ", "সুনামগঞ্জ", "সিলেট", "টাঙ্গাইল", "ঠাকুরগাঁও"
    };

    /**
     * All 64 districts of Bangladesh in English
     */
    public static final String[] DISTRICTS_EN = {
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

    /**
     * Districts with both Bangla and English names
     */
    public static final String[] DISTRICTS = {
        "বাগেরহাট (Bagerhat)", "বান্দরবান (Bandarban)", "বরগুনা (Barguna)", "বরিশাল (Barisal)", 
        "ভোলা (Bhola)", "বগুড়া (Bogra)", "ব্রাহ্মণবাড়িয়া (Brahmanbaria)", "চাঁদপুর (Chandpur)", 
        "চাঁপাইনবাবগঞ্জ (Chapainawabganj)", "চট্টগ্রাম (Chittagong)", "চুয়াডাঙ্গা (Chuadanga)",
        "কুমিল্লা (Comilla)", "কক্সবাজার (Cox's Bazar)", "ঢাকা (Dhaka)", "দিনাজপুর (Dinajpur)", 
        "ফরিদপুর (Faridpur)", "ফেনী (Feni)", "গাইবান্ধা (Gaibandha)", "গাজীপুর (Gazipur)", 
        "গোপালগঞ্জ (Gopalganj)", "হবিগঞ্জ (Habiganj)", "জামালপুর (Jamalpur)", "যশোর (Jessore)",
        "ঝালকাঠি (Jhalokati)", "ঝিনাইদহ (Jhenaidah)", "জয়পুরহাট (Joypurhat)", 
        "খাগড়াছড়ি (Khagrachhari)", "খুলনা (Khulna)", "কিশোরগঞ্জ (Kishoreganj)",
        "কুড়িগ্রাম (Kurigram)", "কুষ্টিয়া (Kushtia)", "লক্ষ্মীপুর (Lakshmipur)", 
        "লালমনিরহাট (Lalmonirhat)", "মাদারীপুর (Madaripur)", "মাগুরা (Magura)",
        "মানিকগঞ্জ (Manikganj)", "মেহেরপুর (Meherpur)", "মৌলভীবাজার (Moulvibazar)", 
        "মুন্সীগঞ্জ (Munshiganj)", "ময়মনসিংহ (Mymensingh)", "নওগাঁ (Naogaon)",
        "নড়াইল (Narail)", "নারায়ণগঞ্জ (Narayanganj)", "নরসিংদী (Narsingdi)", 
        "নাটোর (Natore)", "নেত্রকোণা (Netrokona)", "নীলফামারী (Nilphamari)",
        "নোয়াখালী (Noakhali)", "পাবনা (Pabna)", "পঞ্চগড় (Panchagarh)", 
        "পটুয়াখালী (Patuakhali)", "পিরোজপুর (Pirojpur)", "রাজবাড়ী (Rajbari)",
        "রাজশাহী (Rajshahi)", "রাঙ্গামাটি (Rangamati)", "রংপুর (Rangpur)", 
        "সাতক্ষীরা (Satkhira)", "শরীয়তপুর (Shariatpur)", "শেরপুর (Sherpur)",
        "সিরাজগঞ্জ (Sirajganj)", "সুনামগঞ্জ (Sunamganj)", "সিলেট (Sylhet)", 
        "টাঙ্গাইল (Tangail)", "ঠাকুরগাঁও (Thakurgaon)"
    };

    /**
     * Transport options for crop delivery
     */
    public static final String[] TRANSPORT_OPTIONS = {
        "ট্রাক (Truck)",
        "পিকআপ ভ্যান (Pickup Van)",
        "ভ্যান/রিকশা ভ্যান (Van/Rickshaw Van)",
        "নৌকা/দেশি নৌকা (Boat/Country Boat)",
        "সিএনজি/অটোরিকশা (CNG/Auto Rickshaw)",
        "মোটরসাইকেল (Motorcycle)",
        "ক্রেতা নিয়ে যাবেন (Buyer Pickup)",
        "বিক্রেতা সরবরাহ করবেন (Farmer Delivery)",
        "কুরিয়ার সার্ভিস (Courier Service)",
        "আলোচনা সাপেক্ষ (Negotiable)"
    };

    /**
     * Crop categories
     */
    public static final String[] CROP_CATEGORIES = {
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
        "মাছ (Fish)",
        "মুরগি/ডিম (Poultry/Eggs)",
        "দুধ/দুগ্ধজাত (Dairy)",
        "অন্যান্য (Others)"
    };

    /**
     * Units for quantity
     */
    public static final String[] UNITS = {
        "কেজি (kg)",
        "মণ (Maund - 40 kg)",
        "টন (Ton)",
        "লিটার (Liter)",
        "পিস/টা (Piece)",
        "ডজন (Dozen)",
        "বস্তা (Sack)",
        "কাঠা (Bundle)"
    };

    public static ObservableList<String> getDistrictsList() {
        return FXCollections.observableArrayList(DISTRICTS);
    }

    public static ObservableList<String> getTransportOptions() {
        return FXCollections.observableArrayList(TRANSPORT_OPTIONS);
    }

    public static ObservableList<String> getCropCategories() {
        return FXCollections.observableArrayList(CROP_CATEGORIES);
    }

    public static ObservableList<String> getUnits() {
        return FXCollections.observableArrayList(UNITS);
    }
}
