package io.github.ysh061109.ironhud;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/ironhud-client.toml, also editable from the mod list screen). */
public final class HudConfig {
    public enum Theme {
        ARC_BLUE(0x00E5FF, 0xFFC53D),
        CLASSIC_GOLD(0xFFB300, 0xFF5A36),
        STEALTH_GREEN(0x39FF88, 0xFFC53D);

        public final int primary, accent;

        Theme(int primary, int accent) {
            this.primary = primary;
            this.accent = accent;
        }
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<Theme> THEME =
            B.comment("HUD color theme").defineEnum("theme", Theme.ARC_BLUE);
    public static final ModConfigSpec.BooleanValue SOUNDS =
            B.comment("Play HUD sound effects").define("sounds", true);
    public static final ModConfigSpec.DoubleValue VOLUME =
            B.comment("HUD sound volume").defineInRange("volume", 0.8, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue HIDE_VANILLA_BARS =
            B.comment("Hide vanilla health / armor / food bars while the HUD is on").define("hideVanillaBars", true);
    public static final ModConfigSpec.BooleanValue SHOW_COMPASS =
            B.comment("Compass tape").define("showCompass", true);
    public static final ModConfigSpec.BooleanValue SHOW_RADAR =
            B.comment("Plan-view radar").define("showRadar", true);
    public static final ModConfigSpec.BooleanValue SHOW_SUIT_STATUS =
            B.comment("Armor / suit component status diagram").define("showSuitStatus", true);
    public static final ModConfigSpec.BooleanValue SHOW_MARKERS =
            B.comment("World-space markers on hostile mobs and waypoint").define("showMarkers", true);
    public static final ModConfigSpec.BooleanValue SHOW_CRACKS =
            B.comment("Visor crack effect after heavy hits").define("showCracks", true);
    public static final ModConfigSpec.BooleanValue HORIZON_ALWAYS =
            B.comment("Show the artificial horizon / pitch ladder all the time (default: only while flying)")
                    .define("horizonAlways", false);
    public static final ModConfigSpec.IntValue SCAN_RANGE =
            B.comment("Target lock range in blocks").defineInRange("scanRange", 64, 16, 128);
    public static final ModConfigSpec.IntValue RADAR_RANGE =
            B.comment("Radar range in blocks").defineInRange("radarRange", 32, 16, 64);
    public static final ModConfigSpec.DoubleValue ZOOM_MAX =
            B.comment("Maximum optical zoom").defineInRange("zoomMax", 8.0, 2.0, 16.0);

    public static final ModConfigSpec SPEC = B.build();

    private HudConfig() {}

    public static int primary() {
        return THEME.get().primary;
    }

    public static int accent() {
        return THEME.get().accent;
    }
}
