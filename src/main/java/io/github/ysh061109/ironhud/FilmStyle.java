package io.github.ysh061109.ironhud;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Elements taken from studying film stills of the HUD: the row of small round gauges along the bottom,
 * the vertical tape ladder on the left, bracket arcs and thin cross lines around the reticle, the flat
 * target rings drawn on the ground under tracked objects, and inward-pointing lock arrows.
 */
public final class FilmStyle {
    private static final float[] A = new float[2];
    private static final float[] B = new float[2];

    private FilmStyle() {}

    private static final String[] LABELS = {"HP", "AR", "FD", "O2", "SP", "XP"};

    /** Six round gauges centered at the bottom edge, present in almost every film frame. */
    public static void gaugeRow(LocalPlayer p, int w, int h, float t, float boot, int pri, int warn) {
        float[] f = new float[6];
        f[0] = Mth.clamp(p.getHealth() / Math.max(1f, p.getMaxHealth()), 0f, 1f);
        f[1] = Mth.clamp(p.getArmorValue() / 20f, 0f, 1f);
        f[2] = Mth.clamp(p.getFoodData().getFoodLevel() / 20f, 0f, 1f);
        f[3] = Mth.clamp(p.getAirSupply() / (float) Math.max(1, p.getMaxAirSupply()), 0f, 1f);
        f[4] = Mth.clamp(HudState.speed / (p.isFallFlying() ? 40f : 8f), 0f, 1f);
        f[5] = Mth.clamp(p.experienceProgress, 0f, 1f);
        float r = 9f, gap = 30f, y = h - 16f;
        float x0 = w / 2f - gap * 2.5f;
        for (int i = 0; i < 6; i++) {
            float x = x0 + i * gap;
            float s = Mth.clamp(boot * 6f - i * 0.6f, 0f, 1f);
            if (s <= 0f) continue;
            boolean low = f[i] < 0.25f && i < 4;
            int rgb = low ? warn : pri;
            float a = s * (low ? 0.65f + 0.35f * (float) Math.sin(t * 0.6f) : 1f);
            Draw.ring(x, y, r, 1f, Draw.col(rgb, 0.35f * a));
            Draw.arc(x, y, r - 1.5f, r + 0.5f, -90f, 360f * f[i], Draw.col(rgb, a));
            Draw.dashedRing(x, y, r + 3.5f, 0.8f, t * (i % 2 == 0 ? 1.5f : -1.5f), 12, Draw.col(pri, 0.25f * s));
            Draw.textCenter(LABELS[i], x, y - 3f, Draw.col(0xDFF9FF, 0.85f * s), 0.5f);
        }
    }

    /** Left vertical tape ladder (altitude), with a value box and a marker. */
    public static void ladder(LocalPlayer p, float pt, int h, float boot, int pri) {
        double alt = Mth.lerp(pt, p.yo, p.getY());
        float x = 10f, y0 = h * 0.36f, y1 = h * 0.50f, mid = (y0 + y1) / 2f;
        float sp = 5f; // pixels per block
        int c = Draw.col(pri, 0.8f * boot);
        Draw.line(x + 16f, y0, x + 16f, y1, 1f, Draw.col(pri, 0.5f * boot));
        int base = (int) Math.floor(alt);
        for (int k = -14; k <= 14; k++) {
            int v = base + k;
            float y = mid - (float) ((v - alt) * sp);
            if (y < y0 || y > y1) continue;
            float edge = 1f - Math.abs(y - mid) / ((y1 - y0) / 2f);
            boolean major = v % 5 == 0;
            Draw.line(x + 16f, y, x + (major ? 8f : 12f), y, 1f, Draw.col(pri, (0.3f + 0.6f * edge) * boot));
            if (major) Draw.text(String.valueOf(v), x - 4f + 14f, y - 3f, Draw.col(pri, (0.3f + 0.6f * edge) * boot), 0.5f);
        }
        Draw.rect(x - 2f, mid - 5f, x + 15f, mid + 5f, Draw.col(0x001018, 0.8f * boot));
        Draw.box(x - 2f, mid - 5f, 17f, 10f, pri, 0f, 0.9f * boot);
        Draw.textCenter(String.valueOf((int) Math.floor(alt)), x + 6.5f, mid - 3f, c, 0.6f);
        Draw.text("MSL", x, y0 - 9f, c, 0.55f);
    }

    /** Thin cross lines, flanking bracket arcs, and the heading tag above the reticle. */
    public static void reticleExtras(float cx, float cy, int w, float heading, float boot, int pri) {
        int thin = Draw.col(pri, 0.22f * boot);
        Draw.line(cx - w * 0.28f, cy, cx - 70f, cy, 0.8f, thin);
        Draw.line(cx + 70f, cy, cx + w * 0.28f, cy, 0.8f, thin);
        int c = Draw.col(pri, 0.75f * boot);
        Draw.arc(cx, cy, 58f, 60f, 150f, 60f, c);
        Draw.arc(cx, cy, 58f, 60f, -30f, 60f, c);
        Draw.triDown(cx, cy - 66f, 3f, 1f, c);
        Draw.textCenter(String.format(Locale.ROOT, "%03d", Math.round(heading) % 360), cx, cy - 80f, c, 0.7f);
    }

