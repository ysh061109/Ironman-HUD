package io.github.ysh061109.ironhud;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cinematic lock-on: rotating rings snap onto the target, a wireframe box with a sweeping scan
 * plane wraps it, and a typed-out readout of Minecraft data (type, health, armor, attack, speed,
 * status effects, ...) streams out on a leader line.
 */
public final class TargetScan {
    private static final int RED = 0xFF3B30, AMBER = 0xFFB020, WHITE = 0xDFF9FF, GRAY = 0x7B8794;

    private TargetScan() {}

    private record Line(String text, int rgb, boolean bar, float barFrac) {}

    public static void render(Cam cam, float pt, float t, int w, int h, int pri, int acc) {
        LivingEntity e = HudState.targetEntity;
        if (!HudState.hasTarget || e == null) return;

        boolean hostile = HudState.targetHostile, dead = HudState.targetDead;
        int rgb = dead ? GRAY : hostile ? RED : pri;
        int sec = dead ? GRAY : hostile ? AMBER : acc;

        // ---- project the (interpolated) bounding box
        Vec3 off = e.getPosition(pt).subtract(e.position());
        AABB bb = e.getBoundingBox().move(off);
        float[][] sc = new float[8][2];
        boolean[] ok = new boolean[8];
        float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f;
        int okCount = 0;
        for (int i = 0; i < 8; i++) {
            Vec3 c = new Vec3((i & 1) != 0 ? bb.maxX : bb.minX, (i & 4) != 0 ? bb.maxY : bb.minY,
                    (i & 2) != 0 ? bb.maxZ : bb.minZ);
            ok[i] = cam.project(c, sc[i]);
            if (ok[i]) {
                okCount++;
                minX = Math.min(minX, sc[i][0]);
                maxX = Math.max(maxX, sc[i][0]);
                minY = Math.min(minY, sc[i][1]);
                maxY = Math.max(maxY, sc[i][1]);
            }
        }
        if (okCount == 0) return;
        float[] ctr = new float[2];
        if (!cam.project(bb.getCenter(), ctr)) {
            ctr[0] = (minX + maxX) * 0.5f;
            ctr[1] = (minY + maxY) * 0.5f;
        }
        float cx = ctr[0], cy = ctr[1];

        float k = Mth.clamp((HudState.lockTicks + pt) / 12f, 0f, 1f);
        float ease = 1f - (1f - k) * (1f - k) * (1f - k);
        float a = 0.25f + 0.75f * ease;
        float size = Math.max(maxX - minX, maxY - minY);
        float r = Mth.clamp(size * 0.55f, 20f, Math.min(w, h) * 0.36f);
        float R = (r + 8f) * (1f + (1f - ease) * 1.2f);
        float spin = dead ? 0f : t;

        // ---- wireframe box
        for (int i = 0; i < 8; i++) {
            for (int j = i + 1; j < 8; j++) {
                if (Integer.bitCount(i ^ j) != 1 || !ok[i] || !ok[j]) continue;
                Draw.line(sc[i][0], sc[i][1], sc[j][0], sc[j][1], 1f, Draw.col(rgb, 0.5f * a));
            }
        }

        // ---- sweeping scan plane (with a short trail)
        if (!dead) {
            float phase = (t * 0.025f) % 1f;
            float tri = phase < 0.5f ? phase * 2f : 2f - phase * 2f;
            double hgt = bb.maxY - bb.minY;
            for (int trail = 0; trail < 3; trail++) {
                double y = bb.minY + hgt * Mth.clamp(tri - trail * 0.05f * (phase < 0.5f ? 1 : -1), 0f, 1f);
                float[][] q = new float[4][2];
                boolean all = cam.project(new Vec3(bb.minX, y, bb.minZ), q[0])
                        & cam.project(new Vec3(bb.maxX, y, bb.minZ), q[1])
                        & cam.project(new Vec3(bb.maxX, y, bb.maxZ), q[2])
                        & cam.project(new Vec3(bb.minX, y, bb.maxZ), q[3]);
                if (!all) continue;
                int c = Draw.col(sec, (0.95f - trail * 0.3f) * a);
                for (int i = 0; i < 4; i++) {
                    float[] p0 = q[i], p1 = q[(i + 1) % 4];
                    Draw.line(p0[0], p0[1], p1[0], p1[1], trail == 0 ? 1.4f : 1f, c);
                }
            }
        }

        // ---- ring stack
        for (int i = 0; i < 12; i++) {
            Draw.arc(cx, cy, R - 0.8f, R + 0.8f, i * 30f + spin * 1.5f, 18f, Draw.col(rgb, 0.95f * a));
        }
        Draw.ring(cx, cy, R - 7f, 1f, Draw.col(rgb, 0.35f * a));
        for (int i = 0; i < 48; i++) {
            float ang = (float) Math.toRadians(i * 7.5f - spin * 2f);
            float len = i % 4 == 0 ? 4.5f : 2.2f;
            float c0 = (float) Math.cos(ang), s0 = (float) Math.sin(ang);
            Draw.line(cx + c0 * (R - 7f), cy + s0 * (R - 7f), cx + c0 * (R - 7f - len), cy + s0 * (R - 7f - len),
                    1f, Draw.col(rgb, 0.7f * a));
        }
        float hpF = Mth.clamp(HudState.targetHp / HudState.targetMaxHp, 0f, 1f);
        Draw.arc(cx, cy, R - 15f, R - 12f, -90f, 360f, Draw.col(sec, 0.15f * a));
        Draw.arc(cx, cy, R - 15f, R - 12f, -90f, 360f * hpF * ease, Draw.col(hpF < 0.3f ? RED : sec, 0.95f * a));
        for (int i = 0; i < 3; i++) {
            Draw.arc(cx, cy, R - 21f, R - 19.5f, i * 120f + spin * 3f, 50f, Draw.col(rgb, 0.8f * a));
        }
        // cardinal pointers
        for (int i = 0; i < 4; i++) {
            float ang = (float) Math.toRadians(i * 90f + 45f);
            float c0 = (float) Math.cos(ang), s0 = (float) Math.sin(ang);
            Draw.line(cx + c0 * (R + 3f), cy + s0 * (R + 3f), cx + c0 * (R + 10f), cy + s0 * (R + 10f), 1.4f,
                    Draw.col(sec, a));
        }
        // center cross
        Draw.line(cx - 5f, cy, cx + 5f, cy, 1f, Draw.col(rgb, 0.8f * a));
        Draw.line(cx, cy - 5f, cx, cy + 5f, 1f, Draw.col(rgb, 0.8f * a));

        // ---- leader line + readout
        int side = (cx + R + 175f > w) ? -1 : 1;
        float ang = (float) Math.toRadians(side == 1 ? -45f : -135f);
        float ax = cx + (float) Math.cos(ang) * (R + 10f), ay = cy + (float) Math.sin(ang) * (R + 10f);
        float ex = ax + side * 18f, ey = ay - 18f;
        Draw.line(ax, ay, ex, ey, 1.2f, Draw.col(sec, a));
        Draw.line(ex, ey, ex + side * 12f, ey, 1.2f, Draw.col(sec, a));

        WireCard.render(e, cx, cy, R, side, ease, t, w, h, rgb, sec, dead);

        List<Line> lines = buildLines(e, rgb, sec);
        float lh = 9.5f, pw = 150f, scale = 0.7f;
        float ph = 22f + lines.size() * lh;
        float px = side == 1 ? ex + 12f : ex - 12f - pw;
        float py = Mth.clamp(ey - 8f, 8f, h - ph - 8f);

        float pa = ease;
        Draw.rect(px, py, px + pw, py + ph, Draw.col(0x000000, 0.45f * pa));
        Draw.rect(px, py, px + pw, py + 1.5f, Draw.col(sec, pa));
        Draw.rect(px, py + ph - 1f, px + pw, py + ph, Draw.col(sec, 0.6f * pa));
        float edgeX = side == 1 ? px : px + pw - 1.5f;
        Draw.rect(edgeX, py, edgeX + 1.5f, py + ph, Draw.col(sec, pa));

        // header (instant), blinks while acquiring
        boolean acquiring = HudState.lockTicks < 20;
        String head = dead ? ">> TARGET NEUTRALIZED" : acquiring ? ">> TARGET ACQUIRED" : ">> TRACKING";
        float hb = acquiring && !dead ? 0.55f + 0.45f * (float) Math.sin(t * 0.9f) : 1f;
        Draw.text(head, px + 6f, py + 5f, Draw.col(sec, hb * pa), 0.8f);

        // typed-out body
        int budget = (int) ((HudState.lockTicks + pt) * 6f);
        float ty = py + 17f;
        for (Line ln : lines) {
            int n = Math.min(ln.text().length(), Math.max(0, budget));
            budget -= ln.text().length();
            if (n > 0) {
                Draw.text(ln.text().substring(0, n), px + 6f, ty, Draw.col(ln.rgb(), pa), scale);
            }
            if (n < ln.text().length()) {
                float cw = Draw.width(ln.text().substring(0, n), scale);
                if (((int) (t * 0.5f)) % 2 == 0) {
                    Draw.rect(px + 6f + cw, ty, px + 6f + cw + 3f, ty + 6f, Draw.col(sec, pa));
                }
                break;
            }
            if (ln.bar()) {
                float bx = px + 6f + Draw.width(ln.text(), scale) + 4f;
                float bw = Math.max(10f, px + pw - 6f - bx);
                Draw.rect(bx, ty + 1f, bx + bw, ty + 5f, Draw.col(ln.rgb(), 0.2f));
                Draw.rect(bx, ty + 1f, bx + bw * ln.barFrac(), ty + 5f, Draw.col(ln.rgb(), 0.95f));
            }
            ty += lh;
        }
    }

