package com.jda.orrery.view;

import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.stage.Stage;

public class OrreryView {
    private final Group root3D = new Group();
    private final Group world = new Group();

    public Scene createScene(Stage stage) {
        // Create Sun
        Sphere sun = new Sphere(20);
        PhongMaterial sunMaterial = new PhongMaterial(Color.YELLOW);
        sun.setMaterial(sunMaterial);

        // Group planets inside "world"
        world.getChildren().add(sun);

        // Add camera
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-200);

        // Add light source
        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateZ(-100);

        // Add elements to the root
        root3D.getChildren().addAll(world, light);
        Scene scene = new Scene(root3D, 800, 600, true);
        scene.setFill(Color.BLACK);
        scene.setCamera(camera);

        return scene;
    }

    public Group getWorld() {
        return world;
    }
}
