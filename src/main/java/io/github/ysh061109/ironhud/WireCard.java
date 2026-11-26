package io.github.ysh061109.ironhud;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Locale;

/**
 * Hologram info card for the locked target, modelled on the film's weapon/object cards: a rotating
 * wireframe of the target, a bent leader line, corner-bracket frame, a tag line, small trend graphs,
 * a ring gauge and a "projected" box with a contact countdown. All numbers are Minecraft data.
 */
public final class WireCard {
    private static final int WHITE = 0xFFFFFF, RED = 0xFF3B30;
    private static final int N = 28;
    private static final float[] distHist = new float[N];
    private static final float[] hpHist = new float[N];
    private static int histId = -1, lastAge = -1;

    private WireCard() {}

    private static void sample(LivingEntity e) {
        if (e.getId() != histId) {
            histId = e.getId();
            java.util.Arrays.fill(distHist, -1f);
            java.util.Arrays.fill(hpHist, -1f);
        }
        if (HudState.age == lastAge || HudState.age % 4 != 0) return;
        lastAge = HudState.age;
        System.arraycopy(distHist, 1, distHist, 0, N - 1);
        System.arraycopy(hpHist, 1, hpHist, 0, N - 1);
        var p = Minecraft.getInstance().player;
        distHist[N - 1] = p == null ? 0f : p.distanceTo(e);
        hpHist[N - 1] = e.getHealth() / Math.max(1f, e.getMaxHealth());
    }

    private static void graph(float x, float y, float w, float h, float[] d, float lo, float hi, int rgb, float a) {
        Draw.line(x, y + h, x + w, y + h, 1f, Draw.col(rgb, 0.3f * a));
        Draw.line(x, y, x, y + h, 1f, Draw.col(rgb, 0.3f * a));
        float px = 0, py = 0;
        boolean have = false;
        for (int i = 0; i < N; i++) {
            if (d[i] < 0f) continue;
            float gx = x + w * i / (N - 1f);
            float gy = y + h - Mth.clamp((d[i] - lo) / Math.max(1e-3f, hi - lo), 0f, 1f) * h;
            if (have) Draw.line(px, py, gx, gy, 1f, Draw.col(rgb, 0.9f * a));
            px = gx;
            py = gy;
            have = true;
        }
        if (have) Draw.diamond(px, py, 2f, 1f, Draw.col(WHITE, a));
    }

    private static float prof(float u) {
        // humanoid-ish radius profile: legs, torso, arms flare, neck, head
        if (u < 0.42f) return 0.42f;
        if (u < 0.46f) return Mth.lerp((u - 0.42f) / 0.04f, 0.42f, 0.95f);
        if (u < 0.78f) return 0.95f;
        if (u < 0.82f) return Mth.lerp((u - 0.78f) / 0.04f, 0.95f, 0.3f);
        if (u < 0.86f) return 0.3f;
        if (u < 0.9f) return Mth.lerp((u - 0.86f) / 0.04f, 0.3f, 0.6f);
        return 0.6f * (float) Math.sqrt(Math.max(0f, 1f - Mth.square((u - 0.93f) / 0.07f)) + 0.05f);
    }

    /** Dotted trend line with a numeric end label, as in the film's small side graphs. */
    private static void dotted(float x, float y, float w, float[] d, float lo, float hi, int rgb, float a, String label) {
        float px = 0, py = 0;
        boolean have = false;
        for (int i = 0; i < N; i++) {
            if (d[i] < 0f) continue;
            float gx = x + w * i / (N - 1f);
            float gy = y - Mth.clamp((d[i] - lo) / Math.max(1e-3f, hi - lo), 0f, 1f) * 7f;
            if (have) {
                for (int k = 0; k < 3; k++) {
                    float f = k / 3f;
                    Draw.rect(px + (gx - px) * f, py + (gy - py) * f, px + (gx - px) * f + 1f, py + (gy - py) * f + 1f,
                            Draw.col(rgb, 0.85f * a));
                }
            }
            px = gx;
            py = gy;
            have = true;
        }
        if (have) Draw.diamond(px, py, 1.6f, 1f, Draw.col(WHITE, a));
        Draw.text(label, x + w + 4f, y - 6f, Draw.col(WHITE, 0.85f * a), 0.45f);
    }

