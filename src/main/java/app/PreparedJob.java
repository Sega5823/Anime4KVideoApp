package app;

import java.nio.file.Path;
import java.util.List;

record PreparedJob(
        VideoItem item,
        Path commandOutputPath,
        Path finalOutputPath,
        boolean deleteSourceAfterSuccess,
        boolean replaceSourceInPlace,
        List<String> command
) {
}
