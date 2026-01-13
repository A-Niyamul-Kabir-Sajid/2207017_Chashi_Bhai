module com.sajid._207017_chashi_bhai {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;
    requires transitive java.sql;
    requires java.net.http;
    requires java.desktop;
    requires org.xerial.sqlitejdbc;
    requires jbcrypt;
    requires com.google.gson;

    opens com.sajid._207017_chashi_bhai to javafx.fxml;
    opens com.sajid._207017_chashi_bhai.controllers to javafx.fxml, javafx.base;
    opens com.sajid._207017_chashi_bhai.models to javafx.fxml, javafx.base, com.google.gson;
    opens com.sajid._207017_chashi_bhai.services to javafx.fxml;
    opens com.sajid._207017_chashi_bhai.utils to javafx.fxml;

    exports com.sajid._207017_chashi_bhai;
    exports com.sajid._207017_chashi_bhai.models;
    exports com.sajid._207017_chashi_bhai.utils;
    exports com.sajid._207017_chashi_bhai.services;
}