    /** L-shaped white bracket: a vertical rule with a short return at both ends. */
    private static void lBracket(float x, float y0, float y1, float dir, float arm, int col) {
        Draw.line(x, y0, x, y1, 1.2f, col);
        Draw.line(x, y0, x + dir * arm, y0, 1.2f, col);
        Draw.line(x, y1, x + dir * arm * 1.6f, y1, 1.2f, col);
    }

    public static void render(LivingEntity e, float cx, float cy, float R, int side, float ease, float t,
                              int w, int h, int rgb, int sec, boolean dead) {
        sample(e);
        if (ease < 0.15f) return;
        float a = ease;
        float cw = 150f, ch = 112f;
        int wl = Draw.col(WHITE, 0.9f * a);
        // anchor: diagonal straight leader from the ring, like the film's callout lines
        float ang = (float) Math.toRadians(side == 1 ? -50f : -130f);
        float ax = cx + (float) Math.cos(ang) * (R + 12f), ay = cy + (float) Math.sin(ang) * (R + 12f);
        float x0 = side == 1 ? ax + 22f : ax - 22f - cw;
        float y0 = ay - 40f - ch + 30f;
        x0 = Mth.clamp(x0, 10f, w - cw - 10f);
        y0 = Mth.clamp(y0, 40f, h - ch - 100f);
        float lx = side == 1 ? x0 : x0 + cw;
        float ly = y0 + 70f;
        Draw.line(ax, ay, lx - side * 2f, Math.max(y0 + 20f, Math.min(ly, ay)), 1f, wl);

        // title: red caps with a thin subtitle, no panel behind it
        String name = e.getName().getString().toUpperCase(Locale.ROOT);
        float jitter = HudState.lockTicks < 14 ? (float) Math.sin(t * 5f) * 1.2f : 0f;
        Draw.text(name, x0 + 2f + jitter, y0, Draw.col(dead ? 0x7B8794 : rgb, a), 1.4f);
        String cat = e.getType().getCategory().getName().toUpperCase(Locale.ROOT).replace('_', ' ');
        Draw.text(cat + "  //  " + e.getType().builtInRegistryHolder().key().location().getPath().toUpperCase(Locale.ROOT),
                x0 + 2f, y0 + 13f, Draw.col(WHITE, 0.6f * a), 0.45f);

        // large floating hologram of the creature, no frame
        float mx = x0 + cw * 0.42f, my = y0 + 84f, mh = 68f;
        EntityHolo.renderCard(e, mx, my, mh, t, a, ease, rgb, sec);

        // white bracket along the right edge of the hologram
        lBracket(x0 + cw, y0 + 20f, y0 + 90f, -1f, 9f, Draw.col(WHITE, 0.85f * a));
        Draw.line(x0 + cw - 8f, y0 + 20f, x0 + cw - 8f, y0 + 26f, 1f, Draw.col(WHITE, 0.5f * a));

        // red class tag with fine print and hazard glyph
        float ty = y0 + 96f;
        float hpF = Mth.clamp(e.getHealth() / Math.max(1f, e.getMaxHealth()), 0f, 1f);
        boolean hostile = HudState.targetHostile;
        int tc = dead ? 0x7B8794 : hostile ? RED : sec;
        Draw.text(dead ? "NEUTRALIZED" : hostile ? "HOSTILE" : "NON-HOSTILE", x0 + 2f, ty, Draw.col(tc, a), 1.0f);
        Draw.text(String.format(Locale.ROOT, "HEALTH %.0f/%.0f   ARMOR %.0f   STRIKE %.1f", e.getHealth(), e.getMaxHealth(),
                e.getAttributeValue(Attributes.ARMOR), TargetScan.attack(e)), x0 + 2f, ty + 11f,
                Draw.col(WHITE, 0.75f * a), 0.42f);
        Draw.line(x0 + 2f, ty + 9f, x0 + 2f, ty + 17f, 1f, Draw.col(WHITE, 0.5f * a));
        if (hostile && !dead) {
            float hx = x0 + cw - 16f, hy = ty + 7f;
            Draw.triDown(hx, hy, 6f, 1f, Draw.col(RED, a));
            Draw.disc(hx, hy - 1f, 1.2f, Draw.col(RED, a));
        }

        // small targeting reticle floating beside the callout
        float rx = x0 + cw * 0.5f, rY = y0 + 100f - 4f;
        float pul = 0.7f + 0.3f * (float) Math.sin(t * 0.2f);
        Draw.diamond(rx + 36f, rY, 4.5f, 1f, Draw.col(WHITE, 0.8f * a * pul));
        Draw.line(rx + 29f, rY, rx + 32f, rY, 1f, wl);
        Draw.line(rx + 40f, rY, rx + 43f, rY, 1f, wl);

        // dotted trend rows with numeric end labels
        var pl = Minecraft.getInstance().player;
        float dist = pl == null ? 0f : pl.distanceTo(e);
        float gy = ty + 30f;
        dotted(x0 + 2f, gy, 58f, distHist, 0f, Math.max(8f, dist * 1.6f), sec, a, Math.round(dist) + "M");
        dotted(x0 + 2f, gy + 11f, 58f, hpHist, 0f, 1f, rgb, a, Math.round(hpF * 100f) + "%");

        // ---- "projected" card below the ring: ghost-digit countdown, red title, white L frame
        float px = x0 + 4f, py = ty + 52f, pw = 118f, ph = 40f;
        // clamp so the card never leaves the screen
        py = Math.min(py, h - ph - 96f);
        double bps = e.getAttributeValue(Attributes.MOVEMENT_SPEED) * 43.0;
        String eta;
        if (dead || bps < 0.2) eta = "--:--";
        else {
            float s = (float) (dist / bps);
            eta = String.format(Locale.ROOT, "%02d:%02d", (int) (s / 60f), (int) (s % 60f));
        }
        Draw.rect(px, py - 15f, px + 78f, py - 3f, Draw.col(0x02101A, 0.35f * a));
        Draw.text("00", px + 3f, py - 14f, Draw.col(WHITE, 0.25f * a), 1.0f);
        Draw.text(":", px + 17f, py - 14f, Draw.col(WHITE, 0.4f * a), 1.0f);
        Draw.text(eta.replace(':', ' '), px + 22f, py - 14f, Draw.col(WHITE, a), 1.0f);
        Draw.text("TIME TO CONTACT", px + 55f, py - 12f, Draw.col(WHITE, 0.55f * a), 0.4f);
        Draw.line(px, py, px + 8f, py, 1.2f, wl);
        Draw.line(px, py, px, py + 8f, 1.2f, wl);
        Draw.line(px + pw, py, px + pw, py + ph, 1.2f, wl);
        Draw.line(px + pw - 10f, py + ph, px + pw, py + ph, 1.2f, wl);
        Draw.text("PROJECTED", px + 4f, py + 3f, Draw.col(WHITE, 0.75f * a), 0.4f);
        Draw.text("REACH RADIUS", px + 4f, py + 9f, Draw.col(tc, a), 0.8f);
        // reach map: concentric rings scaled to the creature's real reach, hex marker in the middle
        float reach = e.getBbWidth() * 2f + 0.3f;
        float mcx = px + 20f, mcy = py + 28f;
        float rr = Mth.clamp(reach * 4f, 5f, 11f);
        Draw.ring(mcx, mcy, rr, 1f, Draw.col(RED, 0.8f * a));
        Draw.ring(mcx, mcy, rr * 0.6f, 1f, Draw.col(sec, 0.7f * a));
        Draw.diamond(mcx, mcy, 2.2f, 1f, Draw.col(WHITE, a));
        for (int i = 0; i < 6; i++) {
            float aa = (float) Math.toRadians(i * 60f + t * 1.5f);
            Draw.line(mcx + (float) Math.cos(aa) * rr, mcy + (float) Math.sin(aa) * rr,
                    mcx + (float) Math.cos(aa) * (rr + 2.5f), mcy + (float) Math.sin(aa) * (rr + 2.5f), 1f, Draw.col(RED, a));
        }
        Draw.text(String.format(Locale.ROOT, "%.1f M REACH", reach), px + 40f, py + 22f, Draw.col(WHITE, 0.85f * a), 0.45f);
        Draw.text(String.format(Locale.ROOT, "%.1f M/S SPEED", bps), px + 40f, py + 29f, Draw.col(WHITE, 0.85f * a), 0.45f);
        Draw.text(String.format(Locale.ROOT, "%.0f HP  %s", e.getHealth(), dead ? "DOWN" : "LIVE"), px + 40f, py + 36f - 1f,
                Draw.col(WHITE, 0.6f * a), 0.4f);
    }
}
