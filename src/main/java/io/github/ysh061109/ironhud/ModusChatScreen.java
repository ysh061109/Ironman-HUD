package io.github.ysh061109.ironhud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Dedicated HUD chat line: type to Modus. The wake word is optional here. */
public class ModusChatScreen extends Screen {
    private EditBox box;

    public ModusChatScreen() {
        super(Component.literal("Modus"));
    }

    @Override
    protected void init() {
        int w = 320;
        box = new EditBox(this.font, this.width / 2 - w / 2 + 52, this.height - 44, w - 60, 12, Component.literal("Modus"));
        box.setBordered(false);
        box.setMaxLength(160);
        box.setTextColor(0xFFDFF9FF);
        box.setHint(Component.literal(HudConfig.VOICE_LANG.get() == HudConfig.VoiceLang.KO
                ? "Modus에게 말하기... (예: 상태 보고)" : "Talk to Modus... (e.g. status report)"));
        addRenderableWidget(box);
        setInitialFocus(box);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String t = box.getValue().trim();
            if (!t.isEmpty()) Modus.submitText(t);
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int pri = HudConfig.primary();
        int w = 320, x = this.width / 2 - w / 2, y = this.height - 48;
        g.fill(x, y, x + w, y + 20, 0xAA000814);
        g.fill(x, y, x + w, y + 1, 0xFF000000 | pri);
        g.fill(x, y + 19, x + w, y + 20, 0x66000000 | pri);
        g.fill(x, y, x + 1, y + 20, 0xFF000000 | pri);
        g.drawString(this.font, "MODUS >", x + 6, y + 6, 0xFF000000 | pri, false);

        List<Modus.Msg> log = Modus.LOG;
        int shown = Math.min(6, log.size());
        for (int i = 0; i < shown; i++) {
            Modus.Msg m = log.get(log.size() - shown + i);
            int ly = y - 6 - (shown - i) * 11;
            String line = (m.modus() ? "MODUS: " : "YOU: ") + m.text();
            g.drawString(this.font, this.font.plainSubstrByWidth(line, w), x, ly,
                    m.modus() ? (0xFF000000 | HudConfig.accent()) : 0xFFB8C7D1, false);
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
