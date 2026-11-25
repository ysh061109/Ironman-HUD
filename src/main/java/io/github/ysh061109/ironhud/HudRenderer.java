package io.github.ysh061109.ironhud;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.ysh061109.ironhud.Alerts.Alert;
import io.github.ysh061109.ironhud.HudState.Contact;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public final class HudRenderer {
    private static final int RED = 0xFF3B30;
    private static final int AMBER = 0xFFB020;
    private static final int WHITE = 0xDFF9FF;
    private static final int GRAY = 0x7B8794;
    private static final int GLASS = 0xCFEFFF;

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static float fps = 60f;
    private static long lastNano = 0L;
    private static final float[] XY = new float[2];

    private HudRenderer() {}

    // ------------------------------------------------------------------ main

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!HudState.active(mc)) return;
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;

        float pt = dt.getGameTimeDeltaPartialTick(true);
        float t = HudState.age + pt;
        float boot = Mth.clamp(t / 40f, 0f, 1f);
        updateFps();

        int w = g.guiWidth(), h = g.guiHeight();
        Cam cam = Cam.of(mc, w, h);
        int pri = HudConfig.primary();
        int acc = HudConfig.accent();
        boolean flying = p.isFallFlying() || p.getAbilities().flying;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        Draw.begin(g, mc.font);

        screenFx(w, h, t, pri);
        if (HudConfig.SHOW_HELMET.get()) Overlays.helmetFrame(w, h, pri);
        if (HudConfig.SHOW_CRACKS.get()) cracks(w, h, t);
        cornerBrackets(w, h, boot, pri);
        if (flying || HudConfig.HORIZON_ALWAYS.get()) horizon(cam, p, pt, w, h, pri, acc);
        scanPulse(w, h, pt, t, pri, acc);
        if (HudConfig.SHOW_MARKERS.get()) markers(cam, p, pt, t, w, h, pri, acc);
        Overlays.multiLock(cam, pt, t, w, h, pri);
        FilmStyle.lockGraph(cam, pt, t, pri);
        TargetScan.render(cam, pt, t, w, h, pri, acc);
        Overlays.hits(p, pt, w, h, t);
        if (HudConfig.SHOW_COMPASS.get()) compass(p, pt, w, t, boot, pri, acc);
        if (HudConfig.SHOW_SUIT_STATUS.get()) suitStatus(h, t, boot, pri);
        if (HudConfig.SHOW_WEAPONS.get()) WeaponPanel.render(p, 14f, h / 2f + 38f, t, boot, pri, acc);
        reactor(p, 58f, h - 58f, t, boot, pri);
        FilmStyle.thrustArc(58f, h - 58f, t, boot, pri, RED, Mth.clamp((p.isFallFlying() ? Math.max(HudState.speed, HudState.flightSpeed) / 40f : HudState.speed / 8f), 0f, 1f));
        FilmStyle.diagnosticCube(p, w, h, t, boot, acc == RED ? AMBER : AMBER);
        if (HudConfig.SHOW_RADAR.get()) radar(p, pt, w - 58f, h - 58f, t, boot, pri, acc);
        if (HudConfig.SHOW_TERRAIN.get()) TerrainMap.render(p, pt, w - 142f, h - 196f, 130f, 80f, t, boot, pri, acc);
        infoPanel(p, level, w, t, boot, pri);
        reticle(w / 2f, h / 2f, t, boot, pri);
        FilmStyle.reticleExtras(w / 2f, h / 2f, w, heading(p, pt), boot, pri);
        FilmStyle.bottomArc(w, h, t, boot, pri);
        FilmStyle.acquisitionTag(w, h, boot, HudState.multiLock ? HudState.locks.size() : HudState.contacts.size());
        FilmStyle.gaugeRow(p, w, h, t, boot, pri, RED);
        FilmStyle.ladder(p, pt, h, boot, pri);
        collisionReadout(w / 2f, h / 2f, t, p);
        alertBanners(w, t);
        if (HudConfig.SHOW_SYSLOG.get()) {
            Overlays.sysLog(14f, 26f, boot, pri);
            Overlays.modusPanel(w, h, pt, t, pri, acc);
        }
        Overlays.priorityList(14f, 78f, t, pri);
        Comms.draw(w, h, t, pri, acc);
        Diagnostics.draw(p, level, w, h, t, pri, acc);
        Overlays.boot(w, h, pt, pri, acc);

        Draw.end(g);
        Comms.drawFace(g, w, h);
        RenderSystem.enableCull();
    }

    private static void updateFps() {
        long now = System.nanoTime();
        if (lastNano != 0L) {
            float inst = 1.0e9f / Math.max(1L, now - lastNano);
            fps += (inst - fps) * 0.05f;
        }
        lastNano = now;
    }

    private static float pulse(float t, float speed) {
        return 0.5f + 0.5f * (float) Math.sin(t * speed);
    }

    // ------------------------------------------------------------ screen fx

    private static void screenFx(int w, int h, float t, int pri) {
        // slow scanline
        float y = (t * 3f) % h;
        Draw.rect(0, y, w, y + 1f, Draw.col(pri, 0.06f));

        // red edge flash after taking damage
        float f = HudState.hitFlash;
        if (f > 0.02f) {
            int c = Draw.col(RED, 0.55f * f), z = Draw.col(RED, 0f);
            float e = Math.min(w, h) * 0.22f;
            Draw.gradV(0, 0, w, e, c, z);
            Draw.gradV(0, h - e, w, h, z, c);
            Draw.gradH(0, 0, e, h, c, z);
            Draw.gradH(w - e, 0, w, h, z, c);
        }
        // persistent pulsing red edges at low hull
        float hp = HudState.dispHp / Math.max(1f, Minecraft.getInstance().player.getMaxHealth());
        if (hp < 0.3f) {
            float a = 0.10f + 0.12f * pulse(t, 0.35f);
            int c = Draw.col(RED, a), z = Draw.col(RED, 0f);
            float e = Math.min(w, h) * 0.16f;
            Draw.gradV(0, 0, w, e, c, z);
            Draw.gradV(0, h - e, w, h, z, c);
            Draw.gradH(0, 0, e, h, c, z);
            Draw.gradH(w - e, 0, w, h, z, c);
        }
    }

    private static void cracks(int w, int h, float t) {
        for (Cracks.Crack c : Cracks.all()) {
            float fade = 1f;
            if (c.life >= 0) {
                float age = HudState.age - c.born;
                fade = Mth.clamp(1f - (age - c.life * 0.6f) / (c.life * 0.4f), 0f, 1f);
            }
            if (fade <= 0f) continue;
            int bright = Draw.col(GLASS, 0.55f * fade);
            int dark = Draw.col(0x000000, 0.35f * fade);
            for (float[] s : c.segs) {
                float x0 = s[0] * w, y0 = s[1] * h, x1 = s[2] * w, y1 = s[3] * h;
                Draw.line(x0 + 1f, y0 + 1f, x1 + 1f, y1 + 1f, s[4], dark);
                Draw.line(x0, y0, x1, y1, s[4] * 0.7f, bright);
            }
        }
    }

    private static void cornerBrackets(int w, int h, float boot, int pri) {
        float m = 6f, len = 26f * boot;
        int c = Draw.col(pri, 0.6f);
        Draw.line(m, m, m + len, m, 1.2f, c);
        Draw.line(m, m, m, m + len, 1.2f, c);
        Draw.line(w - m, m, w - m - len, m, 1.2f, c);
        Draw.line(w - m, m, w - m, m + len, 1.2f, c);
        Draw.line(m, h - m, m + len, h - m, 1.2f, c);
        Draw.line(m, h - m, m, h - m - len, 1.2f, c);
        Draw.line(w - m, h - m, w - m - len, h - m, 1.2f, c);
        Draw.line(w - m, h - m, w - m, h - m - len, 1.2f, c);
    }

    // --------------------------------------------------------------- compass

    private static float heading(LocalPlayer p, float pt) {
        float yaw = Mth.rotLerp(pt, p.yRotO, p.getYRot());
        return ((yaw + 180f) % 360f + 360f) % 360f; // 0 = north
    }

    private static float bearingTo(LocalPlayer p, float pt, Vec3 target) {
        Vec3 me = p.getPosition(pt);
        double dx = target.x - me.x, dz = target.z - me.z;
        double yawT = Math.toDegrees(Math.atan2(-dx, dz));
        return (float) (((yawT + 180.0) % 360.0 + 360.0) % 360.0);
    }

    private static void compass(LocalPlayer p, float pt, int w, float t, float boot, int pri, int acc) {
        float heading = heading(p, pt);
        float cx = w / 2f, y = 16f, span = 70f, half = 110f * boot;
        int start = (int) Math.floor((heading - span) / 5f) * 5;

        for (int deg = start; deg <= heading + span; deg += 5) {
            float rel = deg - heading;
            float x = cx + rel / span * half;
            float fade = 1f - Math.abs(rel) / span;
            if (fade <= 0f) continue;
            int d = ((deg % 360) + 360) % 360;
            boolean major = d % 45 == 0, mid = d % 15 == 0;
            float len = major ? 8f : (mid ? 5f : 3f);
            Draw.line(x, y, x, y + len, 1f, Draw.col(pri, 0.8f * fade));
            if (major) {
                Draw.textCenter(DIRS[d / 45], x, y + 11f, Draw.col(d == 0 ? RED : WHITE, fade), 1f);
            }
        }

        // hostile ticks under the tape
        for (Contact c : HudState.contacts) {
            if (c.kind() != HudState.HOSTILE || c.dist() > 32) continue;
            float rel = Mth.wrapDegrees(bearingTo(p, pt, c.e().position()) - heading);
            if (Math.abs(rel) >= span) continue;
            float x = cx + rel / span * half;
            Draw.rect(x - 1f, y + 20f, x + 1f, y + 24f, Draw.col(RED, 0.9f * (1f - Math.abs(rel) / span)));
        }
        // waypoint marker
        if (HudState.waypoint != null) {
            float rel = Mth.wrapDegrees(bearingTo(p, pt, HudState.waypoint) - heading);
            float clamped = Mth.clamp(rel, -span, span);
            float x = cx + clamped / span * half;
            Draw.diamond(x, y + 4f, 3.5f, 1f, Draw.col(acc, 1f));
        }

        Draw.line(cx - 4, y - 6, cx, y - 1, 1.2f, Draw.col(acc, 1f));
        Draw.line(cx + 4, y - 6, cx, y - 1, 1.2f, Draw.col(acc, 1f));
        String hs = String.format(Locale.ROOT, "%03d", Math.round(heading) % 360);
        Draw.textCenter(hs, cx, y - 14f, Draw.col(pri, boot), 0.75f);
    }

    // ----------------------------------------------------------- suit status

    private static int partColor(int i, float t, int pri) {
        if (!HudState.armorPresent[i]) return GRAY;
        float pct = HudState.armorPct[i];
        if (HudState.armorFlash[i] > 0) return 0xFFFFFF;
        if (pct >= 0.6f) return pri;
        if (pct >= 0.3f) return AMBER;
        return RED;
    }

    private static float partAlpha(int i, float t) {
        if (!HudState.armorPresent[i]) return 0.35f;
        if (HudState.armorFlash[i] > 0) return 0.95f;
        if (HudState.armorPct[i] < 0.3f) return 0.45f + 0.5f * pulse(t, 0.6f);
        return 0.85f;
    }

    private static void suitStatus(int h, float t, float boot, int pri) {
        float x0 = 14f, y0 = h / 2f - 52f;
        Draw.text("SUIT STATUS", x0, y0 - 14f, Draw.col(pri, boot), 0.8f);
        Draw.line(x0, y0 - 4f, x0 + 90f * boot, y0 - 4f, 1f, Draw.col(pri, 0.6f));

        // head
        int c0 = partColor(0, t, pri);
        float a0 = partAlpha(0, t) * boot;
        Draw.disc(x0 + 22f, y0 + 8f, 6f, Draw.col(c0, 0.28f * a0));
        Draw.ring(x0 + 22f, y0 + 8f, 6.5f, 1.2f, Draw.col(c0, a0));

        // chest + arms
        int c1 = partColor(1, t, pri);
        float a1 = partAlpha(1, t) * boot;
        Draw.box(x0 + 11f, y0 + 17f, 22f, 26f, c1, 0.28f * a1, a1);
        Draw.box(x0 + 1f, y0 + 17f, 8f, 22f, c1, 0.22f * a1, a1);
        Draw.box(x0 + 35f, y0 + 17f, 8f, 22f, c1, 0.22f * a1, a1);

        // legs
        int c2 = partColor(2, t, pri);
        float a2 = partAlpha(2, t) * boot;
        Draw.box(x0 + 11f, y0 + 45f, 10f, 26f, c2, 0.28f * a2, a2);
        Draw.box(x0 + 23f, y0 + 45f, 10f, 26f, c2, 0.28f * a2, a2);

        // boots
        int c3 = partColor(3, t, pri);
        float a3 = partAlpha(3, t) * boot;
        Draw.box(x0 + 10f, y0 + 73f, 11f, 8f, c3, 0.28f * a3, a3);
        Draw.box(x0 + 23f, y0 + 73f, 11f, 8f, c3, 0.28f * a3, a3);

        // labels
        float lx = x0 + 50f;
        float[] ly = {y0 + 3f, y0 + 24f, y0 + 54f, y0 + 73f};
        for (int i = 0; i < 4; i++) {
            String s;
            if (!HudState.armorPresent[i]) s = HudState.PART[i] + " --";
            else if (i == 1 && HudState.hasThrusters && HudState.armorPct[i] >= 0f
                    && Minecraft.getInstance().player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                    .is(net.minecraft.world.item.Items.ELYTRA)) {
                s = "THRUST " + Math.round(HudState.armorPct[i] * 100f) + "%";
            } else s = HudState.PART[i] + " " + Math.round(HudState.armorPct[i] * 100f) + "%";
            Draw.text(s, lx, ly[i], Draw.col(partColor(i, t, pri), 0.95f * boot), 0.7f);
        }
    }

    // --------------------------------------------------------------- reactor

    private static void reactor(LocalPlayer p, float cx, float cy, float t, float boot, int pri) {
        float maxHp = Math.max(1f, p.getMaxHealth());
        float hpF = Mth.clamp(HudState.dispHp / maxHp, 0f, 1f);
        float foodF = Mth.clamp(p.getFoodData().getFoodLevel() / 20f, 0f, 1f);
        float armF = Mth.clamp(p.getArmorValue() / 20f, 0f, 1f);
        boolean low = hpF < 0.3f;
        int hpCol = low ? RED : pri;
        float pl = 0.65f + 0.35f * (float) Math.sin(t * (low ? 0.5f : 0.15f));

        for (int i = 0; i < 36; i++) {
            float a = (float) Math.toRadians(i * 10f + t * 1.2f);
            float r0 = 40f, r1 = (i % 3 == 0) ? 46f : 43f;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            Draw.line(cx + cs * r0, cy + sn * r0, cx + cs * r1, cy + sn * r1, 1f, Draw.col(pri, 0.45f * boot));
        }

        gauge(cx, cy, 28f, 32f, hpF * boot, hpCol);
        gauge(cx, cy, 22f, 25f, foodF * boot, AMBER);
        gauge(cx, cy, 16f, 19f, armF * boot, WHITE);

        Draw.disc(cx, cy, 9f, Draw.col(hpCol, 0.30f * pl * boot));
        Draw.ring(cx, cy, 10f, 2f, Draw.col(hpCol, 0.9f * boot));
        String hp = Integer.toString(Math.round(p.getHealth()));
        Draw.textCenter(hp, cx, cy - 4f, Draw.col(WHITE, boot), 1f);

        float lx = cx + 52f, ly = cy - 22f;
        Draw.text("HULL " + Math.round(p.getHealth()) + "/" + Math.round(maxHp), lx, ly, Draw.col(hpCol, boot), 0.8f);
        Draw.text("PWR  " + Math.round(foodF * 100f) + "%", lx, ly + 10f, Draw.col(AMBER, boot), 0.8f);
        Draw.text("ARM  " + p.getArmorValue(), lx, ly + 20f, Draw.col(WHITE, boot), 0.8f);

        float air = p.getAirSupply() / (float) Math.max(1, p.getMaxAirSupply());
        if (air < 0.999f) {
            float bw = 50f;
            Draw.text("O2", lx, ly + 30f, Draw.col(pri, boot), 0.8f);
            Draw.rect(lx + 14f, ly + 31f, lx + 14f + bw, ly + 36f, Draw.col(pri, 0.2f));
            Draw.rect(lx + 14f, ly + 31f, lx + 14f + bw * Mth.clamp(air, 0f, 1f), ly + 36f,
                    Draw.col(air < 0.3f ? RED : pri, 0.9f));
        }
        routing(lx, ly + 42f, boot, pri);
    }

    private static void routing(float lx, float ay, float boot, int pri) {
        Draw.text("ROUTE " + Power.preset.label, lx, ay, Draw.col(pri, boot), 0.65f);
        float[] sh = {Power.preset.sensors, Power.preset.optics, Power.preset.analysis};
        String[] nm = {"SEN", "OPT", "ANL"};
        for (int i = 0; i < 3; i++) {
            float bx = lx + i * 27f;
            Draw.rect(bx, ay + 9f, bx + 23f, ay + 12f, Draw.col(pri, 0.2f * boot));
            Draw.rect(bx, ay + 9f, bx + 23f * Mth.clamp(sh[i] / 0.6f, 0f, 1f), ay + 12f, Draw.col(pri, 0.95f * boot));
            Draw.text(nm[i], bx, ay + 13f, Draw.col(WHITE, 0.7f * boot), 0.5f);
        }
    }

    /** 270-degree gauge, opening at the bottom. */
    private static void gauge(float cx, float cy, float r0, float r1, float frac, int rgb) {
        Draw.arc(cx, cy, r0, r1, 135f, 270f, Draw.col(rgb, 0.18f));
        Draw.arc(cx, cy, r0, r1, 135f, 270f * Mth.clamp(frac, 0f, 1f), Draw.col(rgb, 0.9f));
    }

    // ----------------------------------------------------------------- radar

    private static void radar(LocalPlayer p, float pt, float cx, float cy, float t, float boot, int pri, int acc) {
        float R = 44f * boot;
        double range = HudState.radarRange();
        Draw.disc(cx, cy, R, Draw.col(0x000000, 0.28f));
        Draw.ring(cx, cy, R, 1.2f, Draw.col(pri, 0.8f));
        Draw.ring(cx, cy, R * 0.66f, 1f, Draw.col(pri, 0.28f));
        Draw.ring(cx, cy, R * 0.33f, 1f, Draw.col(pri, 0.28f));
        Draw.line(cx - R, cy, cx + R, cy, 1f, Draw.col(pri, 0.2f));
        Draw.line(cx, cy - R, cx, cy + R, 1f, Draw.col(pri, 0.2f));

        // sweeping arm with a fading trail
        float ang = (t * 4f) % 360f - 90f;
        for (int k = 0; k < 8; k++) {
            Draw.arc(cx, cy, 0f, R, ang - (k + 1) * 6f, 6f, Draw.col(pri, 0.20f * (1f - k / 8f)));
        }
        double ar = Math.toRadians(ang);
        Draw.line(cx, cy, cx + (float) Math.cos(ar) * R, cy + (float) Math.sin(ar) * R, 1.2f, Draw.col(pri, 0.9f));

        // wide-area scan ripple
        float since = HudState.age + pt - HudState.scanStart;
        boolean scanning = since >= 0f && since < 100f;
        if (scanning) {
            float pr = Mth.clamp(since / 40f, 0f, 1f);
            float fade = 1f - Mth.clamp((since - 40f) / 60f, 0f, 1f);
            Draw.ring(cx, cy, R * pr, 1.6f, Draw.col(acc, 0.8f * (1f - pr * 0.6f) * fade));
        }

        float yawR = (float) Math.toRadians(Mth.rotLerp(pt, p.yRotO, p.getYRot()));
        float fx = -(float) Math.sin(yawR), fz = (float) Math.cos(yawR);
        float rx = -(float) Math.cos(yawR), rz = -(float) Math.sin(yawR);
        Vec3 me = p.getPosition(pt);

        for (Contact c : HudState.contacts) {
            LivingEntity e = c.e();
            Vec3 ep = e.getPosition(pt);
            double dx = ep.x - me.x, dz = ep.z - me.z;
            double sx = (dx * rx + dz * rz) / range, sy = (dx * fx + dz * fz) / range;
            double len = Math.sqrt(sx * sx + sy * sy);
            if (len > 1.0) continue;
            float bx = cx + (float) sx * R, by = cy - (float) sy * R;
            int rgb = c.kind() == HudState.HOSTILE ? RED : c.kind() == HudState.PLAYER ? acc : pri;
            float a = c.kind() == HudState.NEUTRAL ? 0.55f : 1f;
            float s = c.kind() == HudState.HOSTILE ? 2.4f : 1.8f;
            Draw.rect(bx - s, by - s, bx + s, by + s, Draw.col(rgb, a * boot));
            if (c.kind() == HudState.HOSTILE) {
                Draw.ring(bx, by, s + 2f, 0.8f, Draw.col(rgb, 0.5f * pulse(t, 0.3f)));
            }
        }

        if (HudState.waypoint != null) {
            double dx = HudState.waypoint.x - me.x, dz = HudState.waypoint.z - me.z;
            double sx = (dx * rx + dz * rz) / range, sy = (dx * fx + dz * fz) / range;
            double len = Math.sqrt(sx * sx + sy * sy);
            if (len > 1.0) {
                sx /= len;
                sy /= len;
            }
            Draw.diamond(cx + (float) sx * R, cy - (float) sy * R, 3.5f, 1f, Draw.col(acc, 1f));
        }

        // player marker
        Draw.line(cx, cy - 4f, cx - 3f, cy + 3f, 1.2f, Draw.col(WHITE, 1f));
        Draw.line(cx, cy - 4f, cx + 3f, cy + 3f, 1.2f, Draw.col(WHITE, 1f));
        Draw.line(cx - 3f, cy + 3f, cx + 3f, cy + 3f, 1.2f, Draw.col(WHITE, 1f));
        Draw.textCenter("RNG " + (int) range + "M", cx, cy - R - 10f, Draw.col(pri, 0.85f * boot), 0.7f);
    }

    // ------------------------------------------------------------ info panel

    private static void infoPanel(LocalPlayer p, ClientLevel level, int w, float t, float boot, int pri) {
        float x = w - 12f, y = 36f, s = 0.8f;
        int c = Draw.col(pri, boot), dim = Draw.col(WHITE, 0.85f * boot);

        Draw.textRight("SUIT SYSTEMS ONLINE", x, y, c, s);
        Draw.line(x - 120f * boot, y + 10f, x, y + 10f, 1f, Draw.col(pri, 0.6f));
        y += 15f;

        Draw.textRight(String.format(Locale.ROOT, "POS %d  %d  %d", p.getBlockX(), p.getBlockY(), p.getBlockZ()), x, y, dim, s);
        y += 10f;
        String biome = level.getBiome(p.blockPosition()).unwrapKey()
                .map(k -> k.location().getPath().replace('_', ' ').toUpperCase(Locale.ROOT))
                .orElse("UNKNOWN");
        Draw.textRight(biome, x, y, dim, s);
        y += 10f;

        float spd = Math.max(HudState.speed, HudState.flightSpeed * (p.isFallFlying() ? 1f : 0f));
        Draw.textRight(String.format(Locale.ROOT, "SPD %.1f M/S", spd), x, y, dim, s);
        y += 10f;
        Draw.textRight(String.format(Locale.ROOT, "VS  %+.1f M/S", HudState.vSpeed), x, y, dim, s);
        y += 10f;
        String agl = HudState.groundDist < 0 ? "---" : String.format(Locale.ROOT, "%.0f", HudState.groundDist);
        Draw.textRight("ALT " + p.getBlockY() + "  AGL " + agl, x, y, dim, s);
        y += 10f;

        long dayTime = level.getDayTime() % 24000L;
        int hours = (int) ((dayTime / 1000L + 6L) % 24L);
        int mins = (int) ((dayTime % 1000L) * 60L / 1000L);
        Draw.textRight(String.format(Locale.ROOT, "TIME %02d:%02d   FPS %d", hours, mins, Math.round(fps)), x, y, dim, s);
        y += 10f;
        String ms = Voice.isSpeaking() ? "SPEAKING" : Mic.isRunning() ? "LISTENING" : "ONLINE";
        Draw.textRight("MODUS " + ms, x, y, Draw.col(Mic.isRunning() ? 0x39FF88 : pri, boot), s);
        y += 13f;

        // altitude trace
        float gw = 110f, gh = 20f, gx = x - gw;
        Draw.box(gx, y, gw, gh, pri, 0.10f * boot, 0.35f * boot);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float v : HudState.altHist) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float span = Math.max(8f, max - min);
        int n = HudState.altHist.length, idx = HudState.altIndex();
        float px = 0, py = 0;
        for (int i = 0; i < n; i++) {
            float v = HudState.altHist[(idx + 1 + i) % n];
            float fx = gx + 2f + (gw - 4f) * i / (n - 1f);
            float fy = y + gh - 2f - (gh - 4f) * Mth.clamp((v - min) / span, 0f, 1f);
            if (i > 0) Draw.line(px, py, fx, fy, 1f, Draw.col(pri, 0.9f * boot));
            px = fx;
            py = fy;
        }
        y += gh + 5f;

        boolean alert = HudState.threats > 0;
        float blink = alert ? (0.6f + 0.4f * (float) Math.sin(t * 0.4f)) : 1f;
        Draw.textRight("HOSTILES " + HudState.threats + "    ELIM " + HudState.kills, x, y,
                Draw.col(alert ? RED : pri, blink * boot), s);
    }

    // ------------------------------------------------------------ scan pulse

    /** Full-screen expanding ring (the wide-area scan). */
    private static void scanPulse(int w, int h, float pt, float t, int pri, int acc) {
        float since = HudState.age + pt - HudState.scanStart;
        if (since < 0f || since >= 100f) return;
        float pr = Mth.clamp(since / 40f, 0f, 1f);
        float e = 1f - (1f - pr) * (1f - pr);
        float fade = 1f - Mth.clamp((since - 40f) / 30f, 0f, 1f);
        float cx = w / 2f, cy = h / 2f;
        float r = e * (float) Math.hypot(cx, cy);
        for (int k = 0; k < 6; k++) {
            float rr = r - k * 7f;
            if (rr < 2f) continue;
            float al = (k == 0 ? 0.85f : 0.32f / k) * fade * (1f - pr * 0.5f);
            Draw.ring(cx, cy, rr, k == 0 ? 2.2f : 1f, Draw.col(acc, al));
        }
        if (r > 24f) {
            for (int i = 0; i < 90; i++) {
                float a = (float) Math.toRadians(i * 4f + t * 2f);
                float len = i % 5 == 0 ? 8f : 3.5f;
                float c0 = (float) Math.cos(a), s0 = (float) Math.sin(a);
                Draw.line(cx + c0 * r, cy + s0 * r, cx + c0 * (r - len), cy + s0 * (r - len), 1f,
                        Draw.col(pri, 0.7f * fade));
            }
        }
        Draw.textCenter(String.format(Locale.ROOT, "WIDE-AREA SCAN  %d%%", Math.round(pr * 100f)), cx, cy + 72f,
                Draw.col(acc, fade), 0.8f);
    }

    // --------------------------------------------------------------- reticle

    private static void reticle(float cx, float cy, float t, float boot, int pri) {
        if (HudState.zoomCur > 1.05f) {
            float r = 34f;
            Draw.ring(cx, cy, r, 1f, Draw.col(pri, 0.7f));
            for (int i = 0; i < 24; i++) {
                float a = (float) Math.toRadians(i * 15f);
                float l = i % 6 == 0 ? 7f : 3.5f;
                Draw.line(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r,
                        cx + (float) Math.cos(a) * (r - l), cy + (float) Math.sin(a) * (r - l), 1f, Draw.col(pri, 0.8f));
            }
            Draw.line(cx - 12f, cy, cx - 4f, cy, 1f, Draw.col(pri, 0.9f));
            Draw.line(cx + 4f, cy, cx + 12f, cy, 1f, Draw.col(pri, 0.9f));
            Draw.line(cx, cy - 12f, cx, cy - 4f, 1f, Draw.col(pri, 0.9f));
            Draw.line(cx, cy + 4f, cx, cy + 12f, 1f, Draw.col(pri, 0.9f));
            Draw.textCenter(String.format(Locale.ROOT, "OPTICAL ZOOM x%.1f", HudState.zoomCur), cx, cy + r + 6f,
                    Draw.col(pri, 1f), 0.8f);
            Draw.textCenter("SCROLL: ADJUST", cx, cy + r + 16f, Draw.col(WHITE, 0.6f), 0.65f);
        }

        if (!HudState.hasTarget) {
            for (int i = 0; i < 8; i++) {
                Draw.arc(cx, cy, 13f, 14f, i * 45f + t * 2f, 20f, Draw.col(pri, 0.45f * boot));
            }
            return;
        }

        int rgb = HudState.targetDead ? GRAY : (HudState.targetHostile ? RED : pri);
        float lock = Math.min(1f, HudState.lockTicks / 8f);
        float d = 22f - 8f * lock;
        Draw.brackets(cx, cy, d, 6f, 1.2f, Draw.col(rgb, 0.95f));
        if (!HudState.targetDead) FilmStyle.lockArrows(cx, cy, lock, t, rgb);
        if (lock >= 1f && !HudState.targetDead) {
            Draw.ring(cx, cy, 2.5f, 1f, Draw.col(rgb, 0.7f * pulse(t, 0.5f) + 0.3f));
        }

    }

    // ---------------------------------------------------- collision readout

    private static void collisionReadout(float cx, float cy, float t, LocalPlayer p) {
        double ttc = HudState.collisionTtc;
        if (ttc < 0) return;
        boolean fall = HudState.collisionIsFall;
        if (!p.isFallFlying() && (!fall || HudState.predictedFallDmg < 3f)) return;
        float a = 0.55f + 0.45f * pulse(t, ttc < 1.0 ? 1.2f : 0.7f);
        int c = Draw.col(RED, a);
        Draw.brackets(cx, cy, 52f, 14f, 1.6f, c);
        Draw.textCenter(String.format(Locale.ROOT, "IMPACT T-%.1f", ttc), cx, cy + 58f, c, 1.2f);
        if (!fall) Draw.textCenter("PULL UP", cx, cy - 66f, c, 1.4f);
    }

    // --------------------------------------------------- world-space markers

    private static void markers(Cam cam, LocalPlayer p, float pt, float t, int w, int h, int pri, int acc) {
        float since = HudState.age + pt - HudState.scanStart;
        boolean scanning = since >= 0f && since < 100f;
        float scanFade = 1f - Mth.clamp((since - 60f) / 40f, 0f, 1f);
        double range = HudState.radarRange();
        int drawn = 0;

        for (Contact c : HudState.contacts) {
            boolean hostile = c.kind() == HudState.HOSTILE;
            if (!scanning && (!hostile || c.dist() > 32)) continue;
            if (scanning) {
                double reach = range * Mth.clamp(since / 40f, 0f, 1f);
                if (c.dist() > reach) continue;
            }
            LivingEntity e = c.e();
            if (e.getId() == HudState.targetId && HudState.hasTarget) continue;
            if (drawn >= 24) break;
            double yMul = scanning ? 0.5 : 1.0;
            Vec3 ep = e.getPosition(pt).add(0, e.getBbHeight() * yMul + (scanning ? 0.0 : 0.35), 0);
            if (!cam.project(ep, XY)) continue;
            if (!cam.onScreen(XY, 12f)) continue;
            drawn++;
            FilmStyle.groundRing(cam, e, pt, t, hostile ? RED : c.kind() == HudState.PLAYER ? acc : pri, hostile ? 0.8f : 0.55f);
            float x = XY[0], y = XY[1];
            int rgb = hostile ? RED : c.kind() == HudState.PLAYER ? acc : pri;
            float a = (scanning ? scanFade : 1f) * (hostile ? 0.9f : 0.75f);
            String name = e.getName().getString().toUpperCase(Locale.ROOT);
            if (hostile && !scanning) {
                Draw.triDown(x, y, 3.5f, 1.2f, Draw.col(rgb, a));
                Draw.textCenter(String.format(Locale.ROOT, "%s %.0fHP  %.0fM", name, e.getHealth(), c.dist()),
                        x, y - 15f, Draw.col(rgb, a), 0.65f);
            } else {
                float reveal = since - (float) (c.dist() / range * 40.0);
                Draw.dashedRing(x, y, 10f, 1.2f, t * 2f, 8, Draw.col(rgb, a));
                if (reveal >= 0f && reveal < 14f) {
                    Draw.ring(x, y, 10f + reveal * 1.6f, 1.2f, Draw.col(rgb, a * (1f - reveal / 14f)));
                }
                Draw.textCenter(String.format(Locale.ROOT, "%s  %.0fM", name, c.dist()), x, y - 17f,
                        Draw.col(rgb, a), 0.65f);
                if (e.getMaxHealth() > 0f) {
                    float bw = 24f, f = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0f, 1f);
                    Draw.rect(x - bw / 2f, y + 14f, x + bw / 2f, y + 16f, Draw.col(rgb, 0.25f * a));
                    Draw.rect(x - bw / 2f, y + 14f, x - bw / 2f + bw * f, y + 16f, Draw.col(rgb, a));
                }
            }
        }

        if (HudState.waypoint != null) {
            Vec3 wp = HudState.waypoint.add(0, 1.0, 0);
            if (cam.project(wp, XY) && cam.onScreen(XY, 14f)) {
                float x = XY[0], y = XY[1];
                Draw.diamond(x, y, 6f, 1.4f, Draw.col(acc, 1f));
                Draw.line(x, y + 6f, x, y + 18f, 1f, Draw.col(acc, 0.7f));
                double dist = p.getPosition(pt).distanceTo(HudState.waypoint);
                Draw.textCenter(String.format(Locale.ROOT, "WP %.0fM", dist), x, y - 16f, Draw.col(acc, 1f), 0.8f);
            }
        }
    }

    // ---------------------------------------------------------- horizon / FPM

    private static void horizon(Cam cam, LocalPlayer p, float pt, int w, int h, int pri, int acc) {
        float cx = w / 2f, cy = h / 2f;
        float pitch = Mth.lerp(pt, p.xRotO, p.getXRot()); // + = looking down
        double elev = -pitch;                              // elevation angle of the view center
        double tanV = cam.tanV;

        for (int a = -60; a <= 60; a += 10) {
            double delta = Math.toRadians(a - elev);
            if (Math.abs(delta) > 1.2) continue;
            float y = cy - (float) (Math.tan(delta) / tanV * (h / 2.0));
            if (y < 40f || y > h - 40f) continue;
            boolean horizon = a == 0;
            float inner = 26f, outer = horizon ? 120f : 70f;
            int c = Draw.col(horizon ? acc : pri, horizon ? 0.85f : 0.55f);
            Draw.line(cx - outer, y, cx - inner, y, horizon ? 1.4f : 1f, c);
            Draw.line(cx + inner, y, cx + outer, y, horizon ? 1.4f : 1f, c);
            if (!horizon) {
                float tick = a > 0 ? 5f : -5f;
                Draw.line(cx - outer, y, cx - outer, y + tick, 1f, c);
                Draw.line(cx + outer, y, cx + outer, y + tick, 1f, c);
                String s = Integer.toString(Math.abs(a));
                Draw.text(s, cx - outer - 14f, y - 4f, c, 0.65f);
                Draw.text(s, cx + outer + 5f, y - 4f, c, 0.65f);
            }
        }

        // flight-path marker
        Vec3 vel = p.getDeltaMovement();
        if (vel.length() * 20.0 > 3.0) {
            Vec3 target = cam.pos.add(vel.normalize().scale(40.0));
            if (cam.project(target, XY)) {
                float x = XY[0], y = XY[1];
                int c = Draw.col(acc, 0.95f);
                Draw.ring(x, y, 5f, 1.2f, c);
                Draw.line(x - 12f, y, x - 5f, y, 1.2f, c);
                Draw.line(x + 5f, y, x + 12f, y, 1.2f, c);
                Draw.line(x, y - 5f, x, y - 10f, 1.2f, c);
            }
        }
    }

    // ---------------------------------------------------------- alert banners

    private static void alertBanners(int w, float t) {
        List<Alert> list = Alerts.sorted();
        float cx = w / 2f, y = 50f;
        int shown = 0;
        for (Alert a : list) {
            if (shown >= 3) break;
            shown++;
            float age = HudState.age + 0f - a.start;
            float left = a.end - HudState.age;
            float in = Mth.clamp(age / 4f, 0f, 1f);
            float out = Mth.clamp(left / 8f, 0f, 1f);
            float vis = Math.min(in, out);
            if (vis <= 0f) continue;

            int rgb = a.level == Alerts.Level.CRIT ? RED : a.level == Alerts.Level.WARN ? AMBER : HudConfig.primary();
            float flick = a.level == Alerts.Level.CRIT ? 0.65f + 0.35f * pulse(t, 0.8f) : 1f;
            float bw = 230f * vis, bh = a.sub.isEmpty() ? 24f : 36f;
            float x0 = cx - bw / 2f, cut = 6f;
            int fr = Draw.col(rgb, flick * vis);
            Draw.rect(x0 + cut, y, x0 + bw - cut, y + bh, Draw.col(0x02101A, 0.7f * vis));
            Draw.rect(x0, y + cut, x0 + cut, y + bh - cut, Draw.col(0x02101A, 0.7f * vis));
            Draw.rect(x0 + bw - cut, y + cut, x0 + bw, y + bh - cut, Draw.col(0x02101A, 0.7f * vis));
            // angled-corner frame like the film's warning plates
            Draw.line(x0 + cut, y, x0 + bw - cut, y, 1.4f, fr);
            Draw.line(x0 + cut, y + bh, x0 + bw - cut, y + bh, 1.4f, fr);
            Draw.line(x0, y + cut, x0, y + bh - cut, 1.4f, fr);
            Draw.line(x0 + bw, y + cut, x0 + bw, y + bh - cut, 1.4f, fr);
            Draw.line(x0, y + cut, x0 + cut, y, 1.4f, fr);
            Draw.line(x0 + bw - cut, y, x0 + bw, y + cut, 1.4f, fr);
            Draw.line(x0, y + bh - cut, x0 + cut, y + bh, 1.4f, fr);
            Draw.line(x0 + bw, y + bh - cut, x0 + bw - cut, y + bh, 1.4f, fr);
            if (a.level != Alerts.Level.INFO) {
                // small "DANGER ///" tag riding on the top edge, plus a chevron icon
                String tag = a.level == Alerts.Level.CRIT ? "DANGER" : "CAUTION";
                Draw.rect(x0 + 10f, y - 5f, x0 + 10f + Draw.width(tag, 0.5f) + 22f, y + 4f, Draw.col(rgb, 0.9f * vis));
                Draw.text(tag, x0 + 13f, y - 3f, Draw.col(0x02101A, vis), 0.5f);
                for (int k = 0; k < 3; k++) {
                    float sx = x0 + 16f + Draw.width(tag, 0.5f) + k * 4f;
                    Draw.line(sx, y + 3.5f, sx + 3f, y - 4.5f, 1.2f, Draw.col(0x02101A, vis));
                }
                float ix = x0 + bw - 18f, iy = y + bh / 2f;
                for (int k = 0; k < 2; k++) {
                    Draw.line(ix - 6f, iy - 3f + k * 5f, ix, iy + 2f + k * 5f, 1.8f, fr);
                    Draw.line(ix + 6f, iy - 3f + k * 5f, ix, iy + 2f + k * 5f, 1.8f, fr);
                }
            }
            Draw.textCenter(a.title, cx, y + 6f, Draw.col(a.level == Alerts.Level.INFO ? rgb : 0xFFFFFF, flick * vis), 1.2f);
            if (!a.sub.isEmpty()) {
                Draw.textCenter(a.sub, cx, y + 22f, Draw.col(rgb, 0.9f * vis), 0.6f);
            }
            y += bh + 8f;
        }
    }
}
