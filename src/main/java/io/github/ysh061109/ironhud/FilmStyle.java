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

    private static final String[] LABELS = {"SUIT", "OPTICS", "RADAR", "HORIZON", "MAP"};

    /**
     * The five persistent round icons along the bottom of the film HUD: suit status, targeting and
     * optics, radar, artificial horizon and map. Each one shows a live value as an arc.
     */
    public static void gaugeRow(LocalPlayer p, int w, int h, float t, float boot, int pri, int warn) {
        float armor = 0f;
        int n = 0;
        for (int i = 0; i < 4; i++) if (HudState.armorPresent[i]) {
            armor += Mth.clamp(HudState.armorPct[i], 0f, 1f);
            n++;
        }
        armor = n == 0 ? 0f : armor / n;
        int hostiles = 0;
        for (HudState.Contact c : HudState.contacts) if (c.kind() == HudState.HOSTILE) hostiles++;
        float[] f = new float[5];
        f[0] = armor;
        f[1] = Mth.clamp((HudState.zoomCur - 1f) / Math.max(0.1f, HudConfig.ZOOM_MAX.get().floatValue() - 1f), 0f, 1f);
        f[2] = Mth.clamp(hostiles / 8f, 0f, 1f);
        f[3] = Mth.clamp((p.getXRot() + 90f) / 180f, 0f, 1f);
        f[4] = HudState.waypoint != null ? 1f : 0f;
        boolean[] lit = {armor < 0.3f && n > 0, HudState.hasTarget || HudState.zoomCur > 1.05f, hostiles > 0,
                p.isFallFlying(), HudState.waypoint != null};
        float r = 10.5f, gap = 36f, y = h - 27f;
        float x0 = w / 2f - gap * 2f;
        for (int i = 0; i < 5; i++) {
            float x = x0 + i * gap;
            float s = Mth.clamp(boot * 6f - i * 0.7f, 0f, 1f);
            if (s <= 0f) continue;
            boolean alert = (i == 0 && lit[0]) || (i == 2 && lit[2]);
            int rgb = alert ? warn : pri;
            float a = s * (alert ? 0.7f + 0.3f * (float) Math.sin(t * 0.6f) : 1f);
            float glow = lit[i] ? 1f : 0.55f;
            Draw.ring(x, y, r, 1f, Draw.col(rgb, 0.4f * a * glow));
            Draw.arc(x, y, r + 1.5f, r + 3f, -90f, Math.max(8f, 360f * f[i]), Draw.col(rgb, 0.85f * a * glow));
            Draw.dashedRing(x, y, r + 5.5f, 0.8f, t * (i % 2 == 0 ? 1.5f : -1.5f), 14, Draw.col(pri, 0.22f * s));
            int ic = Draw.col(rgb, a * glow);
            switch (i) {
                case 0 -> { // suit: tiny figure
                    Draw.disc(x, y - 5f, 2f, ic);
                    Draw.rect(x - 3f, y - 2f, x + 3f, y + 3f, ic);
                    Draw.rect(x - 2.5f, y + 3.5f, x - 0.8f, y + 7f, ic);
                    Draw.rect(x + 0.8f, y + 3.5f, x + 2.5f, y + 7f, ic);
                }
                case 1 -> { // optics: reticle
                    Draw.ring(x, y, 4f, 1f, ic);
                    Draw.line(x - 8f, y, x - 2f, y, 1f, ic);
                    Draw.line(x + 2f, y, x + 8f, y, 1f, ic);
                    Draw.line(x, y - 8f, x, y - 2f, 1f, ic);
                    Draw.line(x, y + 2f, x, y + 8f, 1f, ic);
                }
                case 2 -> { // radar: rings + sweep
                    Draw.ring(x, y, 3f, 1f, ic);
                    Draw.ring(x, y, 6f, 1f, Draw.col(rgb, 0.6f * a));
                    float sw = t * 0.12f;
                    Draw.line(x, y, x + (float) Math.cos(sw) * 7f, y + (float) Math.sin(sw) * 7f, 1f, ic);
                }
                case 3 -> { // horizon: tilted line offset by pitch
                    float off = Mth.clamp(p.getXRot() / 90f, -1f, 1f) * 5f;
                    Draw.line(x - 7f, y + off, x + 7f, y + off, 1.2f, ic);
                    Draw.line(x - 3f, y - 1.5f, x + 3f, y - 1.5f, 1f, Draw.col(rgb, 0.6f * a));
                }
                default -> { // map: grid + marker
                    Draw.box(x - 6f, y - 6f, 12f, 12f, rgb, 0f, a * glow);
                    Draw.line(x - 6f, y, x + 6f, y, 1f, Draw.col(rgb, 0.5f * a));
                    Draw.line(x, y - 6f, x, y + 6f, 1f, Draw.col(rgb, 0.5f * a));
                    Draw.disc(x + 2f, y - 2f, 1.5f, ic);
                }
            }
            Draw.textCenter(LABELS[i], x, y + r + 6f, Draw.col(0xDFF9FF, 0.55f * s), 0.4f);
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
        float x = w / 2f + 24f, y = h - 62f;
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

    /** Film-style classification tag ("HOSTILE", "CIVILIAN", ...) as in the hostage-rescue targeting shot. */
    public static String classify(LivingEntity e, boolean hostile) {
        if (hostile) return "HOSTILE";
        if (e instanceof net.minecraft.world.entity.player.Player) return "ALLY";
        if (e instanceof net.minecraft.world.entity.npc.AbstractVillager) return "CIVILIAN";
        if (e instanceof net.minecraft.world.entity.animal.Animal) return "WILDLIFE";
        return "NEUTRAL";
    }

    public static void classTag(float x, float y, String tag, int rgb, float alpha) {
        float tw = Draw.width(tag, 0.5f);
        Draw.rect(x - tw / 2f - 3f, y, x + tw / 2f + 3f, y + 8f, Draw.col(0x02101A, 0.55f * alpha));
        Draw.box(x - tw / 2f - 3f, y, tw + 6f, 8f, rgb, 0f, 0.9f * alpha);
        Draw.textCenter(tag, x, y + 1.5f, Draw.col(0xFFFFFF, alpha), 0.5f);
    }

    private static void plate(float x, float y, float w, float h, int rgb, float a) {
        float c = 3f;
        int fr = Draw.col(rgb, 0.85f * a);
        Draw.rect(x + c, y, x + w, y + h, Draw.col(0x02101A, 0.5f * a));
        Draw.line(x + c, y, x + w, y, 1f, fr);
        Draw.line(x, y + c, x + c, y, 1f, fr);
        Draw.line(x, y + c, x, y + h, 1f, fr);
        Draw.line(x, y + h, x + w - c, y + h, 1f, fr);
        Draw.line(x + w - c, y + h, x + w, y + h - c, 1f, fr);
        Draw.line(x + w, y, x + w, y + h - c, 1f, fr);
    }

    private static void segBar(float x, float y, float w, float h, int n, float frac, int rgb, float a) {
        float sw = (w - (n - 1) * 1f) / n;
        int on = Math.round(Mth.clamp(frac, 0f, 1f) * n);
        for (int i = 0; i < n; i++) {
            float sx = x + i * (sw + 1f);
            Draw.rect(sx, y, sx + sw, y + h, Draw.col(rgb, (i < on ? 0.95f : 0.18f) * a));
        }
    }

    /** Food, oxygen and experience as angled plates with segmented bars (film-style readouts). */
    public static void vitals(LocalPlayer p, float x, float y, float t, float boot, int pri, int warn) {
        float food = Mth.clamp(p.getFoodData().getFoodLevel() / 20f, 0f, 1f);
        float air = Mth.clamp(p.getAirSupply() / (float) Math.max(1, p.getMaxAirSupply()), 0f, 1f);
        float xp = Mth.clamp(p.experienceProgress, 0f, 1f);
        boolean underwater = air < 0.999f;
        float pw = 112f, ph = 11f;
        float[] vals = {food, air, xp};
        String[] lab = {"NUTRIENT", "O2 RESERVE", "XP  LV " + p.experienceLevel};
        for (int i = 0; i < 3; i++) {
            float yy = y + i * (ph + 3f);
            float v = vals[i];
            boolean low = i < 2 && v < 0.3f && (i == 0 || underwater);
            int rgb = low ? warn : (i == 2 ? 0x8DFFB0 : pri);
            float a = boot * (i == 1 && !underwater ? 0.45f : 1f) * (low ? 0.65f + 0.35f * (float) Math.sin(t * 0.7f) : 1f);
            plate(x, yy, pw, ph, rgb, a);
            Draw.text(lab[i], x + 5f, yy + 3f, Draw.col(0xDFF9FF, 0.85f * a), 0.45f);
            segBar(x + 52f, yy + 3f, 40f, 5f, 10, v, rgb, a);
            Draw.textRight(Math.round(v * 100f) + "%", x + pw - 4f, yy + 3f, Draw.col(rgb, a), 0.45f);
        }
    }
}
