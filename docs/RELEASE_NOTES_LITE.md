# Release Notes: Lite

## Anime4K Video Converter 1.0.0 Lite

Portable Windows build without bundled FFmpeg tools.

### Includes

- Java runtime
- Application launcher
- Bundled Anime4K shader presets

### Requirements

- `ffmpeg` available in `PATH`
- `ffprobe` available in `PATH`
- FFmpeg build with `libplacebo`
- One of the supported encoders: `h264_nvenc`, `h264_amf`, `h264_qsv`, `libx264`

### Highlights

- Smaller package than the Full build
- Same UI and workflow as the Full build
- Batch folder scanning for supported video files
- Saved paths and settings between launches
- Auto-scan of the saved input folder on startup

### Notes

- Distribute the whole portable folder, not only the `.exe`
- This package does not include FFmpeg binaries
- Best suited for users who already maintain their own FFmpeg installation

### Download

Recommended asset:

- `Anime4KVideoAppLite` portable folder or archive
