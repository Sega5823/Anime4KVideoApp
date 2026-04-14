package app;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;

final class VideoItem {
    private final String name;
    private final Path path;
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty status = new SimpleStringProperty("Pending");

    VideoItem(String name, Path path) {
        this.name = name;
        this.path = path;
    }

    String name() {
        return name;
    }

    Path path() {
        return path;
    }

    DoubleProperty progressProperty() {
        return progress;
    }

    StringProperty statusProperty() {
        return status;
    }
}
