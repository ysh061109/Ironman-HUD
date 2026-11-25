package io.github.ysh061109.ironhud;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Rotating wireframe hologram of the armored suit, built from Minecraft's player-model proportions
 * (32 px tall, 16 px wide) with film-suit details layered on top: faceplate, shoulder pauldrons,
 * chest plate with the arc reactor, gauntlets with palm repulsors, boot repulsors and back thrusters.
 * Every armor part is tinted by its real durability, flickers when damaged and materializes from the
 * feet up while the HUD boots.
 */
public final class SuitHologram {
    private SuitHologram() {}

    // cx, cy, cz, sx, sy, sz, part(0 head,1 chest/arms,2 legs,3 boots), slices   (y up, feet at 0, +z front)
    private static final float[][] BOXES = {
            {0, 28, 0, 8.6f, 8.6f, 8.6f, 0, 2},
            {0, 27.4f, 4.8f, 6.2f, 6.6f, 1.4f, 0, 1},
            {0, 24.2f, 4.0f, 5f, 1.6f, 2.4f, 0, 0},
            {-4.8f, 28, 0, 1.2f, 3.6f, 3.2f, 0, 0},
            {4.8f, 28, 0, 1.2f, 3.6f, 3.2f, 0, 0},
            {0, 24.1f, 0, 3.2f, 1.3f, 3.2f, 1, 0},
            {0, 18, 0, 8.4f, 12f, 4.4f, 1, 3},
            {0, 20.6f, 2.7f, 8f, 6.6f, 1.2f, 1, 1},
            {0, 15.2f, 2.5f, 6f, 1.3f, 0.9f, 1, 0},
            {0, 13.4f, 2.5f, 5f, 1.1f, 0.9f, 1, 0},
            {0, 12.3f, 0, 8.8f, 1.6f, 4.9f, 2, 0},
            {-6.5f, 23, 0, 5.8f, 3.4f, 5.8f, 1, 1},
            {6.5f, 23, 0, 5.8f, 3.4f, 5.8f, 1, 1},
            {-6.3f, 19, 0, 4.4f, 6f, 4.4f, 1, 1},
            {6.3f, 19, 0, 4.4f, 6f, 4.4f, 1, 1},
            {-6.3f, 15.6f, 0, 4.8f, 1.2f, 4.8f, 1, 0},
            {6.3f, 15.6f, 0, 4.8f, 1.2f, 4.8f, 1, 0},
            {-6.3f, 12.6f, 0, 5.1f, 6f, 5.1f, 1, 1},
            {6.3f, 12.6f, 0, 5.1f, 6f, 5.1f, 1, 1},
            {-6.3f, 9.2f, 0, 3f, 1.6f, 3f, 1, 0},
            {6.3f, 9.2f, 0, 3f, 1.6f, 3f, 1, 0},
            {-2.5f, 19, -3.3f, 2.4f, 5f, 2f, 1, 0},
            {2.5f, 19, -3.3f, 2.4f, 5f, 2f, 1, 0},
            {-2.3f, 9.8f, 0, 4.6f, 4.8f, 4.8f, 2, 1},
            {2.3f, 9.8f, 0, 4.6f, 4.8f, 4.8f, 2, 1},
            {-2.3f, 7.4f, 1.5f, 3.4f, 1.4f, 2.2f, 2, 0},
            {2.3f, 7.4f, 1.5f, 3.4f, 1.4f, 2.2f, 2, 0},
            {-2.3f, 5.6f, 0, 4.4f, 3.8f, 4.6f, 2, 1},
            {2.3f, 5.6f, 0, 4.4f, 3.8f, 4.6f, 2, 1},
            {-2.3f, 2.2f, 0.9f, 4.8f, 4.4f, 6.4f, 3, 1},
            {2.3f, 2.2f, 0.9f, 4.8f, 4.4f, 6.4f, 3, 1},
    };

    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static final String[] NAME = {"HELMET", "CHEST", "LEGS", "BOOTS"};

