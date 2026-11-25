module ui.cloudclusterschedulerui {
    requires javafx.controls;
    requires javafx.fxml;


    opens ui.cloudclusterschedulerui to javafx.fxml;
    exports ui.cloudclusterschedulerui;
}