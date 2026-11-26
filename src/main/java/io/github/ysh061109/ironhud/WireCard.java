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

    public static void render(LivingEntity e, float cx, float cy, float R, int side, float ease, float t,
                              int w, int h, int rgb, int sec, boolean dead) {
        sample(e);
        if (ease < 0.15f) return;
        float a = ease;
        float cw = 150f, ch = 98f;
        // anchor: opposite side of the readout, above the ring
        float ang = (float) Math.toRadians(side == 1 ? -135f : -45f);
        float ax = cx + (float) Math.cos(ang) * (R + 4f), ay = cy + (float) Math.sin(ang) * (R + 4f);
        float bx = ax - side * 26f, by = ay - 26f;
        float x0 = side == 1 ? bx - 14f - cw : bx + 14f;
        float y0 = by - ch + 10f;
        x0 = Mth.clamp(x0, 10f, w - cw - 10f);
        y0 = Mth.clamp(y0, 40f, h - ch - 90f);
        int wl = Draw.col(WHITE, 0.9f * a);
        Draw.line(ax, ay, bx, by, 1f, wl);
        Draw.line(bx, by, side == 1 ? x0 + cw : x0, by, 1f, wl);
        Draw.line(side == 1 ? x0 + cw : x0, by, side == 1 ? x0 + cw : x0, y0 + ch, 1f, wl);

        // frame: top-left & bottom-right brackets, thin rules
        float arm = 12f;
        int fr = Draw.col(WHITE, 0.85f * a);
        Draw.rect(x0, y0, x0 + cw, y0 + ch, Draw.col(0x02101A, 0.35f * a));
        Draw.line(x0, y0, x0 + arm, y0, 1.2f, fr);
        Draw.line(x0, y0, x0, y0 + arm, 1.2f, fr);
        Draw.line(x0 + cw, y0 + ch, x0 + cw - arm, y0 + ch, 1.2f, fr);
        Draw.line(x0 + cw, y0 + ch, x0 + cw, y0 + ch - arm, 1.2f, fr);
        Draw.line(x0 + 6f, y0 + 26f, x0 + cw - 6f, y0 + 26f, 1f, Draw.col(rgb, 0.4f * a));

        // title (red, caps) + subtitle
        String name = e.getName().getString().toUpperCase(Locale.ROOT);
        float jitter = HudState.lockTicks < 14 ? (float) Math.sin(t * 5f) * 1.2f : 0f;
        Draw.text(name, x0 + 6f + jitter, y0 + 4f, Draw.col(dead ? 0x7B8794 : rgb, a), 1.15f);
        String cat = e.getType().getCategory().getName().toUpperCase(Locale.ROOT).replace('_', ' ');
        Draw.text(cat + " // " + e.getType().builtInRegistryHolder().key().location().getPath().toUpperCase(Locale.ROOT),
                x0 + 6f, y0 + 17f, Draw.col(WHITE, 0.65f * a), 0.5f);

        // shape-aware hologram of the creature (left half)
        float mx = x0 + 34f, my = y0 + 60f, mh = 54f;
        EntityHolo.renderCard(e, mx, my, mh, t, a, ease, rgb, sec);

        // right half: tag, stats, graphs
        float rx = x0 + 74f;
        float hpF = Mth.clamp(e.getHealth() / Math.max(1f, e.getMaxHealth()), 0f, 1f);
        boolean hostile = HudState.targetHostile;
        Draw.text(dead ? "NEUTRALIZED" : hostile ? "HOSTILE" : "NON-HOSTILE", rx, y0 + 31f,
                Draw.col(dead ? 0x7B8794 : hostile ? RED : sec, a), 0.8f);
        Draw.text(String.format(Locale.ROOT, "HP %.0f/%.0f", e.getHealth(), e.getMaxHealth()), rx, y0 + 42f,
                Draw.col(WHITE, 0.85f * a), 0.5f);
        Draw.text(String.format(Locale.ROOT, "ARM %.0f  ATK %.1f", e.getAttributeValue(Attributes.ARMOR),
                TargetScan.attack(e)), rx, y0 + 50f, Draw.col(WHITE, 0.85f * a), 0.5f);
        var pl = Minecraft.getInstance().player;
        float dist = pl == null ? 0f : pl.distanceTo(e);
        graph(rx, y0 + 60f, 62f, 12f, distHist, 0f, Math.max(8f, dist * 1.6f), sec, a);
        Draw.text("RANGE " + Math.round(dist) + "M", rx, y0 + 74f, Draw.col(sec, 0.8f * a), 0.45f);
        graph(rx, y0 + 82f, 62f, 8f, hpHist, 0f, 1f, rgb, a);
        Draw.text("INTEGRITY", rx + 66f, y0 + 86f, Draw.col(rgb, 0.7f * a), 0.4f);

        // red ring gauge hanging off the lower-left corner (the film's round icon)
        float gx = x0 - 6f, gy = y0 + ch + 6f, gr = 17f;
        Draw.arc(gx, gy, gr - 4f, gr, 0f, 360f, Draw.col(RED, 0.25f * a));
        Draw.arc(gx, gy, gr - 4f, gr, -90f, 360f * hpF, Draw.col(RED, 0.95f * a));
        Draw.ring(gx, gy, gr + 3f, 1f, Draw.col(WHITE, 0.4f * a));
        Draw.ring(gx, gy, gr - 8f, 1f, Draw.col(WHITE, 0.8f * a));
        Draw.disc(gx, gy, 3f, Draw.col(WHITE, 0.9f * a));
        Draw.textCenter(Math.round(hpF * 100f) + "%", gx, gy + gr + 6f, Draw.col(WHITE, 0.8f * a), 0.45f);

        // "projected" box: contact countdown + reach
        float px = x0 + 20f, py = y0 + ch + 8f, pw = 112f, ph = 34f;
        Draw.rect(px, py, px + pw, py + ph, Draw.col(0x02101A, 0.5f * a));
        Draw.line(px, py, px + 10f, py, 1.2f, fr);
        Draw.line(px, py, px, py + 10f, 1.2f, fr);
        Draw.line(px + pw, py + ph, px + pw - 10f, py + ph, 1.2f, fr);
        Draw.line(px + pw, py + ph, px + pw, py + ph - 10f, 1.2f, fr);
        double bps = e.getAttributeValue(Attributes.MOVEMENT_SPEED) * 43.0;
        String eta;
        if (dead || bps < 0.2) eta = "--:--";
        else {
            float s = (float) (dist / bps);
            eta = String.format(Locale.ROOT, "%02d:%02d", (int) (s / 60f), (int) (s % 60f));
        }
        Draw.text("PROJECTED", px + 5f, py + 3f, Draw.col(rgb, a), 0.55f);
        Draw.text("CONTACT", px + 5f, py + 10f, Draw.col(rgb, a), 0.55f);
        Draw.text(eta, px + 52f, py + 4f, Draw.col(WHITE, a), 1.3f);
        float reach = e.getBbWidth() * 2f + 0.3f;
        Draw.text(String.format(Locale.ROOT, "REACH %.1fM   SPD %.1f M/S", reach, bps), px + 5f, py + 24f,
                Draw.col(WHITE, 0.75f * a), 0.45f);
    }
}