    // transform state
    private static float cs, sn, cp, sp, ox, oy, sc, reveal;
    private static final float[] P = new float[3];

    /** world(model) -> screen; P = {x, y, depth}. */
    private static void tf(float x, float y, float z) {
        float xr = x * cs + z * sn;
        float zr = -x * sn + z * cs;
        float yy = y - 16f;
        float y2 = yy * cp - zr * sp;
        float d = zr * cp + yy * sp;
        P[0] = ox + xr * sc;
        P[1] = oy - y2 * sc;
        P[2] = d;
    }

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** @param x0 left edge, yTop top of the figure, H figure height in GUI units. */
    public static void render(Player p, float x0, float yTop, float H, float t, float boot, int pri) {
        float sweepY = ((t * 0.55f) % 52f) - 10f;      // scanning plane rides up the suit
        reveal = boot * 40f;                            // materialize from the feet
        sc = H / 36f;
        ox = x0 + 34f;
        oy = yTop + H * 0.5f + 1f;
        float yaw = (float) Math.toRadians(t * 0.9f + 14f * Math.sin(t * 0.03f));
        cs = (float) Math.cos(yaw);
        sn = (float) Math.sin(yaw);
        float pitch = (float) Math.toRadians(-11f);
        cp = (float) Math.cos(pitch);
        sp = (float) Math.sin(pitch);
        boolean lod = Draw.lod;
        boolean flying = p.isFallFlying();

        pad(t, boot, pri);

        int edgeNo = 0;
        for (float[] b : BOXES) {
            int part = (int) b[6];
            int base = HudRenderer.partColor(part, t, pri);
            float pa = HudRenderer.partAlpha(part, t) * boot;
            boolean dmg = HudState.armorPresent[part] && HudState.armorPct[part] < 0.3f;
            int slices = lod ? 0 : (int) b[7];
            float hx = b[3] / 2f, hy = b[4] / 2f, hz = b[5] / 2f;
            float[][] c = new float[8][3];
            for (int i = 0; i < 8; i++) {
                float x = b[0] + ((i & 1) == 0 ? -hx : hx);
                float y = b[1] + ((i & 2) == 0 ? -hy : hy);
                float z = b[2] + ((i & 4) == 0 ? -hz : hz);
                tf(x, y, z);
                c[i] = new float[]{P[0], P[1], P[2], y};
            }
            for (int[] e : EDGES) {
                edgeNo++;
                float[] a = c[e[0]], d = c[e[1]];
                edge(a, d, base, pa, dmg, edgeNo, t, sweepY, 0.5f);
            }
            for (int k = 1; k <= slices; k++) {
                float y = b[1] - hy + b[4] * k / (slices + 1f);
                float[][] r = new float[4][];
                int[][] xz = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (int i = 0; i < 4; i++) {
                    tf(b[0] + xz[i][0] * hx, y, b[2] + xz[i][1] * hz);
                    r[i] = new float[]{P[0], P[1], P[2], y};
                }
                for (int i = 0; i < 4; i++) {
                    edgeNo++;
                    edge(r[i], r[(i + 1) & 3], base, pa, dmg, edgeNo, t, sweepY, 0.22f);
                }
            }
        }

        // ---- faceplate eye slits
        int eye = mix(HudRenderer.partColor(0, t, pri), 0xFFFFFF, 0.8f);
        float ea = HudState.armorPresent[0] ? 0.95f * boot : 0.25f;
        if (reveal > 26f) {
            for (int s = -1; s <= 1; s += 2) {
                tf(s * 0.7f, 28.2f, 5.55f); float ax = P[0], ay = P[1], ad = P[2];
                tf(s * 2.6f, 28.7f, 5.55f);
                if (ad > -2f) Draw.line(ax, ay, P[0], P[1], 1.4f, Draw.col(eye, ea));
            }
        }

        // ---- arc reactor + repulsors
        float pl = 0.7f + 0.3f * (float) Math.sin(t * 0.25f);
        int rc = mix(HudRenderer.partColor(1, t, pri), 0xFFFFFF, 0.65f);
        if (reveal > 18f) {
            ring3(0, 20.4f, 3.4f, 1.9f, 0, rc, 0.9f * pl * boot, true);
            ring3(0, 20.4f, 3.4f, 1.0f, 0, rc, 0.8f * boot, true);
            tf(0, 20.4f, 3.4f);
            if (P[2] > -1f) Draw.disc(P[0], P[1], Math.max(0.9f, 1.5f * sc * 0.4f), Draw.col(0xFFFFFF, 0.8f * pl * boot));
        }
        if (reveal > 12f) {
            for (int s = -1; s <= 1; s += 2) {
                float flick = flying ? 0.95f : 0.45f + 0.25f * (float) Math.sin(t * 0.3f + s);
                ring3(s * 6.3f, 8.3f, 0, 1.4f, 1, mix(pri, 0xFFFFFF, 0.5f), flick * boot, false);
                ring3(s * 2.3f, 0.05f, 0.9f, 2f, 1, mix(HudRenderer.partColor(3, t, pri), 0xFFFFFF, 0.4f),
                        (flying ? 0.95f : 0.5f) * boot, false);
            }
        }
        if (flying && !lod) {
            // thruster exhaust streaks
            for (int s = -1; s <= 1; s += 2) {
                for (int k = 0; k < 4; k++) {
                    float f = ((t * 0.12f + k * 0.25f) % 1f);
                    tf(s * 2.3f, -f * 7f, 0.9f);
                    float px = P[0], py = P[1];
                    tf(s * 2.3f, -f * 7f - 1.5f, 0.9f);
                    Draw.line(px, py, P[0], P[1], 1f, Draw.col(0xFFFFFF, (1f - f) * 0.7f * boot));
                }
            }
        }

        // ---- sweep ring
        if (sweepY > 0f && sweepY < 33f && sweepY < reveal) {
            ring3(0, sweepY, 0, 10.5f, 1, pri, 0.55f * boot, false);
        }

        labels(x0, yTop, H, t, boot, pri);
    }

