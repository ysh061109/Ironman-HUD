package io.github.ysh061109.ironhud;

import io.github.ysh061109.ironhud.HudState.Contact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** Smaller HUD pieces: helmet frame, boot sequence, hit indicator, system log, Modus subtitles, multi-lock. */
public final class Overlays {
    private static final int RED = 0xFF3B30, AMBER = 0xFFB020, WHITE = 0xDFF9FF;
    private static final float[] XY = new float[2];

    private Overlays() {}

    // ------------------------------------------------------------ helmet frame

    public static void helmetFrame(int w, int h, int pri) {
        int dark = Draw.col(0x000000, 0.42f), clear = Draw.col(0x000000, 0f);
        float ex = w * 0.10f, ey = h * 0.12f;
        Draw.gradV(0, 0, w, ey, dark, clear);
        Draw.gradV(0, h - ey, w, h, clear, dark);
        Draw.gradH(0, 0, ex, h, dark, clear);
        Draw.gradH(w - ex, 0, w, h, clear, dark);
        // visor rim: rounded corners and thin edge lines
        float r = 34f, m = 3f;
        int c = Draw.col(pri, 0.28f);
        Draw.arc(m + r, m + r, r - 0.6f, r + 0.6f, 180f, 90f, c);
        Draw.arc(w - m - r, m + r, r - 0.6f, r + 0.6f, 270f, 90f, c);
        Draw.arc(m + r, h - m - r, r - 0.6f, r + 0.6f, 90f, 90f, c);
        Draw.arc(w - m - r, h - m - r, r - 0.6f, r + 0.6f, 0f, 90f, c);
        Draw.rect(m + r, m, w - m - r, m + 1f, c);
        Draw.rect(m + r, h - m - 1f, w - m - r, h - m, c);
        Draw.rect(m, m + r, m + 1f, h - m - r, c);
        Draw.rect(w - m - 1f, m + r, w - m, h - m - r, c);
    }

    // ------------------------------------------------------------ boot sequence

    private static final String[][] BOOT_LINES = {
            {"MODUS CORE v1.0", "ONLINE"},
            {"HELMET SEAL", "LOCKED"},
            {"REACTOR LINK", "STABLE"},
            {"SENSOR ARRAY", "CALIBRATED"},
            {"OPTICS / ZOOM", "READY"},
            {"ARMAMENT BUS", "READY"},
            {"VOICE LINK", "?"},
            {"MIC ARRAY", "?"},
    };

    public static void boot(int w, int h, float pt, int pri, int acc) {
        float a = HudState.age + pt;
        if (a > 100f) return;

        float open = Mth.clamp((a - 10f) / 14f, 0f, 1f);
        open = open * open * (3f - 2f * open);
        if (open < 1f) {
            float half = h / 2f * (1f - open);
            Draw.rect(0, 0, w, half, Draw.col(0x000000, 0.95f));
            Draw.rect(0, h - half, w, h, Draw.col(0x000000, 0.95f));
            if (a > 6f && a < 14f) {
                float f = 1f - Math.abs(a - 10f) / 4f;
                Draw.rect(0, h / 2f - 1f, w, h / 2f + 1f, Draw.col(pri, 0.9f * Math.max(0f, f)));
            }
        }
        float fade = Mth.clamp((100f - a) / 18f, 0f, 1f);
        float x = w / 2f - 100f, y = h / 2f - 44f;
        Draw.text("MODUS // INITIALIZING", x, y - 14f, Draw.col(acc, fade), 0.85f);
        Draw.rect(x, y - 4f, x + 200f, y - 3f, Draw.col(pri, 0.7f * fade));
        for (int i = 0; i < BOOT_LINES.length; i++) {
            float start = 14f + i * 7f;
            if (a < start) break;
            String label = BOOT_LINES[i][0];
            String status = BOOT_LINES[i][1];
            if (status.equals("?")) {
                status = label.startsWith("VOICE")
                        ? (Voice.backend() == Voice.Backend.NONE ? "OFFLINE" : "ONLINE")
                        : (Mic.isRunning() ? "LISTENING" : "STANDBY");
            }
            int chars = (int) ((a - start) * 3f);
            float ly = y + i * 10f;
            Draw.text(label.substring(0, Math.min(label.length(), chars)), x, ly, Draw.col(WHITE, 0.9f * fade), 0.7f);
            if (chars > label.length() + 3) {
                int ok = status.equals("OFFLINE") ? AMBER : 0x39FF88;
                Draw.textRight(status, x + 200f, ly, Draw.col(ok, fade), 0.7f);
            }
        }
    }

    // ------------------------------------------------------------ hit indicator

