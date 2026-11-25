package io.github.ysh061109.ironhud;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
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

    public static final KeyMapping CHAT = new KeyMapping("key.ironhud.chat", GLFW.GLFW_KEY_M, CAT);
    public static final KeyMapping PTT = new KeyMapping("key.ironhud.ptt", GLFW.GLFW_KEY_V, CAT);
    public static final KeyMapping MULTI = new KeyMapping("key.ironhud.multilock", GLFW.GLFW_KEY_R, CAT);
    public static final KeyMapping POWER = new KeyMapping("key.ironhud.power", GLFW.GLFW_KEY_P, CAT);
    public static final KeyMapping DIAG = new KeyMapping("key.ironhud.diag", GLFW.GLFW_KEY_G, CAT);

    private boolean pttWasDown = false;
    private int pttStopTimer = -1;

    public IronHudMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(this::registerKeys);
        modBus.addListener(this::registerLayers);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onGuiLayerPre);
        NeoForge.EVENT_BUS.addListener(this::onFov);
        NeoForge.EVENT_BUS.addListener(this::onScroll);
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.Player e) -> Comms.receive(e));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> ModusCommands.register(e));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> Modus.onLogout());
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(ZOOM);
        event.register(WAYPOINT);
        event.register(SCAN);
        event.register(CHAT);
        event.register(PTT);
        event.register(MULTI);
        event.register(POWER);
        event.register(DIAG);
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
            while (MULTI.consumeClick()) {
                HudState.multiLock = !HudState.multiLock;
                SysLog.add(HudState.multiLock ? "MULTI-LOCK ENGAGED" : "MULTI-LOCK OFF");
            }
            while (POWER.consumeClick()) Power.cycle();
            while (DIAG.consumeClick()) {
                Diagnostics.open = !Diagnostics.open;
                Diagnostics.openedAt = HudState.age;
            }
        } else {
            while (WAYPOINT.consumeClick()) { /* swallow */ }
            while (SCAN.consumeClick()) { /* swallow */ }
            HudState.zoomHeld = false;
        }
        while (CHAT.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ModusChatScreen());
        }
        handlePtt(mc);
        HudState.tick(mc);
    }

    /** Push-to-talk: starts the recognizer on demand and stops it again if the mic is not set to always-on. */
    private void handlePtt(Minecraft mc) {
        boolean down = PTT.isDown() && mc.screen == null;
        if (down && !pttWasDown && !Mic.isRunning()) {
            if (Mic.installed()) Mic.start();
            else Modus.sysLater("Voice input is not installed. Type /modus install");
        }
        Mic.setPushToTalk(down);
        if (!down && pttWasDown && !HudConfig.MIC_ENABLED.get()) pttStopTimer = 50;
        if (pttStopTimer > 0 && --pttStopTimer == 0) Mic.stop();
        pttWasDown = down;
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
        if (HudState.enabled && (HudState.zoomHeld || HudState.zoomLatched)) {
            HudState.adjustZoom(event.getScrollDeltaY());
            event.setCanceled(true);
        }
    }
}
