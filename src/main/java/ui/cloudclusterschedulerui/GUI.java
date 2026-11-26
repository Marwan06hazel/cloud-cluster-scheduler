package ui.cloudclusterschedulerui;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;

public class GUI extends Application {

    private StackPane contentHost;
    private SimulatorView simulatorView;
    private AnalysisReportView analysisReportView;

    private Button simulatorNavBtn;
    private Button analysisNavBtn;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        analysisReportView = new AnalysisReportView();
        simulatorView = new SimulatorView(this::onSimulationCompleted);

        contentHost = new StackPane();
        contentHost.getStyleClass().add("content-host");

        root.setLeft(buildSidebar());
        root.setCenter(contentHost);

        showView(simulatorView, simulatorNavBtn);

        Scene scene = new Scene(root, 1360, 860);
        URL cssUrl = getClass().getResource("styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("Warning: styles.css not found at /ui/cloudclusterschedulerui/styles.css");
        }

        stage.setTitle("Cloud Cluster Scheduler Simulator");
        stage.setMinWidth(1040);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(18);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(225);
        sidebar.setMinWidth(215);

        Label sectionLabel = new Label("WORKSPACE");
        sectionLabel.getStyleClass().add("sidebar-section-label");

        simulatorNavBtn = createNavButton(
                "Simulator",
                "M2 9h3l2-5 4 10 2-5h5"
        );
        simulatorNavBtn.setOnAction(e -> showView(simulatorView, simulatorNavBtn));

        analysisNavBtn = createNavButton(
                "Analysis report",
                "M3 15V9h3v6H3zm5 0V4h3v11H8zm5 0V7h3v8h-3z"
        );
        analysisNavBtn.setOnAction(e -> showView(analysisReportView, analysisNavBtn));

        VBox navGroup = new VBox(6, sectionLabel, simulatorNavBtn);

        sidebar.getChildren().addAll( navGroup);
        return sidebar;
    }

    private Button createNavButton(String text, String svgPathData) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPathData);
        icon.getStyleClass().add("nav-icon");

        StackPane iconWrap = new StackPane(icon);
        iconWrap.setMinWidth(35);
        iconWrap.setPrefWidth(35);

        Label label = new Label(text);
        label.getStyleClass().add("nav-label");

        HBox content = new HBox(10, iconWrap, label);
        content.setAlignment(Pos.CENTER_LEFT);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.getStyleClass().add("nav-button");
        return btn;
    }

    private void showView(Node view, Button activeBtn) {
        contentHost.getChildren().setAll(view);

        simulatorNavBtn.getStyleClass().remove("nav-button-active");
        analysisNavBtn.getStyleClass().remove("nav-button-active");
        if (!activeBtn.getStyleClass().contains("nav-button-active")) {
            activeBtn.getStyleClass().add("nav-button-active");
        }
    }

    private void onSimulationCompleted(List<SimulationResult> results, int q, int cs) {
        analysisReportView.updateResults(results, q, cs);
    }

    public static void main(String[] args) {
        launch(GUI.class, args);
    }
}