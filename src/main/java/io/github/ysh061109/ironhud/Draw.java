package io.github.ysh061109.ironhud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Immediate-mode 2D drawing. All shapes of a frame go into ONE quad batch (a single draw call);
 * text is queued and drawn afterwards, so text always sits on top of shapes.
 */
public final class Draw {
    private record Txt(String s, float x, float y, int argb, float scale) {}

    private static BufferBuilder bb;
    private static Matrix4f mat = new Matrix4f();
    private static Font font;
    private static final List<Txt> TEXTS = new ArrayList<>();

    private Draw() {}

    public static void begin(GuiGraphics g, Font f) {
        font = f;
        mat = new Matrix4f(g.pose().last().pose());
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        TEXTS.clear();
    }

    public static void end(GuiGraphics g) {
        MeshData md = bb.build();
        bb = null;
        if (md != null) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(md);
        }
        for (Txt t : TEXTS) {
            g.pose().pushPose();
            g.pose().translate(t.x, t.y, 0f);
            g.pose().scale(t.scale, t.scale, 1f);
            g.drawString(font, t.s, 0, 0, t.argb, false);
            g.pose().popPose();
        }
        TEXTS.clear();
    }

    // ----------------------------------------------------------------- colors

    public static int col(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    // --------------------------------------------------------------- shapes

    private static void v(float x, float y, int c) {
        bb.addVertex(mat, x, y, 0f).setColor(c);
    }

    public static void rect(float x0, float y0, float x1, float y1, int argb) {
        v(x0, y0, argb);
        v(x0, y1, argb);
        v(x1, y1, argb);
        v(x1, y0, argb);
    }

    public static void gradV(float x0, float y0, float x1, float y1, int top, int bottom) {
        v(x0, y0, top);
        v(x0, y1, bottom);
        v(x1, y1, bottom);
        v(x1, y0, top);
    }

    public static void gradH(float x0, float y0, float x1, float y1, int left, int right) {
        v(x0, y0, left);
        v(x0, y1, left);
        v(x1, y1, right);
        v(x1, y0, right);
    }

    public static void box(float x, float y, float w, float h, int rgb, float fillA, float borderA) {
        if (fillA > 0f) rect(x, y, x + w, y + h, col(rgb, fillA));
        int b = col(rgb, borderA);
        rect(x, y, x + w, y + 1, b);
        rect(x, y + h - 1, x + w, y + h, b);
        rect(x, y + 1, x + 1, y + h - 1, b);
        rect(x + w - 1, y + 1, x + w, y + h - 1, b);
    }

    public static void line(float x0, float y0, float x1, float y1, float th, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-4f) return;
        float nx = -dy / len * th * 0.5f, ny = dx / len * th * 0.5f;
        v(x0 + nx, y0 + ny, argb);
        v(x0 - nx, y0 - ny, argb);
        v(x1 - nx, y1 - ny, argb);
        v(x1 + nx, y1 + ny, argb);
    }

    /** Ring segment. Angles in degrees, screen space (0 = right, 90 = down). */
    /** Cheaper rendering mode (coarser arcs, fewer decorative dashes). */
    public static boolean lod = false;

    public static void arc(float cx, float cy, float r0, float r1, float startDeg, float sweepDeg, int argb) {
        if (sweepDeg <= 0.01f) return;
        float step = r1 < 14f ? 15f : (lod ? 10f : 6f);
        int segs = Math.max(1, (int) Math.ceil(sweepDeg / step));
        float pc = 0, ps = 0;
        for (int i = 0; i <= segs; i++) {
            double a = Math.toRadians(startDeg + sweepDeg * i / segs);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            if (i > 0) {
                v(cx + pc * r0, cy + ps * r0, argb);
                v(cx + pc * r1, cy + ps * r1, argb);
                v(cx + cs * r1, cy + sn * r1, argb);
                v(cx + cs * r0, cy + sn * r0, argb);
            }
            pc = cs;
            ps = sn;
        }
    }

    public static void ring(float cx, float cy, float r, float th, int argb) {
        arc(cx, cy, r - th * 0.5f, r + th * 0.5f, 0f, 360f, argb);
    }

    /** n dashes around a circle, each covering 60% of its slot. */
    public static void dashedRing(float cx, float cy, float r, float th, float rotDeg, int n, int argb) {
        if (lod) n = Math.max(4, n / 2);
        float slot = 360f / n;
        for (int i = 0; i < n; i++) {
            arc(cx, cy, r - th * 0.5f, r + th * 0.5f, rotDeg + i * slot, slot * 0.6f, argb);
        }
    }

    public static void disc(float cx, float cy, float r, int argb) {
        arc(cx, cy, 0f, r, 0f, 360f, argb);
    }

    public static void diamond(float cx, float cy, float r, float th, int argb) {
        line(cx, cy - r, cx + r, cy, th, argb);
        line(cx + r, cy, cx, cy + r, th, argb);
        line(cx, cy + r, cx - r, cy, th, argb);
        line(cx - r, cy, cx, cy - r, th, argb);
    }

    /** Downward-pointing triangle outline whose tip is at (cx, cy). */
    public static void triDown(float cx, float cy, float s, float th, int argb) {
        line(cx - s, cy - s * 1.4f, cx + s, cy - s * 1.4f, th, argb);
        line(cx - s, cy - s * 1.4f, cx, cy, th, argb);
        line(cx + s, cy - s * 1.4f, cx, cy, th, argb);
    }

    /** Four corner brackets around (cx, cy) with half-size d and arm length arm. */
    public static void brackets(float cx, float cy, float d, float arm, float th, int argb) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                line(cx + sx * d, cy + sy * d, cx + sx * (d - arm), cy + sy * d, th, argb);
                line(cx + sx * d, cy + sy * d, cx + sx * d, cy + sy * (d - arm), th, argb);
            }
        }
    }

    // ----------------------------------------------------------------- text

    public static void text(String s, float x, float y, int argb, float scale) {
        TEXTS.add(new Txt(s, x, y, argb, scale));
    }

    public static float width(String s, float scale) {
        return font.width(s) * scale;
    }

    public static void textRight(String s, float xRight, float y, int argb, float scale) {
        text(s, xRight - width(s, scale), y, argb, scale);
    }

    public static void textCenter(String s, float cx, float y, int argb, float scale) {
        text(s, cx - width(s, scale) * 0.5f, y, argb, scale);
    }
}
