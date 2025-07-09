package com.jda.orrery.view;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

public class AxesHelper {

    /**
     * Creates a visual representation of the coordinate axes
     * X = RED (horizontal, left-right)
     * Y = GREEN (vertical, up-down)
     * Z = BLUE (depth, forward-back into screen)
     */
    public static Group createAxes(double length) {
        Group axes = new Group();

        // Materials
        PhongMaterial redMaterial = new PhongMaterial(Color.RED);
        PhongMaterial greenMaterial = new PhongMaterial(Color.GREEN);
        PhongMaterial blueMaterial = new PhongMaterial(Color.BLUE);
        PhongMaterial yellowMaterial = new PhongMaterial(Color.YELLOW);

        // X-axis (RED) - pointing right
        Cylinder xAxis = new Cylinder(2, length);
        xAxis.setMaterial(redMaterial);
        xAxis.setRotate(90);
        xAxis.setRotationAxis(Rotate.Z_AXIS);
        xAxis.setTranslateX(length / 2);

        Sphere xSphere = new Sphere(5);
        xSphere.setMaterial(redMaterial);
        xSphere.setTranslateX(length);

        // Y-axis (GREEN) - pointing up
        Cylinder yAxis = new Cylinder(2, length);
        yAxis.setMaterial(greenMaterial);
        yAxis.setTranslateY(-length / 2);

        Sphere ySphere = new Sphere(5);
        ySphere.setMaterial(greenMaterial);
        ySphere.setTranslateY(-length);

        // Z-axis (BLUE) - pointing forward (into screen)
        Cylinder zAxis = new Cylinder(2, length);
        zAxis.setMaterial(blueMaterial);
        zAxis.setRotate(90);
        zAxis.setRotationAxis(Rotate.X_AXIS);
        zAxis.setTranslateZ(length / 2);

        Sphere zSphere = new Sphere(5);
        zSphere.setMaterial(blueMaterial);
        zSphere.setTranslateZ(length);

        // Origin sphere (YELLOW)
        Sphere origin = new Sphere(8);
        origin.setMaterial(yellowMaterial);

        axes.getChildren().addAll(
                xAxis, xSphere,
                yAxis, ySphere,
                zAxis, zSphere,
                origin
        );

        return axes;
    }
}