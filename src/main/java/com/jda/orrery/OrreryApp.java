package com.jda.orrery;

import com.jda.orrery.controller.OrreryController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main JavaFX Application class for the 3D Orrery.
 */
public class OrreryApp extends Application {
    // Window size constants
    private static final int WINDOW_WIDTH = 3840;
    private static final int WINDOW_HEIGHT = 2160;
    private static final int MIN_WINDOW_WIDTH = 1920;
    private static final int MIN_WINDOW_HEIGHT = 1080;

    private OrreryController controller;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/orrery.fxml"));
            BorderPane root = loader.load();

            // Get controller reference
            controller = loader.getController();

            // Create scene
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

            // Add CSS styling
            String css = getClass().getResource("/css/style.css").toExternalForm();
            scene.getStylesheets().add(css);

            // Configure stage
            primaryStage.setTitle("3D Orrery");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(MIN_WINDOW_WIDTH);
            primaryStage.setMinHeight(MIN_WINDOW_HEIGHT);
            primaryStage.setMaximized(true);

            // Ensure cleanup on close
            primaryStage.setOnCloseRequest(event -> {
                if (controller != null) {
                    controller.shutdown();
                }
            });

            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        // Additional cleanup if needed
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}