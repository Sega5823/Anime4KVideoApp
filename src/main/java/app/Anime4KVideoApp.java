package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JavaFX wrapper for FFmpeg + libplacebo Anime4K shader presets.
 *
 * Features:
 * - bundled ffmpeg / ffprobe binaries
 * - bundled Anime4K shaders from resources
 * - choose input folder and scan multiple videos
 * - choose output folder
 * - validate resources and folders
 * - auto-build a combined temporary shader file for the selected preset
 * - run jobs sequentially or in parallel
 * - show generated command and live log
 *
 * Requirements:
 * - Java 17+
 * - JavaFX configured
 */
public class Anime4KVideoApp extends Application {

    private final List<Process> currentProcesses = new CopyOnWriteArrayList<>();
    private final Preferences prefs = Preferences.userNodeForPackage(Anime4KVideoApp.class);
    private final BundledTools bundledTools = new BundledTools();
    private final BatchPreparationService batchPreparationService = new BatchPreparationService();
    private ExecutorService taskExecutor;
    private volatile boolean batchStopRequested;
    private volatile List<VideoItem> activeBatchItems = List.of();

    private String ffmpegPath;
    private TextField inputFolderField;
    private TextField outputFolderField;
    private ComboBox<String> resolutionComboBox;
    private ComboBox<String> cqComboBox;
    private ComboBox<String> videoEncoderComboBox;
    private ComboBox<String> threadsComboBox;
    private ComboBox<Preset> presetComboBox;
    private ComboBox<String> runModeComboBox;
    private TextArea commandArea;
    private TextArea logArea;
    private Button startButton;
    private Button stopButton;
    private Button scanButton;
    private Label statusLabel;
    private Label validationLabel;
    private TableView<VideoItem> videoTable;
    private CheckBox testModeCheckBox;
    private ComboBox<String> testDurationComboBox;
    private ProgressBar overallProgressBar;
    private Label overallProgressLabel;
    private ComboBox<String> existingFileModeComboBox;
    private ComboBox<String> outputNamingModeComboBox;
    private TextField outputSuffixField;
    private CheckBox deleteProcessedSourceCheckBox;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Anime4K FFmpeg UI");
        loadSettings();
        if (inputFolderField == null) inputFolderField = new TextField();
        if (outputFolderField == null) outputFolderField = new TextField();
        resolutionComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "3840x2160",
                "2560x1440",
                "1920x1080",
                "1600x900",
                "1280x720"
        ));
        resolutionComboBox.setEditable(true);
        resolutionComboBox.getEditor().setText(prefs.get("resolution", "3840x2160"));
        cqComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "14",
                "16",
                "18",
                "20",
                "22"
        ));
        cqComboBox.getEditor().setText(prefs.get("cq", "18"));
        videoEncoderComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "NVIDIA (h264_nvenc)",
                "AMD (h264_amf)",
                "Intel (h264_qsv)",
                "CPU (libx264)"
        ));
        videoEncoderComboBox.getSelectionModel().select(prefs.get("videoEncoder", "NVIDIA (h264_nvenc)"));
        threadsComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "Auto",
                "2",
                "4",
                "6",
                "8",
                "10",
                "12",
                "16"
        ));
        threadsComboBox.getSelectionModel().select(prefs.get("threads", "10"));
        testModeCheckBox = new CheckBox("Test mode");

        testDurationComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "15",
                "30",
                "60",
                "120",
                "300"
        ));
        testDurationComboBox.getSelectionModel().select(prefs.get("testDuration", "60"));
        testModeCheckBox.setSelected(prefs.getBoolean("testMode", false));
        testDurationComboBox.setDisable(!testModeCheckBox.isSelected());

        existingFileModeComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "Overwrite",
                "Skip",
                "Auto rename"
        ));
        existingFileModeComboBox.getSelectionModel().select(prefs.get("existingFileMode", "Overwrite"));

        outputNamingModeComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "Preset name + suffix",
                "Original filename"
        ));
        outputNamingModeComboBox.getSelectionModel().select(prefs.get("outputNamingMode", "Preset name + suffix"));

        outputSuffixField = new TextField();
        outputSuffixField.setText(prefs.get("outputSuffix", ""));
        outputSuffixField.setPromptText("Suffix, for example _test or _cq16");
        deleteProcessedSourceCheckBox = new CheckBox("Delete processed source files");
        deleteProcessedSourceCheckBox.setSelected(prefs.getBoolean("deleteProcessedSource", false));

        threadsComboBox.valueProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
        });
        videoEncoderComboBox.valueProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
            validateAll();
        });
        existingFileModeComboBox.valueProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
            videoTable.refresh();
        });
        outputNamingModeComboBox.valueProperty().addListener((obs, o, n) -> {
            saveSettings();
            updateOutputNamingControls();
            refreshCommandPreview();
            videoTable.refresh();
        });
        outputSuffixField.textProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
            videoTable.refresh();
        });
        deleteProcessedSourceCheckBox.selectedProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
            videoTable.refresh();
        });
        overallProgressBar = new ProgressBar(0);
        overallProgressBar.setPrefWidth(220);

        overallProgressLabel = new Label("0 / 0");

        testModeCheckBox.selectedProperty().addListener((obs, oldVal, enabled) -> {
            saveSettings();
            testDurationComboBox.setDisable(!enabled);
            refreshCommandPreview();
        });
        testDurationComboBox.valueProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
        });

        cqComboBox.setEditable(true);

        presetComboBox = new ComboBox<>(FXCollections.observableArrayList(PresetCatalog.defaultPresets()));
        presetComboBox.getSelectionModel().selectFirst();
        String savedPreset = prefs.get("preset", null);
        if (savedPreset != null) {
            presetComboBox.getItems().stream()
                    .filter(p -> p.name().equals(savedPreset))
                    .findFirst()
                    .ifPresent(p -> presetComboBox.getSelectionModel().select(p));
        }

        runModeComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "Sequential",
                "Parallel (2 workers)",
                "Parallel (4 workers)"
        ));
        runModeComboBox.getSelectionModel().select(prefs.get("runMode", "Sequential"));

        commandArea = new TextArea();
        commandArea.setEditable(false);
        commandArea.setPrefRowCount(15);
        commandArea.setWrapText(true);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(16);
        logArea.setWrapText(false);

        startButton = new Button("Start batch");
        stopButton = new Button("Stop");
        stopButton.setDisable(true);
        scanButton = new Button("Scan videos");
        statusLabel = new Label("Idle");
        validationLabel = new Label("Not validated");

        Button inputFolderBrowse = new Button("...");
        inputFolderBrowse.setOnAction(e -> chooseInputFolder(stage));

        Button outputFolderBrowse = new Button("...");
        outputFolderBrowse.setOnAction(e -> chooseOutputFolder(stage));

        Button refreshButton = new Button("Refresh command");
        Button validateButton = new Button("Validate paths");

        refreshButton.setOnAction(e -> refreshCommandPreview());
        validateButton.setOnAction(e -> validateAll());
        scanButton.setOnAction(e -> scanVideos());

        startButton.setOnAction(e -> startBatch());
        stopButton.setOnAction(e -> stopBatch());

        presetComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            saveSettings();
            refreshCommandPreview();
            validateAll();
            videoTable.refresh();
        });
        inputFolderField.textProperty().addListener((obs, o, n) -> {
            saveSettings();
            validateAll();
        });
        outputFolderField.textProperty().addListener((obs, o, n) -> {
            saveSettings();
            validateAll();
            videoTable.refresh();
        });

        resolutionComboBox.valueProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
        });
        cqComboBox.getEditor().textProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
        });
        resolutionComboBox.getEditor().textProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
        });
        cqComboBox.valueProperty().addListener((obs, o, n) -> {
            saveSettings();
            refreshCommandPreview();
        });

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));

        int row = 0;

        grid.add(new Label("Input folder"), 0, row);
        grid.add(inputFolderField, 1, row);
        grid.add(inputFolderBrowse, 2, row++);

        grid.add(new Label("Output folder"), 0, row);
        grid.add(outputFolderField, 1, row);
        grid.add(outputFolderBrowse, 2, row++);

        grid.add(new Label("Preset"), 0, row);
        grid.add(presetComboBox, 1, row++);

        Label runModeHelp = new Label("?");
        runModeHelp.setStyle("-fx-font-weight: bold; -fx-text-fill: #2b579a; -fx-cursor: hand;");
        Tooltip runModeTooltip = new Tooltip(
                "Sequential: process one video at a time.\n\n" +
                        "Parallel: process several videos at once.\n\n" +
                        "For Anime4K, Sequential is usually safer because GPU load is high."
        );

        runModeTooltip.setWrapText(true);
        runModeTooltip.setMaxWidth(300);

        bindClickableTooltip(runModeHelp, runModeTooltip);

        HBox runModeBox = new HBox(6,
                runModeComboBox,
                runModeHelp
        );
        runModeBox.setAlignment(Pos.CENTER_LEFT);

        grid.add(new Label("Run mode"), 0, row);
        grid.add(runModeBox, 1, row++);

        Label cqHelp = new Label("?");
        cqHelp.setStyle("-fx-font-weight: bold; -fx-text-fill: #2b579a; -fx-cursor: hand;");
        Tooltip cqTooltip = new Tooltip(
                "CQ controls output quality.\n\n" +
                        "Lower value = higher quality and larger file.\n" +
                        "Higher value = lower quality and smaller file.\n\n" +
                        "Suggested values:\n" +
                        "16 = high quality\n" +
                        "18 = balanced\n" +
                        "20+ = faster and smaller"
        );

        cqTooltip.setWrapText(true);
        cqTooltip.setMaxWidth(300);

        bindClickableTooltip(cqHelp, cqTooltip);

        HBox cqBox = new HBox(4,
                new Label("CQ"),
                cqComboBox,
                cqHelp
        );
        cqBox.setAlignment(Pos.CENTER_LEFT);

        HBox threadsBox = new HBox(4,
                new Label("Threads"),
                threadsComboBox
        );
        threadsBox.setAlignment(Pos.CENTER_LEFT);

        HBox encoderBox = new HBox(4,
                new Label("Encoder"),
                videoEncoderComboBox
        );
        encoderBox.setAlignment(Pos.CENTER_LEFT);

        HBox testBox = new HBox(6,
                testModeCheckBox,
                new Label("Seconds"),
                testDurationComboBox
        );
        testBox.setAlignment(Pos.CENTER_LEFT);

        HBox sizeBox = new HBox(12,
                resolutionComboBox,
                cqBox,
                encoderBox,
                threadsBox,
                testBox
        );
        sizeBox.setAlignment(Pos.CENTER_LEFT);

        grid.add(new Label("Resolution"), 0, row);
        grid.add(sizeBox, 1, row++);


        videoTable = new TableView<>();
        videoTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        videoTable.setItems(FXCollections.observableArrayList());

        TableColumn<VideoItem, String> nameCol = new TableColumn<>("Video");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        nameCol.setPrefWidth(220);

        TableColumn<VideoItem, String> pathCol = new TableColumn<>("Input");
        pathCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().path().toString()));
        pathCol.setPrefWidth(360);

        TableColumn<VideoItem, String> outputCol = new TableColumn<>("Output");
        outputCol.setCellValueFactory(data -> new SimpleStringProperty(previewOutputPath(data.getValue())));
        outputCol.setPrefWidth(420);

        TableColumn<VideoItem, Double> progressCol = new TableColumn<>("Progress");
        progressCol.setCellValueFactory(data -> data.getValue().progressProperty().asObject());
        progressCol.setCellFactory(column -> new TableCell<>() {
            private final ProgressBar progressBar = new ProgressBar(0);

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    double value = item == null ? 0.0 : item;
                    progressBar.setProgress(value);
                    setGraphic(progressBar);
                }
            }
        });
        progressCol.setPrefWidth(140);

        videoTable.getColumns().addAll(nameCol, pathCol, outputCol, progressCol);
        videoTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(VideoItem item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setStyle("");
                } else {
                    switch (item.statusProperty().get()) {
                        case "Processing" -> setStyle("-fx-background-color: #fff3cd;");
                        case "Done" -> setStyle("-fx-background-color: #d4edda;");
                        case "Error" -> setStyle("-fx-background-color: #f8d7da;");
                        case "Stopped" -> setStyle("-fx-background-color: #e2e3e5;");
                        default -> setStyle("");
                    }
                }
            }
        });
        HBox actions = new HBox(8,
                refreshButton,
                validateButton,
                scanButton,
                startButton,
                stopButton,
                statusLabel,
                validationLabel,
                overallProgressBar,
                overallProgressLabel
        );
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox outputOptionsBox = new HBox(12,
                new Label("If file exists"), existingFileModeComboBox,
                new Label("Output name"), outputNamingModeComboBox,
                new Label("Output suffix"), outputSuffixField,
                deleteProcessedSourceCheckBox
        );
        outputOptionsBox.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10,
                grid,
                outputOptionsBox,
                new Label("Discovered videos"),
                videoTable,
                new Label("Generated command sample"),
                commandArea,
                actions,
                new Label("FFmpeg log"),
                logArea
        );
        root.setPadding(new Insets(12));
        VBox.setVgrow(videoTable, Priority.ALWAYS);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        VBox.setVgrow(commandArea, Priority.SOMETIMES);

        updateOutputNamingControls();
        refreshCommandPreview();
        validateAll();
        autoScanSavedInputFolder();

        stage.setScene(new Scene(root, 1180, 860));
        stage.show();
    }

    private void autoScanSavedInputFolder() {
        String inputFolderText = inputFolderField.getText();
        if (inputFolderText == null || inputFolderText.isBlank()) {
            return;
        }

        Path folder = Paths.get(inputFolderText.trim());
        if (!Files.isDirectory(folder) || !videoTable.getItems().isEmpty()) {
            return;
        }

        scanVideos();
    }

    private void loadSettings() {
        AppSettings settings = AppSettings.load(prefs);
        ffmpegPath = bundledTools.getBundledFfmpegPath();
        inputFolderField = new TextField(settings.inputFolder());
        outputFolderField = new TextField(settings.outputFolder());
    }

    private void saveSettings() {
        String resolution = resolutionComboBox == null
                ? "3840x2160"
                : (resolutionComboBox.isEditable() ? resolutionComboBox.getEditor().getText() : resolutionComboBox.getValue());
        String cq = cqComboBox == null
                ? "18"
                : (cqComboBox.isEditable() ? cqComboBox.getEditor().getText() : cqComboBox.getValue());

        new AppSettings(
                inputFolderField == null ? "" : inputFolderField.getText(),
                outputFolderField == null ? "" : outputFolderField.getText(),
                resolution,
                cq,
                videoEncoderComboBox == null ? "NVIDIA (h264_nvenc)" : videoEncoderComboBox.getValue(),
                threadsComboBox == null ? "10" : threadsComboBox.getValue(),
                presetComboBox == null || presetComboBox.getValue() == null ? null : presetComboBox.getValue().name(),
                runModeComboBox == null ? "Sequential" : runModeComboBox.getValue(),
                testModeCheckBox != null && testModeCheckBox.isSelected(),
                testDurationComboBox == null ? "60" : testDurationComboBox.getValue(),
                existingFileModeComboBox == null ? "Overwrite" : existingFileModeComboBox.getValue(),
                outputNamingModeComboBox == null ? "Preset name + suffix" : outputNamingModeComboBox.getValue(),
                outputSuffixField == null ? "" : outputSuffixField.getText(),
                deleteProcessedSourceCheckBox != null && deleteProcessedSourceCheckBox.isSelected()
        ).save(prefs);
    }

    private void chooseInputFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select input folder");

        File initialDir = getExistingDirectory(inputFolderField.getText());
        if (initialDir != null) {
            chooser.setInitialDirectory(initialDir);
        }

        File dir = chooser.showDialog(stage);
        if (dir != null) {
            inputFolderField.setText(dir.getAbsolutePath());
            scanVideos();
        }
    }

    private void chooseOutputFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select output folder");

        File initialDir = getExistingDirectory(outputFolderField.getText());
        if (initialDir != null) {
            chooser.setInitialDirectory(initialDir);
        }

        File dir = chooser.showDialog(stage);
        if (dir != null) {
            outputFolderField.setText(dir.getAbsolutePath());
        }
    }

    private void refreshCommandPreview() {
        try {
            VideoItem sample = getSampleVideoItem();
            BatchConfig config = captureBatchConfig();
            PreparedJob preparedJob = batchPreparationService.prepareJob(sample, config);
            commandArea.setText(prettyCommand(preparedJob.command()));
            statusLabel.setText("Ready");
        } catch (IllegalArgumentException ex) {
            commandArea.setText("Cannot build command yet: " + ex.getMessage());
            statusLabel.setText("Waiting for input");
        }
    }

    private VideoItem getSampleVideoItem() {
        if (!videoTable.getItems().isEmpty()) {
            return videoTable.getItems().get(0);
        }
        String inputFolder = inputFolderField.getText();
        if (inputFolder == null || inputFolder.isBlank()) {
            throw new IllegalArgumentException("input folder is empty");
        }
        Path folder = Paths.get(inputFolder.trim());
        return new VideoItem("sample.mkv", folder.resolve("sample.mkv"));
    }

    private File getExistingDirectory(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return null;
        }

        File dir = new File(pathText.trim());
        if (dir.exists() && dir.isDirectory()) {
            return dir;
        }

        return null;
    }
    private BatchConfig captureBatchConfig() {
        String ffmpeg = notBlank(ffmpegPath, "ffmpeg path is empty");
        String resolution = resolutionComboBox.isEditable()
                ? resolutionComboBox.getEditor().getText()
                : resolutionComboBox.getValue();

        resolution = notBlank(resolution, "resolution is empty");

        String[] parts = resolution.toLowerCase().split("x");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Resolution must be in WIDTHxHEIGHT format, for example 3840x2160");
        }

        String width = parts[0].trim();
        String height = parts[1].trim();

        if (!width.matches("\\d+") || !height.matches("\\d+")) {
            throw new IllegalArgumentException("Resolution must contain only numbers, for example 3840x2160");
        }
        String cq = cqComboBox.isEditable()
                ? cqComboBox.getEditor().getText()
                : cqComboBox.getValue();

        cq = notBlank(cq, "CQ is empty");

        if (!cq.matches("\\d+")) {
            throw new IllegalArgumentException("CQ must be a number, for example 18");
        }
        String outputFolder = notBlank(outputFolderField.getText(), "output folder is empty");
        Preset preset = Objects.requireNonNull(presetComboBox.getValue(), "preset is null");
        String videoEncoder = Objects.requireNonNullElse(videoEncoderComboBox.getValue(), "NVIDIA (h264_nvenc)");
        String existingMode = Objects.requireNonNullElse(existingFileModeComboBox.getValue(), "Overwrite");
        String outputNamingMode = Objects.requireNonNullElse(outputNamingModeComboBox.getValue(), "Preset name + suffix");
        String outputSuffix = "Original filename".equals(outputNamingMode)
                ? ""
                : (outputSuffixField.getText() == null ? "" : outputSuffixField.getText());
        boolean deleteProcessedSource = deleteProcessedSourceCheckBox.isSelected();

        Integer threadCount = null;
        String threads = threadsComboBox.getValue();
        if (threads != null && !"Auto".equalsIgnoreCase(threads)) {
            if (!threads.matches("\\d+")) {
                throw new IllegalArgumentException("Threads must be Auto or a number");
            }
            threadCount = Integer.parseInt(threads);
        }

        boolean testMode = testModeCheckBox.isSelected();
        int testDurationSeconds = 0;
        if (testMode) {
            String seconds = Objects.requireNonNull(testDurationComboBox.getValue(), "test duration is null");
            if (!seconds.matches("\\d+")) {
                throw new IllegalArgumentException("Test duration must be a number");
            }
            testDurationSeconds = Integer.parseInt(seconds);
        }

        Path combinedShaderFile = bundledTools.createCombinedShaderFile(preset);
        return new BatchConfig(
                ffmpeg,
                bundledTools.getBundledFfprobePath(),
                outputFolder,
                width,
                height,
                cq,
                videoEncoder,
                preset,
                existingMode,
                outputNamingMode,
                outputSuffix,
                deleteProcessedSource,
                threadCount,
                testMode,
                testDurationSeconds,
                combinedShaderFile
        );
    }

    private void updateOutputNamingControls() {
        boolean preserveOriginalName = "Original filename".equals(outputNamingModeComboBox.getValue());
        outputSuffixField.setDisable(preserveOriginalName);
        if (preserveOriginalName) {
            outputSuffixField.setPromptText("Suffix is disabled when original filename is preserved");
        } else {
            outputSuffixField.setPromptText("Suffix, for example _test or _cq16");
        }
    }

    private String previewOutputPath(VideoItem item) {
        try {
            BatchConfig config = captureBatchConfig();
            return batchPreparationService.previewOutputPath(item, config).toString();
        } catch (Exception e) {
            return "";
        }
    }
    private String prettyCommand(List<String> cmd) {
        StringBuilder sb = new StringBuilder();
        for (String part : cmd) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(quoteIfNeeded(part));
        }
        return sb.toString();
    }

    private String quoteIfNeeded(String value) {
        if (value.contains(" ") || value.contains("\t")) {
            return '"' + value + '"';
        }
        return value;
    }

    private String notBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void validateAll() {
        List<String> problems = new ArrayList<>();

        String ffmpegText = ffmpegPath;
        if (ffmpegText == null || ffmpegText.isBlank()) {
            problems.add("ffmpeg path is empty");
        } else if (!batchPreparationService.isFfmpegResolvable(ffmpegText.trim())) {
            problems.add("ffmpeg not found");
        }

        Preset preset = presetComboBox.getValue();
        if (preset != null) {
            for (String shader : preset.shaders()) {
                String resourcePath = "/shaders/" + shader;
                if (!bundledTools.hasShaderResource(shader)) {
                    problems.add("missing shader resource: " + shader);
                }
            }
        }

        String inputFolderText = inputFolderField.getText();
        if (inputFolderText == null || inputFolderText.isBlank()) {
            problems.add("input folder is empty");
        } else if (!Files.isDirectory(Paths.get(inputFolderText.trim()))) {
            problems.add("input folder does not exist");
        }

        String outputFolderText = outputFolderField.getText();
        if (outputFolderText == null || outputFolderText.isBlank()) {
            problems.add("output folder is empty");
        } else if (!Files.isDirectory(Paths.get(outputFolderText.trim()))) {
            problems.add("output folder does not exist");
        }

        String resolution = resolutionComboBox.isEditable()
                ? resolutionComboBox.getEditor().getText()
                : resolutionComboBox.getValue();

        if (resolution == null || resolution.isBlank()) {
            problems.add("resolution is empty");
        } else {
            String[] parts = resolution.toLowerCase().split("x");
            if (parts.length != 2 || !parts[0].trim().matches("\\d+") || !parts[1].trim().matches("\\d+")) {
                problems.add("resolution must be WIDTHxHEIGHT, for example 3840x2160");
            }
        }

        if (problems.isEmpty()) {
            validationLabel.setText("Validation: OK");
        } else {
            validationLabel.setText("Validation: " + String.join(" | ", problems));
        }
    }

    private void bindClickableTooltip(Label label, Tooltip tooltip) {
        label.setOnMouseClicked(e -> {
            tooltip.show(label, e.getScreenX(), e.getScreenY() + 10);

            Scene scene = label.getScene();
            EventHandler<MouseEvent> handler = new EventHandler<>() {
                @Override
                public void handle(MouseEvent event) {
                    tooltip.hide();
                    scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, this);
                }
            };

            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, handler);
            e.consume();
        });
    }

    private void scanVideos() {
        String inputFolderText = inputFolderField.getText();
        if (inputFolderText == null || inputFolderText.isBlank()) {
            appendLog("Input folder is empty\n");
            return;
        }

        Path folder = Paths.get(inputFolderText.trim());
        if (!Files.isDirectory(folder)) {
            appendLog("Input folder does not exist: " + folder + "\n");
            return;
        }

        try (Stream<Path> files = Files.list(folder)) {
            List<VideoItem> videos = files
                    .filter(Files::isRegularFile)
                    .filter(this::isVideoFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .map(path -> new VideoItem(path.getFileName().toString(), path))
                    .collect(Collectors.toList());

            videoTable.getItems().setAll(videos);
            appendLog("Scanned videos: " + videos.size() + "\n");
            refreshCommandPreview();
        } catch (IOException e) {
            appendLog("Scan error: " + e.getMessage() + "\n");
        }
    }

    private boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mkv") || name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".webm");
    }

    private void startBatch() {
        if (taskExecutor != null && !taskExecutor.isShutdown()) {
            appendLog("Batch is already running\n");
            return;
        }

        validateAll();
        if (!validationLabel.getText().equals("Validation: OK")) {
            appendLog("Cannot start batch. Fix validation errors first.\n");
            return;
        }

        List<VideoItem> selected = new ArrayList<>(videoTable.getSelectionModel().getSelectedItems());
        List<VideoItem> jobs = selected.isEmpty() ? new ArrayList<>(videoTable.getItems()) : selected;

        if (jobs.isEmpty()) {
            appendLog("No videos found to process\n");
            return;
        }

        BatchConfig config;
        List<PreparedJob> preparedJobs;
        try {
            config = captureBatchConfig();
            preparedJobs = batchPreparationService.prepareJobs(jobs, config);
            batchPreparationService.validateBatchStart(config, preparedJobs);
        } catch (IllegalArgumentException e) {
            appendLog("Cannot start batch: " + e.getMessage() + "\n");
            return;
        }

        logArea.clear();
        int threads = switch (runModeComboBox.getValue()) {
            case "Parallel (2 workers)" -> 2;
            case "Parallel (4 workers)" -> 4;
            default -> 1;
        };

        preparedJobs.forEach(job -> {
            job.item().progressProperty().set(0.0);
            job.item().statusProperty().set("Pending");
        });
        taskExecutor = Executors.newFixedThreadPool(threads);
        batchStopRequested = false;
        activeBatchItems = preparedJobs.stream().map(PreparedJob::item).toList();
        setBatchControlsRunning(true);
        statusLabel.setText("Running batch: " + preparedJobs.size() + " file(s)");
        appendLog("Starting batch with " + preparedJobs.size() + " file(s), mode: " + runModeComboBox.getValue() + "\n\n");
        overallProgressBar.setProgress(0);
        overallProgressLabel.setText("0 / " + preparedJobs.size());

        CountDownLatch latch = new CountDownLatch(preparedJobs.size());

        for (PreparedJob job : preparedJobs) {
            taskExecutor.submit(() -> {
                try {
                    runSingleJob(job, config);
                } finally {
                    latch.countDown();
                }
            });
        }

        ExecutorService executor = taskExecutor;
        Thread completionThread = new Thread(() -> {
            try {
                latch.await();
                Platform.runLater(() -> {
                    updateOverallProgress();
                    statusLabel.setText(batchStopRequested ? "Stopped" : "Batch finished");
                    setBatchControlsRunning(false);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (taskExecutor == executor) {
                    executor.shutdown();
                    taskExecutor = null;
                }
                activeBatchItems = List.of();
            }
        }, "anime4k-batch-completion");
        completionThread.setDaemon(true);
        completionThread.start();
    }
    private Double extractTimeSeconds(String line) {
        int idx = line.indexOf("time=");
        if (idx < 0) {
            return null;
        }

        String value = line.substring(idx + 5).trim();
        int space = value.indexOf(' ');
        if (space >= 0) {
            value = value.substring(0, space);
        }

        String[] parts = value.split(":");
        if (parts.length != 3) {
            return null;
        }

        try {
            double h = Double.parseDouble(parts[0]);
            double m = Double.parseDouble(parts[1]);
            double s = Double.parseDouble(parts[2]);
            return h * 3600 + m * 60 + s;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    private Double extractProgressSeconds(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        if (line.startsWith("out_time=")) {
            return parseClockValue(line.substring("out_time=".length()).trim());
        }

        if (line.startsWith("out_time_ms=")) {
            return parseMicrosValue(line.substring("out_time_ms=".length()).trim());
        }

        if (line.startsWith("out_time_us=")) {
            return parseMicrosValue(line.substring("out_time_us=".length()).trim());
        }

        return extractTimeSeconds(line);
    }

    private Double parseClockValue(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) {
            return null;
        }

        try {
            double h = Double.parseDouble(parts[0]);
            double m = Double.parseDouble(parts[1]);
            double s = Double.parseDouble(parts[2]);
            return h * 3600 + m * 60 + s;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseMicrosValue(String value) {
        try {
            return Double.parseDouble(value) / 1_000_000.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateOverallProgress() {
        List<VideoItem> items = activeBatchItems;
        if (items == null || items.isEmpty()) {
            overallProgressBar.setProgress(0);
            overallProgressLabel.setText("0 / 0");
            return;
        }

        double totalProgress = 0.0;
        int completed = 0;
        for (VideoItem item : items) {
            double progress = Math.max(0.0, Math.min(1.0, item.progressProperty().get()));
            totalProgress += progress;
            String status = item.statusProperty().get();
            if ("Done".equals(status)) {
                completed++;
            }
        }

        double overallProgress = totalProgress / items.size();
        int overallPercent = (int) Math.round(overallProgress * 100.0);
        overallProgressBar.setProgress(overallProgress);
        overallProgressLabel.setText(completed + " / " + items.size() + ", " + overallPercent + "% overall");
    }

    private void setBatchControlsRunning(boolean running) {
        startButton.setDisable(running);
        stopButton.setDisable(!running);
        scanButton.setDisable(running);
        inputFolderField.setDisable(running);
        outputFolderField.setDisable(running);
        resolutionComboBox.setDisable(running);
        cqComboBox.setDisable(running);
        videoEncoderComboBox.setDisable(running);
        threadsComboBox.setDisable(running);
        presetComboBox.setDisable(running);
        runModeComboBox.setDisable(running);
        testModeCheckBox.setDisable(running);
        testDurationComboBox.setDisable(running || !testModeCheckBox.isSelected());
        existingFileModeComboBox.setDisable(running);
        outputNamingModeComboBox.setDisable(running);
        outputSuffixField.setDisable(running || "Original filename".equals(outputNamingModeComboBox.getValue()));
        deleteProcessedSourceCheckBox.setDisable(running);
    }

    private void runSingleJob(PreparedJob job, BatchConfig config) {
        VideoItem item = job.item();
        List<String> command = job.command();

        Process process = null;
        try {
            appendLog("=== START: " + item.name() + " ===\n");
            Platform.runLater(() -> item.statusProperty().set("Processing"));
            appendLog(prettyCommand(command) + "\n");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();
            double totalSeconds = config.testMode()
                    ? config.testDurationSeconds()
                    : batchPreparationService.probeDurationSeconds(item.path(), config);
            currentProcesses.add(process);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLog("[" + item.name() + "] " + line + "\n");

                    Double current = extractProgressSeconds(line);
                    if (current != null && totalSeconds > 0) {
                        double progress = Math.min(1.0, current / totalSeconds);
                        int percent = (int) Math.round(progress * 100.0);

                        Platform.runLater(() -> {
                            item.progressProperty().set(progress);
                            statusLabel.setText("Processing: " + item.name() + " (" + percent + "%)");
                            updateOverallProgress();
                        });
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                finalizeSuccessfulJob(job);
            } else if (job.replaceSourceInPlace()) {
                cleanupTemporaryOutput(job.commandOutputPath());
            }

            boolean success = exitCode == 0;
            Platform.runLater(() -> {
                item.progressProperty().set(success ? 1.0 : 0.0);
                item.statusProperty().set(success ? "Done" : "Error");
                updateOverallProgress();
                videoTable.refresh();
            });
            appendLog("=== END: " + item.name() + " (exit " + exitCode + ") ===\n\n");
        } catch (IOException e) {
            if (job.replaceSourceInPlace()) {
                cleanupTemporaryOutput(job.commandOutputPath());
            }
            Platform.runLater(() -> {
                item.progressProperty().set(0.0);
                item.statusProperty().set("Error");
                updateOverallProgress();
                videoTable.refresh();
            });
            appendLog("I/O error for " + item.name() + ": " + e.getMessage() + "\n");
        } catch (InterruptedException e) {
            Platform.runLater(() -> {
                item.progressProperty().set(0.0);
                item.statusProperty().set("Stopped");
                updateOverallProgress();
                videoTable.refresh();
            });
            Thread.currentThread().interrupt();
            appendLog("Interrupted: " + item.name() + "\n");
        } finally {
            if (process != null) {
                currentProcesses.remove(process);
            }
        }
    }

    private void finalizeSuccessfulJob(PreparedJob job) throws IOException {
        Path sourcePath = job.item().path();
        Path commandOutputPath = job.commandOutputPath();
        Path finalOutputPath = job.finalOutputPath();

        if (job.replaceSourceInPlace()) {
            Files.delete(sourcePath);
            Files.move(commandOutputPath, finalOutputPath, StandardCopyOption.REPLACE_EXISTING);
            appendLog("Replaced source with processed file: " + finalOutputPath + "\n");
            return;
        }

        if (job.deleteSourceAfterSuccess()) {
            Files.delete(sourcePath);
            appendLog("Deleted processed source: " + sourcePath + "\n");
        }
    }

    private void cleanupTemporaryOutput(Path temporaryOutputPath) {
        try {
            Files.deleteIfExists(temporaryOutputPath);
        } catch (IOException e) {
            appendLog("Cannot delete temporary output: " + temporaryOutputPath + " (" + e.getMessage() + ")\n");
        }
    }

    private void stopBatch() {
        appendLog("Stopping batch...\n");
        batchStopRequested = true;
        for (Process process : currentProcesses) {
            process.destroy();
        }
        currentProcesses.clear();
        if (taskExecutor != null) {
            taskExecutor.shutdownNow();
            taskExecutor = null;
        }
        activeBatchItems = List.of();
        setBatchControlsRunning(false);
        statusLabel.setText("Stopped");
    }

    private void appendLog(String text) {
        Platform.runLater(() -> logArea.appendText(text));
    }

    @Override
    public void stop() {
        stopBatch();
    }

}



