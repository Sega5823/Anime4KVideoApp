package app;

import java.util.ArrayList;
import java.util.List;

final class PresetCatalog {
    private PresetCatalog() {
    }

    static List<Preset> defaultPresets() {
        List<Preset> presets = new ArrayList<>();

        presets.add(new Preset(
                "Anime4K: Mode A (HQ)",
                List.of(
                        "Anime4K_Clamp_Highlights.glsl",
                        "Anime4K_Restore_CNN_VL.glsl",
                        "Anime4K_Upscale_CNN_x2_VL.glsl",
                        "Anime4K_AutoDownscalePre_x2.glsl",
                        "Anime4K_AutoDownscalePre_x4.glsl",
                        "Anime4K_Upscale_CNN_x2_M.glsl"
                )
        ));

        presets.add(new Preset(
                "Anime4K: Mode B (HQ)",
                List.of(
                        "Anime4K_Clamp_Highlights.glsl",
                        "Anime4K_Restore_CNN_Soft_VL.glsl",
                        "Anime4K_Upscale_CNN_x2_VL.glsl",
                        "Anime4K_AutoDownscalePre_x2.glsl",
                        "Anime4K_AutoDownscalePre_x4.glsl",
                        "Anime4K_Upscale_CNN_x2_M.glsl"
                )
        ));

        presets.add(new Preset(
                "Anime4K: Mode C (HQ)",
                List.of(
                        "Anime4K_Clamp_Highlights.glsl",
                        "Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
                        "Anime4K_AutoDownscalePre_x2.glsl",
                        "Anime4K_AutoDownscalePre_x4.glsl",
                        "Anime4K_Upscale_CNN_x2_M.glsl"
                )
        ));

        presets.add(new Preset(
                "Anime4K: Mode A+A (HQ)",
                List.of(
                        "Anime4K_Clamp_Highlights.glsl",
                        "Anime4K_Restore_CNN_VL.glsl",
                        "Anime4K_Upscale_CNN_x2_VL.glsl",
                        "Anime4K_Restore_CNN_M.glsl",
                        "Anime4K_AutoDownscalePre_x2.glsl",
                        "Anime4K_AutoDownscalePre_x4.glsl",
                        "Anime4K_Upscale_CNN_x2_M.glsl"
                )
        ));

        presets.add(new Preset(
                "Anime4K: Mode B+B (HQ)",
                List.of(
                        "Anime4K_Clamp_Highlights.glsl",
                        "Anime4K_Restore_CNN_Soft_VL.glsl",
                        "Anime4K_Upscale_CNN_x2_VL.glsl",
                        "Anime4K_AutoDownscalePre_x2.glsl",
                        "Anime4K_AutoDownscalePre_x4.glsl",
                        "Anime4K_Restore_CNN_Soft_M.glsl",
                        "Anime4K_Upscale_CNN_x2_M.glsl"
                )
        ));

        presets.add(new Preset(
                "Anime4K: Mode C+A (HQ)",
                List.of(
                        "Anime4K_Clamp_Highlights.glsl",
                        "Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
                        "Anime4K_AutoDownscalePre_x2.glsl",
                        "Anime4K_AutoDownscalePre_x4.glsl",
                        "Anime4K_Restore_CNN_M.glsl",
                        "Anime4K_Upscale_CNN_x2_M.glsl"
                )
        ));

        presets.add(new Preset(
                "Anime4K: Fast",
                List.of(
                        "Anime4K_Clamp_Highlights.glsl",
                        "Anime4K_Upscale_CNN_x2_M.glsl"
                )
        ));

        return presets;
    }
}
