package io.github.ysh061109.ironhud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Big banner alerts (the red "label alerts" of the movie HUD) with per-key cooldowns. */
public final class Alerts {
    public enum Level {
        INFO(0), WARN(1), CRIT(2);
        public final int rank;

        Level(int rank) {
            this.rank = rank;
        }
    }

    public static final class Alert {
        public final String key, title, sub;
        public final Level level;
        public final int start, end;

        Alert(String key, String title, String sub, Level level, int start, int end) {
            this.key = key;
            this.title = title;
            this.sub = sub;
            this.level = level;
            this.start = start;
            this.end = end;
        }
    }

    private static final List<Alert> ACTIVE = new ArrayList<>();
    private static final Map<String, Integer> LAST_FIRED = new HashMap<>();

    private Alerts() {}

    public static void clear() {
        ACTIVE.clear();
        LAST_FIRED.clear();
    }

    /**
     * @param key      identity for cooldown / replacement
     * @param duration how long the banner stays (ticks)
     * @param cooldown minimum ticks between two firings of this key
     */
    public static void fire(String key, String title, String sub, Level level,
                            int duration, int cooldown, Sfx sound) {
        int now = HudState.age;
        Integer last = LAST_FIRED.get(key);
        if (last != null && now - last < cooldown) return;
        LAST_FIRED.put(key, now);
        ACTIVE.removeIf(a -> a.key.equals(key));
        ACTIVE.add(new Alert(key, title, sub, level, now, now + duration));
        if (sound != null) sound.play();
        SysLog.add(sub.isEmpty() ? title : title + " - " + sub);
        Modus.onAlert(key, title, sub, level);
    }

    /** Refreshes a banner that is still true (no sound, no cooldown). */
    public static void keep(String key, String title, String sub, Level level, int duration) {
        int now = HudState.age;
        ACTIVE.removeIf(a -> a.key.equals(key));
        ACTIVE.add(new Alert(key, title, sub, level, now, now + duration));
    }

    public static void tick() {
        int now = HudState.age;
        ACTIVE.removeIf(a -> now >= a.end);
        while (ACTIVE.size() > 6) ACTIVE.remove(0);
    }

    /** Highest level first, newest first. */
    public static List<Alert> sorted() {
        List<Alert> copy = new ArrayList<>(ACTIVE);
        copy.sort(Comparator.<Alert>comparingInt(a -> -a.level.rank).thenComparingInt(a -> -a.start));
        return copy;
    }
}