    private static void edge(float[] a, float[] b, int col, float pa, boolean dmg, int no, float t,
                             float sweepY, float base) {
        float ymax = Math.max(a[3], b[3]), ymin = Math.min(a[3], b[3]);
        if (ymin > reveal) return;
        if (dmg && ((no * 31 + (int) (t / 3f)) % 6 == 0)) return;
        float depth = (a[2] + b[2]) * 0.5f;
        float al = Mth.clamp(base + 0.5f * (depth + 8f) / 16f * base * 1.6f, 0.08f, 0.9f) * pa;
        float dy = Math.abs((ymin + ymax) * 0.5f - sweepY);
        float boost = Math.max(0f, 1f - dy / 3.2f);
        int c = boost > 0f ? mix(col, 0xFFFFFF, boost * 0.8f) : col;
        al = Math.min(1f, al + boost * 0.55f);
        if (ymax > reveal - 1.2f && ymax < reveal + 3f && reveal < 40f) { c = 0xFFFFFF; al = 0.95f; }
        Draw.line(a[0], a[1], b[0], b[1], 1f, Draw.col(c, al));
    }

    /** Circle in the XY (plane 0) or XZ (plane 1) plane, projected. */
    private static void ring3(float cx, float cy, float cz, float r, int plane, int rgb, float alpha, boolean frontOnly) {
        int n = Draw.lod ? 10 : 18;
        float px = 0, py = 0;
        for (int i = 0; i <= n; i++) {
            float a = (float) (i * 2 * Math.PI / n);
            float u = (float) Math.cos(a) * r, v = (float) Math.sin(a) * r;
            if (plane == 0) tf(cx + u, cy + v, cz); else tf(cx + u, cy, cz + v);
            if (i > 0 && (!frontOnly || P[2] > -1f)) Draw.line(px, py, P[0], P[1], 1f, Draw.col(rgb, alpha));
            px = P[0]; py = P[1];
        }
    }