    /** Four arrows pointing inward on a ring: the film's lock-on reticle. */
    public static void lockArrows(float cx, float cy, float lock, float t, int rgb) {
        float R = 40f - 6f * lock;
        int c = Draw.col(rgb, 0.9f);
        Draw.dashedRing(cx, cy, R, 1f, t * 3f, 16, Draw.col(rgb, 0.5f));
        for (int i = 0; i < 4; i++) {
            float a = (float) Math.toRadians(45f + i * 90f);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float ox = cx + ca * (R + 12f), oy = cy + sa * (R + 12f);
            float ix = cx + ca * (R + 1f), iy = cy + sa * (R + 1f);
            Draw.line(ox, oy, ix, iy, 1.4f, c);
            float px = -sa, py = ca;
            Draw.line(ix, iy, ix + ca * 6f + px * 4f, iy + sa * 6f + py * 4f, 1.2f, c);
            Draw.line(ix, iy, ix + ca * 6f - px * 4f, iy + sa * 6f - py * 4f, 1.2f, c);
        }
    }

    /** Flat ring on the ground under a tracked entity (projected in 3D), as in the target-acquisition shots. */
    public static void groundRing(Cam cam, LivingEntity e, float pt, float t, int rgb, float alpha) {
        Vec3 f = e.getPosition(pt);
        double r = Math.max(0.6, e.getBbWidth() * 0.9 + 0.3);
        int n = 20;
        boolean have = false;
        float px = 0, py = 0;
        float rot = t * 0.04f;
        for (int i = 0; i <= n; i++) {
            double a = rot + i * Math.PI * 2 / n;
            Vec3 pnt = f.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
            boolean ok = cam.project(pnt, B);
            if (ok && have && (i % 4 != 3)) Draw.line(px, py, B[0], B[1], 1.1f, Draw.col(rgb, alpha));
            have = ok;
            px = B[0];
            py = B[1];
        }
        if (cam.project(f.add(0, 0.05, 0), A)) Draw.disc(A[0], A[1], 1.2f, Draw.col(rgb, alpha));
    }

    /** Luminous curved band along the bottom edge (Iron Man 3 style), with fine ticks. */
    public static void bottomArc(int w, int h, float t, float boot, int pri) {
        float R = w * 1.15f, cy = h + R - 36f;
        float half = (float) Math.toDegrees(Math.asin(Math.min(0.95f, (w * 0.5f) / R)));
        float sweep = half * 2f * boot;
        float st = 270f - sweep / 2f;
        Draw.arc(cx(w), cy, R - 1.2f, R + 0.8f, st, sweep, Draw.col(pri, 0.75f));
        Draw.arc(cx(w), cy, R - 9f, R - 8.3f, st, sweep, Draw.col(pri, 0.3f));
        for (int i = 0; i <= 60; i++) {
            float a = st + sweep * i / 60f;
            double r = Math.toRadians(a);
            float len = i % 5 == 0 ? 6f : 3f;
            float c = (float) Math.cos(r), sn = (float) Math.sin(r);
            Draw.line(cx(w) + c * (R - 1f), cy + sn * (R - 1f), cx(w) + c * (R - 1f - len), cy + sn * (R - 1f - len),
                    1f, Draw.col(pri, 0.4f));
        }
        float sweepPos = (t * 0.012f) % 1f;
        float a = st + sweep * sweepPos;
        Draw.arc(cx(w), cy, R - 2f, R + 1.5f, a, 5f, Draw.col(0xFFFFFF, 0.8f * boot));
    }

    private static float cx(int w) {
        return w / 2f;
    }

    /** Green plate used for "TARGET ACQUISITION" while scanning or multi-locking. */
    public static void acquisitionTag(int w, int h, float boot, int count) {
        if (!HudState.multiLock && HudState.age - HudState.scanStart > 100) return;
        float x = w / 2f - 120f, y = h - 62f;
        int g = 0x5DFFB0;
        Draw.rect(x, y, x + 96f, y + 12f, Draw.col(0x03231A, 0.75f * boot));
        Draw.box(x, y, 96f, 12f, g, 0f, 0.9f * boot);
        Draw.text("TARGET ACQUISITION", x + 4f, y + 3f, Draw.col(g, boot), 0.55f);
        Draw.text("TARGETS " + count, x + 4f, y + 15f, Draw.col(g, 0.85f * boot), 0.5f);
    }

