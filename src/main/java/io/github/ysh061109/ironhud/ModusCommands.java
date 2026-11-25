package io.github.ysh061109.ironhud;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** /modus ... client commands. */
public final class ModusCommands {
    private ModusCommands() {}

    private static void msg(CommandSourceStack src, String s) {
        src.sendSystemMessage(Component.literal("[Modus] " + s));
    }

    public static void register(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("modus")
                .then(Commands.literal("say").then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(c -> {
                            Modus.sayText(StringArgumentType.getString(c, "text"), false);
                            return 1;
                        })))
                .then(Commands.literal("ask").then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(c -> {
                            Modus.submitText(StringArgumentType.getString(c, "text"));
                            return 1;
                        })))
                .then(Commands.literal("install").executes(c -> {
                    msg(c.getSource(), "Installing offline voice recognition (Vosk + small model) into config/modus ...");
                    Mic.install(Modus::chatInfo);
                    Piper.install(Modus::chatInfo);
                    return 1;
                }))
                .then(Commands.literal("voice").then(Commands.literal("install").executes(c -> {
                    msg(c.getSource(), "Downloading the neural voice (Piper + " + Piper.voiceName() + ") into config/modus/piper ...");
                    Piper.install(Modus::chatInfo);
                    return 1;
                })))
                .then(Commands.literal("mic")
                        .then(Commands.literal("on").executes(c -> {
                            HudConfig.MIC_ENABLED.set(true);
                            Mic.start();
                            msg(c.getSource(), "Microphone: " + Mic.status);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(c -> {
                            HudConfig.MIC_ENABLED.set(false);
                            Mic.stop();
                            msg(c.getSource(), "Microphone off.");
                            return 1;
                        })))
                .then(Commands.literal("lang")
                        .then(Commands.literal("en").executes(c -> setLang(c.getSource(), HudConfig.VoiceLang.EN)))
                        .then(Commands.literal("ko").executes(c -> setLang(c.getSource(), HudConfig.VoiceLang.KO))))
                .then(Commands.literal("test").executes(c -> {
                    Modus.sayText(HudConfig.VOICE_LANG.get() == HudConfig.VoiceLang.KO
                            ? "음성 점검 중입니다. 들리십니까?" : "Voice check. Can you hear me?", false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(c -> {
                    msg(c.getSource(), "TTS: " + Voice.backend() + " / " + Voice.status + " (neural installed: " + Piper.ready() + ")");
                    msg(c.getSource(), "Mic: " + Mic.status + " (installed: " + Mic.installed() + ")");
                    return 1;
                })));
    }

    private static int setLang(CommandSourceStack src, HudConfig.VoiceLang l) {
        HudConfig.VOICE_LANG.set(l);
        msg(src, "Language set to " + l + ". Run /modus install again if the speech model for this language is missing.");
        Modus.prewarm();
        return 1;
    }
}
