package app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

final class BundledTools {
    private static final String FFMPEG_RESOURCE = "/tools/ffmpeg-2026-04-09-git-d3d0b7a5ee-full_build/bin/ffmpeg.exe";
    private static final String FFPROBE_RESOURCE = "/tools/ffmpeg-2026-04-09-git-d3d0b7a5ee-full_build/bin/ffprobe.exe";

    String getBundledFfmpegPath() {
        return resolveBundledOrSystemBinary(FFMPEG_RESOURCE, "ffmpeg.exe", "ffmpeg");
    }

    String getBundledFfprobePath() {
        return resolveBundledOrSystemBinary(FFPROBE_RESOURCE, "ffprobe.exe", "ffprobe");
    }

    boolean hasShaderResource(String shaderName) {
        return getClass().getResource("/shaders/" + shaderName) != null;
    }

    Path createCombinedShaderFile(Preset preset) {
        try {
            String slug = preset.name()
                    .replace("Anime4K:", "")
                    .replaceAll("[^a-zA-Z0-9]+", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_|_$", "")
                    .toLowerCase();
            String shaderHash = Integer.toHexString(String.join("|", preset.shaders()).hashCode());

            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "anime4k_ffmpeg_ui");
            Files.createDirectories(tempDir);

            Path combined = tempDir.resolve("preset_" + slug + "_" + shaderHash + ".glsl");
            if (Files.isRegularFile(combined)) {
                return combined;
            }

            StringBuilder sb = new StringBuilder();
            for (String shaderName : preset.shaders()) {
                String resourcePath = "/shaders/" + shaderName;

                try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        throw new IllegalArgumentException("missing shader resource: " + resourcePath);
                    }

                    sb.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    sb.append(System.lineSeparator()).append(System.lineSeparator());
                }
            }

            Files.writeString(
                    combined,
                    sb.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            return combined;
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot create combined shader file: " + e.getMessage(), e);
        }
    }

    private String extractBundledBinary(String resourcePath, String outputFileName) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Bundled resource not found: " + resourcePath);
            }

            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "anime4k_ffmpeg_ui_bin");
            Files.createDirectories(tempDir);

            String cacheKey = Integer.toHexString(resourcePath.hashCode());
            Path target = tempDir.resolve(cacheKey + "_" + outputFileName);
            if (Files.isRegularFile(target)) {
                return target.toAbsolutePath().toString();
            }

            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);

            target.toFile().deleteOnExit();
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot extract bundled binary: " + resourcePath, e);
        }
    }

    private String resolveBundledOrSystemBinary(String resourcePath, String outputFileName, String systemCommand) {
        if (getClass().getResource(resourcePath) != null) {
            return extractBundledBinary(resourcePath, outputFileName);
        }
        return systemCommand;
    }
}
