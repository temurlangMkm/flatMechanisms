package mechiscool;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import mechiscool.render.MechanismCanvasRenderer;
import mechiscool.render.MechanismSimulation;
import mechiscool.json.MechanismConfig;
import mechiscool.json.MechanismConfigLoader;
import mechiscool.render.Point2;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public class Controller {
    private final MechanismConfigLoader configLoader = new MechanismConfigLoader();
    private final MechanismCanvasRenderer canvasRenderer = new MechanismCanvasRenderer();
    private MechanismSimulation simulation;
    private MechanismConfig currentConfig;
    private String currentJsonText;
    private AnimationTimer animationTimer;
    private long lastFrameNanos;
    private boolean running;
    private boolean controlsInitialized = false;

    private Stage velocityStage;
    private Canvas velocityCanvas;
    private Stage accelerationStage;
    private Canvas accelerationCanvas;

    @FXML
    private TextArea contentInput;

    @FXML
    private Button loadB;

    @FXML
    private Canvas myCanvas;

    @FXML
    private Button restartB;

    @FXML
    private Button runB;

    @FXML
    private Button velocityDiagramB;

    @FXML
    private Button accelerationDiagramB;

    @FXML
    private void initialize() {
        loadB.setOnAction(event -> loadJsonFromFile());
        runB.setOnAction(event -> toggleRunPause());
        restartB.setOnAction(event -> restartSimulation());
        
        velocityDiagramB.setOnAction(event -> openDiagramWindow("Velocity Plan (v)", Color.BLUE, true));
        accelerationDiagramB.setOnAction(event -> openDiagramWindow("Acceleration Plan (a)", Color.RED, false));

        drawPlaceholder();
        createAnimationTimer();
        runB.setText("Run");
    }

    private void openDiagramWindow(String title, Color color, boolean isVelocity) {
        Stage stage = new Stage();
        stage.setTitle(title);
        Canvas canvas = new Canvas(500, 500);
        StackPane root = new StackPane(canvas);
        stage.setScene(new Scene(root));
        stage.show();

        if (isVelocity) {
            velocityStage = stage;
            velocityCanvas = canvas;
        } else {
            accelerationStage = stage;
            accelerationCanvas = canvas;
        }
        
        updateDiagrams();
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
            drawLoadedState(selectedFile);
        } catch (IOException exception) {
            showError("File read error", "Failed to read file: " + exception.getMessage());
        }
    }

    private void drawPlaceholder() {
        GraphicsContext graphics = myCanvas.getGraphicsContext2D();
        graphics.setFill(Color.rgb(245, 247, 250));
        graphics.fillRect(0, 0, myCanvas.getWidth(), myCanvas.getHeight());
        graphics.setFill(Color.rgb(90, 98, 112));
        graphics.fillText("Load a JSON mechanism configuration to begin.", 20, 30);
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
    }

    private void toggleRunPause() {
        if (running) {
            stopAnimation();
            return;
        }

        try {
            String jsonText = contentInput.getText();
            if (simulation == null || currentJsonText == null || !currentJsonText.equals(jsonText)) {
                currentConfig = configLoader.load(jsonText);
                currentJsonText = jsonText;
                simulation = new MechanismSimulation(currentConfig);

                if (!controlsInitialized) {
                    canvasRenderer.initializeControls(myCanvas, currentConfig, simulation.getPositions());
                    controlsInitialized = true;
                }
            }
            drawRunState();
            startAnimation();
        } catch (IllegalArgumentException exception) {
            showError("Invalid JSON configuration", exception.getMessage());
        }
    }

    private void restartSimulation() {
        stopAnimation();
        if (simulation != null && currentConfig != null) {
            simulation.reset();
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
        if (simulation == null || currentConfig == null) return;

        if (velocityStage != null && velocityStage.isShowing()) {
            canvasRenderer.renderDiagram(velocityCanvas, currentConfig, simulation.getVelocities(), Color.BLUE, "Velocity Plan", "m/s", simulation.getMaxVelocity());
        }
        if (accelerationStage != null && accelerationStage.isShowing()) {
            canvasRenderer.renderDiagram(accelerationCanvas, currentConfig, simulation.getAccelerations(), Color.RED, "Acceleration Plan", "m/s²", simulation.getMaxAcceleration());
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

    private void showError(String title, String message) {
        stopAnimation();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
