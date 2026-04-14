# Anime4K Video Converter

Windows GUI for batch video processing with Anime4K shaders and FFmpeg.

The application scans a folder of videos, builds a shader pipeline from bundled Anime4K presets, and runs FFmpeg jobs with progress tracking in a JavaFX desktop UI.

![Application screenshot](docs/images/app-screenshot.png)

## Versions

### Full

- Includes bundled `ffmpeg` and `ffprobe`.
- Intended for end users who want a self-contained portable package.
- Output package: `dist-package/Anime4KVideoApp/`

### Lite

- Does **not** include bundled `ffmpeg` or `ffprobe`.
- Requires both commands to be available in `PATH`.
- Intended for smaller redistribution or users who already manage their own FFmpeg install.
- Output package: `dist-package-lite/Anime4KVideoAppLite/`

## Requirements

### Runtime

- Windows
- FFmpeg build with `libplacebo`
- One of the supported H.264 encoders:
  - `h264_nvenc` for NVIDIA
  - `h264_amf` for AMD
  - `h264_qsv` for Intel
  - `libx264` for CPU-only fallback
- For Lite: `ffmpeg` and `ffprobe` available in `PATH`

### Build

- JDK with `jpackage`
- Maven Wrapper is included

## Build Commands

### Standard JAR build

```powershell
.\mvnw.cmd clean package
```

### Run from source

```powershell
.\mvnw.cmd javafx:run
```

### Portable Full EXE bundle

```powershell
.\mvnw.cmd -Pportable-package clean package
```

Result:

- `dist-package/Anime4KVideoApp/Anime4KVideoApp.exe`
- ZIP release asset: `.\mvnw.cmd -Pportable-package-zip clean package`

### Portable Lite EXE bundle

```powershell
.\mvnw.cmd -Pportable-lite-package clean package
```

Result:

- `dist-package-lite/Anime4KVideoAppLite/Anime4KVideoAppLite.exe`
- ZIP release asset: `.\mvnw.cmd -Pportable-lite-package-zip clean package`

## Downloads

Current release:

- Release page: https://github.com/Sega5823/Anime4KVideoApp/releases/tag/v1.0.0
- Full portable ZIP: https://github.com/Sega5823/Anime4KVideoApp/releases/download/v1.0.0/Anime4KVideoApp-full-portable.zip
- Lite portable ZIP: https://github.com/Sega5823/Anime4KVideoApp/releases/download/v1.0.0/Anime4KVideoApp-lite-portable.zip

For GitHub Releases, publish portable archives instead of a standalone `.exe`.

Recommended release assets:

- Full: archive of `dist-package/Anime4KVideoApp/`
- Lite: archive of `dist-package-lite/Anime4KVideoAppLite/`

Auto-generated ZIP assets are written to:

- `release-assets/Anime4KVideoApp-full-portable.zip`
- `release-assets/Anime4KVideoApp-lite-portable.zip`

Suggested labels:

- `Anime4KVideoApp-full-portable.zip`
- `Anime4KVideoApp-lite-portable.zip`

## Usage

1. Start the application from the portable package folder.
2. Select input and output folders.
3. Choose a preset, encoder, and output settings.
4. Scan videos.
5. Start batch processing.

If an input folder is already saved in settings, the application will auto-scan it on startup.

## Anime4K Modes

The preset descriptions below are based on the Anime4K advanced GLSL instructions:
https://github.com/bloc97/Anime4K/blob/master/md/GLSL_Instructions_Advanced.md#advanced-usage-instructions-glsl--mpv-v4x

| App preset | Best for | Strengths | Risks if chosen incorrectly |
| --- | --- | --- | --- |
| `Mode A (HQ)` | Most 1080p anime, some older 720p anime, most older SD anime with heavier blur, resampling artifacts, or compression smearing | Strong perceptual cleanup, restores damaged lines, reduces blur, compression artifacts, and noise | Can make ringing or banding more obvious; stronger denoising can soften textures |
| `Mode B (HQ)` | Most 720p anime, some 1080p anime, and 1080p-to-720p downscales with lighter blur or ringing | Good balance for mixed sources; reduces ringing, aliasing, blur, compression artifacts, and noise | Some defects may remain; some lines can still look soft; denoising can soften textures |
| `Mode C (HQ)` | Clean sources, 1080p-to-480p downscales, some animated movies, wallpapers, and Pixiv-style art | Highest fidelity on already clean material; reduces noise with minimal restoration bias | Lower perceived improvement on damaged video; can make ringing or resampling artifacts stand out more |
| `Mode A+A (HQ)` | Same source types as `Mode A`, but for stronger restoration | Strongest perceptual restoration and line recovery of the default modes | Can oversharpen badly, introduce severe ringing, banding, or aliasing; slower than `Mode A` |
| `Mode B+B (HQ)` | Same source types as `Mode B`, but for stronger restoration | Higher perceptual quality than `Mode B` while keeping the same general behavior | Same failure cases as `Mode B`, with more processing time |
| `Mode C+A (HQ)` | Same source types as `Mode C`, but with a little more restoration | Slightly more perceived sharpness than `Mode C` | Same failure cases as `Mode C`, with more processing time |

Notes:

- Secondary modes `A+A`, `B+B`, and `C+A` are best used at `x2` upscale ratios or higher.
- In this app, the `(HQ)` suffix means the preset uses higher-quality shader variants and is slower than a lighter configuration would be.
- `Fast` is an app convenience preset and is not part of that reference table; use it when you want lower GPU cost at the expense of restoration quality.

## Distribution Notes

### Portable packages

Do not distribute only the `.exe` file.

Distribute the whole application folder:

- Full: `dist-package/Anime4KVideoApp/`
- Lite: `dist-package-lite/Anime4KVideoAppLite/`

Each package contains:

- launcher `.exe`
- `app/`
- `runtime/`

### Full vs Lite

Use Full if you want the simplest end-user experience.

Use Lite if you want a smaller package and are comfortable requiring a system FFmpeg installation.

## Third-Party Components

- FFmpeg: https://ffmpeg.org
- Anime4K shaders by bloc97: https://github.com/bloc97/Anime4K
- Bundled FFmpeg build source reference:
  https://github.com/GyanD/codexffmpeg/releases/tag/2026-04-09-git-d3d0b7a5ee

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for distribution notes.

## License

This repository's own code is distributed under `GPL-3.0-or-later`.

See [LICENSE](LICENSE).
