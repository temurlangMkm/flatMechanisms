module com.example.mechiscool {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires javafx.controls;
    requires javafx.fxml;

    opens mechiscool to javafx.fxml;
    opens mechiscool.json to com.fasterxml.jackson.databind;
    exports mechiscool;
}
