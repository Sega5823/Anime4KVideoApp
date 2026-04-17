package app;

import java.util.prefs.Preferences;

record AppSettings(
        String inputFolder,
        String outputFolder,
        String resolution,
        String cq,
        String videoEncoder,
        String threads,
        String preset,
        String runMode,
        boolean testMode,
        String testDuration,
        String existingFileMode,
        String outputNamingMode,
        String outputSuffix,
        boolean deleteProcessedSource
) {
    static AppSettings load(Preferences prefs) {
        return new AppSettings(
                prefs.get("inputFolder", ""),
                prefs.get("outputFolder", ""),
                prefs.get("resolution", "3840x2160"),
                prefs.get("cq", "18"),
                prefs.get("videoEncoder", "NVIDIA (h264_nvenc)"),
                prefs.get("threads", "10"),
                prefs.get("preset", null),
                prefs.get("runMode", "Sequential"),
                prefs.getBoolean("testMode", false),
                prefs.get("testDuration", "60"),
                prefs.get("existingFileMode", "Overwrite"),
                prefs.get("outputNamingMode", "Preset name + suffix"),
                prefs.get("outputSuffix", ""),
                prefs.getBoolean("deleteProcessedSource", false)
        );
    }

    void save(Preferences prefs) {
        prefs.put("inputFolder", inputFolder == null ? "" : inputFolder);
        prefs.put("outputFolder", outputFolder == null ? "" : outputFolder);
        prefs.put("resolution", resolution == null ? "" : resolution);
        prefs.put("cq", cq == null ? "" : cq);
        prefs.put("videoEncoder", videoEncoder == null ? "" : videoEncoder);
        prefs.put("threads", threads == null ? "" : threads);
        if (preset != null) {
            prefs.put("preset", preset);
        }
        if (runMode != null) {
            prefs.put("runMode", runMode);
        }
        prefs.putBoolean("testMode", testMode);
        if (testDuration != null) {
            prefs.put("testDuration", testDuration);
        }
        if (existingFileMode != null) {
            prefs.put("existingFileMode", existingFileMode);
        }
        if (outputNamingMode != null) {
            prefs.put("outputNamingMode", outputNamingMode);
        }
        prefs.put("outputSuffix", outputSuffix == null ? "" : outputSuffix);
        prefs.putBoolean("deleteProcessedSource", deleteProcessedSource);
    }
}
