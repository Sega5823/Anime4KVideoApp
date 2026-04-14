package app;

import java.util.List;

record Preset(String name, List<String> shaders) {
    @Override
    public String toString() {
        return name;
    }
}
