package io.github.ysh061109.ironhud;

import java.util.ArrayList;
import java.util.List;

/** Rolling "system log" lines shown top-left. */
public final class SysLog {
    public record Entry(String text, int born) {}

    private static final List<Entry> LINES = new ArrayList<>();

    private SysLog() {}

    public static void add(String text) {
        for (int i = LINES.size() - 1; i >= 0 && i >= LINES.size() - 4; i--) {
            if (LINES.get(i).text().equals(text) && HudState.age - LINES.get(i).born() < 600) {
                LINES.remove(i); // same message again: just refresh it instead of stacking duplicates
                break;
            }
        }
        LINES.add(new Entry(text, HudState.age));
        while (LINES.size() > 6) LINES.remove(0);
    }

    public static void clear() {
        LINES.clear();
    }

    public static List<Entry> all() {
        return LINES;
    }
}
