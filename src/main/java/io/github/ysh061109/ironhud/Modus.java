package io.github.ysh061109.ironhud;

import io.github.ysh061109.ironhud.Alerts.Level;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Modus, the suit AI. It talks (proactive callouts + replies), listens (wake word through the microphone, or the
 * dedicated HUD chat) and understands a set of English / Korean commands.
 */
public final class Modus {
    public record Msg(boolean modus, String text, int born) {}

    public static final List<Msg> LOG = new ArrayList<>();
    private static final List<String[]> PHRASES = new ArrayList<>();
    private static final Map<String, Integer> THROTTLE = new HashMap<>();
    private static long awakeUntil = 0L;
    private static LocalPlayer greeted = null;

    private static String[] ph(String en, String ko) {
        String[] a = {en, ko};
        PHRASES.add(a);
        return a;
    }

    // ------------------------------------------------------------ phrases
    private static final String[] GREET_MORNING = ph("Good morning, {sir}. All systems online.", "좋은 아침입니다, {sir}. 모든 시스템이 정상 가동 중입니다.");
    private static final String[] GREET_DAY = ph("Good afternoon, {sir}. All systems online.", "안녕하십니까, {sir}. 모든 시스템이 정상 가동 중입니다.");
    private static final String[] GREET_EVE = ph("Good evening, {sir}. All systems online.", "좋은 저녁입니다, {sir}. 모든 시스템이 정상 가동 중입니다.");
    private static final String[] GREET_NIGHT = ph("Working late, {sir}? All systems online.", "늦은 시간이군요, {sir}. 모든 시스템이 정상 가동 중입니다.");
    private static final String[] HULL_CRIT = ph("Hull integrity critical, {sir}. I strongly advise retreat.", "기체 손상이 심각합니다, {sir}. 즉시 후퇴하실 것을 권장합니다.");
    private static final String[] HULL_50 = ph("Structural damage detected.", "구조 손상이 감지되었습니다.");
    private static final String[] BIGHIT = ph("Significant impact detected.", "강한 충격이 감지되었습니다.");
    private static final String[] POWER_LOW = ph("Power reserves are low, {sir}. Refueling is recommended.", "전력이 부족합니다, {sir}. 재충전을 권장합니다.");
    private static final String[] POWER_OUT = ph("Power cells depleted. Refuel immediately.", "전력이 고갈되었습니다. 즉시 재충전하십시오.");
    private static final String[] THR_LOW = ph("Thruster fuel is low.", "추진기 연료가 부족합니다.");
    private static final String[] FIRE = ph("Thermal warning. Hull temperature rising.", "열 경고. 기체 온도가 상승하고 있습니다.");
    private static final String[] O2 = ph("Oxygen levels low.", "산소 수치가 낮습니다.");
    private static final String[] LAVA = ph("Lava proximity detected.", "용암 근접이 감지되었습니다.");
    private static final String[] CREEPER = ph("Explosive ignition detected. Take cover!", "폭발물 점화가 감지되었습니다. 엄폐하십시오!");
    private static final String[] PROX = ph("Hostile in close proximity.", "적이 근접해 있습니다.");
    private static final String[] COLL = ph("Collision warning!", "충돌 경고!");
    private static final String[] TERRAIN = ph("Terrain impact imminent. Pull up!", "지면 충돌이 임박했습니다. 상승하십시오!");
    private static final String[] FATAL = ph("Fatal impact predicted!", "치명적인 충돌이 예측됩니다!");
    private static final String[] KILL = ph("Target neutralized.", "목표 제거 완료.");
    private static final String[] SCAN_START = ph("Initiating wide-area scan.", "광역 스캔을 시작합니다.");
    private static final String[] WP_SET = ph("Waypoint set.", "웨이포인트를 설정했습니다.");
    private static final String[] WP_CLR = ph("Waypoint cleared.", "웨이포인트를 해제했습니다.");
    private static final String[] LOCK_ON = ph("Multi-target lock engaged.", "다중 락온을 시작합니다.");
    private static final String[] LOCK_OFF = ph("Multi-target lock disengaged.", "다중 락온을 해제합니다.");
    private static final String[] YES = ph("Yes, {sir}?", "네, {sir}?");
    private static final String[] SORRY = ph("I'm sorry, {sir}, I didn't catch that.", "죄송합니다, {sir}. 알아듣지 못했습니다.");
    private static final String[] THANKS = ph("Always a pleasure, {sir}.", "별말씀을요, {sir}.");
    private static final String[] HELLO = ph("At your service, {sir}.", "무엇이든 말씀하십시오, {sir}.");
    private static final String[] HELP = ph("I can run scans, report status, mark waypoints, route power, zoom, lock targets and run diagnostics, {sir}.", "스캔, 상태 보고, 웨이포인트, 전력 배분, 줌, 락온, 진단을 도와드릴 수 있습니다, {sir}.");
    private static final String[] ZOOM_ON = ph("Optical zoom engaged.", "광학 줌을 활성화합니다.");
    private static final String[] ZOOM_OFF = ph("Optical zoom disengaged.", "광학 줌을 해제합니다.");
    private static final String[] DIAG_START = ph("Running full diagnostics.", "전체 진단을 실행합니다.");
    private static final String[] DIAG_OK = ph("All systems nominal.", "모든 시스템이 정상입니다.");
    private static final String[] PWR_BAL = ph("Power balanced across all systems.", "전력을 균등하게 배분했습니다.");
    private static final String[] PWR_SENS = ph("Rerouting power to sensors.", "센서에 전력을 집중합니다.");
    private static final String[] PWR_OPT = ph("Rerouting power to optics.", "광학계에 전력을 집중합니다.");
    private static final String[] PWR_ANL = ph("Rerouting power to analysis.", "분석 모듈에 전력을 집중합니다.");
    private static final String[] HUD_OFF = ph("Powering down display.", "디스플레이를 종료합니다.");
    private static final String[] HUD_ON = ph("Display online.", "디스플레이를 켭니다.");
    private static final String[] NO_HOSTILE = ph("No hostile contacts, {sir}.", "적대 신호는 없습니다, {sir}.");
    private static final String[] OK = ph("Done, {sir}.", "완료했습니다, {sir}.");
    private static final String[][] BREACH = new String[4][];
    private static final String[][] ARMOR_LOW = new String[4][];

