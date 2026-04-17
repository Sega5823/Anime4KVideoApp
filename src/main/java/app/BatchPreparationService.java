package app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

final class BatchPreparationService {
    static final String DEFAULT_VIDEO_ENCODER = "NVIDIA (h264_nvenc)";

    boolean isFfmpegResolvable(String ffmpegValue) {
        Path path = Paths.get(ffmpegValue);
        if (Files.exists(path)) {
            return true;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegValue, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    List<PreparedJob> prepareJobs(List<VideoItem> items, BatchConfig config) {
        Set<Path> reservedOutputPaths = new HashSet<>();
        List<PreparedJob> preparedJobs = new ArrayList<>();

        for (VideoItem item : items) {
            PreparedPath preparedPath = buildPreparedPath(item.path(), config, reservedOutputPaths);
            preparedJobs.add(new PreparedJob(
                    item,
                    preparedPath.commandOutputPath(),
                    preparedPath.finalOutputPath(),
                    config.deleteProcessedSource(),
                    preparedPath.replaceSourceInPlace(),
                    buildCommand(item, config, preparedPath.commandOutputPath())
            ));
        }

        return preparedJobs;
    }

    PreparedJob prepareJob(VideoItem item, BatchConfig config) {
        PreparedPath preparedPath = buildPreparedPath(item.path(), config, new HashSet<>());
        return new PreparedJob(
                item,
                preparedPath.commandOutputPath(),
                preparedPath.finalOutputPath(),
                config.deleteProcessedSource(),
                preparedPath.replaceSourceInPlace(),
                buildCommand(item, config, preparedPath.commandOutputPath())
        );
    }

    Path previewOutputPath(VideoItem item, BatchConfig config) {
        return buildPreparedPath(item.path(), config, new HashSet<>()).finalOutputPath();
    }

    void validateBatchStart(BatchConfig config, List<PreparedJob> preparedJobs) {
        ensureFfmpegComponent(config.ffmpegPath(), "filter=libplacebo", "libplacebo filter");
        String encoderId = encoderId(config.videoEncoder());
        ensureFfmpegComponent(config.ffmpegPath(), "encoder=" + encoderId, encoderId + " encoder");

        Path expectedOutputDir = Paths.get(config.outputFolder()).toAbsolutePath().normalize();
        for (PreparedJob preparedJob : preparedJobs) {
            Path commandOutputPath = preparedJob.commandOutputPath();
            if (!commandOutputPath.getParent().equals(expectedOutputDir)) {
                throw new IllegalArgumentException("output path escaped output folder: " + commandOutputPath);
            }
        }
    }

    double probeDurationSeconds(Path inputFile, BatchConfig config) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    config.ffprobePath(),
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    inputFile.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                process.waitFor(5, TimeUnit.SECONDS);

                if (line != null && !line.isBlank()) {
                    return Double.parseDouble(line.trim());
                }
            }
        } catch (Exception ignored) {
        }

