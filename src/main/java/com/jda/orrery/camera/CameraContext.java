package com.jda.orrery.camera;

import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;

public class CameraContext {
    public final PerspectiveCamera camera;
    public final Group world;

    public CameraContext(PerspectiveCamera camera, Group world) {
        this.camera = camera;
        this.world = world;
    }
}