    static {
        String[] en = {"helmet", "chestplate", "leggings", "boots"};
        String[] ko = {"투구", "흉갑", "레깅스", "부츠"};
        for (int i = 0; i < 4; i++) {
            BREACH[i] = ph("Armor breach. " + en[i] + " destroyed.", "장갑 파손. " + ko[i] + " 파괴되었습니다.");
            ARMOR_LOW[i] = ph("Warning. " + en[i] + " integrity below twenty five percent.", "경고. " + ko[i] + " 내구도가 25퍼센트 미만입니다.");
        }
    }

    private Modus() {}

    // ------------------------------------------------------------ helpers
    private static boolean ko() {
        return HudConfig.VOICE_LANG.get() == HudConfig.VoiceLang.KO;
    }

    private static String honor() {
        return ko() ? HudConfig.HONORIFIC_KO.get() : HudConfig.HONORIFIC_EN.get();
    }

    private static String pick(String[] p) {
        return (ko() ? p[1] : p[0]).replace("{sir}", honor());
    }

    private static boolean throttle(String key, int ticks) {
        Integer last = THROTTLE.get(key);
        if (last != null && HudState.age - last < ticks) return false;
        THROTTLE.put(key, HudState.age);
        return true;
    }

    public static void say(String[] p, boolean urgent) {
        sayText(pick(p), urgent);
    }

    public static void sayText(String text, boolean urgent) {
        Minecraft.getInstance().execute(() -> {
            LOG.add(new Msg(true, text, HudState.age));
            while (LOG.size() > 40) LOG.remove(0);
        });
        Voice.speak(text, urgent);
    }

    private static void userSaid(String text) {
        LOG.add(new Msg(false, text, HudState.age));
        while (LOG.size() > 40) LOG.remove(0);
    }

    public static void sysLater(String s) {
        Minecraft.getInstance().execute(() -> SysLog.add(s));
    }

