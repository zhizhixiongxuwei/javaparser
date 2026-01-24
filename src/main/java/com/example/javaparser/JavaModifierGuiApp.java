package com.example.javaparser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.geometry.Pos;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.layout.GridPane;

public class JavaModifierGuiApp extends Application {
    private final JavaModifierProcessor processor = new JavaModifierProcessor();
    private final ObservableList<FileChangePlan> plans = FXCollections.observableArrayList();

    private final ListView<FileChangePlan> fileList = new ListView<>(plans);
    private final ListView<DiffRow> diffList = new ListView<>();
    private final ListView<String> detailsList = new ListView<>();
    private final ComboBox<OutputFilePreview> outputSelector = new ComboBox<>();
    private final Label statusLabel = new Label("Ready");

    private Path inputDir;
    private Path outputDir;
    private String currentOriginal;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        TextField inputField = new TextField();
        inputField.setEditable(false);
        Button inputButton = new Button("Choose Input");

        TextField outputField = new TextField();
        outputField.setEditable(false);
        Button outputButton = new Button("Choose Output");

        Button scanButton = new Button("Scan");
        Button applyButton = new Button("Apply Selected");
        Button applyAllButton = new Button("Apply All");
        applyButton.setDisable(true);
        applyAllButton.setDisable(true);

        inputButton.setOnAction(event -> {
            Path selected = chooseDirectory(stage, "Select input directory");
            if (selected != null) {
                inputDir = selected;
                inputField.setText(selected.toString());
            }
        });

        outputButton.setOnAction(event -> {
            Path selected = chooseDirectory(stage, "Select output directory");
            if (selected != null) {
                outputDir = selected;
                outputField.setText(selected.toString());
            }
        });

        scanButton.setOnAction(event -> runScan(scanButton, applyAllButton, false));
        applyButton.setOnAction(event -> applySelected());
        applyAllButton.setOnAction(event -> applyAll(scanButton, applyButton, applyAllButton));

        fileList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(FileChangePlan item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.summary());
                }
            }
        });

        fileList.getSelectionModel().selectedItemProperty().addListener((obs, oldPlan, newPlan) -> {
            applyButton.setDisable(newPlan == null);
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
        diffList.setCellFactory(list -> new DiffRowCell());

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

        VBox topBox = new VBox(8, inputRow, outputRow);
        topBox.setPadding(new Insets(10));

        HBox outputFileRow = new HBox(8, new Label("Output file"), outputSelector);
        HBox.setHgrow(outputSelector, Priority.ALWAYS);

        GridPane diffHeader = new GridPane();
        ColumnConstraints leftCol = new ColumnConstraints();
        leftCol.setPercentWidth(50);
        leftCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints rightCol = new ColumnConstraints();
        rightCol.setPercentWidth(50);
        rightCol.setHgrow(Priority.ALWAYS);
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
        splitPane.getItems().addAll(fileList, tabPane);
        splitPane.setDividerPositions(0.35);

        HBox bottomBox = new HBox(12, applyButton, applyAllButton, statusLabel);
        bottomBox.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(splitPane);
        root.setBottom(bottomBox);

        Scene scene = new Scene(root, 1000, 640);
        stage.setTitle("JavaParser Modifier Preview");
        stage.setScene(scene);
        stage.show();
    }

    private Path chooseDirectory(Stage stage, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        File selected = chooser.showDialog(stage);
        return selected == null ? null : selected.toPath();
    }

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

        Thread thread = new Thread(task, "scan-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void applySelected() {
        FileChangePlan selected = fileList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        statusLabel.setText("Applying " + selected.getRelativePath());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                processor.applySingle(inputDir, outputDir, selected.getSourceFile());
                return null;
            }
        };

        task.setOnSucceeded(event -> statusLabel.setText("Applied: " + selected.getRelativePath()));
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            statusLabel.setText("Apply failed: " + (ex == null ? "unknown error" : ex.getMessage()));
        });

        Thread thread = new Thread(task, "apply-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyAll(Button scanButton, Button applySelectedButton, Button applyAllButton) {
        if (plans.isEmpty()) {
            return;
        }
        if (inputDir == null || outputDir == null) {
            statusLabel.setText("Select both input and output directories");
            return;
        }

        List<FileChangePlan> snapshot = List.copyOf(plans);
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
                    processor.applySingle(inputDir, outputDir, plan.getSourceFile());
                }
                return null;
            }
        };

        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Applied " + snapshot.size() + " file(s), rescanning...");
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

    private void clearPreview() {
        outputSelector.getItems().clear();
        outputSelector.setDisable(true);
        currentOriginal = null;
        diffList.getItems().clear();
    }

    private record PreviewData(String originalContent, List<OutputFilePreview> outputs) {}

    private static final class DiffRowCell extends ListCell<DiffRow> {
        private final Label leftLine = new Label();
        private final Label rightLine = new Label();
        private final TextFlow leftFlow = new TextFlow();
        private final TextFlow rightFlow = new TextFlow();
        private final HBox leftBox = new HBox(6);
        private final HBox rightBox = new HBox(6);
        private final GridPane grid = new GridPane();

        private DiffRowCell() {
            ColumnConstraints leftCol = new ColumnConstraints();
            leftCol.setPercentWidth(50);
            leftCol.setHgrow(Priority.ALWAYS);
            ColumnConstraints rightCol = new ColumnConstraints();
            rightCol.setPercentWidth(50);
            rightCol.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(leftCol, rightCol);

            leftLine.setMinWidth(48);
            leftLine.setAlignment(Pos.TOP_RIGHT);
            leftLine.setStyle("-fx-text-fill: #666666; -fx-font-family: 'Consolas'; -fx-font-size: 11px;");
            rightLine.setMinWidth(48);
            rightLine.setAlignment(Pos.TOP_RIGHT);
            rightLine.setStyle("-fx-text-fill: #666666; -fx-font-family: 'Consolas'; -fx-font-size: 11px;");

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
            leftBox.setStyle(rowStyle(item.getLeftType()));
            rightBox.setStyle(rowStyle(item.getRightType()));
            setGraphic(grid);
        }

        private List<Text> buildText(List<DiffSegment> segments, boolean isLeft) {
            List<Text> nodes = new java.util.ArrayList<>();
            for (DiffSegment segment : segments) {
                Text text = new Text(segment.getText());
                text.setStyle(textStyle(segment.isHighlight(), isLeft));
                nodes.add(text);
            }
            return nodes;
        }

        private String textStyle(boolean highlight, boolean isLeft) {
            if (!highlight) {
                return "-fx-fill: #111111;";
            }
            return isLeft ? "-fx-fill: #b71c1c; -fx-font-weight: bold;" : "-fx-fill: #1b5e20; -fx-font-weight: bold;";
        }

        private String rowStyle(DiffType type) {
            String base = "-fx-padding: 2 6 2 6;";
            return switch (type) {
                case ADDED -> base + " -fx-background-color: #eaffea;";
                case REMOVED -> base + " -fx-background-color: #ffecec;";
                case CHANGED -> base + " -fx-background-color: #fff6d6;";
                case EMPTY -> base + " -fx-background-color: #f5f5f5;";
                case SAME -> base + " -fx-background-color: transparent;";
            };
        }
    }
}
