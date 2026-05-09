package mechiscool;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import mechiscool.json.MechanismConfig;
import mechiscool.json.MechanismConfigLoader;
import mechiscool.render.MechanismCanvasRenderer;
import mechiscool.render.MechanismSimulation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public class Controller {
    private static final double PLAN_ZOOM_FACTOR = 1.15;
    private static final double MIN_PLAN_ZOOM = 0.2;
    private static final double MAX_PLAN_ZOOM = 8.0;

    private final MechanismConfigLoader configLoader = new MechanismConfigLoader();
    private final MechanismCanvasRenderer canvasRenderer = new MechanismCanvasRenderer();
    private MechanismSimulation simulation;
    private MechanismConfig currentConfig;
    private String currentJsonText;
    private AnimationTimer animationTimer;
    private long lastFrameNanos;
    private boolean running;
    private boolean controlsInitialized;
    private boolean updatingAngleSlider;
    private double velocityPlanZoom = 1.0;
    private double accelerationPlanZoom = 1.0;

    @FXML
    private TextArea contentInput;

    @FXML
    private Button loadB;

    @FXML
    private Canvas myCanvas;

    @FXML
    private StackPane myCanvasPane;

    @FXML
    private Button restartB;

    @FXML
    private Button runB;

    @FXML
    private Slider crankAngleSlider;

    @FXML
    private Label angleValueLabel;

    @FXML
    private Canvas velocityCanvas;

    @FXML
    private StackPane velocityCanvasPane;

    @FXML
    private Button velocityZoomInB;

    @FXML
    private Button velocityZoomOutB;

    @FXML
    private Canvas accelerationCanvas;

    @FXML
    private StackPane accelerationCanvasPane;

    @FXML
    private Button accelerationZoomInB;

    @FXML
    private Button accelerationZoomOutB;

    @FXML
    private void initialize() {
        bindCanvasToPane(myCanvas, myCanvasPane);
        bindCanvasToPane(velocityCanvas, velocityCanvasPane);
        bindCanvasToPane(accelerationCanvas, accelerationCanvasPane);

        loadB.setOnAction(event -> loadJsonFromFile());
        runB.setOnAction(event -> toggleRunPause());
        restartB.setOnAction(event -> restartSimulation());
        velocityZoomInB.setOnAction(event -> zoomVelocityPlan(PLAN_ZOOM_FACTOR));
        velocityZoomOutB.setOnAction(event -> zoomVelocityPlan(1.0 / PLAN_ZOOM_FACTOR));
        accelerationZoomInB.setOnAction(event -> zoomAccelerationPlan(PLAN_ZOOM_FACTOR));
        accelerationZoomOutB.setOnAction(event -> zoomAccelerationPlan(1.0 / PLAN_ZOOM_FACTOR));
        crankAngleSlider.valueProperty().addListener((observable, oldValue, newValue) -> handleAngleSliderChanged(newValue.doubleValue()));

        drawPlaceholder();
        createAnimationTimer();
        runB.setText("Run");
        updateAngleSlider(0);
    }

    private void bindCanvasToPane(Canvas canvas, StackPane pane) {
        canvas.widthProperty().bind(pane.widthProperty());
        canvas.heightProperty().bind(pane.heightProperty());
        canvas.widthProperty().addListener((observable, oldValue, newValue) -> redrawCurrentState());
        canvas.heightProperty().addListener((observable, oldValue, newValue) -> redrawCurrentState());
    }

    private void loadJsonFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open JSON file");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files", "*.json")
        );

        Window window = loadB.getScene() == null ? null : loadB.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile == null) {
            return;
        }

        try {
            String jsonContent = Files.readString(selectedFile.toPath(), StandardCharsets.UTF_8);
            contentInput.setText(jsonContent);
            simulation = null;
            currentConfig = null;
            currentJsonText = null;
            drawLoadedState(selectedFile);
        } catch (IOException exception) {
            showError("File read error", "Failed to read file: " + exception.getMessage());
        }
    }

    private void handleAngleSliderChanged(double degrees) {
        updateAngleLabel(degrees);
        if (updatingAngleSlider || simulation == null || currentConfig == null) {
            return;
        }

        if (currentJsonText == null || !currentJsonText.equals(contentInput.getText())) {
            try {
                ensureSimulationLoaded();
            } catch (IllegalArgumentException exception) {
                showError("Invalid JSON configuration", exception.getMessage());
                return;
            }
        }

        stopAnimation();
        simulation.setPhaseDegrees(degrees);
        drawRunState();
    }

    private void drawPlaceholder() {
        GraphicsContext graphics = myCanvas.getGraphicsContext2D();
        graphics.setFill(Color.rgb(245, 247, 250));
        graphics.fillRect(0, 0, myCanvas.getWidth(), myCanvas.getHeight());
        graphics.setFill(Color.rgb(90, 98, 112));
        graphics.fillText("Load a JSON mechanism configuration to begin.", 20, 30);

        drawPlanPlaceholder(velocityCanvas, "Velocity Plan");
        drawPlanPlaceholder(accelerationCanvas, "Acceleration Plan");
    }

    private void drawPlanPlaceholder(Canvas canvas, String title) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setFill(Color.rgb(255, 254, 249));
        graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        graphics.setFill(Color.rgb(90, 98, 112));
        graphics.fillText(title, 12, 22);
    }

    private void drawLoadedState(File file) {
        stopAnimation();
        GraphicsContext graphics = myCanvas.getGraphicsContext2D();
        graphics.setFill(Color.WHITE);
        graphics.fillRect(0, 0, myCanvas.getWidth(), myCanvas.getHeight());
        graphics.setFill(Color.rgb(30, 30, 30));
        graphics.fillText("JSON file loaded", 20, 30);
        graphics.fillText("File: " + file.getName(), 20, 55);
        graphics.fillText("Path: " + file.getAbsolutePath(), 20, 80);

        drawPlanPlaceholder(velocityCanvas, "Velocity Plan");
        drawPlanPlaceholder(accelerationCanvas, "Acceleration Plan");
        updateAngleSlider(0);
    }

    private void toggleRunPause() {
        if (running) {
            stopAnimation();
            return;
        }

        try {
            ensureSimulationLoaded();
            drawRunState();
            startAnimation();
        } catch (IllegalArgumentException exception) {
            showError("Invalid JSON configuration", exception.getMessage());
        }
    }

    private void ensureSimulationLoaded() {
        String jsonText = contentInput.getText();
        if (simulation == null || currentJsonText == null || !currentJsonText.equals(jsonText)) {
            currentConfig = configLoader.load(jsonText);
            currentJsonText = jsonText;
            simulation = new MechanismSimulation(currentConfig);
            updateAngleSlider(simulation.getPhaseDegrees());

            if (!controlsInitialized) {
                canvasRenderer.initializeControls(myCanvas, this::drawRunState);
                controlsInitialized = true;
            }
        }
    }

    private void restartSimulation() {
        stopAnimation();
        if (simulation != null && currentConfig != null) {
            simulation.reset();
            updateAngleSlider(simulation.getPhaseDegrees());
            drawRunState();
        } else {
            drawPlaceholder();
        }
    }

    private void drawRunState() {
        if (simulation == null || currentConfig == null) {
            return;
        }
        canvasRenderer.render(myCanvas, currentConfig, simulation.getPositions(), Map.of(), Map.of());
        updateDiagrams();
    }

    private void updateDiagrams() {
        if (simulation == null || currentConfig == null) {
            return;
        }

        canvasRenderer.renderVelocityPlan(
                velocityCanvas,
                currentConfig,
                simulation.getPositions(),
                simulation.getVelocities(),
                simulation.getMaxVelocity(),
                velocityPlanZoom
        );
        canvasRenderer.renderAccelerationPlan(
                accelerationCanvas,
                currentConfig,
                simulation.getPositions(),
                simulation.getVelocities(),
                simulation.getAccelerations(),
                simulation.getMaxAcceleration(),
                accelerationPlanZoom
        );
    }

    private void zoomVelocityPlan(double factor) {
        velocityPlanZoom = clamp(velocityPlanZoom * factor, MIN_PLAN_ZOOM, MAX_PLAN_ZOOM);
        updateDiagrams();
    }

    private void zoomAccelerationPlan(double factor) {
        accelerationPlanZoom = clamp(accelerationPlanZoom * factor, MIN_PLAN_ZOOM, MAX_PLAN_ZOOM);
        updateDiagrams();
    }

    private void redrawCurrentState() {
        if (simulation != null && currentConfig != null) {
            drawRunState();
        } else {
            drawPlaceholder();
        }
    }

    private void createAnimationTimer() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = now;
                    return;
                }

                double deltaSeconds = Math.min((now - lastFrameNanos) / 1_000_000_000.0, 0.05);
                lastFrameNanos = now;

                if (simulation != null && currentConfig != null) {
                    simulation.step(deltaSeconds);
                    updateAngleSlider(simulation.getPhaseDegrees());
                    drawRunState();
                }
            }
        };
    }

    private void startAnimation() {
        if (animationTimer == null) {
            createAnimationTimer();
        }
        lastFrameNanos = 0L;
        running = true;
        runB.setText("Pause");
        animationTimer.start();
    }

    private void stopAnimation() {
        running = false;
        lastFrameNanos = 0L;
        runB.setText("Run");
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    private void updateAngleSlider(double degrees) {
        updatingAngleSlider = true;
        crankAngleSlider.setValue(degrees);
        updatingAngleSlider = false;
        updateAngleLabel(degrees);
    }

    private void updateAngleLabel(double degrees) {
        angleValueLabel.setText(String.format("%.0f deg", degrees));
    }

    private void showError(String title, String message) {
        stopAnimation();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
