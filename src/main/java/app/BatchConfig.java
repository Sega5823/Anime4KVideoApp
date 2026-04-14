package app;

import java.nio.file.Path;

record BatchConfig(
        String ffmpegPath,
        String ffprobePath,
        String outputFolder,
        String width,
        String height,
        String cq,
        String videoEncoder,
        Preset preset,
        String existingFileMode,
        String outputSuffix,
        Integer threadCount,
        boolean testMode,
        int testDurationSeconds,
        Path combinedShaderFile
) {
}
