package io.github.ysh061109.ironhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * World-anchored terrain scan. When a scan starts, the surface around the player is sampled once into
 * a snapshot grid. A wave then travels outward at a constant speed: behind its front the real terrain
 * is traced with a glowing wireframe draped on the ground (bright at the front, settling to a faint
 * persistent mesh, then fading), the front itself is drawn as a ring that follows the relief, and new
 * relief columns pulse up as the wave passes.
 */
public final class ScanWave {
    private ScanWave() {}

    public static final int N = 25, STEP = 2;          // 48 x 48 blocks
    public static final float SPEED = 0.75f;           // blocks per tick
    private static final float[][] H = new float[N][N];
    private static final float[][] SX = new float[N][N], SY = new float[N][N];
    private static final boolean[][] OK = new boolean[N][N];
    private static double cx, cz, baseY;
    private static boolean valid = false;
    private static final float[] XY = new float[2];

    /** Radius of the wave front in blocks, {@code since} ticks after the scan started. */
    public static float radius(float since) {
        return Math.min(HudState.radarRange() + 4f, Math.max(0f, since) * SPEED);
    }

    public static void capture(LocalPlayer p, ClientLevel level) {
        int half = (N - 1) / 2;
        int gx = (Mth.floor(p.getX()) / STEP) * STEP, gz = (Mth.floor(p.getZ()) / STEP) * STEP;
        cx = p.getX();
        cz = p.getZ();
        baseY = p.getY();
        float[][] raw = new float[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                int x = gx + (i - half) * STEP, z = gz + (j - half) * STEP;
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    raw[i][j] = Float.NaN;
                } else {
                    raw[i][j] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                }
            }
        }
        // light smoothing keeps the mesh calm over single-block noise but keeps cliffs readable
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                OK[i][j] = !Float.isNaN(raw[i][j]);
                if (!OK[i][j]) continue;
                float s = raw[i][j] * 0.5f, w = 0.5f;
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        if ((di | dj) == 0) continue;
                        int a = i + di, b = j + dj;
                        if (a < 0 || b < 0 || a >= N || b >= N || Float.isNaN(raw[a][b])) continue;
                        float d = Math.abs(raw[a][b] - raw[i][j]);
                        float k = d > 3f ? 0f : 0.0625f; // do not blur across cliffs
                        s += raw[a][b] * k;
                        w += k;
                    }
                }
                H[i][j] = s / w;
            }
        }
        gridX = gx;
        gridZ = gz;
        valid = true;
    }

    private static int gridX, gridZ;

    private static double wx(int i) { return gridX + (i - (N - 1) / 2) * STEP + 0.5; }
    private static double wz(int j) { return gridZ + (j - (N - 1) / 2) * STEP + 0.5; }

    private static float groundAt(double x, double z) {
        int half = (N - 1) / 2;
        int i = Mth.clamp((int) Math.round((x - 0.5 - gridX) / STEP) + half, 0, N - 1);
        int j = Mth.clamp((int) Math.round((z - 0.5 - gridZ) / STEP) + half, 0, N - 1);
        return OK[i][j] ? H[i][j] : (float) baseY;
    }

    public static void render(Cam cam, float pt, float t, int pri, int acc) {
        if (!valid) return;
        float since = HudState.age + pt - HudState.scanStart;
        if (since < 0f || since > 170f) return;
        float R = radius(since);
        float life = 1f - Mth.clamp((since - 110f) / 55f, 0f, 1f);
        if (life <= 0f) return;
        boolean lod = Draw.lod;
        float margin = Math.max(cam.w, cam.h) * 2.5f;
        float far = Math.max(18f, HudState.radarRange() * 0.55f);

        // project all nodes once
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                OK_P[i][j] = false;
                if (!OK[i][j]) continue;
                double dx = wx(i) - cx, dz = wz(j) - cz;
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d > R) continue;
                if (!cam.project(new Vec3(wx(i), H[i][j] + 0.06, wz(j)), XY)) continue;
                if (Math.abs(XY[0] - cam.w * 0.5f) > margin || Math.abs(XY[1] - cam.h * 0.5f) > margin) continue;
                SX[i][j] = XY[0];
                SY[i][j] = XY[1];
                DD[i][j] = d;
                OK_P[i][j] = true;
            }
        }

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (!OK_P[i][j]) continue;
                if (i + 1 < N) seg(i, j, i + 1, j, R, life, far, pri, acc, cam);
                if (j + 1 < N) seg(i, j, i, j + 1, R, life, far, pri, acc, cam);
                // point-cloud node: the film's holographics are dot fields, brightest at the wave front
                float behind = R - DD[i][j];
                float nf = Mth.clamp(1f - behind / 16f, 0f, 1f);
                float dot = 0.9f + 1.1f * nf;
                Draw.rect(SX[i][j] - dot * 0.5f, SY[i][j] - dot * 0.5f, SX[i][j] + dot * 0.5f, SY[i][j] + dot * 0.5f,
                        Draw.col(nf > 0.6f ? 0xFFFFFF : acc, (0.35f + 0.5f * nf) * life));
                // relief column that pulses up as the wave passes
                if (!lod && behind < 9f && ((i + j) & 1) == 0) {
                    float k = 1f - behind / 9f;
                    float hgt = 0.9f * k;
                    if (cam.project(new Vec3(wx(i), H[i][j] + 0.06 + hgt, wz(j)), XY)) {
                        Draw.line(SX[i][j], SY[i][j], XY[0], XY[1], 1f, Draw.col(0xFFFFFF, 0.55f * k * life));
                    }
                }
            }
        }

        // wave front: a ring that follows the relief
        if (R < HudState.radarRange() + 3f) {
            int n = lod ? 48 : 96;
            for (int pass = lod ? 1 : -1; pass < 2; pass++) {
                float rr = R - Math.max(pass, 0) * 1.6f;
                if (rr < 1f) continue;
                float px = 0, py = 0;
                boolean prev = false;
                for (int k = 0; k <= n; k++) {
                    double a = k * 2 * Math.PI / n;
                    double x = cx + Math.cos(a) * rr, z = cz + Math.sin(a) * rr;
                    boolean ok = cam.project(new Vec3(x, groundAt(x, z) + 0.12, z), XY)
                            && Math.abs(XY[0] - cam.w * 0.5f) < margin && Math.abs(XY[1] - cam.h * 0.5f) < margin;
                    if (ok && prev) {
                        if (pass < 0) Draw.line(px, py, XY[0], XY[1], 5f, Draw.col(acc, 0.14f * life)); // bloom
                        else Draw.line(px, py, XY[0], XY[1], pass == 0 ? 1.8f : 1f,
                                Draw.col(pass == 0 ? 0xFFFFFF : acc, (pass == 0 ? 0.85f : 0.4f) * life));
                    }
                    prev = ok;
                    px = XY[0];
                    py = XY[1];
                }
            }
        }
    }

    private static final boolean[][] OK_P = new boolean[N][N];
    private static final float[][] DD = new float[N][N];

    private static void seg(int i0, int j0, int i1, int j1, float R, float life, float far, int pri, int acc, Cam cam) {
        if (!OK_P[i1][j1]) return;
        float d = (DD[i0][j0] + DD[i1][j1]) * 0.5f;
        float behind = R - d;
        float flash = Mth.clamp(1f - behind / 14f, 0f, 1f);
        float a = (0.16f + 0.62f * flash * flash) * life;
        // nearer segments read stronger than distant ones
        double mx = (wx(i0) + wx(i1)) * 0.5 - cam.pos.x, mz = (wz(j0) + wz(j1)) * 0.5 - cam.pos.z;
        float cd = (float) Math.sqrt(mx * mx + mz * mz);
        a *= Mth.clamp(1.25f - cd / (far * 1.6f), 0.25f, 1f);
        float slope = Math.abs(H[i0][j0] - H[i1][j1]);
        boolean steep = slope >= 3f;
        float lift = (H[i0][j0] + H[i1][j1]) * 0.5f - (float) baseY;
        int col = steep ? 0xFFB020 : lift > 3f ? acc : pri;
        if (flash > 0.8f) col = 0xFFFFFF;
        Draw.line(SX[i0][j0], SY[i0][j0], SX[i1][j1], SY[i1][j1], flash > 0.6f ? 1.4f : 1f, Draw.col(col, Math.min(1f, a)));
    }
}