    public static void chatInfo(String s) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.getChat().addMessage(Component.literal("[Modus] " + s)));
    }

    // ------------------------------------------------------------- lifecycle

    public static void onWorldJoin(LocalPlayer p) {
        if (greeted == p) return;
        greeted = p;
        prewarm();
        if (HudConfig.MIC_ENABLED.get()) Mic.start();
    }

    /** Pre-renders every fixed phrase in the background so alerts play without delay. */
    public static void prewarm() {
        Voice.init();
        List<String> texts = new ArrayList<>();
        for (String[] a : PHRASES) texts.add(pick(a));
        Voice.prewarm(texts);
    }

    public static void onLogout() {
        greeted = null;
        Mic.stop();
        Voice.stop();
    }

    // ------------------------------------------------------ proactive voice

    public static void onAlert(String key, String title, String sub, Level lv) {
        boolean urgent = lv == Level.CRIT;
        switch (key) {
            case "boot" -> greet();
            case "hull" -> say(HULL_CRIT, true);
            case "hull50" -> say(HULL_50, false);
            case "bighit" -> say(BIGHIT, true);
            case "power" -> say(title.contains("DEPLETED") ? POWER_OUT : POWER_LOW, urgent);
            case "thr" -> say(THR_LOW, false);
            case "fire" -> {
                if (throttle("fire", 200)) say(FIRE, true);
            }
            case "o2" -> {
                if (throttle("o2", 160)) say(O2, urgent);
            }
            case "lava" -> say(LAVA, false);
            case "creeper" -> {
                if (throttle("creeper", 100)) say(CREEPER, true);
            }
            case "prox" -> say(PROX, false);
            case "coll" -> {
                if (throttle("coll", 60)) say(title.contains("FATAL") ? FATAL : title.contains("TERRAIN") ? TERRAIN : COLL, true);
            }
            case "kill" -> {
                if (throttle("kill", 100)) say(KILL, false);
            }
            case "wp" -> say(title.contains("CLEARED") ? WP_CLR : WP_SET, false);
            case "scan" -> say(SCAN_START, false);
            default -> {
                if (key.startsWith("brk") && key.length() == 4) say(BREACH[key.charAt(3) - '0'], true);
                else if (key.startsWith("low") && key.length() == 4) say(ARMOR_LOW[key.charAt(3) - '0'], false);
            }
        }
    }

    private static void greet() {
        int h = LocalTime.now().getHour();
        say(h >= 5 && h < 12 ? GREET_MORNING : h < 17 ? GREET_DAY : h < 22 ? GREET_EVE : GREET_NIGHT, false);
    }

    public static void onNewTarget(LivingEntity e) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if (!throttle("tgt:" + path, 1200)) return;
        if (ko()) sayText(e.getDisplayName().getString() + " 감지.", false);
        else sayText(path.replace('_', ' ') + " detected.", false);
    }

    public static void onTransmission(String name) {
        sayText(ko() ? name + "님으로부터 통신이 수신되었습니다." : "Incoming transmission from " + name + ".", false);
    }

    public static void afterScan(int hostiles, int total) {
        if (hostiles == 0) {
            say(NO_HOSTILE, false);
        } else {
            sayText(ko() ? "스캔 완료. 적대 신호 " + hostiles + "개, 전체 " + total + "개 식별."
                    : "Scan complete. " + hostiles + " hostile" + (hostiles == 1 ? "" : "s") + " among " + total + " contacts.", false);
        }
    }

    // ----------------------------------------------------------- wake word

    private static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static final Set<String> NOT_WAKE = Set.of("mods", "mode", "model", "models", "modes", "mod");
    private static final Set<String> ALIASES = Set.of("mostus", "moedus", "modess", "modis", "moodus", "mowdus",
            "moldus", "modos", "modas", "moduss", "moduz", "modeus", "mohdus", "modust");
    private static final String[] KO_WAKE = {"모더스", "모두스", "모듀스", "모다스", "모도스", "모더쓰"};

    private static boolean isWake(String w) {
        if (w.length() < 3 || NOT_WAKE.contains(w)) return false;
        if (w.startsWith("modus") || ALIASES.contains(w)) return true;
        for (String k : KO_WAKE) {
            if (w.startsWith(k) || lev(w, k) <= 1) return true;
        }
        return lev(w, "modus") <= 1;
    }

    /** @return the text after the wake word, or null if the wake word is absent. */
    public static String stripWake(String text) {
        String[] tok = text.toLowerCase(Locale.ROOT).trim().split("\\s+");
        for (int i = 0; i < tok.length; i++) {
            String w = tok[i].replaceAll("[^\\p{L}\\p{N}]", "");
            int used = 0;
            if (isWake(w)) used = 1;
            else if (i + 1 < tok.length && isWake(w + tok[i + 1].replaceAll("[^\\p{L}\\p{N}]", ""))) used = 2;
            if (used > 0) {
                StringBuilder sb = new StringBuilder();
                for (int j = i + used; j < tok.length; j++) sb.append(tok[j]).append(' ');
                return sb.toString().trim();
            }
        }
        return null;
    }

    /** Called from the microphone thread with each finished utterance. */
    public static void onTranscript(String text, boolean pushToTalk) {
        Minecraft.getInstance().execute(() -> {
            String rest = stripWake(text);
            long now = System.currentTimeMillis();
            if (rest != null) {
                Sfx.PING.play(1.4f);
                userSaid(text);
                if (rest.isBlank()) {
                    awakeUntil = now + 8000;
                    say(YES, false);
                } else {
                    awakeUntil = 0;
                    handle(rest);
                }
            } else if (pushToTalk || now < awakeUntil) {
                awakeUntil = 0;
                userSaid(text);
                handle(text);
            }
        });
    }

    /** Called by the dedicated HUD chat. The wake word is optional there. */
    public static void submitText(String raw) {
        userSaid(raw);
        String rest = stripWake(raw);
        if (rest != null && rest.isBlank()) {
            say(YES, false);
            return;
        }
        handle(rest != null ? rest : raw);
    }

    // ------------------------------------------------------------- intents

    private static boolean has(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    private static boolean word(String s, String w) {
        return Pattern.compile("\\b" + w + "\\b").matcher(s).find();
    }

    /** TRUE = turn on, FALSE = turn off, null = toggle. */
    private static Boolean onOff(String s) {
        if (word(s, "off") || has(s, "disable", "hide", "꺼", "끄", "숨")) return Boolean.FALSE;
        if (word(s, "on") || has(s, "enable", "show", "켜", "표시", "보여")) return Boolean.TRUE;
        return null;
    }

    private static boolean flip(Boolean v, boolean cur) {
        return v == null ? !cur : v;
    }

    private static void handle(String raw) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;
        String s = raw.toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty()) {
            say(YES, false);
            return;
        }

        if (has(s, "help", "what can you", "도움", "뭘 할 수", "무엇을 할 수")) {
            say(HELP, false);
        } else if (has(s, "diagnos", "진단")) {
            Diagnostics.open = true;
            Diagnostics.openedAt = HudState.age;
            say(DIAG_START, false);
            int warn = 0;
            if (p.getHealth() < p.getMaxHealth() * 0.5f) warn++;
            if (p.getFoodData().getFoodLevel() <= 6) warn++;
            for (int i = 0; i < 4; i++) if (HudState.armorPresent[i] && HudState.armorPct[i] < 0.3f) warn++;
            if (warn == 0) say(DIAG_OK, false);
            else sayText(ko() ? "경고 " + warn + "건이 확인되었습니다." : warn + " warning" + (warn == 1 ? "" : "s") + " found.", false);
        } else if (has(s, "power", "전력", "에너지", "routing") && has(s, "sensor", "센서", "optic", "광학", "analy", "분석", "balanc", "균형", "균등", "reset")) {
            if (has(s, "sensor", "센서")) {
                Power.set(Power.Preset.SENSORS);
                say(PWR_SENS, false);
            } else if (has(s, "optic", "광학")) {
                Power.set(Power.Preset.OPTICS);
                say(PWR_OPT, false);
            } else if (has(s, "analy", "분석")) {
                Power.set(Power.Preset.ANALYSIS);
                say(PWR_ANL, false);
            } else {
                Power.set(Power.Preset.BALANCED);
                say(PWR_BAL, false);
            }
        } else if (has(s, "status", "report", "상태", "보고", "how am i", "how are")) {
            int hull = Math.round(p.getHealth() / Math.max(1f, p.getMaxHealth()) * 100f);
            int pwr = p.getFoodData().getFoodLevel() * 5;
            sayText(ko()
                    ? String.format(Locale.ROOT, "기체 %d퍼센트, 전력 %d퍼센트, 방어력 %d. %s", hull, pwr, p.getArmorValue(),
                    HudState.threats == 0 ? "적대 신호 없음." : "적대 신호 " + HudState.threats + "개.")
                    : String.format(Locale.ROOT, "Hull at %d percent, power at %d percent, armor rating %d. %s", hull, pwr,
                    p.getArmorValue(), HudState.threats == 0 ? "No hostile contacts." : HudState.threats + " hostile contacts in range."),
                    false);
        } else if (has(s, "scan", "스캔", "주변")) {
            HudState.startScan();
        } else if (has(s, "hostile", "enem", "threat", "적", "위협")) {
            if (HudState.threats == 0) say(NO_HOSTILE, false);
            else sayText(ko()
                    ? String.format(Locale.ROOT, "적대 신호 %d개. 가장 가까운 적은 %.0f미터 거리입니다.", HudState.threats, HudState.nearestHostile)
                    : String.format(Locale.ROOT, "%d hostile contacts. Nearest is %.0f meters away.", HudState.threats, HudState.nearestHostile),
                    false);
        } else if (has(s, "zoom out", "zoom off", "축소", "줌 아웃", "줌아웃", "줌 해제")) {
            HudState.zoomLatched = false;
            say(ZOOM_OFF, false);
        } else if (has(s, "zoom", "magnif", "확대", "줌")) {
            HudState.zoomLatched = has(s, "zoom in", "magnif", "확대", "줌 인", "줌인")
                    || flip(onOff(s), HudState.zoomLatched);
            say(HudState.zoomLatched ? ZOOM_ON : ZOOM_OFF, false);
        } else if (has(s, "waypoint", "way point", "웨이포인트", "마크", "mark")) {
            HudState.toggleWaypoint(mc);
        } else if (has(s, "lock", "락온", "조준")) {
            HudState.multiLock = flip(onOff(s), HudState.multiLock);
            say(HudState.multiLock ? LOCK_ON : LOCK_OFF, false);
        } else if (has(s, "hud") && onOff(s) != null) {
            HudState.enabled = onOff(s);
            if (HudState.enabled) HudState.age = 0;
            say(HudState.enabled ? HUD_ON : HUD_OFF, false);
        } else if (has(s, "radar", "레이더")) {
            HudConfig.SHOW_RADAR.set(flip(onOff(s), HudConfig.SHOW_RADAR.get()));
            say(OK, false);
        } else if (has(s, "compass", "나침반")) {
            HudConfig.SHOW_COMPASS.set(flip(onOff(s), HudConfig.SHOW_COMPASS.get()));
            say(OK, false);
        } else if (has(s, "map", "terrain", "지도", "지형")) {
            HudConfig.SHOW_TERRAIN.set(flip(onOff(s), HudConfig.SHOW_TERRAIN.get()));
            say(OK, false);
        } else if (has(s, "weapon", "armament", "무기")) {
            HudConfig.SHOW_WEAPONS.set(flip(onOff(s), HudConfig.SHOW_WEAPONS.get()));
            say(OK, false);
        } else if (has(s, "unmute", "음성 켜", "목소리 켜")) {
            HudConfig.VOICE_ENABLED.set(true);
            say(OK, false);
        } else if (has(s, "mute", "be quiet", "조용", "음소거", "음성 꺼", "목소리 꺼")) {
            Voice.stop();
            HudConfig.VOICE_ENABLED.set(false);
            LOG.add(new Msg(true, ko() ? "음성 출력을 껐습니다." : "Voice output muted.", HudState.age));
        } else if (has(s, "where am i", "location", "position", "위치", "어디")) {
            String biome = level.getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("unknown");
            sayText(ko()
                    ? String.format(Locale.ROOT, "현재 좌표는 %d, %d, %d입니다. 바이옴은 %s입니다.", p.getBlockX(), p.getBlockY(), p.getBlockZ(), biome)
                    : String.format(Locale.ROOT, "You are at %d, %d, %d, in a %s biome.", p.getBlockX(), p.getBlockY(), p.getBlockZ(), biome), false);
        } else if (has(s, "time", "시간", "몇 시")) {
            long dt = level.getDayTime() % 24000L;
            int hh = (int) ((dt / 1000L + 6L) % 24L), mm = (int) ((dt % 1000L) * 60L / 1000L);
            sayText(ko() ? String.format(Locale.ROOT, "게임 시간은 %d시 %d분입니다.", hh, mm)
                    : String.format(Locale.ROOT, "It is %02d:%02d in the game world.", hh, mm), false);
        } else if (has(s, "weather", "날씨")) {
            sayText(ko() ? (level.isThundering() ? "뇌우가 치고 있습니다." : level.isRaining() ? "비가 내리고 있습니다." : "하늘은 맑습니다.")
                    : (level.isThundering() ? "A thunderstorm is in progress." : level.isRaining() ? "It is raining." : "The skies are clear."), false);
        } else if (has(s, "thank", "고마", "감사")) {
            say(THANKS, false);
        } else if (has(s, "hello", "hi ", "hey", "안녕", "여보세요") || s.equals("hi")) {
            say(HELLO, false);
        } else {
            say(SORRY, false);
        }
    }
}