    /** Linked hex nodes between locked targets (the network graph seen in the film's lock scenes). */
    public static void lockGraph(Cam cam, float pt, float t, int pri) {
        if (!HudState.multiLock) return;
        boolean have = false;
        float px = 0, py = 0;
        int i = 0;
        for (HudState.Contact c : HudState.locks) {
            LivingEntity e = c.e();
            Vec3 off = e.getPosition(pt).subtract(e.position());
            if (!cam.project(e.getBoundingBox().move(off).getCenter(), B)) {
                have = false;
                i++;
                continue;
            }
            if (have) Draw.line(px, py, B[0], B[1], 1f, Draw.col(pri, 0.55f));
            px = B[0];
            py = B[1];
            have = true;
            float r = 4f + (i == 0 ? 1.5f * (float) Math.sin(t * 0.3f) : 0f);
            for (int k = 0; k < 6; k++) {
                double a1 = Math.PI / 3 * k, a2 = Math.PI / 3 * (k + 1);
                Draw.line(px + (float) Math.cos(a1) * r, py + (float) Math.sin(a1) * r,
                        px + (float) Math.cos(a2) * r, py + (float) Math.sin(a2) * r, 1f, Draw.col(pri, 0.9f));
            }
            i++;
        }
    }

    /** Segmented thrust-output arc (thick bars that turn red when pushed), wrapped around the reactor. */
    public static void thrustArc(float cx, float cy, float t, float boot, int pri, int warn, float frac) {
        int n = 18;
        for (int i = 0; i < n; i++) {
            float a0 = 135f + 270f * i / n;
            boolean on = i < Math.round(frac * n);
            int rgb = i >= n - 5 ? warn : pri;
            float len = 270f / n - 3f;
            Draw.arc(cx, cy, 54f, 55f + (on ? 6f : 2f), a0, len * boot, Draw.col(rgb, on ? 0.9f : 0.2f));
        }
        Draw.textCenter("THRUST", cx, cy + 66f, Draw.col(pri, 0.7f * boot), 0.5f);
    }

    /** Amber angled plate with a slowly turning wireframe cube: the film's "diagnostic feed" panel. */
    public static void diagnosticCube(LocalPlayer p, int w, int h, float t, float boot, int amber) {
        float x = w - 92f, y = h * 0.40f, pw = 80f, ph = 64f, cut = 8f;
        int fr = Draw.col(amber, 0.85f * boot);
        Draw.rect(x + cut, y, x + pw, y + ph, Draw.col(0x1A1204, 0.45f * boot));
        Draw.rect(x, y + cut, x + cut, y + ph, Draw.col(0x1A1204, 0.45f * boot));
        Draw.line(x + cut, y, x + pw, y, 1.2f, fr);
        Draw.line(x, y + cut, x + cut, y, 1.2f, fr);
        Draw.line(x, y + cut, x, y + ph, 1.2f, fr);
        Draw.line(x, y + ph, x + pw, y + ph, 1.2f, fr);
        Draw.line(x + pw, y, x + pw, y + ph, 1.2f, fr);
        Draw.text("DIAGNOSTIC FEED", x + 12f, y + 3f, Draw.col(amber, boot), 0.45f);
        float cx = x + pw / 2f, cy = y + 31f, r = 13f;
        float ay = t * 0.03f, ax = 0.6f;
        float[][] v = new float[8][2];
        for (int i = 0; i < 8; i++) {
            float vx = (i & 1) == 0 ? -1 : 1, vy = (i & 2) == 0 ? -1 : 1, vz = (i & 4) == 0 ? -1 : 1;
            float x1 = vx * (float) Math.cos(ay) + vz * (float) Math.sin(ay);
            float z1 = -vx * (float) Math.sin(ay) + vz * (float) Math.cos(ay);
            float y2 = vy * (float) Math.cos(ax) - z1 * (float) Math.sin(ax);
            v[i][0] = cx + x1 * r;
            v[i][1] = cy + y2 * r;
        }
        int ec = Draw.col(amber, 0.9f * boot);
        for (int i = 0; i < 8; i++) {
            for (int b = 1; b <= 4; b <<= 1) {
                if ((i & b) == 0) Draw.line(v[i][0], v[i][1], v[i | b][0], v[i | b][1], 1f, ec);
            }
        }
        float avg = 0;
        int n = 0;
        for (int i = 0; i < 4; i++) if (HudState.armorPresent[i]) {
            avg += Mth.clamp(HudState.armorPct[i], 0f, 1f);
            n++;
        }
        String s = n == 0 ? "ARMOR --" : "ARMOR " + Math.round(avg / n * 100f) + "%";
        Draw.textCenter(s, cx, y + ph - 12f, Draw.col(0xFFFFFF, 0.85f * boot), 0.5f);
    }
}
