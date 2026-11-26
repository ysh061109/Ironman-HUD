package io.github.ysh061109.ironhud;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape-aware wireframe hologram of a creature. A simplified box skeleton is built per body plan
 * (biped, creeper, quadruped, spider, flyer, cube, plain box) from the entity's real hitbox and is
 * posed with the entity's actual head turn and walk cycle. The same model is drawn twice: overlaid on
 * the creature in the world (scan wireframe with a sweeping band) and rotating inside the info card.
 */
public final class EntityHolo {
    private EntityHolo() {}

    private static final float WHITE_ALPHA = 1f;

    /** Box rows: cx, cy, cz, sx, sy, sz, group, pivotY, pivotZ, slices. Line rows: x0,y0,z0,x1,y1,z1, group, pivotY, pivotZ. */
    private static final class Model {
        final List<float[]> boxes = new ArrayList<>();
        final List<float[]> lines = new ArrayList<>();
        float h = 1f, w = 1f, headY = 0.9f, headZ = 0f;
        float ext = 1f;
        String plan = "BOX";
    }

    private static Model cached;
    private static Object cacheKey;

    private static void box(Model m, float cx, float cy, float cz, float sx, float sy, float sz, int g, float py, float pz, int sl) {
        m.boxes.add(new float[]{cx, cy, cz, sx, sy, sz, g, py, pz, sl});
    }

    private static void line(Model m, float x0, float y0, float z0, float x1, float y1, float z1, int g, float py, float pz) {
        m.lines.add(new float[]{x0, y0, z0, x1, y1, z1, g, py, pz});
    }

    private static Model model(LivingEntity e) {
        float H = Math.max(0.2f, e.getBbHeight()), W = Math.max(0.2f, e.getBbWidth());
        Object key = java.util.List.of(e.getType(), Math.round(H * 40f), Math.round(W * 40f));
        if (key.equals(cacheKey) && cached != null) return cached;
        Model m = new Model();
        m.h = H;
        m.w = W;
        if (e instanceof Player || e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof Witch
                || e instanceof EnderMan || e instanceof AbstractIllager || e instanceof AbstractVillager
                || e instanceof AbstractPiglin || e instanceof IronGolem || e instanceof Warden) {
            biped(m, e, H);
        } else if (e instanceof Creeper) {
            creeper(m, H);
        } else if (e instanceof Spider) {
            spider(m, H, W);
        } else if (e instanceof Slime || e instanceof Ghast) {
            cube(m, H, W, e instanceof Ghast);
        } else if (e instanceof Phantom || e instanceof Bat || e instanceof Bee || e instanceof Parrot
                || e instanceof Vex || e instanceof Allay) {
            flyer(m, H, W);
        } else if (e instanceof WaterAnimal) {
            plain(m, H, W);
        } else if (e instanceof Animal || W > H * 0.7f) {
            quadruped(m, H, W);
        } else if (H > W * 1.5f) {
            biped(m, e, H);
        } else {
            plain(m, H, W);
        }
        m.ext = Math.max(H, Math.max(W, W * 1.5f));
        cached = m;
        cacheKey = key;
        return m;
    }

    private static void biped(Model m, LivingEntity e, float H) {
        m.plan = "BIPED";
        float u = H / 32f;
        boolean thin = e instanceof AbstractSkeleton || e instanceof EnderMan;
        boolean golem = e instanceof IronGolem || e instanceof Warden;
        boolean fwd = e instanceof Zombie;
        float wf = golem ? 1.6f : 1f;
        float lw = thin ? 2f : 4f;
        box(m, 0, 28 * u, 0, 8 * u, 8 * u, 8 * u, 1, 0, 0, 2);
        if (e instanceof AbstractVillager || e instanceof Witch || e instanceof AbstractIllager) {
            box(m, 0, 27 * u, 5 * u, 2 * u, 4 * u, 2 * u, 1, 0, 0, 0);
        }
        box(m, 0, 18 * u, 0, 8 * u * wf, 12 * u, 4 * u * wf, 0, 0, 0, 3);
        for (int s = -1; s <= 1; s += 2) {
            int ga = s < 0 ? 5 : 4;   // arms
            int gl = s < 0 ? 3 : 2;   // legs
            float ax = s * (4f + lw / 2f) * u * wf;
            if (fwd) {
                box(m, ax, 21 * u, 6 * u, lw * u, lw * u, 12 * u, 0, 0, 0, 1);
            } else {
                box(m, ax, 18 * u, 0, lw * u * wf, 12 * u, lw * u * wf, ga, 24 * u, 0, 1);
            }
            box(m, s * 2 * u * wf, 6 * u, 0, lw * u * wf, 12 * u, lw * u * wf, gl, 12 * u, 0, 1);
        }
        m.headY = 28 * u;
    }

