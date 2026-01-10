module com.sajid._207017_chashi_bhai {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;
    requires transitive java.sql;
    requires java.desktop;
    requires org.xerial.sqlitejdbc;
    requires jbcrypt;
    requires com.google.gson;
    requires google.cloud.firestore;
    requires google.cloud.core;
    
    // Firebase Admin SDK (unnamed modules - accessed via classpath)
    requires static firebase.admin;
    requires static com.google.auth;
    requires static com.google.auth.oauth2;

    opens com.sajid._207017_chashi_bhai to javafx.fxml;
    opens com.sajid._207017_chashi_bhai.controllers to javafx.fxml;
    opens com.sajid._207017_chashi_bhai.models to javafx.fxml;
    opens com.sajid._207017_chashi_bhai.services to javafx.fxml;

    exports com.sajid._207017_chashi_bhai;
}