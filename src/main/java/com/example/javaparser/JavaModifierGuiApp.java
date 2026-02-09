package com.example.javaparser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.palexdev.materialfx.theming.JavaFXThemes;
import io.github.palexdev.materialfx.theming.MaterialFXStylesheets;
import io.github.palexdev.materialfx.theming.UserAgentBuilder;

import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.layout.GridPane;

/**
 * JavaFX UI for scanning, previewing, and applying Java modifier changes.
 * <p>
 * The UI flow is:
 * <ol>
 *   <li>Select input and output directories.</li>
 *   <li>Scan for planned changes.</li>
 *   <li>Preview details/diff per file.</li>
 *   <li>Apply selected or all plans.</li>
 * </ol>
 */
public class JavaModifierGuiApp extends Application {
    private static final int CONFLICT_PREVIEW_LIMIT = 10;
    private static final double DEFAULT_SPLIT_RATIO = 0.5;
    private static final double MIN_SPLIT_RATIO = 0.2;
    private static final double MAX_SPLIT_RATIO = 0.8;
    private static final String DEFAULT_THEME_NAME = "Light";
    private static final List<ThemeOption> THEMES = List.of(
        new ThemeOption("Light", "/styles/theme-light.css"),
        new ThemeOption("High Contrast", "/styles/theme-contrast.css"),
        new ThemeOption("Warm", "/styles/theme-warm.css")
    );

    private static final List<String> DIFF_ROW_STYLE_CLASSES = List.of(
        "diff-row-added", "diff-row-removed", "diff-row-changed", "diff-row-empty", "diff-row-same"
    );

    // Core services and persisted UI preferences.
    private final JavaModifierProcessor processor = new JavaModifierProcessor();
    private final ObservableList<FileChangePlan> plans = FXCollections.observableArrayList();
    private final GuiPreferences preferences = new GuiPreferences();
    private final DoubleProperty diffSplitRatio = new SimpleDoubleProperty(DEFAULT_SPLIT_RATIO);
    private String appStylesheet;

    // File list search/filter and checkbox multi-select.
    private final Set<FileChangePlan> checkedPlans = new HashSet<>();
    private final FilteredList<FileChangePlan> filteredPlans = new FilteredList<>(plans, p -> true);
    private TextField filterField;

    // Primary UI widgets (held as fields so background tasks can update them).
    private final ListView<FileChangePlan> fileList = new ListView<>(filteredPlans);
    private final ListView<DiffRow> diffList = new ListView<>();
    private final ListView<String> detailsList = new ListView<>();
    private final ComboBox<OutputFilePreview> outputSelector = new ComboBox<>();
    private final Label statusLabel = new Label("Ready");

    private Path inputDir;
    private Path outputDir;
    private String currentOriginal;