        return -1.0;
    }

    private List<String> buildCommand(VideoItem videoItem, BatchConfig config, Path outputPath) {
        String shaderPath = config.combinedShaderFile().toAbsolutePath().toString().replace('\\', '/');
        if (shaderPath.length() >= 2 && shaderPath.charAt(1) == ':') {
            shaderPath = shaderPath.charAt(0) + "\\:" + shaderPath.substring(2);
        }
        String vf = "libplacebo=w=" + config.width() + ":h=" + config.height() + ":custom_shader_path='" + shaderPath + "'";
        String encoderId = encoderId(config.videoEncoder());

        List<String> command = new ArrayList<>();
        command.add(config.ffmpegPath());
        command.add("-progress");
        command.add("pipe:1");
        command.add("-nostats");
        if ("Overwrite".equals(config.existingFileMode())) {
            command.add("-y");
        } else if ("Skip".equals(config.existingFileMode())) {
            command.add("-n");
        }
        if (config.threadCount() != null) {
            command.add("-threads");
            command.add(String.valueOf(config.threadCount()));
        }
        if (config.testMode()) {
            command.add("-t");
            command.add(String.valueOf(config.testDurationSeconds()));
        }
        command.add("-i");
        command.add(videoItem.path().toString());
        command.add("-vf");
        command.add(vf);
        appendVideoEncoderArgs(command, encoderId, config.cq());
        command.add("-c:a");
        command.add("copy");
        command.add("-movflags");
        command.add("+faststart");
        command.add(outputPath.toString());

        return command;
    }

    private PreparedPath buildPreparedPath(Path inputVideo, BatchConfig config, Set<Path> reservedOutputPaths) {
        String fileName = inputVideo.getFileName().toString();
        String outputName = buildOutputFileName(fileName, config);
        Path finalOutputPath = Paths.get(config.outputFolder()).resolve(outputName).toAbsolutePath().normalize();
        Path normalizedInputPath = inputVideo.toAbsolutePath().normalize();
        boolean replaceSourceInPlace = config.deleteProcessedSource() && normalizedInputPath.equals(finalOutputPath);
        Path commandOutputPath = replaceSourceInPlace
                ? buildTemporaryOutputPath(finalOutputPath, reservedOutputPaths)
                : finalOutputPath;

        if ("Auto rename".equals(config.existingFileMode())) {
            int counter = 1;
            while (Files.exists(commandOutputPath) || reservedOutputPaths.contains(commandOutputPath)) {
                String renamed = appendCounterToFileName(outputName, counter);
                finalOutputPath = Paths.get(config.outputFolder()).resolve(renamed).toAbsolutePath().normalize();
                commandOutputPath = finalOutputPath;
                counter++;
            }
        } else if (reservedOutputPaths.contains(commandOutputPath)) {
            throw new IllegalArgumentException("duplicate output path within current batch: " + commandOutputPath);
        }

        if (normalizedInputPath.equals(commandOutputPath)) {
            throw new IllegalArgumentException("output file matches input file: " + commandOutputPath);
        }

        reservedOutputPaths.add(commandOutputPath);
        if (!commandOutputPath.equals(finalOutputPath)) {
            reservedOutputPaths.add(finalOutputPath);
        }
        return new PreparedPath(commandOutputPath, finalOutputPath, replaceSourceInPlace);
    }

    private String buildOutputFileName(String inputFileName, BatchConfig config) {
        if ("Original filename".equals(config.outputNamingMode())) {
            return inputFileName;
        }

        int dot = inputFileName.lastIndexOf('.');
        String base = dot > 0 ? inputFileName.substring(0, dot) : inputFileName;

        String presetSlug = config.preset().name()
                .replace("Anime4K:", "")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        String suffix = config.outputSuffix().trim();
        if (!suffix.isEmpty()) {
            suffix = suffix.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!suffix.startsWith("_") && !suffix.startsWith("-")) {
                suffix = "_" + suffix;
            }
        }

        return base + "_" + presetSlug + suffix + ".mkv";
    }

    private String appendCounterToFileName(String fileName, int counter) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName + "_" + counter;
        }
        return fileName.substring(0, dot) + "_" + counter + fileName.substring(dot);
    }

    private Path buildTemporaryOutputPath(Path finalOutputPath, Set<Path> reservedOutputPaths) {
        String fileName = finalOutputPath.getFileName().toString();
        int counter = 1;
        Path candidate = finalOutputPath.resolveSibling(appendTempMarker(fileName, counter));
        while (Files.exists(candidate) || reservedOutputPaths.contains(candidate)) {
            counter++;
            candidate = finalOutputPath.resolveSibling(appendTempMarker(fileName, counter));
        }
        return candidate;
    }

    private String appendTempMarker(String fileName, int counter) {
        int dot = fileName.lastIndexOf('.');
        String marker = ".anime4k_tmp_" + counter;
        if (dot <= 0) {
            return fileName + marker;
        }
        return fileName.substring(0, dot) + marker + fileName.substring(dot);
    }

    private record PreparedPath(
            Path commandOutputPath,
            Path finalOutputPath,
            boolean replaceSourceInPlace
    ) {
    }

    private void appendVideoEncoderArgs(List<String> command, String encoderId, String cq) {
        command.add("-c:v");
        command.add(encoderId);

        switch (encoderId) {
            case "h264_nvenc" -> {
                command.add("-preset");
                command.add("p7");
                command.add("-rc");
                command.add("vbr");
                command.add("-cq");
                command.add(cq);
                command.add("-profile:v");
                command.add("high");
                command.add("-b_ref_mode");
                command.add("middle");
                command.add("-temporal-aq");
                command.add("1");
                command.add("-spatial-aq");
                command.add("1");
            }
            case "h264_amf" -> {
                command.add("-quality");
                command.add("quality");
                command.add("-rc");
                command.add("cqp");
                command.add("-qp_i");
                command.add(cq);
                command.add("-qp_p");
                command.add(cq);
            }
            case "h264_qsv" -> {
                command.add("-preset");
                command.add("slow");
                command.add("-global_quality");
                command.add(cq);
                command.add("-look_ahead");
                command.add("1");
            }
            case "libx264" -> {
                command.add("-preset");
                command.add("slow");
                command.add("-crf");
                command.add(cq);
                command.add("-profile:v");
                command.add("high");
                command.add("-pix_fmt");
                command.add("yuv420p");
            }
            default -> throw new IllegalArgumentException("unsupported video encoder: " + encoderId);
        }
    }

    private String encoderId(String videoEncoder) {
        return switch (videoEncoder == null ? DEFAULT_VIDEO_ENCODER : videoEncoder) {
            case "NVIDIA (h264_nvenc)" -> "h264_nvenc";
            case "AMD (h264_amf)" -> "h264_amf";
            case "Intel (h264_qsv)" -> "h264_qsv";
            case "CPU (libx264)" -> "libx264";
            default -> throw new IllegalArgumentException("unknown video encoder selection: " + videoEncoder);
        };
    }

    private void ensureFfmpegComponent(String ffmpegPath, String componentQuery, String label) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-hide_banner",
                    "-h",
                    componentQuery
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            String normalizedOutput = output.toLowerCase();
            if (!finished
                    || normalizedOutput.contains("unknown")
                    || normalizedOutput.contains("not recognized")
                    || normalizedOutput.contains("not found")) {
                throw new IllegalArgumentException("ffmpeg does not support required " + label);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot run ffmpeg preflight: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("ffmpeg preflight interrupted", e);
        }
    }
}
