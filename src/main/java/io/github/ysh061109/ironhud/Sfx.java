package io.github.ysh061109.ironhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * HUD sound effects. They are synthesized originals (see tools/gen_sounds.py) and are played as
 * client-side UI sounds, so nothing needs to be registered and the server never knows about them.
 */
public enum Sfx {
    BOOT("boot"),
    POWER_DOWN("power_down"),
    ALERT("alert"),
    CRITICAL("critical"),
    BREACH("breach"),
    LOCK("lock"),
    KILL("kill"),
    TICK("tick"),
    COLLISION("collision"),
    PING("ping");

    private final SoundEvent event;

    Sfx(String name) {
        this.event = SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(IronHudMod.MODID, name));
    }

    public void play() {
        play(1.0f);
    }

    public void play(float pitch) {
        if (!HudConfig.SOUNDS.get()) return;
        float vol = HudConfig.VOLUME.get().floatValue();
        if (vol <= 0f) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, vol));
    }
}
