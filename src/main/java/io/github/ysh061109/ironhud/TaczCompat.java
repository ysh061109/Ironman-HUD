package io.github.ysh061109.ironhud;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only bridge to Timeless and Classics Zero (TACZ) guns. No compile-time dependency: guns and ammo are
 * recognised from their item data (GunId / GunCurrentAmmoCount / AmmoId ...) and the per-gun magazine size
 * is looked up through TACZ's own API by reflection, so every gun pack that TACZ loads works unchanged.
 */
public final class TaczCompat {
    private TaczCompat() {}

    public record Meta(String ammoId, int cap, int[] ext, int rpm, String type) {}

    public record Gun(String gunId, String ammoId, int mag, int cap, boolean chambered, String fire,
                      int reserve, boolean infinite, boolean dummy, int rpm, String type) {}

    private static final Map<String, Meta> META = new HashMap<>();
    private static Method getIndex, getData, dAmmoId, dAmmo, dExt, dRpm, iType;
    private static boolean apiTried, apiOk;

    private static CompoundTag tag(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.getUnsafe();
    }

    public static boolean isGun(ItemStack s) {
        if (s.isEmpty()) return false;
        CompoundTag t = tag(s);
        return t != null && t.contains("GunId", Tag.TAG_STRING);
    }

    private static void initApi() {
        if (apiTried) return;
        apiTried = true;
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            getIndex = api.getMethod("getCommonGunIndex", ResourceLocation.class);
            Class<?> idx = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
            getData = idx.getMethod("getGunData");
            iType = idx.getMethod("getType");
            Class<?> gd = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            dAmmoId = gd.getMethod("getAmmoId");
            dAmmo = gd.getMethod("getAmmoAmount");
            dExt = gd.getMethod("getExtendedMagAmmoAmount");
            dRpm = gd.getMethod("getRoundsPerMinute");
            apiOk = true;
        } catch (Throwable ignored) {
            apiOk = false;
        }
    }

    private static Meta meta(String gunId) {
        Meta m = META.get(gunId);
        if (m != null) return m;
        initApi();
        if (!apiOk) return null;
        try {
            ResourceLocation id = ResourceLocation.tryParse(gunId);
            if (id == null) return null;
            Optional<?> o = (Optional<?>) getIndex.invoke(null, id);
            if (o.isEmpty()) return null;   // resources not loaded yet: do not cache
            Object idx = o.get();
            Object data = getData.invoke(idx);
            Object aid = dAmmoId.invoke(data);
            m = new Meta(aid == null ? "" : aid.toString(), (Integer) dAmmo.invoke(data), (int[]) dExt.invoke(data),
                    (Integer) dRpm.invoke(data), String.valueOf(iType.invoke(idx)));
            META.put(gunId, m);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Gun read(Player p, ItemStack s) {
        CompoundTag t = tag(s);
        if (t == null || !t.contains("GunId", Tag.TAG_STRING)) return null;
        String gid = t.getString("GunId");
        Meta m = meta(gid);
        int mag = Math.max(0, t.getInt("GunCurrentAmmoCount"));
        boolean chamber = t.getBoolean("HasBulletInBarrel");
        String fire = t.contains("GunFireMode", Tag.TAG_STRING) ? t.getString("GunFireMode") : "";
        int cap = m == null ? Math.max(mag, 1) : Math.max(1, m.cap());
        if (m != null && mag > cap && m.ext() != null) {
            int best = mag;
            for (int e : m.ext()) if (e >= mag && (best == mag || e < best)) best = e;
            cap = Math.max(cap, best);
        }
        cap = Math.max(cap, mag);
        boolean dummy = t.contains("DummyAmmo", Tag.TAG_INT);
        int reserve = 0;
        boolean inf = false;
        if (dummy) {
            reserve = Math.max(0, t.getInt("DummyAmmo"));
        } else if (m != null && !m.ammoId().isEmpty()) {
            for (int pass = 0; pass < 3; pass++) {
                var list = pass == 0 ? p.getInventory().items : pass == 1 ? p.getInventory().offhand : p.getInventory().armor;
                for (ItemStack it : list) {
                    if (it.isEmpty()) continue;
                    CompoundTag it0 = tag(it);
                    if (it0 == null) continue;
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(it.getItem());
                    if (!"tacz".equals(key.getNamespace())) continue;
                    String path = key.getPath();
                    if (path.equals("ammo") && m.ammoId().equals(it0.getString("AmmoId"))) {
                        reserve += it.getCount();
                    } else if (path.equals("ammo_box")) {
                        if (it0.getBoolean("AllTypeCreative") || (it0.getBoolean("Creative") && m.ammoId().equals(it0.getString("AmmoId")))) {
                            inf = true;
                        } else if (m.ammoId().equals(it0.getString("AmmoId"))) {
                            reserve += Math.max(0, it0.getInt("AmmoCount"));
                        }
                    }
                }
            }
        }
        return new Gun(gid, m == null ? "" : m.ammoId(), mag, cap, chamber, fire, reserve, inf, dummy,
                m == null ? 0 : m.rpm(), m == null ? "" : m.type());
    }

    // ---- shot detection for the held gun
    private static int lastMag = -1, lastSlot = -1;
    private static String lastId = "";
    public static int shotAt = -1000;
    public static int shots = 0;

    public static void tick(Player p) {
        ItemStack held = p.getMainHandItem();
        if (!isGun(held)) {
            lastMag = -1;
            return;
        }
        CompoundTag t = tag(held);
        int mag = t.getInt("GunCurrentAmmoCount");
        String id = t.getString("GunId");
        int slot = p.getInventory().selected;
        if (lastMag >= 0 && slot == lastSlot && id.equals(lastId) && mag < lastMag) {
            shotAt = HudState.age;
            shots++;
        }
        lastMag = mag;
        lastSlot = slot;
        lastId = id;
    }

    /** 1 right after a shot, fading to 0 over ~8 ticks. */
    public static float flash(float pt) {
        return Math.max(0f, 1f - (HudState.age + pt - shotAt) / 8f);
    }
}
