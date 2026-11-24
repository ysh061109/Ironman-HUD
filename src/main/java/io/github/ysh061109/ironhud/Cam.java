package io.github.ysh061109.ironhud;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Camera basis for projecting world positions onto the GUI. */
public final class Cam {
    public final Vec3 pos, fwd, right, up;
    public final double tanV, tanH;
    public final int w, h;

    private Cam(Vec3 pos, Vec3 fwd, Vec3 right, Vec3 up, double tanV, double tanH, int w, int h) {
        this.pos = pos;
        this.fwd = fwd;
        this.right = right;
        this.up = up;
        this.tanV = tanV;
        this.tanH = tanH;
        this.w = w;
        this.h = h;
    }

    public static Cam of(Minecraft mc, int w, int h) {
        Camera c = mc.gameRenderer.getMainCamera();
        Vector3f f = c.getLookVector();
        Vector3f u = c.getUpVector();
        Vector3f l = c.getLeftVector();
        double vfov = mc.options.fov().get() / (double) Math.max(1f, HudState.zoomCur);
        double tanV = Math.tan(Math.toRadians(vfov) * 0.5);
        double tanH = tanV * w / (double) h;
        return new Cam(c.getPosition(),
                new Vec3(f.x(), f.y(), f.z()),
                new Vec3(-l.x(), -l.y(), -l.z()),
                new Vec3(u.x(), u.y(), u.z()),
                tanV, tanH, w, h);
    }

    /** @return false when the point is behind the camera. */
    public boolean project(Vec3 p, float[] out) {
        Vec3 d = p.subtract(pos);
        double z = d.dot(fwd);
        if (z < 0.05) return false;
        double x = d.dot(right), y = d.dot(up);
        out[0] = (float) (w * 0.5 + x / z / tanH * w * 0.5);
        out[1] = (float) (h * 0.5 - y / z / tanV * h * 0.5);
        return true;
    }

    public boolean onScreen(float[] xy, float margin) {
        return xy[0] > margin && xy[0] < w - margin && xy[1] > margin && xy[1] < h - margin;
    }
}
