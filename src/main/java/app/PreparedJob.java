package app;

import java.nio.file.Path;
import java.util.List;

record PreparedJob(
        VideoItem item,
        Path outputPath,
        List<String> command
) {
}
