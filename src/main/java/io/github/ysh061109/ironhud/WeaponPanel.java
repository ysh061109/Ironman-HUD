package io.github.ysh061109.ironhud;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.function.Predicate;

/** Armament panel: hand items, durability, ammo, bow draw / melee charge, totems and rockets. */
public final class WeaponPanel {
    private WeaponPanel() {}

    private static int count(LocalPlayer p, Predicate<ItemStack> f) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (f.test(s)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (f.test(s)) n += s.getCount();
        return n;
    }

    private static String name(ItemStack s, int max) {
        if (s.isEmpty()) return "EMPTY";
        String n = s.getHoverName().getString().toUpperCase(Locale.ROOT);
        return n.length() > max ? n.substring(0, max) : n;
    }

    private static void bar(float x, float y, float w, float frac, int rgb, float boot) {
        Draw.rect(x, y, x + w, y + 4f, Draw.col(rgb, 0.2f * boot));
        Draw.rect(x, y, x + w * Mth.clamp(frac, 0f, 1f), y + 4f, Draw.col(rgb, 0.95f * boot));
    }

    public static void render(LocalPlayer p, float x, float y, float t, float boot, int pri, int acc) {
        float w = 126f, h = 54f;
        Draw.box(x, y, w, h, pri, 0.10f * boot, 0.45f * boot);
        Draw.text("ARMAMENT", x + 4f, y + 3f, Draw.col(pri, boot), 0.65f);

        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        Draw.text(name(main, 22), x + 4f, y + 12f, Draw.col(0xDFF9FF, boot), 0.75f);

        float ry = y + 23f;
        if (main.isDamageableItem()) {
            float f = 1f - main.getDamageValue() / (float) Math.max(1, main.getMaxDamage());
            Draw.text("DUR", x + 4f, ry, Draw.col(pri, boot), 0.6f);
            bar(x + 24f, ry + 1f, w - 30f, f, f < 0.25f ? 0xFF3B30 : pri, boot);
            ry += 9f;
        }

        if (main.getItem() instanceof BowItem) {
            int arrows = count(p, s -> s.getItem() instanceof ArrowItem);
            Draw.text("ARROWS " + arrows, x + 4f, ry, Draw.col(arrows == 0 ? 0xFF3B30 : acc, boot), 0.65f);
            float draw = p.isUsingItem() ? BowItem.getPowerForTime(p.getTicksUsingItem()) : 0f;
            bar(x + 60f, ry + 1f, w - 66f, draw, acc, boot);
        } else if (main.getItem() instanceof CrossbowItem) {
            int arrows = count(p, s -> s.getItem() instanceof ArrowItem);
            boolean loaded = CrossbowItem.isCharged(main);
            Draw.text("ARROWS " + arrows, x + 4f, ry, Draw.col(arrows == 0 ? 0xFF3B30 : acc, boot), 0.65f);
            Draw.text(loaded ? "LOADED" : "EMPTY", x + 70f, ry, Draw.col(loaded ? 0x39FF88 : 0xFFB020, boot), 0.65f);
        } else {
            Draw.text("STRIKE", x + 4f, ry, Draw.col(pri, boot), 0.6f);
            bar(x + 36f, ry + 1f, w - 42f, p.getAttackStrengthScale(0f), acc, boot);
        }
        ry += 9f;

        int totems = count(p, s -> s.is(Items.TOTEM_OF_UNDYING));
        int rockets = count(p, s -> s.is(Items.FIREWORK_ROCKET));
        Draw.text("OFF " + name(off, 9), x + 4f, ry, Draw.col(0xDFF9FF, 0.85f * boot), 0.6f);
        Draw.text("TOT " + totems + "  FW " + rockets, x + 70f, ry, Draw.col(totems == 0 ? 0x7B8794 : 0xFFB020, boot), 0.6f);
        if (p.isBlocking()) Draw.text("SHIELD UP", x + 4f, y + h + 2f, Draw.col(acc, boot), 0.6f);
    }
}
