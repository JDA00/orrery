module com.jda.orrery {
    requires javafx.controls;
    requires javafx.fxml;

    // If you're loading FXML from the same package:
    opens com.jda.orrery to javafx.fxml;

    // Export the main package to make the application class accessible
    exports com.jda.orrery;
    exports com.jda.orrery.controller;
    opens com.jda.orrery.controller to javafx.fxml;
}