    /**
     * Standard JavaFX entry point.
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    /**
     * Build and wire the UI, then show the primary stage.
     */
    public void start(Stage stage) {
        // Load MaterialFX base theme before applying our custom theme stylesheets.
        applyMaterialFxTheme();
        boolean initialVerbose = getParameters().getRaw().contains("--verbose");
        LogLevelController.setVerbose(initialVerbose);

        double initialRatio = clamp(preferences.loadDiffSplitRatio(DEFAULT_SPLIT_RATIO), MIN_SPLIT_RATIO, MAX_SPLIT_RATIO);
        diffSplitRatio.set(initialRatio);
        ThemeOption initialTheme = resolveTheme(preferences.loadTheme(DEFAULT_THEME_NAME));

        // Restore persisted directories.
        Path savedInputDir = preferences.loadInputDir();
        Path savedOutputDir = preferences.loadOutputDir();

        TextField inputField = new TextField();
        inputField.setEditable(false);
        Button inputButton = new Button("Choose Input");

        TextField outputField = new TextField();
        outputField.setEditable(false);
        Button outputButton = new Button("Choose Output");

        if (savedInputDir != null) {
            inputDir = savedInputDir;
            inputField.setText(savedInputDir.toString());
        }
        if (savedOutputDir != null) {
            outputDir = savedOutputDir;
            outputField.setText(savedOutputDir.toString());
        }

        Button scanButton = new Button("Scan");
        Button applyButton = new Button("Apply Selected");
        Button applyAllButton = new Button("Apply All");
        CheckBox verboseCheckBox = new CheckBox("Verbose logs");
        verboseCheckBox.setSelected(initialVerbose);
        applyButton.setDisable(true);
        applyAllButton.setDisable(true);

        verboseCheckBox.selectedProperty()
            .addListener((obs, oldValue, newValue) -> LogLevelController.setVerbose(newValue));

        Slider splitSlider = new Slider(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO, initialRatio);
        splitSlider.setPrefWidth(160);
        splitSlider.setBlockIncrement(0.05);
        splitSlider.valueProperty().bindBidirectional(diffSplitRatio);
        // Persist slider changes immediately and also on drag finish.
        diffSplitRatio.addListener((obs, oldValue, newValue) -> {
            if (!splitSlider.isValueChanging()) {
                preferences.saveDiffSplitRatio(newValue.doubleValue());
            }
        });
        splitSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging) {
                preferences.saveDiffSplitRatio(diffSplitRatio.get());
            }
        });

        ComboBox<ThemeOption> themeSelector = new ComboBox<>(FXCollections.observableArrayList(THEMES));
        themeSelector.setPrefWidth(130);
        themeSelector.getSelectionModel().select(initialTheme);

        // Rule toggle checkboxes.
        CheckBox classToPublicCb = new CheckBox("Class -> public");
        classToPublicCb.setSelected(true);
        CheckBox fieldToPublicCb = new CheckBox("Field -> public");
        fieldToPublicCb.setSelected(true);
        CheckBox splitFilesCb = new CheckBox("Split files");
        splitFilesCb.setSelected(true);

        classToPublicCb.selectedProperty().addListener((obs, oldVal, newVal) -> updateProcessorConfig(classToPublicCb, fieldToPublicCb, splitFilesCb));
        fieldToPublicCb.selectedProperty().addListener((obs, oldVal, newVal) -> updateProcessorConfig(classToPublicCb, fieldToPublicCb, splitFilesCb));
        splitFilesCb.selectedProperty().addListener((obs, oldVal, newVal) -> updateProcessorConfig(classToPublicCb, fieldToPublicCb, splitFilesCb));

        inputButton.setOnAction(event -> {
            Path selected = chooseDirectory(stage, "Select input directory", inputDir);
            if (selected != null) {
                inputDir = selected;
                inputField.setText(selected.toString());
                preferences.saveInputDir(selected);
            }
        });

        outputButton.setOnAction(event -> {
            Path selected = chooseDirectory(stage, "Select output directory", outputDir);
            if (selected != null) {
                outputDir = selected;
                outputField.setText(selected.toString());
                preferences.saveOutputDir(selected);
            }
        });

        scanButton.setOnAction(event -> runScan(scanButton, applyAllButton, false));
        applyButton.setOnAction(event -> applySelected());
        applyAllButton.setOnAction(event -> applyAll(scanButton, applyButton, applyAllButton));

        // Search/filter field.
        filterField = new TextField();
        filterField.setPromptText("Search files...");
        filterField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredPlans.setPredicate(plan ->
                filter.isEmpty() || plan.getRelativePath().toString().toLowerCase().contains(filter)
            );
        });

        // Select All checkbox for multi-select.
        CheckBox selectAllCb = new CheckBox("Select All");
        selectAllCb.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                checkedPlans.addAll(filteredPlans);
            } else {
                checkedPlans.clear();
            }
            fileList.refresh();
        });

        fileList.setCellFactory(list -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(event -> {
                    FileChangePlan item = getItem();
                    if (item == null) return;
                    if (checkBox.isSelected()) {
                        checkedPlans.add(item);
                    } else {
                        checkedPlans.remove(item);
                    }
                });
            }

            @Override
            protected void updateItem(FileChangePlan item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.summary());
                    checkBox.setSelected(checkedPlans.contains(item));
                    setGraphic(checkBox);
                }
            }
        });

        fileList.getSelectionModel().selectedItemProperty().addListener((obs, oldPlan, newPlan) -> {
            applyButton.setDisable(newPlan == null && checkedPlans.isEmpty());
            if (newPlan == null) {
                detailsList.getItems().clear();
                clearPreview();
            } else {
                detailsList.setItems(FXCollections.observableArrayList(newPlan.detailLines()));
                loadPreview(newPlan);
            }
        });

        detailsList.setFocusTraversable(false);
        detailsList.setPlaceholder(new Label("Select a file to see changes"));

        diffList.setFocusTraversable(false);
        diffList.setPlaceholder(new Label("Select a file to preview"));
        diffList.setCellFactory(list -> new DiffRowCell(diffSplitRatio));

        outputSelector.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(OutputFilePreview item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getRelativePath().toString());
                }
            }
        });
        outputSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(OutputFilePreview item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getRelativePath().toString());
                }
            }
        });
        outputSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                diffList.getItems().clear();
            } else {
                if (currentOriginal == null) {
                    diffList.getItems().clear();
                } else {
                    diffList.setItems(FXCollections.observableArrayList(DiffUtil.diff(currentOriginal, newItem.getContent())));
                }
            }
        });

        HBox inputRow = new HBox(8, new Label("Input"), inputField, inputButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);
        HBox outputRow = new HBox(8, new Label("Output"), outputField, outputButton, scanButton);
        HBox.setHgrow(outputField, Priority.ALWAYS);
        HBox rulesRow = new HBox(12, new Label("Rules"), classToPublicCb, fieldToPublicCb, splitFilesCb);
        rulesRow.setAlignment(Pos.CENTER_LEFT);

        VBox topBox = new VBox(8, inputRow, outputRow, rulesRow);
        topBox.setPadding(new Insets(10));

        HBox fileFilterRow = new HBox(8, filterField, selectAllCb);
        HBox.setHgrow(filterField, Priority.ALWAYS);
        fileFilterRow.setAlignment(Pos.CENTER_LEFT);

        VBox fileListBox = new VBox(6, fileFilterRow, fileList);
        VBox.setVgrow(fileList, Priority.ALWAYS);
        fileListBox.setPadding(new Insets(4));

        HBox outputFileRow = new HBox(8, new Label("Output file"), outputSelector);
        HBox.setHgrow(outputSelector, Priority.ALWAYS);

        GridPane diffHeader = new GridPane();
        ColumnConstraints leftCol = new ColumnConstraints();
        leftCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints rightCol = new ColumnConstraints();
        rightCol.setHgrow(Priority.ALWAYS);
        bindSplitColumns(leftCol, rightCol, diffSplitRatio);
        diffHeader.getColumnConstraints().addAll(leftCol, rightCol);
        Label leftHeader = new Label("Original");
        Label rightHeader = new Label("Modified");
        leftHeader.setStyle("-fx-font-weight: bold;");
        rightHeader.setStyle("-fx-font-weight: bold;");
        diffHeader.add(leftHeader, 0, 0);
        diffHeader.add(rightHeader, 1, 0);

        VBox diffBox = new VBox(8, outputFileRow, diffHeader, diffList);
        diffBox.setPadding(new Insets(8));
        VBox.setVgrow(diffList, Priority.ALWAYS);

        Tab detailsTab = new Tab("Details", detailsList);
        detailsTab.setClosable(false);
        Tab diffTab = new Tab("Diff", diffBox);
        diffTab.setClosable(false);

        TabPane tabPane = new TabPane(detailsTab, diffTab);

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(fileListBox, tabPane);
        splitPane.setDividerPositions(0.35);

        HBox splitControl = new HBox(6, new Label("Diff split"), splitSlider);
        HBox themeControl = new HBox(6, new Label("Theme"), themeSelector);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottomBox = new HBox(12, applyButton, applyAllButton, verboseCheckBox, splitControl, themeControl, spacer, statusLabel);
        bottomBox.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(splitPane);
        root.setBottom(bottomBox);

        Scene scene = new Scene(root, 1000, 640);
        applyTheme(scene, initialTheme);
        // Apply and persist theme changes.
        themeSelector.valueProperty().addListener((obs, oldValue, newValue) -> {
            ThemeOption resolved = newValue == null ? resolveTheme(DEFAULT_THEME_NAME) : newValue;
            applyTheme(scene, resolved);
            preferences.saveTheme(resolved.name());
        });
        stage.setTitle("JavaParser Modifier Preview");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Update the processor config from the rule checkboxes and invalidate the parser cache.
     */
    private void updateProcessorConfig(CheckBox classToPublicCb, CheckBox fieldToPublicCb, CheckBox splitFilesCb) {
        ProcessorConfig config = processor.getConfig();
        config.setClassToPublic(classToPublicCb.isSelected());
        config.setFieldToPublic(fieldToPublicCb.isSelected());
        config.setSplitFiles(splitFilesCb.isSelected());
        processor.invalidateCache();
    }

    /**
     * Show a directory chooser and return the selected path or null.
     */
    private Path chooseDirectory(Stage stage, String title, Path initialDir) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (initialDir != null && Files.isDirectory(initialDir)) {
            chooser.setInitialDirectory(initialDir.toFile());
        }
        File selected = chooser.showDialog(stage);
        return selected == null ? null : selected.toPath();
    }

    /**
     * Kick off background scan and refresh the plans list on success.
     */
    private void runScan(Button scanButton, Button applyAllButton, boolean autoSelectFirst) {
        if (inputDir == null || outputDir == null) {
            statusLabel.setText("Select both input and output directories");
            return;
        }

        statusLabel.setText("Scanning...");
        scanButton.setDisable(true);
        applyAllButton.setDisable(true);

        Task<List<FileChangePlan>> task = new Task<>() {
            @Override
            protected List<FileChangePlan> call() throws Exception {
                return processor.analyze(inputDir);
            }
        };

        task.setOnSucceeded(event -> {
            plans.setAll(task.getValue());
            checkedPlans.clear();
            fileList.getSelectionModel().clearSelection();
            detailsList.getItems().clear();
            clearPreview();
            applyAllButton.setDisable(plans.isEmpty());
            statusLabel.setText("Found " + plans.size() + " file(s) with changes");
            scanButton.setDisable(false);
            if (autoSelectFirst && !plans.isEmpty()) {
                fileList.getSelectionModel().select(0);
                fileList.scrollTo(0);
            }
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            statusLabel.setText("Scan failed: " + (ex == null ? "unknown error" : ex.getMessage()));
            scanButton.setDisable(false);
            applyAllButton.setDisable(plans.isEmpty());
        });

        // Run in a background thread to avoid blocking the JavaFX UI thread.
        Thread thread = new Thread(task, "scan-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Apply changes for the currently selected or checked files.
     */
    private void applySelected() {
        List<FileChangePlan> targets;
        if (!checkedPlans.isEmpty()) {
            targets = List.copyOf(checkedPlans);
        } else {
            FileChangePlan selected = fileList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            targets = List.of(selected);
        }

        if (inputDir == null || outputDir == null) {
            statusLabel.setText("Select both input and output directories");
            return;
        }

        List<Path> conflicts = collectConflicts(targets);
        OutputConflictStrategy conflictStrategy = resolveConflictStrategy(conflicts, "Apply Selected");
        if (conflictStrategy == null) {
            statusLabel.setText("Apply canceled");
            return;
        }
        int conflictCount = conflicts.size();

        statusLabel.setText("Applying " + targets.size() + " file(s)...");

        List<FileChangePlan> snapshot = List.copyOf(targets);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (FileChangePlan plan : snapshot) {
                    processor.applySingle(inputDir, outputDir, plan.getSourceFile(), conflictStrategy);
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            String suffix = "";
            if (conflictStrategy == OutputConflictStrategy.SKIP_EXISTING && conflictCount > 0) {
                suffix = " (skipped " + conflictCount + " existing file(s))";
            }
            statusLabel.setText("Applied " + snapshot.size() + " file(s)" + suffix);
            showInfoDialog("Apply Selected Complete", "Applied files: " + snapshot.size()
                + (conflictStrategy == OutputConflictStrategy.SKIP_EXISTING && conflictCount > 0
                    ? "\nSkipped existing files: " + conflictCount : ""));
        });
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            statusLabel.setText("Apply failed: " + (ex == null ? "unknown error" : ex.getMessage()));
        });

        Thread thread = new Thread(task, "apply-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Apply changes for all scanned files, with progress updates in the status bar.
     */
    private void applyAll(Button scanButton, Button applySelectedButton, Button applyAllButton) {
        if (plans.isEmpty()) {
            return;
        }
        if (inputDir == null || outputDir == null) {
            statusLabel.setText("Select both input and output directories");
            return;
        }

        List<FileChangePlan> snapshot = List.copyOf(plans);
        List<Path> conflicts = collectConflicts(snapshot);
        OutputConflictStrategy conflictStrategy = resolveConflictStrategy(conflicts, "Apply All");
        if (conflictStrategy == null) {
            statusLabel.setText("Apply canceled");
            return;
        }
        int conflictCount = conflicts.size();

        scanButton.setDisable(true);
        applySelectedButton.setDisable(true);
        applyAllButton.setDisable(true);
        fileList.setDisable(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = snapshot.size();
                for (int i = 0; i < total; i++) {
                    FileChangePlan plan = snapshot.get(i);
                    updateMessage("Applying " + (i + 1) + "/" + total + ": " + plan.getRelativePath());
                    processor.applySingle(inputDir, outputDir, plan.getSourceFile(), conflictStrategy);
                }
                return null;
            }
        };

        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            statusLabel.textProperty().unbind();
            String suffix = "";
            if (conflictStrategy == OutputConflictStrategy.SKIP_EXISTING && conflictCount > 0) {
                suffix = " (skipped " + conflictCount + " existing file(s))";
            }
            statusLabel.setText("Applied " + snapshot.size() + " file(s)" + suffix + ", rescanning...");
            String message = "Applied files: " + snapshot.size();
            if (conflictStrategy == OutputConflictStrategy.SKIP_EXISTING && conflictCount > 0) {
                message += "\nSkipped existing files: " + conflictCount;
            }
            showInfoDialog("Apply All Complete", message);
            scanButton.setDisable(false);
            fileList.setDisable(false);
            runScan(scanButton, applyAllButton, true);
            applySelectedButton.setDisable(fileList.getSelectionModel().getSelectedItem() == null);
            applyAllButton.setDisable(plans.isEmpty());
        });

        task.setOnFailed(event -> {
            statusLabel.textProperty().unbind();
            Throwable ex = task.getException();
            statusLabel.setText("Apply all failed: " + (ex == null ? "unknown error" : ex.getMessage()));
            scanButton.setDisable(false);
            applySelectedButton.setDisable(fileList.getSelectionModel().getSelectedItem() == null);
            applyAllButton.setDisable(plans.isEmpty());
            fileList.setDisable(false);
        });

        Thread thread = new Thread(task, "apply-all-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Install MaterialFX base theme and assets as a global stylesheet.
     */
    private static void applyMaterialFxTheme() {
        UserAgentBuilder.builder()
            .themes(JavaFXThemes.MODENA)
            .themes(MaterialFXStylesheets.forAssemble(true))
            .setDeploy(true)
            .setResolveAssets(true)
            .build()
            .setGlobal();
    }

    /**
     * Match a persisted theme name to a theme option (defaults to first).
     */
    private ThemeOption resolveTheme(String name) {
        if (name == null) {
            return THEMES.get(0);
        }
        for (ThemeOption theme : THEMES) {
            if (theme.name().equalsIgnoreCase(name)) {
                return theme;
            }
        }
        return THEMES.get(0);
    }

    /**
     * Remove the previous stylesheet and apply the newly selected theme.
     */
    private void applyTheme(Scene scene, ThemeOption theme) {
        if (scene == null || theme == null) {
            return;
        }
        if (appStylesheet != null) {
            scene.getStylesheets().remove(appStylesheet);
        }
        appStylesheet = resolveStylesheet(theme.resourcePath());
        if (appStylesheet != null) {
            scene.getStylesheets().add(appStylesheet);
        }
    }

    /**
     * Resolve a classpath stylesheet to a URL for JavaFX.
     */
    private String resolveStylesheet(String resourcePath) {
        return Objects.requireNonNull(getClass().getResource(resourcePath)).toExternalForm();
    }

    /**
     * Reuse the current app theme for dialogs.
     */
    private void applyDialogStyle(Alert alert) {
        if (appStylesheet != null) {
            alert.getDialogPane().getStylesheets().add(appStylesheet);
        }
        alert.getDialogPane().getStyleClass().add("cool-dialog");
    }

    /**
     * Show a themed info dialog with the supplied text.
     */
    private void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        applyDialogStyle(alert);
        alert.showAndWait();
    }

    /**
     * Bind column widths in the diff header to the split ratio.
     */
    private static void bindSplitColumns(
        ColumnConstraints leftCol,
        ColumnConstraints rightCol,
        ReadOnlyDoubleProperty ratio
    ) {
        setSplitColumns(leftCol, rightCol, ratio.get());
        ratio.addListener((obs, oldValue, newValue) -> setSplitColumns(leftCol, rightCol, newValue.doubleValue()));
    }

    /**
     * Apply a percentage split to the diff header columns.
     */
    private static void setSplitColumns(ColumnConstraints leftCol, ColumnConstraints rightCol, double ratio) {
        double leftPercent = ratio * 100.0;
        leftCol.setPercentWidth(leftPercent);
        rightCol.setPercentWidth(100.0 - leftPercent);
    }

    /**
     * Clamp a value between a minimum and maximum.
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Small DTO for a displayable theme option.
     */
    private record ThemeOption(String name, String resourcePath) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Ask the user how to handle output conflicts (overwrite/skip/cancel).
     */
    private OutputConflictStrategy resolveConflictStrategy(List<Path> conflicts, String actionLabel) {
        if (conflicts.isEmpty()) {
            return OutputConflictStrategy.OVERWRITE;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Output conflicts");
        alert.setHeaderText(conflicts.size() + " output file(s) already exist.");
        alert.setContentText("Choose how to proceed for " + actionLabel + ".");
        applyDialogStyle(alert);

        ButtonType overwrite = new ButtonType("Overwrite Existing");
        ButtonType skip = new ButtonType("Skip Existing");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(overwrite, skip, cancel);

        TextArea details = new TextArea(formatConflictDetails(conflicts));
        details.setEditable(false);
        details.setWrapText(false);
        details.setPrefRowCount(Math.min(CONFLICT_PREVIEW_LIMIT, conflicts.size()));
        alert.getDialogPane().setExpandableContent(details);
        alert.getDialogPane().setExpanded(conflicts.size() <= CONFLICT_PREVIEW_LIMIT);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancel) {
            return null;
        }
        return result.get() == overwrite ? OutputConflictStrategy.OVERWRITE : OutputConflictStrategy.SKIP_EXISTING;
    }

    /**
     * Format a preview list of conflicting relative paths for the dialog.
     */
    private String formatConflictDetails(List<Path> conflicts) {
        String preview = conflicts.stream()
            .limit(CONFLICT_PREVIEW_LIMIT)
            .map(Path::toString)
            .collect(Collectors.joining(System.lineSeparator()));
        if (conflicts.size() <= CONFLICT_PREVIEW_LIMIT) {
            return preview;
        }
        return preview + System.lineSeparator() + "... and " + (conflicts.size() - CONFLICT_PREVIEW_LIMIT) + " more";
    }

    /**
     * Compute output conflicts for a set of plans based on the current output directory.
     */
    private List<Path> collectConflicts(List<FileChangePlan> plans) {
        if (outputDir == null) {
            return List.of();
        }
        Set<Path> conflicts = new LinkedHashSet<>();
        for (FileChangePlan plan : plans) {
            for (Path relative : computeOutputRelativePaths(plan)) {
                Path outputPath = outputDir.resolve(relative);
                if (Files.exists(outputPath)) {
                    conflicts.add(relative);
                }
            }
        }
        return new ArrayList<>(conflicts);
    }

    /**
     * Compute all output relative paths for a plan (including split outputs).
     */
    private List<Path> computeOutputRelativePaths(FileChangePlan plan) {
        List<Path> outputs = new ArrayList<>();
        Path relative = plan.getRelativePath();
        Path relativeParent = relative.getParent();

        if (plan.getSplitMode() == SplitMode.SPLIT_ALL) {
            for (ClassChangePlan classPlan : plan.getClassPlans()) {
                outputs.add(classOutputPath(relativeParent, classPlan.getClassName()));
            }
            return outputs;
        }

        outputs.add(relative);
        if (plan.getSplitMode() == SplitMode.SPLIT_OTHERS) {
            for (ClassChangePlan classPlan : plan.getClassPlans()) {
                if (classPlan.isMoveToNewFile()) {
                    outputs.add(classOutputPath(relativeParent, classPlan.getClassName()));
                }
            }
        }
        return outputs;
    }

    /**
     * Build an output path for a class split into its own file.
     */
    private Path classOutputPath(Path parent, String className) {
        Path fileName = Path.of(className + ".java");
        return parent == null ? fileName : parent.resolve(fileName);
    }

    /**
     * Load the preview diff for a file on a background thread.
     */
    private void loadPreview(FileChangePlan plan) {
        outputSelector.getItems().clear();
        outputSelector.setDisable(true);
        currentOriginal = null;
        diffList.getItems().clear();
        if (!statusLabel.textProperty().isBound()) {
            statusLabel.setText("Preparing preview...");
        }

        Task<PreviewData> task = new Task<>() {
            @Override
            protected PreviewData call() throws Exception {
                String original = java.nio.file.Files.readString(plan.getSourceFile());
                List<OutputFilePreview> outputs = processor.previewOutputs(inputDir, plan.getSourceFile());
                return new PreviewData(original, outputs);
            }
        };

        task.setOnSucceeded(event -> {
            PreviewData data = task.getValue();
            currentOriginal = data.originalContent();
            outputSelector.setItems(FXCollections.observableArrayList(data.outputs()));
            outputSelector.setDisable(data.outputs().isEmpty());
            if (!data.outputs().isEmpty()) {
                outputSelector.getSelectionModel().select(0);
            }
            if (!statusLabel.textProperty().isBound()) {
                statusLabel.setText("Preview ready");
            }
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            if (!statusLabel.textProperty().isBound()) {
                statusLabel.setText("Preview failed: " + (ex == null ? "unknown error" : ex.getMessage()));
            }
            outputSelector.setDisable(true);
            diffList.getItems().clear();
        });

        Thread thread = new Thread(task, "preview-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Reset preview state and clear diff/output selectors.
     */
    private void clearPreview() {
        outputSelector.getItems().clear();
        outputSelector.setDisable(true);
        currentOriginal = null;
        diffList.getItems().clear();
    }

    /**
     * Background task payload for preview generation.
     */
    private record PreviewData(String originalContent, List<OutputFilePreview> outputs) {}

    /**
     * Map a DiffType to the corresponding CSS style class name.
     */
    private static String diffRowStyleClass(DiffType type) {
        return switch (type) {
            case ADDED -> "diff-row-added";
            case REMOVED -> "diff-row-removed";
            case CHANGED -> "diff-row-changed";
            case EMPTY -> "diff-row-empty";
            case SAME -> "diff-row-same";
        };
    }

    /**
     * Replace any diff-row-* style class on the node with the new one.
     */
    private static void applyRowStyleClass(HBox box, DiffType type) {
        box.getStyleClass().removeAll(DIFF_ROW_STYLE_CLASSES);
        box.getStyleClass().add(diffRowStyleClass(type));
    }

    /**
     * Custom cell to render diff rows with line numbers and character-level highlights.
     */
    private static final class DiffRowCell extends ListCell<DiffRow> {
        private final Label leftLine = new Label();
        private final Label rightLine = new Label();
        private final TextFlow leftFlow = new TextFlow();
        private final TextFlow rightFlow = new TextFlow();
        private final HBox leftBox = new HBox(6);
        private final HBox rightBox = new HBox(6);
        private final GridPane grid = new GridPane();

        private DiffRowCell(ReadOnlyDoubleProperty splitRatio) {
            ColumnConstraints leftCol = new ColumnConstraints();
            leftCol.setHgrow(Priority.ALWAYS);
            ColumnConstraints rightCol = new ColumnConstraints();
            rightCol.setHgrow(Priority.ALWAYS);
            bindSplitColumns(leftCol, rightCol, splitRatio);
            grid.getColumnConstraints().addAll(leftCol, rightCol);

            leftLine.setMinWidth(48);
            leftLine.setAlignment(Pos.TOP_RIGHT);
            leftLine.getStyleClass().add("diff-line-number");
            leftLine.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");
            rightLine.setMinWidth(48);
            rightLine.setAlignment(Pos.TOP_RIGHT);
            rightLine.getStyleClass().add("diff-line-number");
            rightLine.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

            leftFlow.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
            rightFlow.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

            leftBox.getChildren().addAll(leftLine, leftFlow);
            rightBox.getChildren().addAll(rightLine, rightFlow);
            HBox.setHgrow(leftFlow, Priority.ALWAYS);
            HBox.setHgrow(rightFlow, Priority.ALWAYS);

            grid.add(leftBox, 0, 0);
            grid.add(rightBox, 1, 0);

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(DiffRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            leftLine.setText(item.getLeftLineNumber() == 0 ? "" : String.valueOf(item.getLeftLineNumber()));
            rightLine.setText(item.getRightLineNumber() == 0 ? "" : String.valueOf(item.getRightLineNumber()));
            leftFlow.getChildren().setAll(buildText(item.getLeftSegments(), true));
            rightFlow.getChildren().setAll(buildText(item.getRightSegments(), false));
            applyRowStyleClass(leftBox, item.getLeftType());
            applyRowStyleClass(rightBox, item.getRightType());
            setGraphic(grid);
        }

        /**
         * Build a list of styled Text nodes for a diff line, using CSS classes.
         */
        private List<Text> buildText(List<DiffSegment> segments, boolean isLeft) {
            List<Text> nodes = new java.util.ArrayList<>();
            for (DiffSegment segment : segments) {
                Text text = new Text(segment.getText());
                text.getStyleClass().removeAll("diff-highlight-left", "diff-highlight-right", "diff-text-normal");
                if (segment.isHighlight()) {
                    text.getStyleClass().add(isLeft ? "diff-highlight-left" : "diff-highlight-right");
                } else {
                    text.getStyleClass().add("diff-text-normal");
                }
                nodes.add(text);
            }
            return nodes;
        }
    }
}
