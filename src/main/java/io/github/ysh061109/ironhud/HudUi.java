package io.github.ysh061109.ironhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Suit-styled hotbar (fully functional: it mirrors the real selected slot) and inventory screen skin. */
public final class HudUi {
    private static final int PITCH = 20;

    private HudUi() {}

    public static boolean hotbarActive(Minecraft mc) {
        return HudConfig.CUSTOM_HOTBAR.get() && HudState.active(mc) && mc.player != null && !mc.player.isSpectator();
    }

    private static float hx(int w) {
        return w / 2f - PITCH * 4.5f + 1f;
    }

    /** Shapes, drawn inside the shared batch. */
    public static void hotbarFrame(Minecraft mc, int w, int h, float t, float boot, int pri, int acc) {
        if (!hotbarActive(mc)) return;
        LocalPlayer p = mc.player;
        float x0 = hx(w), y = h - 23f;
        // backing strip with angled ends
        float bx0 = x0 - 30f, bx1 = x0 + PITCH * 9 + 4f;
        Draw.rect(bx0, y - 3f, bx1, y + 21f, Draw.col(0x02101A, 0.55f * boot));
        Draw.line(bx0, y - 3f, bx1, y - 3f, 1f, Draw.col(pri, 0.5f * boot));
        for (int i = 0; i < 9; i++) {
            float x = x0 + i * PITCH;
            boolean sel = i == p.getInventory().selected;
            int rgb = sel ? acc : pri;
            Draw.rect(x, y, x + 18f, y + 18f, Draw.col(rgb, sel ? 0.22f : 0.07f));
            Draw.box(x - 0.5f, y - 0.5f, 19f, 19f, rgb, 0f, (sel ? 1f : 0.45f) * boot);
            Draw.text(Integer.toString(i + 1), x + 1.5f, y + 1f, Draw.col(0xDFF9FF, sel ? 0.9f : 0.4f), 0.4f);
            if (sel) {
                Draw.rect(x - 1f, y + 20f, x + 19f, y + 21.5f, Draw.col(acc, 1f));
                Draw.triDown(x + 9f, y - 6f, 2.5f, 1f, Draw.col(acc, 0.9f * boot));
            }
        }
        // off-hand cell
        float ox = x0 - 26f;
        Draw.rect(ox, y, ox + 18f, y + 18f, Draw.col(pri, 0.07f));
        Draw.box(ox - 0.5f, y - 0.5f, 19f, 19f, pri, 0f, 0.45f * boot);
        Draw.text("OFF", ox + 2f, y + 1f, Draw.col(0xDFF9FF, 0.4f), 0.4f);
        // held item name
        ItemStack held = p.getInventory().getSelected();
        if (!held.isEmpty()) {
            String n = held.getHoverName().getString().toUpperCase(Locale.ROOT);
            Draw.textCenter(n, w / 2f, y - 11f, Draw.col(0xDFF9FF, 0.8f * boot), 0.55f);
        }
    }

    /** Item icons, count and durability, drawn after the batch has been flushed. */
    public static void hotbarItems(GuiGraphics g, Minecraft mc, int w, int h) {
        if (!hotbarActive(mc)) return;
        LocalPlayer p = mc.player;
        float x0 = hx(w);
        int y = h - 23;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().items.get(i);
            if (st.isEmpty()) continue;
            int x = (int) (x0 + i * PITCH) + 1;
            g.renderItem(st, x, y + 1);
            g.renderItemDecorations(mc.font, st, x, y + 1);
        }
        ItemStack off = p.getInventory().offhand.get(0);
        if (!off.isEmpty()) {
            int x = (int) (x0 - 26f) + 1;
            g.renderItem(off, x, y + 1);
            g.renderItemDecorations(mc.font, off, x, y + 1);
        }
    }

    /** Overlay for container screens: dark glass panel, brackets and per-slot cells. */
    public static void skinScreen(GuiGraphics g, AbstractContainerScreen<?> scr) {
        Minecraft mc = Minecraft.getInstance();
        if (!HudConfig.SKIN_SCREENS.get() || !HudState.enabled || scr instanceof CreativeModeInventoryScreen) return;
        int pri = HudConfig.primary(), acc = HudConfig.accent();
        int l = scr.getGuiLeft(), tp = scr.getGuiTop(), w = scr.getXSize(), h = scr.getYSize();
        float t = (mc.player == null ? 0 : mc.player.tickCount) + mc.getTimer().getGameTimeDeltaPartialTick(false);
        Draw.begin(g, mc.font);
        Draw.rect(l, tp, l + w, tp + h, Draw.col(0x02101A, 0.62f));
        // frame with corner brackets
        int fr = Draw.col(pri, 0.9f);
        float a = 14f;
        Draw.line(l - 3, tp - 3, l - 3 + a, tp - 3, 1.4f, fr);
        Draw.line(l - 3, tp - 3, l - 3, tp - 3 + a, 1.4f, fr);
        Draw.line(l + w + 3, tp - 3, l + w + 3 - a, tp - 3, 1.4f, fr);
        Draw.line(l + w + 3, tp - 3, l + w + 3, tp - 3 + a, 1.4f, fr);
        Draw.line(l - 3, tp + h + 3, l - 3 + a, tp + h + 3, 1.4f, fr);
        Draw.line(l - 3, tp + h + 3, l - 3, tp + h + 3 - a, 1.4f, fr);
        Draw.line(l + w + 3, tp + h + 3, l + w + 3 - a, tp + h + 3, 1.4f, fr);
        Draw.line(l + w + 3, tp + h + 3, l + w + 3, tp + h + 3 - a, 1.4f, fr);
        // header strip
        Draw.rect(l, tp - 13f, l + w, tp - 4f, Draw.col(pri, 0.12f));
        Draw.line(l, tp - 4f, l + w, tp - 4f, 1f, Draw.col(pri, 0.6f));
        Draw.text("SUIT INTERFACE // " + scr.getTitle().getString().toUpperCase(Locale.ROOT), l + 4f, tp - 11f,
                Draw.col(pri, 1f), 0.7f);
        float sweep = (t * 1.5f) % (h + 20f);
        Draw.rect(l, tp + sweep - 10f, l + w, tp + sweep - 9f, Draw.col(pri, 0.10f));
        for (Slot s : scr.getMenu().slots) {
            float x = l + s.x - 1f, y = tp + s.y - 1f;
            Draw.rect(x, y, x + 18f, y + 18f, Draw.col(0x000000, 0.35f));
            Draw.box(x, y, 18f, 18f, pri, 0f, 0.5f);
        }
        Draw.end(g);
    }
}
