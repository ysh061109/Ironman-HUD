package io.github.ysh061109.ironhud;

/** Suit power routing. Each preset gives one subsystem the lion's share and has real effects on the HUD. */
public final class Power {
    public enum Preset {
        BALANCED("BALANCED", 0.34f, 0.33f, 0.33f),
        SENSORS("SENSORS", 0.60f, 0.20f, 0.20f),
        OPTICS("OPTICS", 0.20f, 0.60f, 0.20f),
        ANALYSIS("ANALYSIS", 0.20f, 0.20f, 0.60f);

        public final String label;
        public final float sensors, optics, analysis;

        Preset(String label, float s, float o, float a) {
            this.label = label;
            this.sensors = s;
            this.optics = o;
            this.analysis = a;
        }
    }

    public static Preset preset = Preset.BALANCED;

    private Power() {}

    /** Multiplies radar range and lock range. */
    public static float sensorsMul() {
        return 0.5f + 1.5f * preset.sensors;
    }

    /** Multiplies the maximum optical zoom. */
    public static float opticsMul() {
        return 0.5f + 1.5f * preset.optics;
    }

    /** How many targets the multi-lock can hold. */
    public static int maxLocks() {
        return Math.round(3 + 6 * preset.analysis);
    }

    public static void cycle() {
        Preset[] v = Preset.values();
        set(v[(preset.ordinal() + 1) % v.length]);
    }

    public static void set(Preset p) {
        preset = p;
        SysLog.add("POWER ROUTED: " + p.label);
    }
}
