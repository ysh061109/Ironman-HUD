package io.github.ysh061109.ironhud;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client-only mod: the server never loads this class. */
@Mod(value = IronHudMod.MODID, dist = Dist.CLIENT)
public class IronHudMod {
    public static final String MODID = "ironhud";
    private static final String CAT = "key.categories.ironhud";

    public static final KeyMapping TOGGLE = new KeyMapping("key.ironhud.toggle", GLFW.GLFW_KEY_H, CAT);
    public static final KeyMapping ZOOM = new KeyMapping("key.ironhud.zoom", GLFW.GLFW_KEY_Z, CAT);
    public static final KeyMapping WAYPOINT = new KeyMapping("key.ironhud.waypoint", GLFW.GLFW_KEY_K, CAT);
    public static final KeyMapping SCAN = new KeyMapping("key.ironhud.scan", GLFW.GLFW_KEY_X, CAT);

    public IronHudMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(this::registerKeys);
        modBus.addListener(this::registerLayers);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onGuiLayerPre);
        NeoForge.EVENT_BUS.addListener(this::onFov);
        NeoForge.EVENT_BUS.addListener(this::onScroll);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(ZOOM);
        event.register(WAYPOINT);
        event.register(SCAN);
    }

    private void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MODID, "hud"), HudRenderer::render);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (TOGGLE.consumeClick()) {
            HudState.enabled = !HudState.enabled;
            if (HudState.enabled) {
                HudState.age = 0; // replay the boot sequence
            } else {
                Sfx.POWER_DOWN.play();
            }
        }
        if (HudState.enabled) {
            while (WAYPOINT.consumeClick()) HudState.toggleWaypoint(mc);
            while (SCAN.consumeClick()) HudState.startScan();
            HudState.zoomHeld = ZOOM.isDown() && mc.screen == null;
        } else {
            while (WAYPOINT.consumeClick()) { /* swallow */ }
            while (SCAN.consumeClick()) { /* swallow */ }
            HudState.zoomHeld = false;
        }
        HudState.tick(mc);
    }

    /** Hide the vanilla health / armor / hunger bars while the HUD replaces them. */
    private void onGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!HudConfig.HIDE_VANILLA_BARS.get() || !HudState.active(mc)
                || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        ResourceLocation n = event.getName();
        if (n.equals(VanillaGuiLayers.PLAYER_HEALTH)
                || n.equals(VanillaGuiLayers.ARMOR_LEVEL)
                || n.equals(VanillaGuiLayers.FOOD_LEVEL)) {
            event.setCanceled(true);
        }
    }

    /** Optical zoom: divides the world FOV (not the hand FOV) while the zoom key is held. */
    private void onFov(ViewportEvent.ComputeFov event) {
        if (HudState.enabled && HudState.zoomCur > 1.001f && event.usedConfiguredFov()) {
            event.setFOV(event.getFOV() / HudState.zoomCur);
        }
    }

    private void onScroll(InputEvent.MouseScrollingEvent event) {
        if (HudState.enabled && HudState.zoomHeld) {
            HudState.adjustZoom(event.getScrollDeltaY());
            event.setCanceled(true);
        }
    }
}
