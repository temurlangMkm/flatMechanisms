module com.example.mechiscool {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires org.apache.pdfbox;
    opens mechiscool to javafx.fxml;
    opens mechiscool.json to com.fasterxml.jackson.databind;
    exports mechiscool;
}