    // ---------------------------------------------------------------- data

    public static double attack(LivingEntity e) {
        return e.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? e.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0;
    }

    private static String up(String s) {
        return s.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private static String pad(String key) {
        StringBuilder sb = new StringBuilder(key);
        while (sb.length() < 7) sb.append(' ');
        return sb.toString();
    }

    private static List<Line> buildLines(LivingEntity e, int rgb, int sec) {
        List<Line> out = new ArrayList<>();
        int white = WHITE;

        String type = up(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
        out.add(new Line(pad("TYPE") + type, white, false, 0));

        String cls;
        if (e instanceof Player) cls = "PLAYER";
        else if (HudState.targetHostile) cls = "HOSTILE";
        else cls = up(e.getType().getCategory().getName());
        if (e.getType().is(EntityTypeTags.UNDEAD)) cls += " / UNDEAD";
        out.add(new Line(pad("CLASS") + cls, white, false, 0));

        float hp = Math.max(0f, e.getHealth()), max = Math.max(1f, e.getMaxHealth());
        out.add(new Line(String.format(Locale.ROOT, "%s%.0f/%.0f", pad("HEALTH"), hp, max), sec, true,
                Mth.clamp(hp / max, 0f, 1f)));

        double atk = attack(e);
        out.add(new Line(String.format(Locale.ROOT, "%s%d   ATK %.1f", pad("ARMOR"), e.getArmorValue(), atk),
                white, false, 0));

        double dx = e.getX() - e.xo, dy = e.getY() - e.yo, dz = e.getZ() - e.zo;
        double v = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
        out.add(new Line(String.format(Locale.ROOT, "%s%.1f M/S", pad("SPEED"), v), white, false, 0));
        out.add(new Line(String.format(Locale.ROOT, "%s%.1f M", pad("RANGE"), HudState.targetDist), white, false, 0));
        out.add(new Line(String.format(Locale.ROOT, "%s%d %d %d", pad("POS"), e.getBlockX(), e.getBlockY(), e.getBlockZ()),
                white, false, 0));
        out.add(new Line(String.format(Locale.ROOT, "%s%.1f x %.1f", pad("SIZE"), e.getBbWidth(), e.getBbHeight()),
                white, false, 0));

        List<String> flags = new ArrayList<>();
        if (e.isOnFire()) flags.add("BURNING");
        if (e.isBaby()) flags.add("JUVENILE");
        if (e.fireImmune()) flags.add("FIREPROOF");
        if (!e.onGround() && !e.isInWater() && !(e instanceof Player)) flags.add("AIRBORNE");
        if (e.isInWater()) flags.add("SUBMERGED");
        if (e.isInvisible()) flags.add("CLOAKED");
        out.add(new Line(pad("STATUS") + (flags.isEmpty() ? "NOMINAL" : String.join(" ", flags)), white, false, 0));

        List<String> fx = new ArrayList<>();
        for (MobEffectInstance m : e.getActiveEffects()) {
            if (fx.size() >= 2) break;
            fx.add(up(m.getEffect().value().getDisplayName().getString()));
        }
        out.add(new Line(pad("EFFECT") + (fx.isEmpty() ? "NONE" : String.join(", ", fx)), white, false, 0));

        ItemStack held = e.getMainHandItem();
        out.add(new Line(pad("HELD") + (held.isEmpty() ? "NONE" : up(held.getHoverName().getString())),
                white, false, 0));

        String threat;
        int trgb;
        if (!HudState.targetHostile && !(e instanceof Player)) {
            threat = "NONE";
            trgb = white;
        } else {
            double score = atk + max * 0.15 + (HudState.targetDist < 8 ? 2 : 0);
            threat = score >= 9 ? "HIGH" : score >= 5 ? "MODERATE" : "LOW";
            trgb = score >= 9 ? RED : score >= 5 ? AMBER : white;
        }
        out.add(new Line(pad("THREAT") + threat, trgb, false, 0));
        out.add(new Line(pad("ID") + "#" + e.getId(), GRAY, false, 0));
        return out;
    }
}
