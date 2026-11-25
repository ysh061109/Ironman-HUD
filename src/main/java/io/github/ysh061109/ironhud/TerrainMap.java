package io.github.ysh061109.ironhud;

import io.github.ysh061109.ironhud.HudState.Contact;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Rotating 3D wireframe of the surrounding terrain, with contacts and the waypoint standing on it. */
public final class TerrainMap {
    private static final float[][] PX = new float[HudState.TN][HudState.TN];
    private static final float[][] PY = new float[HudState.TN][HudState.TN];
    private static final float[][] DEPTH = new float[HudState.TN][HudState.TN];

    private TerrainMap() {}

    public static void render(LocalPlayer p, float pt, float x, float y, float w, float h, float t,
                              float boot, int pri, int acc) {
        Draw.box(x, y, w, h, pri, 0.10f * boot, 0.45f * boot);
        Draw.text("TERRAIN MAP", x + 4f, y + 3f, Draw.col(pri, boot), 0.65f);
        int n = HudState.TN, half = (n - 1) / 2;
        float cx = x + w / 2f, cy = y + h * 0.66f;
        float scale = (w - 10f) / 34f;

        Vec3 me = p.getPosition(pt);
        float yaw = (float) Math.toRadians(Mth.rotLerp(pt, p.yRotO, p.getYRot()));
        float fx = -(float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float rx = -(float) Math.cos(yaw), rz = -(float) Math.sin(yaw);
        float x0 = x + 1f, x1 = x + w - 1f, y0 = y + 10f, y1 = y + h - 1f;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float ty = HudState.terrain[i][j];
                if (Float.isNaN(ty)) {
                    PX[i][j] = Float.NaN;
                    continue;
                }
                double wx = HudState.terrainCx + (i - half) * HudState.TSTEP;
                double wz = HudState.terrainCz + (j - half) * HudState.TSTEP;
                float dx = (float) (wx - me.x), dz = (float) (wz - me.z);
                float r = dx * rx + dz * rz, f = dx * fx + dz * fz;
                float dy = Mth.clamp((float) (ty - me.y), -12f, 16f);
                PX[i][j] = cx + r * scale;
                PY[i][j] = cy - f * scale * 0.42f - dy * 1.1f;
                DEPTH[i][j] = f;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (Float.isNaN(PX[i][j])) continue;
                if (i + 1 < n) seg(i, j, i + 1, j, x0, y0, x1, y1, boot, pri, acc);
                if (j + 1 < n) seg(i, j, i, j + 1, x0, y0, x1, y1, boot, pri, acc);
            }
        }

        // contacts
        double reach = (half * HudState.TSTEP) - 2;
        for (Contact c : HudState.contacts) {
            Vec3 ep = c.e().getPosition(pt);
            float dx = (float) (ep.x - me.x), dz = (float) (ep.z - me.z);
            float r = dx * rx + dz * rz, f = dx * fx + dz * fz;
            if (Math.abs(r) > reach || Math.abs(f) > reach) continue;
            float dy = Mth.clamp((float) (ep.y - me.y), -12f, 16f);
            float px = cx + r * scale, py = cy - f * scale * 0.42f - dy * 1.1f;
            if (px < x0 || px > x1 || py < y0 + 6f || py > y1) continue;
            boolean hostile = c.kind() == HudState.HOSTILE;
            int rgb = hostile ? 0xFF3B30 : c.kind() == HudState.PLAYER ? acc : pri;
            float a = (hostile ? 1f : 0.55f) * boot;
            Draw.line(px, py, px, py - 6f, 1f, Draw.col(rgb, a));
            Draw.disc(px, py - 7f, hostile ? 1.8f : 1.3f, Draw.col(rgb, a));
        }

        // waypoint beacon
        if (HudState.waypoint != null) {
            float dx = (float) (HudState.waypoint.x - me.x), dz = (float) (HudState.waypoint.z - me.z);
            float r = dx * rx + dz * rz, f = dx * fx + dz * fz;
            if (Math.abs(r) <= reach && Math.abs(f) <= reach) {
                float px = cx + r * scale, py = cy - f * scale * 0.42f;
                if (px > x0 && px < x1 && py > y0 + 8f && py < y1) {
                    Draw.line(px, py, px, py - 10f, 1f, Draw.col(acc, boot));
                    Draw.diamond(px, py - 12f, 2.5f, 1f, Draw.col(acc, boot));
                }
            }
        }

        // player marker
        Draw.line(cx, cy - 5f, cx - 3f, cy + 2f, 1.2f, Draw.col(0xDFF9FF, boot));
        Draw.line(cx, cy - 5f, cx + 3f, cy + 2f, 1.2f, Draw.col(0xDFF9FF, boot));
        Draw.line(cx - 3f, cy + 2f, cx + 3f, cy + 2f, 1.2f, Draw.col(0xDFF9FF, boot));
    }

    private static void seg(int i0, int j0, int i1, int j1, float x0, float y0, float x1, float y1,
                            float boot, int pri, int acc) {
        if (Float.isNaN(PX[i1][j1])) return;
        float ax = PX[i0][j0], ay = PY[i0][j0], bx = PX[i1][j1], by = PY[i1][j1];
        if (ax < x0 || ax > x1 || bx < x0 || bx > x1 || ay < y0 || ay > y1 || by < y0 || by > y1) return;
        float depth = (DEPTH[i0][j0] + DEPTH[i1][j1]) * 0.5f;
        float a = Mth.clamp(0.62f - depth / 60f, 0.15f, 0.7f) * boot;
        boolean high = (HudState.terrain[i0][j0] + HudState.terrain[i1][j1]) * 0.5f
                > Minecraft_playerY() + 3f;
        Draw.line(ax, ay, bx, by, 1f, Draw.col(high ? acc : pri, a));
    }

    private static float Minecraft_playerY() {
        return (float) net.minecraft.client.Minecraft.getInstance().player.getY();
    }
}