    public static void hits(LocalPlayer p, float pt, int w, int h, float t) {
        float cx = w / 2f, cy = h / 2f;
        float yaw = Mth.rotLerp(pt, p.yRotO, p.getYRot());
        for (float[] hit : HudState.hits) {
            float age = HudState.age + pt - hit[1];
            if (age > 50f) continue;
            float fade = 1f - age / 50f;
            float rel = Mth.wrapDegrees(hit[0] - yaw); // 0 = ahead
            float ang = rel - 90f;                      // screen angle, 0 = right
            float a = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(t * 0.8f));
            Draw.arc(cx, cy, 72f, 82f, ang - 20f, 40f, Draw.col(RED, a * fade));
            Draw.arc(cx, cy, 84f, 85.5f, ang - 26f, 52f, Draw.col(RED, 0.6f * fade));
        }
    }

    // ------------------------------------------------------------ system log

    public static void sysLog(float x, float y, float boot, int pri) {
        List<SysLog.Entry> lines = SysLog.all();
        int shown = 0;
        for (int i = lines.size() - 1; i >= 0 && shown < 5; i--) {
            SysLog.Entry e = lines.get(i);
            float age = HudState.age - e.born();
            if (age > 300f) continue;
            float fade = Mth.clamp((300f - age) / 50f, 0f, 1f);
            int chars = (int) (age * 3f) + 1;
            String s = "> " + e.text();
            Draw.text(s.substring(0, Math.min(s.length(), chars)), x, y + shown * 9f,
                    Draw.col(pri, 0.9f * fade * boot), 0.65f);
            shown++;
        }
    }

    // ------------------------------------------------------------ Modus subtitles

    public static void modusPanel(int w, int h, float pt, float t, int pri, int acc) {
        List<Modus.Msg> log = Modus.LOG;
        if (log.isEmpty()) return;
        int shown = 0;
        float baseY = h - 62f;
        for (int i = log.size() - 1; i >= 0 && shown < 3; i--) {
            Modus.Msg m = log.get(i);
            float age = HudState.age + pt - m.born();
            if (age > 420f) continue;
            float fade = Mth.clamp((420f - age) / 60f, 0f, 1f);
            String s = (m.modus() ? "MODUS: " : "> ") + m.text();
            int chars = m.modus() ? (int) (age * 2.2f) + 7 : s.length();
            String vis = s.substring(0, Math.min(s.length(), chars));
            float y = baseY - shown * 11f;
            Draw.textCenter(vis, w / 2f, y, Draw.col(m.modus() ? acc : WHITE, (m.modus() ? 1f : 0.75f) * fade), 0.85f);
            shown++;
        }
        // speaking waveform
        if (Voice.isSpeaking()) {
            float x = w / 2f - 52f;
            for (int i = 0; i < 22; i++) {
                float amp = 2f + 7f * (0.5f + 0.5f * (float) Math.sin(t * 0.9f + i * 0.8f))
                        * (0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(t * 0.37f + i * 1.9f)));
                Draw.rect(x + i * 5f, baseY + 14f - amp, x + i * 5f + 2.5f, baseY + 14f + amp, Draw.col(acc, 0.85f));
            }
        }
    }

    // ------------------------------------------------------------ multi-lock

    public static void priorityList(float x, float y, float t, int pri) {
        if (!HudState.multiLock) return;
        Draw.text("PRIORITY TARGETS [" + HudState.locks.size() + "]", x, y, Draw.col(pri, 1f), 0.7f);
        Draw.rect(x, y + 9f, x + 120f, y + 10f, Draw.col(pri, 0.6f));
        int i = 0;
        for (Contact c : HudState.locks) {
            LivingEntity e = c.e();
            int rgb = i == 0 ? RED : i == 1 ? AMBER : pri;
            String s = String.format(Locale.ROOT, "%d %s  %.0fHP  %.0fM", i + 1,
                    e.getName().getString().toUpperCase(Locale.ROOT), e.getHealth(), c.dist());
            Draw.text(s, x, y + 13f + i * 9f, Draw.col(rgb, 0.95f), 0.65f);
            i++;
        }
        if (HudState.locks.isEmpty()) Draw.text("NO TARGETS IN VIEW", x, y + 13f, Draw.col(0x7B8794, 1f), 0.65f);
    }

    public static void multiLock(Cam cam, float pt, float t, int w, int h, int pri) {
        if (!HudState.multiLock) return;
        int i = 0;
        for (Contact c : HudState.locks) {
            LivingEntity e = c.e();
            Vec3 off = e.getPosition(pt).subtract(e.position());
            AABB bb = e.getBoundingBox().move(off);
            if (!cam.project(bb.getCenter(), XY)) {
                i++;
                continue;
            }
            float x = XY[0], y = XY[1];
            int rgb = i == 0 ? RED : i == 1 ? AMBER : pri;
            Draw.line(w / 2f, h / 2f, x, y, 1f, Draw.col(rgb, 0.16f));
            Draw.brackets(x, y, 12f, 4f, 1.2f, Draw.col(rgb, 0.95f));
            Draw.dashedRing(x, y, 17f, 1.2f, t * (i % 2 == 0 ? 2f : -2f), 10, Draw.col(rgb, 0.8f));
            Draw.text(Integer.toString(i + 1), x - 18f, y - 22f, Draw.col(rgb, 1f), 1.0f);
            Draw.textCenter(e.getName().getString().toUpperCase(Locale.ROOT), x, y + 21f, Draw.col(rgb, 0.95f), 0.6f);
            float f = Mth.clamp(e.getHealth() / Math.max(1f, e.getMaxHealth()), 0f, 1f);
            Draw.rect(x - 12f, y + 29f, x + 12f, y + 31f, Draw.col(rgb, 0.25f));
            Draw.rect(x - 12f, y + 29f, x - 12f + 24f * f, y + 31f, Draw.col(rgb, 0.95f));
            i++;
        }
    }

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
