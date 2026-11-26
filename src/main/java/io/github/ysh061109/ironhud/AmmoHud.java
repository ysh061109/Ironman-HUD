package io.github.ysh061109.ironhud;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Suit munitions readout modelled on the film's weapons-bay panels: chamfered frame, a row of upright round
 * icons with a lead arrow, a large red digit readout with a WARNING strip, a boxed reserve count with a slash,
 * a projectile glyph and fine print.
 */
public final class AmmoHud {
    private AmmoHud() {}

    public static final float W = 112f, H = 66f;
    private static final int RED = 0xFF3B30, AMBER = 0xFFB020, WHITE = 0xFFFFFF;

    private static String trim(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    private static void chamfer(float x, float y, float w, float h, float c, int col, float a) {
        Draw.line(x, y + c, x + c, y, 1f, Draw.col(col, a));
        Draw.line(x + c, y, x + w, y, 1f, Draw.col(col, a));
        Draw.line(x + w, y, x + w, y + h - c, 1f, Draw.col(col, a));
        Draw.line(x + w, y + h - c, x + w - c, y + h, 1f, Draw.col(col, a));
        Draw.line(x + w - c, y + h, x, y + h, 1f, Draw.col(col, a));
        Draw.line(x, y + h, x, y + c, 1f, Draw.col(col, a));
    }

    public static void render(LocalPlayer p, TaczCompat.Gun g, String name, float x, float y, float t, float pt,
                              float boot, int pri, int acc) {
        float fl = TaczCompat.flash(pt);
        boolean empty = g.mag() == 0 && !g.chambered();
        float frac = g.cap() <= 0 ? 0f : Mth.clamp(g.mag() / (float) g.cap(), 0f, 1f);
        boolean low = frac <= 0.25f;
        int main = empty ? RED : low ? AMBER : WHITE;
        float jit = fl > 0.4f ? (fl * 1.4f) : 0f;

        // frame
        Draw.rect(x + 10f, y, x + W, y + 10f, Draw.col(0x02101A, 0.42f * boot));
        Draw.rect(x, y + 10f, x + W, y + H - 10f, Draw.col(0x02101A, 0.42f * boot));
        Draw.rect(x, y + H - 10f, x + W - 10f, y + H, Draw.col(0x02101A, 0.42f * boot));
        chamfer(x, y, W, H, 10f, pri, 0.7f * boot);
        Draw.rect(x + W * 0.5f, y - 1f, x + W, y + 1f, Draw.col(AMBER, 0.9f * boot));       // bright top rail
        Draw.rect(x, y + H - 1f, x + W * 0.35f, y + H + 1f, Draw.col(AMBER, 0.7f * boot));   // bottom-left tick

        // title + sub
        Draw.text(trim(name, 20), x + 13f, y + 3f, Draw.col(WHITE, boot), 0.62f);
        String sub = (g.fire().isEmpty() ? "" : g.fire() + " // ") + (g.rpm() > 0 ? g.rpm() + " RPM" : "MUNITIONS");
        Draw.text(sub, x + 13f, y + 11f, Draw.col(pri, 0.75f * boot), 0.38f);

        // lead arrow + round icons
        float ry = y + 19f;
        Draw.triDown(x + 5f, ry + 6f, 2.6f, 1f, Draw.col(AMBER, boot));
        int pips = Math.min(Math.max(g.cap(), 1), 14);
        float pw = 5.2f;
        int lit = Math.round(frac * pips);
        if (g.mag() > 0 && lit == 0) lit = 1;
        for (int i = 0; i < pips; i++) {
            float px = x + 11f + i * pw;
            boolean on = i < lit;
            boolean tip = on && i == lit - 1;
            int c = tip ? (low ? RED : AMBER) : on ? WHITE : pri;
            float a = (on ? 0.95f : 0.28f) * boot;
            float top = ry + (tip ? -fl * 2.5f : 0f);
            Draw.rect(px, top + 3f, px + 2.6f, ry + 12f, Draw.col(c, a));
            Draw.rect(px + 0.6f, top + 1f, px + 2f, top + 3f, Draw.col(c, a));
            Draw.rect(px + 1f, top, px + 1.6f, top + 1f, Draw.col(c, a));
            if (on) Draw.rect(px, ry + 12f, px + 2.6f, ry + 13f, Draw.col(0x8A5A00, 0.7f * boot));
        }

        // large digits
        String digits = String.format(Locale.ROOT, "%02d", g.mag());
        float dx = x + 6f + jit;
        if (empty && ((int) (t / 6f) & 1) == 0) {
            Draw.text("EMPTY", dx, y + 36f, Draw.col(RED, 0.95f * boot), 1.5f);
        } else {
            Draw.text(digits, dx, y + 33f, Draw.col(main, boot), 2.2f);
            if (g.chambered()) Draw.text("+1", dx + 26f, y + 33f, Draw.col(AMBER, 0.9f * boot), 0.5f);
            Draw.text(String.format(Locale.ROOT, "%d%%", Math.round(frac * 100f)), dx + 26f, y + 42f,
                    Draw.col(main, 0.85f * boot), 0.5f);
        }
        if (low || empty) {
            Draw.rect(x + 4f, y + 53f, x + 56f, y + 60f, Draw.col(RED, 0.25f * boot));
            Draw.text(empty ? "RELOAD" : "WARNING", x + 7f, y + 54f, Draw.col(RED, boot), 0.5f);
        } else {
            Draw.text("MAG " + g.cap(), x + 7f, y + 54f, Draw.col(pri, 0.7f * boot), 0.42f);
        }

        // boxed reserve readout with slash
        float bx = x + W - 50f, by = y + 33f;
        Draw.box(bx, by, 28f, 20f, WHITE, 0.06f * boot, 0.85f * boot);
        String res = g.infinite() ? "INF" : g.reserve() > 999 ? "999" : Integer.toString(g.reserve());
        Draw.textCenter(res, bx + 14f, by + 5f, Draw.col(g.reserve() == 0 && !g.infinite() ? RED : WHITE, boot),
                res.length() > 2 ? 0.9f : 1.2f);
        Draw.line(bx + 31f, by + 18f, bx + 37f, by + 3f, 1.3f, Draw.col(WHITE, 0.9f * boot));
        Draw.text(g.dummy() ? "CELLS" : "RESERVE", bx, by - 7f, Draw.col(pri, 0.8f * boot), 0.38f);
        int mags = g.cap() > 0 ? g.reserve() / g.cap() : 0;
        Draw.text(g.infinite() ? "UNLIMITED" : mags + " MAGS", bx, by + 22f, Draw.col(WHITE, 0.6f * boot), 0.38f);

        // projectile glyph
        float gx = x + W - 24f, gy = y + 8f;
        Draw.line(gx, gy + 6f, gx + 14f, gy + 2f, 2.4f, Draw.col(WHITE, 0.55f * boot));
        Draw.line(gx + 14f, gy + 2f, gx + 18f, gy + 1f, 1.4f, Draw.col(AMBER, 0.8f * boot));
        Draw.line(gx - 1f, gy + 7f, gx + 2f, gy + 6f, 1.4f, Draw.col(pri, 0.8f * boot));

        // shot pulse on the frame
        if (fl > 0f) chamfer(x - 1f, y - 1f, W + 2f, H + 2f, 10f, AMBER, 0.9f * fl * boot);
    }
}
