module com.sajid._207017_chashi_bhai {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires jbcrypt;
    requires com.google.gson;


    opens com.sajid._207017_chashi_bhai to javafx.fxml;
    opens com.sajid._207017_chashi_bhai.controllers to javafx.fxml;
    
    exports com.sajid._207017_chashi_bhai;
    exports com.sajid._207017_chashi_bhai.controllers;
    exports com.sajid._207017_chashi_bhai.utils;
}