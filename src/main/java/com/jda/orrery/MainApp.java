package com.jda.orrery;

import com.jda.orrery.controller.OrreryController;
import javafx.application.Application;

/**
 * Main entry point for the JavaFX 3D Orrery.
 */
public class MainApp {
    public static void main(String[] args) {
        Application.launch(OrreryController.OrreryApp.class, args);
    }
}