    private static void creeper(Model m, float H) {
        m.plan = "CREEPER";
        float u = H / 26f;
        box(m, 0, 22 * u, 0, 8 * u, 8 * u, 8 * u, 1, 0, 0, 2);
        box(m, 0, 12 * u, 0, 8 * u, 12 * u, 4 * u, 0, 0, 0, 3);
        box(m, -2 * u, 3 * u, 4 * u, 4 * u, 6 * u, 4 * u, 2, 6 * u, 4 * u, 0);
        box(m, 2 * u, 3 * u, 4 * u, 4 * u, 6 * u, 4 * u, 3, 6 * u, 4 * u, 0);
        box(m, -2 * u, 3 * u, -4 * u, 4 * u, 6 * u, 4 * u, 4, 6 * u, -4 * u, 0);
        box(m, 2 * u, 3 * u, -4 * u, 4 * u, 6 * u, 4 * u, 5, 6 * u, -4 * u, 0);
        m.headY = 22 * u;
    }

    private static void spider(Model m, float H, float W) {
        m.plan = "ARACHNID";
        box(m, 0, H * 0.45f, W * 0.42f, W * 0.30f, H * 0.42f, W * 0.30f, 1, 0, 0, 0);
        box(m, 0, H * 0.45f, 0, W * 0.26f, H * 0.36f, W * 0.26f, 0, 0, 0, 0);
        box(m, 0, H * 0.48f, -W * 0.38f, W * 0.45f, H * 0.55f, W * 0.55f, 0, 0, 0, 1);
        float[] zs = {0.22f, 0.07f, -0.08f, -0.22f};
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 4; i++) {
                float z = zs[i] * W;
                int g = ((i + (s > 0 ? 1 : 0)) & 1) == 0 ? 2 : 3;
                line(m, s * W * 0.14f, H * 0.45f, z, s * W * 0.46f, H * 0.95f, z + (i < 2 ? 0.1f : -0.1f) * W, g, H * 0.45f, z);
                line(m, s * W * 0.46f, H * 0.95f, z + (i < 2 ? 0.1f : -0.1f) * W, s * W * 0.55f, 0, z + (i < 2 ? 0.25f : -0.25f) * W, g, H * 0.45f, z);
            }
        }
        m.headY = H * 0.45f;
        m.headZ = W * 0.42f;
    }

    private static void cube(Model m, float H, float W, boolean ghast) {
        m.plan = ghast ? "AERIAL" : "AMORPHOUS";
        box(m, 0, H * 0.5f, 0, W, H, W, 0, 0, 0, 2);
        box(m, 0, H * 0.5f, 0, W * 0.5f, H * 0.5f, W * 0.5f, 0, 0, 0, 0);
        if (ghast) {
            for (int i = 0; i < 9; i++) {
                float x = ((i % 3) - 1) * W * 0.3f, z = ((i / 3) - 1) * W * 0.3f;
                line(m, x, 0, z, x, -H * 0.35f, z, 0, 0, 0);
            }
        }
        m.headY = H * 0.5f;
        m.headZ = W * 0.5f;
    }

    private static void flyer(Model m, float H, float W) {
        m.plan = "AIRFRAME";
        float wing = Math.max(W * 1.5f, 0.5f);
        box(m, 0, H * 0.5f, 0, W * 0.5f, H * 0.6f, W * 1.1f, 0, 0, 0, 1);
        box(m, 0, H * 0.56f, W * 0.65f, W * 0.4f, H * 0.4f, W * 0.4f, 1, 0, 0, 0);
        box(m, wing / 2f + W * 0.2f, H * 0.6f, 0, wing, Math.max(0.03f, H * 0.06f), W * 0.9f, 6, H * 0.6f, 0, 1);
        box(m, -(wing / 2f + W * 0.2f), H * 0.6f, 0, wing, Math.max(0.03f, H * 0.06f), W * 0.9f, 7, H * 0.6f, 0, 1);
        line(m, 0, H * 0.5f, -W * 0.55f, 0, H * 0.3f, -W * 1.4f, 0, 0, 0);
        m.headY = H * 0.56f;
        m.headZ = W * 0.65f;
        m.w = wing * 2f;
    }

    private static void quadruped(Model m, float H, float W) {
        m.plan = "QUADRUPED";
        float D = Math.max(W * 1.3f, H * 0.9f);
        float ly = H * 0.42f;
        box(m, 0, H * 0.58f, 0, W * 0.82f, H * 0.36f, D * 0.8f, 0, 0, 0, 2);
        box(m, 0, H * 0.74f, D * 0.5f, W * 0.55f, H * 0.4f, W * 0.55f, 1, 0, 0, 1);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int g = ((sx > 0 ? 1 : 0) ^ (sz > 0 ? 1 : 0)) == 0 ? 2 : 3;
                box(m, sx * W * 0.3f, ly / 2f, sz * D * 0.32f, W * 0.2f, ly, W * 0.2f, g, ly, sz * D * 0.32f, 0);
            }
        }
        line(m, 0, H * 0.7f, -D * 0.4f, 0, H * 0.45f, -D * 0.6f, 0, 0, 0);
        m.headY = H * 0.74f;
        m.headZ = D * 0.5f;
        m.w = D;
    }

    private static void plain(Model m, float H, float W) {
        m.plan = "VOLUME";
        box(m, 0, H * 0.5f, 0, W, H, W * 1.2f, 0, 0, 0, 3);
        m.headY = H * 0.5f;
        m.headZ = W * 0.6f;
    }

    // ------------------------------------------------------------------ transforms

    private interface Tf {
        /** local model coordinates -> screen (x, y) and a depth value in out[2]; false when not drawable. */
        boolean apply(float x, float y, float z, float[] out);
    }

    private static final class WorldTf implements Tf {
        final Cam cam;
        final double px, py, pz;
        final float c, s;
        final float[] xy = new float[2];

        WorldTf(Cam cam, double px, double py, double pz, float yawRad) {
            this.cam = cam;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.c = (float) Math.cos(yawRad);
            this.s = (float) Math.sin(yawRad);
        }

        @Override
        public boolean apply(float x, float y, float z, float[] out) {
            double wx = px + x * c - z * s, wz = pz + x * s + z * c;
            if (!cam.project(new Vec3(wx, py + y, wz), xy)) return false;
            if (Math.abs(xy[0] - cam.w * 0.5f) > cam.w * 3f || Math.abs(xy[1] - cam.h * 0.5f) > cam.h * 3f) return false;
            out[0] = xy[0];
            out[1] = xy[1];
            out[2] = 0f;
            return true;
        }
    }

    private static final class CardTf implements Tf {
        final float mx, my, sc, cs, sn, cp, sp, midY, ext;

        CardTf(float mx, float my, float sc, float yaw, float pitch, float midY, float ext) {
            this.mx = mx;
            this.my = my;
            this.sc = sc;
            this.cs = (float) Math.cos(yaw);
            this.sn = (float) Math.sin(yaw);
            this.cp = (float) Math.cos(pitch);
            this.sp = (float) Math.sin(pitch);
            this.midY = midY;
            this.ext = ext;
        }

        @Override
        public boolean apply(float x, float y, float z, float[] out) {
            float xr = x * cs + z * sn;
            float zr = -x * sn + z * cs;
            float yy = y - midY;
            out[0] = mx + xr * sc;
            out[1] = my - (yy * cp - zr * sp) * sc;
            out[2] = (zr * cp + yy * sp) / ext;
            return true;
        }
    }

    // ------------------------------------------------------------------ posing

    private static final class Pose {
        float swing, headYaw, flap;
    }

    private static Pose pose(LivingEntity e, float pt, float t) {
        Pose p = new Pose();
        float pos = e.walkAnimation.position(pt);
        float spd = Math.min(1f, e.walkAnimation.speed(pt));
        p.swing = (float) Math.cos(pos * 0.6662f) * 1.2f * spd;
        float hy = Mth.wrapDegrees(Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
        p.headYaw = (float) Math.toRadians(Mth.clamp(hy, -75f, 75f));
        p.flap = (float) Math.sin(t * 0.45f) * 0.55f;
        return p;
    }

    private static void anim(float[] p, int g, float py, float pz, Pose po) {
        if (g == 0) return;
        float x = p[0], y = p[1], z = p[2];
        if (g == 1) {
            float c = (float) Math.cos(po.headYaw), s = (float) Math.sin(po.headYaw);
            p[0] = x * c + (z - 0) * s;
            p[2] = -x * s + z * c;
        } else if (g >= 2 && g <= 5) {
            float a = po.swing * ((g == 2 || g == 5) ? 1f : -1f);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float dy = y - py, dz = z - pz;
            p[1] = py + dy * c - dz * s;
            p[2] = pz + dy * s + dz * c;
        } else if (g == 6 || g == 7) {
            float a = po.flap * (g == 6 ? 1f : -1f);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float dy = y - py;
            p[0] = x * c - dy * s;
            p[1] = py + x * s + dy * c;
        }
    }

    // ------------------------------------------------------------------ drawing

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    private static final float[] A = new float[3], B = new float[3];

    private static boolean corner(float[] row, int ix, int iy, int iz, float yOver, Pose po, Tf tf, float[] out, float[] localY) {
        float x = row[0] + ix * row[3] * 0.5f;
        float y = Float.isNaN(yOver) ? row[1] + iy * row[4] * 0.5f : yOver;
        float z = row[2] + iz * row[5] * 0.5f;
        float[] p = {x, y, z};
        anim(p, (int) row[6], row[7], row[8], po);
        localY[0] = y;
        return tf.apply(p[0], p[1], p[2], out);
    }

    private static final class Style {
        float reveal, sweep, a;
        int rgb, hi;
        boolean useDepth;
        float h;
    }

    private static void seg(float[] a, float[] b, float ya, float yb, Style st, float base) {
        float ymin = Math.min(ya, yb) / st.h, ymax = Math.max(ya, yb) / st.h;
        if (ymin > st.reveal) return;
        float al = base;
        if (st.useDepth) al = base * Mth.clamp(0.55f + 0.9f * (a[2] + b[2]) * 0.5f, 0.2f, 1.2f);
        int col = st.rgb;
        float mid = (ymin + ymax) * 0.5f;
        float boost = Math.max(0f, 1f - Math.abs(mid - st.sweep) / 0.07f);
        if (boost > 0f) {
            col = mix(col, st.hi, boost);
            al = Math.min(1f, al + boost * 0.5f);
        }
        if (st.reveal < 1f && ymax > st.reveal - 0.04f) {
            col = 0xFFFFFF;
            al = 0.95f;
        }
        Draw.line(a[0], a[1], b[0], b[1], 1f, Draw.col(col, Math.min(1f, al * st.a)));
    }

    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static void drawModel(Model m, Pose po, Tf tf, Style st, float edgeAlpha) {
        boolean lod = Draw.lod;
        float[][] c = new float[8][3];
        float[] ys = new float[8];
        float[] ly = new float[1];
        for (float[] row : m.boxes) {
            boolean allOk = true;
            for (int i = 0; i < 8; i++) {
                if (!corner(row, (i & 1) == 0 ? -1 : 1, (i & 2) == 0 ? -1 : 1, (i & 4) == 0 ? -1 : 1,
                        Float.NaN, po, tf, c[i], ly)) {
                    allOk = false;
                }
                ys[i] = ly[0];
            }
            if (allOk) {
                for (int[] e : EDGES) seg(c[e[0]], c[e[1]], ys[e[0]], ys[e[1]], st, edgeAlpha);
            }
            int sl = lod ? 0 : (int) row[9];
            for (int k = 1; k <= sl; k++) {
                float y = row[1] - row[4] * 0.5f + row[4] * k / (sl + 1f);
                float[][] r = new float[4][3];
                int[][] xz = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                boolean ok = true;
                for (int i = 0; i < 4; i++) {
                    if (!corner(row, xz[i][0], 0, xz[i][1], y, po, tf, r[i], ly)) ok = false;
                }
                if (!ok) continue;
                for (int i = 0; i < 4; i++) seg(r[i], r[(i + 1) & 3], y, y, st, edgeAlpha * 0.5f);
            }
        }
        for (float[] l : m.lines) {
            float[] p0 = {l[0], l[1], l[2]}, p1 = {l[3], l[4], l[5]};
            anim(p0, (int) l[6], l[7], l[8], po);
            anim(p1, (int) l[6], l[7], l[8], po);
            if (tf.apply(p0[0], p0[1], p0[2], A) && tf.apply(p1[0], p1[1], p1[2], B)) {
                seg(A.clone(), B.clone(), l[1], l[4], st, edgeAlpha);
            }
        }
    }

    /** Scan wireframe laid over the creature itself, with a rising scan band; builds up from the feet. */
    public static void renderWorld(Cam cam, LivingEntity e, float pt, float t, int rgb, int hi, float ease, boolean dead) {
        if (dead || ease <= 0.02f) return;
        Vec3 pos = e.getPosition(pt);
        if (pos.distanceToSqr(cam.pos) > 56.0 * 56.0) return;
        Model m = model(e);
        Pose po = pose(e, pt, t);
        float yaw = (float) Math.toRadians(Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
        WorldTf tf = new WorldTf(cam, pos.x, pos.y, pos.z, yaw);
        Style st = new Style();
        st.reveal = ease;
        st.sweep = (t * 0.03f) % 1.4f;
        st.a = 1f;
        st.rgb = rgb;
        st.hi = 0xFFFFFF;
        st.h = Math.max(0.05f, m.h);
        drawModel(m, po, tf, st, 0.55f);

        // weak-point reticle on the head
        float[] out = new float[3];
        float[] hp = {0, m.headY, m.headZ};
        anim(hp, 1, 0, 0, po);
        if (ease > 0.8f && tf.apply(hp[0], hp[1], hp[2], out)) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(t * 0.4f);
            Draw.ring(out[0], out[1], 5f, 1f, Draw.col(hi, 0.8f * pulse));
            Draw.line(out[0] - 9f, out[1], out[0] - 5f, out[1], 1f, Draw.col(hi, 0.8f));
            Draw.line(out[0] + 5f, out[1], out[0] + 9f, out[1], 1f, Draw.col(hi, 0.8f));
        }
    }

    /** Rotating hologram inside the info card, centred on (mx, my) and fitting a box of height mh. */
    public static void renderCard(LivingEntity e, float mx, float my, float mh, float t, float a, float ease,
                                  int rgb, int hi) {
        Model m = model(e);
        Pose po = pose(e, 1f, t);
        po.headYaw = (float) Math.toRadians(Mth.wrapDegrees(
                e.getYHeadRot() - e.yBodyRot) * 0.6f);
        float span = Math.max(m.h, Math.max(m.w, 0.5f) * 0.9f);
        float sc = Math.min(mh / m.h, 66f / Math.max(0.3f, Math.max(m.w, m.h * 0.5f) * 1.25f));
        sc = Math.min(sc, mh / span);
        CardTf tf = new CardTf(mx, my, sc, t * 0.05f, (float) Math.toRadians(16f), m.h * 0.5f, Math.max(0.3f, span));
        Style st = new Style();
        st.reveal = ease * 1.05f;
        st.sweep = (t * 0.025f) % 1.4f;
        st.a = a;
        st.rgb = 0xFFFFFF;
        st.hi = hi;
        st.useDepth = true;
        st.h = Math.max(0.05f, m.h);
        // holo pad under the model
        float rp = Math.max(m.w, m.h * 0.45f) * 0.8f;
        float px = 0, py = 0;
        float[] o = new float[3];
        for (int i = 0; i <= 28; i++) {
            float ang = (float) (i * 2 * Math.PI / 28);
            tf.apply((float) Math.cos(ang) * rp, -0.02f, (float) Math.sin(ang) * rp, o);
            if (i > 0) Draw.line(px, py, o[0], o[1], 1f, Draw.col(hi, 0.45f * a));
            px = o[0];
            py = o[1];
        }
        drawModel(m, po, tf, st, 0.75f);
        // body-plan caption
        Draw.textCenter(m.plan, mx, my + mh * 0.5f + 4f, Draw.col(hi, 0.8f * a), 0.45f);
    }
}
