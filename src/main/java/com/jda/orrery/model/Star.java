package com.jda.orrery.model;

import javafx.scene.paint.Color;

public class Star extends CelestialBody {

    public Star(String name, double radius, String textureFile, double tiltAngle) {
        super(name, radius, textureFile, tiltAngle);
    }

    @Override
    protected Color getDefaultColor(String textureFile) {
        // Default colors for different star types
        switch (textureFile.toLowerCase()) {
            case "sun.png":
                return Color.YELLOW;
            default:
                return Color.WHITE;
        }
    }
}