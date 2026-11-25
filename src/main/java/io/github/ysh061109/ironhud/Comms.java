package io.github.ysh061109.ironhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

import java.util.UUID;

/** Incoming "video call" window: shows the sender's face and message when another player chats. */
public final class Comms {
    private static UUID sender;
    private static String name = "";
    private static String message = "";
    private static int born = -1000;

    private Comms() {}

    public static void receive(ClientChatReceivedEvent.Player e) {
        Minecraft mc = Minecraft.getInstance();
        if (!HudState.enabled || mc.player == null) return;
        UUID id = e.getSender();
        if (id.equals(mc.player.getUUID())) return;
        sender = id;
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
        name = info != null ? info.getProfile().getName() : "UNKNOWN";
        String m = e.getPlayerChatMessage().signedContent();
        message = m == null ? e.getMessage().getString() : m;
        born = HudState.age;
        Alerts.fire("comm", "INCOMING TRANSMISSION", name.toUpperCase(java.util.Locale.ROOT), Alerts.Level.INFO, 40, 20, Sfx.ALERT);
        Modus.onTransmission(name);
    }

    public static boolean active() {
        return HudState.age - born < 220;
    }

    public static float x(int w) {
        return w / 2f + 96f;
    }

    public static float y(int h) {
        return h - 118f;
    }

    /** Frame and text (batched shapes). */
    public static void draw(int w, int h, float t, int pri, int acc) {
        if (!active()) return;
        float age = HudState.age - born;
        float vis = Math.min(Math.min(age / 6f, 1f), Math.min((220f - age) / 12f, 1f));
        float x = x(w), y = y(h), bw = 170f, bh = 46f;
        Draw.rect(x, y, x + bw, y + bh, Draw.col(0x000000, 0.5f * vis));
        Draw.box(x, y, bw, bh, pri, 0f, 0.8f * vis);
        Draw.box(x + 5f, y + 5f, 26f, 26f, acc, 0.15f * vis, 0.9f * vis);
        float blink = 0.5f + 0.5f * (float) Math.sin(t * 0.4f);
        Draw.text("LIVE", x + 6f, y + 34f, Draw.col(0xFF3B30, blink * vis), 0.6f);
        Draw.text(name.toUpperCase(java.util.Locale.ROOT), x + 38f, y + 5f, Draw.col(acc, vis), 0.8f);
        String m = message.length() > 46 ? message.substring(0, 46) + "..." : message;
        Draw.text(m, x + 38f, y + 17f, Draw.col(0xDFF9FF, 0.9f * vis), 0.7f);
        if (message.length() > 46) {
            String m2 = message.substring(46);
            Draw.text(m2.length() > 46 ? m2.substring(0, 46) + "..." : m2, x + 38f, y + 27f, Draw.col(0xDFF9FF, 0.9f * vis), 0.7f);
        }
    }

    /** The face texture itself (drawn after the shape batch). */
    public static void drawFace(GuiGraphics g, int w, int h) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() == null || sender == null ? null : mc.getConnection().getPlayerInfo(sender);
        if (info == null) return;
        PlayerFaceRenderer.draw(g, info.getSkin(), (int) x(w) + 6, (int) y(h) + 6, 24);
    }
}
