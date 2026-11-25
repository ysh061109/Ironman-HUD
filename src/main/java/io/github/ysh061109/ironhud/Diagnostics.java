package io.github.ysh061109.ironhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full-screen "tabular data" window listing every subsystem. */
public final class Diagnostics {
    public static boolean open = false;
    public static int openedAt = 0;

    private static final int RED = 0xFF3B30, AMBER = 0xFFB020, WHITE = 0xDFF9FF, GREEN = 0x39FF88;

    private record Row(String name, String value, int rgb) {}

    private Diagnostics() {}

    private static int level(float frac, int pri) {
        return frac >= 0.6f ? pri : frac >= 0.3f ? AMBER : RED;
    }

    private static List<Row> rows(LocalPlayer p, ClientLevel level, int pri) {
        List<Row> r = new ArrayList<>();
        float hp = p.getHealth() / Math.max(1f, p.getMaxHealth());
        float food = p.getFoodData().getFoodLevel() / 20f;
        r.add(new Row("REACTOR / HULL", Math.round(hp * 100f) + "%", level(hp, pri)));
        r.add(new Row("POWER CELLS", Math.round(food * 100f) + "%", level(food, pri)));
        for (int i = 0; i < 4; i++) {
            boolean on = HudState.armorPresent[i];
            r.add(new Row("ARMOR " + HudState.PART[i], on ? Math.round(HudState.armorPct[i] * 100f) + "%" : "OFFLINE",
                    on ? level(HudState.armorPct[i], pri) : 0x7B8794));
        }
        r.add(new Row("THRUSTERS", HudState.hasThrusters ? Math.round(HudState.thrusterPct * 100f) + "%" : "N/A",
                HudState.hasThrusters ? level(HudState.thrusterPct, pri) : 0x7B8794));
        r.add(new Row("POWER ROUTING", Power.preset.label, pri));
        r.add(new Row("SENSOR ARRAY", "RADAR " + HudState.radarRange() + "M  LOCK " + HudState.scanRange() + "M", pri));
        r.add(new Row("OPTICS", "ZOOM x" + String.format(Locale.ROOT, "%.0f", HudState.zoomMax()), pri));
        r.add(new Row("ANALYSIS", Power.maxLocks() + " SIMULTANEOUS LOCKS", pri));
        r.add(new Row("MODUS CORE", "ONLINE", GREEN));
        r.add(new Row("VOICE OUTPUT", HudConfig.VOICE_ENABLED.get() ? Voice.backend() + " / " + Voice.status : "MUTED",
                Voice.status.startsWith("voice error") || Voice.status.startsWith("tts error") ? RED : pri));
        r.add(new Row("VOICE INPUT", Mic.status.toUpperCase(Locale.ROOT), Mic.status.startsWith("listening") ? GREEN : 0x7B8794));
        PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null
                : Minecraft.getInstance().getConnection().getPlayerInfo(p.getUUID());
        r.add(new Row("UPLINK LATENCY", info == null ? "N/A" : info.getLatency() + " MS", pri));
        r.add(new Row("DIMENSION", level.dimension().location().getPath().toUpperCase(Locale.ROOT), WHITE));
        r.add(new Row("AMBIENT LIGHT", level.getMaxLocalRawBrightness(p.blockPosition()) + " / 15", WHITE));
        r.add(new Row("WEATHER", level.isThundering() ? "THUNDERSTORM" : level.isRaining() ? "PRECIPITATION" : "CLEAR", WHITE));
        r.add(new Row("CONTACTS", HudState.threats + " HOSTILE / " + HudState.contacts.size() + " TOTAL",
                HudState.threats > 0 ? RED : pri));
        r.add(new Row("ELIMINATIONS", String.valueOf(HudState.kills), WHITE));
        return r;
    }

    public static void draw(LocalPlayer p, ClientLevel level, int w, int h, float t, int pri, int acc) {
        if (!open) return;
        float age = HudState.age - openedAt;
        float vis = Mth.clamp(age / 6f, 0f, 1f);
        List<Row> rows = rows(p, level, pri);
        float rh = 10f, pw = 300f, ph = 34f + rows.size() * rh + 10f;
        float x = w / 2f - pw / 2f, y = Math.max(8f, h / 2f - ph / 2f);

        Draw.rect(x, y, x + pw, y + ph, Draw.col(0x000814, 0.78f * vis));
        Draw.box(x, y, pw, ph, pri, 0f, 0.9f * vis);
        Draw.rect(x, y, x + pw, y + 14f, Draw.col(pri, 0.18f * vis));
        Draw.text("MODUS // SYSTEM DIAGNOSTICS", x + 6f, y + 4f, Draw.col(acc, vis), 0.85f);
        Draw.textRight("G: CLOSE", x + pw - 6f, y + 4f, Draw.col(WHITE, 0.6f * vis), 0.7f);
        Draw.text("SUBSYSTEM", x + 6f, y + 18f, Draw.col(pri, 0.7f * vis), 0.65f);
        Draw.text("STATUS", x + 120f, y + 18f, Draw.col(pri, 0.7f * vis), 0.65f);
        Draw.rect(x + 4f, y + 27f, x + pw - 4f, y + 28f, Draw.col(pri, 0.5f * vis));

        int shown = (int) (age * 1.2f);
        for (int i = 0; i < rows.size() && i < shown; i++) {
            Row row = rows.get(i);
            float ry = y + 31f + i * rh;
            if (i % 2 == 0) Draw.rect(x + 4f, ry - 1f, x + pw - 4f, ry + rh - 2f, Draw.col(pri, 0.06f * vis));
            Draw.text(row.name(), x + 6f, ry, Draw.col(WHITE, 0.85f * vis), 0.7f);
            Draw.text(row.value(), x + 120f, ry, Draw.col(row.rgb(), vis), 0.7f);
        }
    }
}
