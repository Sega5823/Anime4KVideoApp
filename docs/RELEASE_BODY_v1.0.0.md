# Anime4K Video Converter 1.0.0

First public release of the JavaFX-based Anime4K batch video converter for Windows.

## Highlights

- Batch processing for video folders
- Anime4K preset-based shader pipeline
- Per-file and overall progress tracking
- Saved settings and auto-scan of the last input folder
- Portable Windows builds
- Encoder selection:
  - `h264_nvenc` for NVIDIA
  - `h264_amf` for AMD
  - `h264_qsv` for Intel
  - `libx264` for CPU fallback

## Assets

- `Anime4KVideoApp-full-portable.zip`
  - Includes bundled `ffmpeg` and `ffprobe`
  - Best for end users who want a self-contained package

- `Anime4KVideoApp-lite-portable.zip`
  - Does not include bundled FFmpeg tools
  - Requires `ffmpeg` and `ffprobe` in `PATH`
  - Best for smaller distribution or users with their own FFmpeg setup

## Notes

- Distribute the whole portable package, not only the `.exe`
- Requires an FFmpeg build with `libplacebo`
- Third-party notices: see `THIRD_PARTY_NOTICES.md`
- Bundled FFmpeg source reference:
  - https://github.com/GyanD/codexffmpeg/releases/tag/2026-04-09-git-d3d0b7a5ee
