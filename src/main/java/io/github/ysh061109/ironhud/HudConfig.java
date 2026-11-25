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

    public static final ModConfigSpec.BooleanValue SHOW_TERRAIN =
            B.comment("3D terrain wireframe map").define("showTerrain", true);
    public static final ModConfigSpec.BooleanValue SHOW_WEAPONS =
            B.comment("Armament / ammo panel").define("showWeapons", true);
    public static final ModConfigSpec.BooleanValue SHOW_SYSLOG =
            B.comment("System log and Modus subtitles").define("showSyslog", true);
    public static final ModConfigSpec.BooleanValue SHOW_HELMET =
            B.comment("Helmet frame vignette").define("showHelmet", true);

    // ---- Modus AI
    public enum VoiceLang { EN, KO }

    public static final ModConfigSpec.EnumValue<VoiceLang> VOICE_LANG =
            B.comment("Language Modus speaks and understands").defineEnum("voiceLang", VoiceLang.EN);
    public static final ModConfigSpec.BooleanValue VOICE_ENABLED =
            B.comment("Modus spoken replies and alerts").define("voiceEnabled", true);
    public static final ModConfigSpec.DoubleValue VOICE_VOLUME =
            B.comment("Modus voice volume").defineInRange("voiceVolume", 0.9, 0.0, 1.0);
    public static final ModConfigSpec.IntValue VOICE_RATE =
            B.comment("Speech rate offset (-10 slow .. 10 fast)").defineInRange("voiceRate", 0, -10, 10);
    public static final ModConfigSpec.ConfigValue<String> VOICE_NAME =
            B.comment("Optional voice name (part of the Windows voice name / macOS voice / espeak voice)")
                    .define("voiceName", "");
    public static final ModConfigSpec.ConfigValue<String> TTS_COMMAND =
            B.comment("Optional custom TTS command (run through the shell). It must write a WAV file to $MODUS_OUT "
                            + "(%MODUS_OUT% on Windows) and read the text from $MODUS_TEXT. Example for Piper: "
                            + "echo \"$MODUS_TEXT\" | piper --model en_GB-alan-medium.onnx --output_file \"$MODUS_OUT\"")
                    .define("ttsCommand", "");
    public static final ModConfigSpec.BooleanValue NEURAL_VOICE =
            B.comment("Use the downloaded Piper neural voice when installed (/modus voice install)")
                    .define("neuralVoice", true);
    public static final ModConfigSpec.ConfigValue<String> PIPER_URL =
            B.comment("Override the Piper engine archive URL (blank = official release for your OS)")
                    .define("piperUrl", "");
    public static final ModConfigSpec.ConfigValue<String> PIPER_VOICE_URL =
            B.comment("Override the voice .onnx URL (its .onnx.json must sit next to it). Blank = British male "
                    + "en_GB-alan-medium (English) / ko_KR-kss-medium (Korean)")
                    .define("piperVoiceUrl", "");
    public static final ModConfigSpec.BooleanValue VOICE_FX =
            B.comment("Apply the helmet radio filter to Modus' voice").define("voiceFx", true);
    public static final ModConfigSpec.BooleanValue MIC_ENABLED =
            B.comment("Listen for \"Modus\" through the microphone (offline speech recognition; needs /modus install)")
                    .define("micEnabled", false);
    public static final ModConfigSpec.BooleanValue MIC_PTT_ONLY =
            B.comment("Only listen while the push-to-talk key is held (no wake word needed)")
                    .define("micPushToTalkOnly", false);
    public static final ModConfigSpec.ConfigValue<String> HONORIFIC_EN =
            B.comment("How Modus addresses you in English").define("honorificEn", "sir");
    public static final ModConfigSpec.ConfigValue<String> HONORIFIC_KO =
            B.comment("How Modus addresses you in Korean").define("honorificKo", "대장님");
    public static final ModConfigSpec.ConfigValue<String> VOSK_JAR_URL =
            B.comment("Override the Vosk library download URL (blank = default)").define("voskJarUrl", "");
    public static final ModConfigSpec.ConfigValue<String> VOSK_MODEL_URL =
            B.comment("Override the speech model zip URL (blank = default for voiceLang)").define("voskModelUrl", "");

    public static final ModConfigSpec SPEC = B.build();

    private HudConfig() {}

    public static int primary() {
        return THEME.get().primary;
    }

    public static int accent() {
        return THEME.get().accent;
    }
}
