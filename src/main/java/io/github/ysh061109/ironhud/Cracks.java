package io.github.ysh061109.ironhud;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Procedural visor cracks: spawn on heavy hits, stay while the suit is nearly dead. */
public final class Cracks {
    public static final class Crack {
        /** Segments as {x0, y0, x1, y1, width}, in 0..1 screen space. */
        public final List<float[]> segs = new ArrayList<>();
        public final int born;
        public final int life; // -1 = persistent

        Crack(int born, int life) {
            this.born = born;
            this.life = life;
        }
    }

    private static final List<Crack> LIST = new ArrayList<>();
    private static final Random RND = new Random();
    private static boolean persistentSpawned = false;

    private Cracks() {}

    public static void clear() {
        LIST.clear();
        persistentSpawned = false;
    }

    public static List<Crack> all() {
        return LIST;
    }

    public static void add(float severity, boolean persistent) {
        if (LIST.size() >= 7) {
            for (int i = 0; i < LIST.size(); i++) {
                if (LIST.get(i).life >= 0) {
                    LIST.remove(i);
                    break;
                }
            }
            if (LIST.size() >= 7) return;
        }
        Crack c = new Crack(HudState.age, persistent ? -1 : 160 + (int) (severity * 120));
        // origin on a random edge
        int side = RND.nextInt(4);
        float t = 0.08f + RND.nextFloat() * 0.84f;
        float x = side == 0 ? t : side == 1 ? 1f : side == 2 ? t : 0f;
        float y = side == 0 ? 0f : side == 1 ? t : side == 2 ? 1f : t;
        double inward = Math.atan2(0.5f - y, 0.5f - x);
        float len = 0.07f + 0.06f * Math.min(1.5f, severity);
        trunk(c, x, y, inward + (RND.nextDouble() - 0.5) * 0.9, len, 6 + RND.nextInt(3), 1.8f, true);
        LIST.add(c);
    }

    private static void trunk(Crack c, float x, float y, double ang, float segLen, int n, float width, boolean branch) {
        for (int i = 0; i < n; i++) {
            ang += (RND.nextDouble() - 0.5) * 0.7;
            float l = segLen * (0.6f + RND.nextFloat() * 0.8f);
            float nx = x + (float) Math.cos(ang) * l * 0.62f; // screen is wider than tall
            float ny = y + (float) Math.sin(ang) * l;
            c.segs.add(new float[]{x, y, nx, ny, width});
            if (branch && i >= 1 && RND.nextFloat() < 0.5f) {
                double ba = ang + (RND.nextBoolean() ? 1 : -1) * (0.6 + RND.nextDouble() * 0.7);
                trunk(c, nx, ny, ba, segLen * 0.7f, 2 + RND.nextInt(3), width * 0.6f, false);
            }
            x = nx;
            y = ny;
            width = Math.max(0.8f, width * 0.9f);
        }
    }

    /** Called every tick: keeps or drops persistent cracks depending on remaining hull. */
    public static void tick(float hullFraction) {
        LIST.removeIf(c -> c.life >= 0 && HudState.age - c.born > c.life);
        if (hullFraction <= 0.3f) {
            if (!persistentSpawned) {
                persistentSpawned = true;
                add(1.2f, true);
                add(0.9f, true);
            }
        } else if (hullFraction > 0.45f && persistentSpawned) {
            persistentSpawned = false;
            LIST.removeIf(c -> c.life < 0);
        }
    }
}