    private static void pad(float t, float boot, int pri) {
        ring3(0, -1.2f, 0, 15f, 1, pri, 0.55f * boot, false);
        ring3(0, -1.2f, 0, 10.5f, 1, pri, 0.35f * boot, false);
        int n = 24;
        for (int i = 0; i < n; i++) {
            float a = (float) Math.toRadians(i * 360f / n - t * 2.4f);
            float r0 = 15f, r1 = i % 3 == 0 ? 17.5f : 16.2f;
            tf((float) Math.cos(a) * r0, -1.2f, (float) Math.sin(a) * r0);
            float x0 = P[0], y0 = P[1];
            tf((float) Math.cos(a) * r1, -1.2f, (float) Math.sin(a) * r1);
            Draw.line(x0, y0, P[0], P[1], 1f, Draw.col(pri, 0.5f * boot));
        }
        if (!Draw.lod) {
            // rising projector beams
            for (int i = 0; i < 8; i++) {
                float a = (float) Math.toRadians(i * 45f + t * 0.8f);
                float f = ((t * 0.03f + i * 0.125f) % 1f);
                tf((float) Math.cos(a) * 13f, -1.2f + f * 9f, (float) Math.sin(a) * 13f);
                float px = P[0], py = P[1];
                tf((float) Math.cos(a) * 13f, -1.2f + f * 9f + 3f, (float) Math.sin(a) * 13f);
                Draw.line(px, py, P[0], P[1], 1f, Draw.col(pri, (1f - f) * 0.4f * boot));
            }
        }
    }

    private static void labels(float x0, float yTop, float H, float t, float boot, int pri) {
        float lx = x0 + 74f;
        float[][] anchor = {{0, 29f, 0}, {0, 20.4f, 3.4f}, {-2.3f, 9.8f, 0}, {2.3f, 2.2f, 0.9f}};
        float[] fy = {0.06f, 0.30f, 0.60f, 0.86f};
        for (int i = 0; i < 4; i++) {
            int col = HudRenderer.partColor(i, t, pri);
            float ly = yTop + H * fy[i];
            String s;
            if (!HudState.armorPresent[i]) s = NAME[i] + " --";
            else if (i == 1 && HudState.hasThrusters && HudState.armorPct[i] >= 0f
                    && Minecraft.getInstance().player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                    .is(net.minecraft.world.item.Items.ELYTRA)) {
                s = "THRUST " + Math.round(HudState.armorPct[i] * 100f) + "%";
            } else s = NAME[i] + " " + Math.round(HudState.armorPct[i] * 100f) + "%";
            float a = 0.95f * boot;
            Draw.text(s, lx, ly, Draw.col(col, a), 0.7f);
            float pct = HudState.armorPresent[i] ? Mth.clamp(HudState.armorPct[i], 0f, 1f) : 0f;
            Draw.rect(lx, ly + 9f, lx + 40f, ly + 10f, Draw.col(col, 0.2f * boot));
            Draw.rect(lx, ly + 9f, lx + 40f * pct, ly + 10f, Draw.col(col, 0.9f * boot));
            // leader line from the part to its label
            tf(anchor[i][0], anchor[i][1], anchor[i][2]);
            float ax = P[0], ay = P[1];
            float ex = lx - 4f, ey = ly + 3.5f;
            Draw.line(ex, ey, ex - 6f, ey, 1f, Draw.col(col, 0.5f * boot));
            Draw.line(ex - 6f, ey, ax, ay, 1f, Draw.col(col, 0.32f * boot));
            Draw.disc(ax, ay, 1.1f, Draw.col(col, 0.9f * boot));
        }
    }
}
