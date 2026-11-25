package io.github.ysh061109.ironhud;

import io.github.ysh061109.ironhud.Alerts.Level;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** All per-tick HUD data. Client thread only. */
public final class HudState {
    public static final int HOSTILE = 0, PLAYER = 1, NEUTRAL = 2;

    public record Contact(LivingEntity e, int kind, double dist) {}

    // ------------------------------------------------------------- lifecycle
    public static boolean enabled = true;
    public static int age = 0;
    private static LocalPlayer lastPlayer;

    // ---------------------------------------------------------------- vitals
    public static float dispHp = 20f;
    private static float lastHp = 20f;
    public static float hitFlash = 0f;

    private static void registerHit(LocalPlayer p) {
        double best = 1e9;
        Vec3 dir = null;
        for (Contact c : contacts) {
            if (c.kind() == HOSTILE && c.dist() < 12 && c.dist() < best) {
                best = c.dist();
                dir = c.e().position().subtract(p.position());
            }
        }
        for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(5.0))) {
            double dd = pr.distanceTo(p);
            if (dd < best) {
                Entity owner = pr.getOwner();
                best = dd;
                dir = owner != null ? owner.position().subtract(p.position()) : pr.getDeltaMovement().reverse();
            }
        }
        if (dir == null || dir.horizontalDistanceSqr() < 1.0e-4) return;
        hits.add(new float[]{(float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), age});
        while (hits.size() > 6) hits.remove(0);
    }

    // ----------------------------------------------------------------- armor
    public static final String[] PART = {"HELM", "CHEST", "LEGS", "BOOTS"};
    private static final EquipmentSlot[] SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final float[] armorPct = new float[4];
    public static final boolean[] armorPresent = new boolean[4];
    public static final int[] armorFlash = new int[4];
    private static final int[] lastDmg = new int[4];
    private static final float[] lastPct = new float[4];
    private static final boolean[] lastPresent = new boolean[4];
    public static boolean hasThrusters = false;
    public static float thrusterPct = 0f;

    // -------------------------------------------------------------- contacts
    public static final List<Contact> contacts = new ArrayList<>();
    public static int threats = 0;
    public static double nearestHostile = -1;

    private static void computeLocks(LocalPlayer p, ClientLevel level) {
        locks.clear();
        if (!multiLock) {
            lastLockCount = 0;
            return;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        double range = scanRange();
        List<Contact> cand = new ArrayList<>();
        for (Contact c : contacts) {
            if (c.kind() != HOSTILE || c.dist() > range) continue;
            Vec3 ctr = c.e().getBoundingBox().getCenter();
            Vec3 to = ctr.subtract(eye);
            double d = to.length();
            if (d < 1.0e-3 || to.dot(look) / d < 0.8) continue;
            BlockHitResult bh = level.clip(new ClipContext(eye, ctr, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (bh.getType() == HitResult.Type.BLOCK && bh.getLocation().distanceToSqr(eye) < d * d - 1.0) continue;
            cand.add(c);
        }
        cand.sort(Comparator.comparingDouble((Contact c) -> -(TargetScan.attack(c.e()) + c.e().getMaxHealth() * 0.15 + (range - c.dist()) * 0.1)));
        int max = Power.maxLocks();
        for (int i = 0; i < cand.size() && i < max; i++) locks.add(cand.get(i));
        if (locks.size() > lastLockCount) {
            Sfx.LOCK.play(1.0f + 0.1f * locks.size());
            SysLog.add("MULTI-LOCK: " + locks.size() + " TARGETS");
        }
        lastLockCount = locks.size();
    }

    private static void sampleTerrain(LocalPlayer p, ClientLevel level) {
        int half = (TN - 1) / 2;
        terrainCx = (Mth.floor(p.getX()) / TSTEP) * TSTEP;
        terrainCz = (Mth.floor(p.getZ()) / TSTEP) * TSTEP;
        for (int i = 0; i < TN; i++) {
            for (int j = 0; j < TN; j++) {
                int x = terrainCx + (i - half) * TSTEP, z = terrainCz + (j - half) * TSTEP;
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    terrain[i][j] = Float.NaN;
                } else {
                    terrain[i][j] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                }
            }
        }
    }

    // ---------------------------------------------------------------- target
    public static boolean hasTarget = false;
    public static boolean targetHostile = false;
    public static boolean targetDead = false;
    public static String targetName = "";
    public static double targetDist = 0;
    public static float targetHp = 0, targetMaxHp = 1;
    public static int targetId = -1;
    public static LivingEntity targetEntity = null;
    public static int lockTicks = 0;
    public static int kills = 0;
    private static int lastKillId = -1;

    // ---------------------------------------------------------------- motion
    public static float speed = 0f;        // horizontal blocks/s
    public static float flightSpeed = 0f;  // 3D blocks/s
    public static float vSpeed = 0f;       // blocks/s
    public static double groundDist = -1;  // blocks to ground below, -1 = none
    public static final float[] altHist = new float[64];
    private static int altIdx = 0;
    public static double collisionTtc = -1;
    public static boolean collisionIsFall = false;
    public static float predictedFallDmg = 0f;

    // ----------------------------------------------------------------- tools
    public static boolean zoomHeld = false;
    public static float zoomTarget = 3f, zoomCur = 1f;
    public static int scanStart = -1000;
    public static Vec3 waypoint = null;
    public static boolean zoomLatched = false;
    public static boolean multiLock = false;
    public static final List<Contact> locks = new ArrayList<>();
    private static int lastLockCount = 0;
    public static final List<float[]> hits = new ArrayList<>();

    // ------------------------------------------------------------ terrain grid
    public static final int TN = 21, TSTEP = 2;
    public static final float[][] terrain = new float[TN][TN];
    public static int terrainCx, terrainCz;

    private HudState() {}

    /** Radar / lock range after power routing. */
    public static int radarRange() {
        return Math.min(96, Math.round(HudConfig.RADAR_RANGE.get() * Power.sensorsMul()));
    }

    public static int scanRange() {
        return Math.min(160, Math.round(HudConfig.SCAN_RANGE.get() * Power.sensorsMul()));
    }

    public static float zoomMax() {
        return Mth.clamp(HudConfig.ZOOM_MAX.get().floatValue() * Power.opticsMul(), 2f, 24f);
    }

    public static boolean active(Minecraft mc) {
        return enabled && mc.player != null && mc.level != null
                && !mc.options.hideGui && !mc.player.isSpectator();
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) {
            lastPlayer = null;
            return;
        }
        if (p != lastPlayer) reset(p);
        if (!active(mc)) {
            zoomCur = 1f;
            return;
        }
        age++;

        vitals(p);
        armor(p);
        environment(p, level);
        if (age % 4 == 0) scanContacts(p, level);
        scanTarget(p, level);
        motion(p, level);
        if (age % 10 == 0) sampleTerrain(p, level);
        zoom();
        hits.removeIf(h -> age - h[1] > 60f);
        if (age == scanStart + 42) Modus.afterScan(threats, contacts.size());

        if (age == 1) {
            Alerts.fire("boot", "SUIT SYSTEMS ONLINE", "ALL MODULES NOMINAL", Level.INFO, 60, 0, Sfx.BOOT);
        }
        Cracks.tick(Mth.clamp(p.getHealth() / Math.max(1f, p.getMaxHealth()), 0f, 1f));
        Alerts.tick();
    }

    private static void reset(LocalPlayer p) {
        lastPlayer = p;
        age = 0;
        dispHp = lastHp = p.getHealth();
        hitFlash = 0f;
        for (int i = 0; i < 4; i++) {
            armorFlash[i] = 0;
            lastPresent[i] = false;
            lastDmg[i] = 0;
            lastPct[i] = 1f;
        }
        contacts.clear();
        threats = 0;
        hasTarget = false;
        lastKillId = -1;
        kills = 0;
        waypoint = null;
        collisionTtc = -1;
        java.util.Arrays.fill(altHist, (float) p.getY());
        Alerts.clear();
        Cracks.clear();
        SysLog.clear();
        hits.clear();
        locks.clear();
        lastLockCount = 0;
        Modus.onWorldJoin(p);
    }

    // ---------------------------------------------------------------- vitals

    private static void vitals(LocalPlayer p) {
        float hp = p.getHealth();
        float max = Math.max(1f, p.getMaxHealth());
        dispHp += (hp - dispHp) * 0.2f;

        if (hp < lastHp - 0.01f) {
            float d = lastHp - hp;
            hitFlash = Math.min(1f, 0.35f + d / 8f);
            registerHit(p);
            if (d >= 3f && HudConfig.SHOW_CRACKS.get()) Cracks.add(d / 6f, false);
            if (d >= 6f) {
                Alerts.fire("bighit", "MAJOR IMPACT",
                        String.format("STRUCTURAL DAMAGE  -%.0f HP", d), Level.CRIT, 50, 40, Sfx.BREACH);
            }
        }
        lastHp = hp;
        hitFlash *= 0.88f;

        float frac = hp / max;
        if (frac <= 0.3f) {
            Alerts.fire("hull", "HULL INTEGRITY CRITICAL",
                    String.format("%.0f%% REMAINING", frac * 100f), Level.CRIT, 70, 90, Sfx.CRITICAL);
        } else if (frac <= 0.5f) {
            Alerts.fire("hull50", "HULL DAMAGE DETECTED",
                    String.format("%.0f%% REMAINING", frac * 100f), Level.WARN, 50, 400, Sfx.ALERT);
        }

        int food = p.getFoodData().getFoodLevel();
        if (food <= 2) {
            Alerts.fire("power", "POWER CELLS DEPLETED", "REFUEL IMMEDIATELY", Level.CRIT, 60, 600, Sfx.CRITICAL);
        } else if (food <= 6) {
            Alerts.fire("power", "POWER LOW", "REFUEL RECOMMENDED", Level.WARN, 50, 900, Sfx.ALERT);
        }
    }

    // ----------------------------------------------------------------- armor

    private static void armor(LocalPlayer p) {
        hasThrusters = false;
        for (int i = 0; i < 4; i++) {
            ItemStack st = p.getItemBySlot(SLOTS[i]);
            boolean isElytra = i == 1 && st.is(Items.ELYTRA);
            boolean present = !st.isEmpty() && (st.getItem() instanceof ArmorItem || isElytra);
            int dmg = present ? st.getDamageValue() : 0;
            int max = present ? Math.max(1, st.getMaxDamage()) : 1;
            float pct = present ? Mth.clamp(1f - dmg / (float) max, 0f, 1f) : 0f;

            if (age > 1) {
                if (lastPresent[i] && !present && lastPct[i] <= 0.08f) {
                    Alerts.fire("brk" + i, "ARMOR BREACH", PART[i] + " PLATE DESTROYED", Level.CRIT, 80, 20, Sfx.BREACH);
                    armorFlash[i] = 24;
                } else if (present && lastPresent[i] && dmg > lastDmg[i]) {
                    armorFlash[i] = 12;
                    if (pct < 0.25f) {
                        Alerts.fire("low" + i, "ARMOR INTEGRITY LOW",
                                String.format("%s %.0f%%", PART[i], pct * 100f), Level.WARN, 50, 300, Sfx.ALERT);
                    }
                }
            }
            if (armorFlash[i] > 0) armorFlash[i]--;

            if (isElytra) {
                hasThrusters = true;
                thrusterPct = pct;
                if (pct < 0.15f && p.isFallFlying()) {
                    Alerts.fire("thr", "THRUSTER FUEL LOW", String.format("%.0f%%", pct * 100f),
                            Level.WARN, 50, 400, Sfx.ALERT);
                }
            }
            armorPresent[i] = present;
            armorPct[i] = pct;
            lastPresent[i] = present;
            lastDmg[i] = dmg;
            lastPct[i] = pct;
        }
    }

    // ----------------------------------------------------------- environment

    private static void environment(LocalPlayer p, ClientLevel level) {
        if (p.isOnFire()) {
            Alerts.fire("fire", "THERMAL WARNING", "HULL TEMPERATURE RISING", Level.CRIT, 40, 60, Sfx.ALERT);
        }
        int maxAir = Math.max(1, p.getMaxAirSupply());
        float air = p.getAirSupply() / (float) maxAir;
        if (air < 0.3f) {
            Alerts.fire("o2", "OXYGEN LOW", String.format("%.0f%%", Math.max(0f, air) * 100f),
                    air < 0.1f ? Level.CRIT : Level.WARN, 40, 50, air < 0.1f ? Sfx.CRITICAL : Sfx.ALERT);
        }
        if (age % 10 == 0) {
            BlockPos bp = p.blockPosition();
            boolean lava = false;
            for (BlockPos q : BlockPos.betweenClosed(bp.offset(-3, -2, -3), bp.offset(3, 2, 3))) {
                if (level.getFluidState(q).is(FluidTags.LAVA)) {
                    lava = true;
                    break;
                }
            }
            if (lava) {
                Alerts.fire("lava", "LAVA PROXIMITY", "EXTREME HEAT SOURCE", Level.WARN, 50, 120, Sfx.ALERT);
            }
        }
    }

    // -------------------------------------------------------------- contacts

    private static void scanContacts(LocalPlayer p, ClientLevel level) {
        double range = Math.max(radarRange(), 32);
        AABB box = p.getBoundingBox().inflate(range);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive() && !(e instanceof ArmorStand));
        List<Contact> list = new ArrayList<>(found.size());
        for (LivingEntity e : found) {
            double d = Math.sqrt(p.distanceToSqr(e));
            if (d > range) continue;
            int kind = e instanceof Enemy ? HOSTILE : (e instanceof Player ? PLAYER : NEUTRAL);
            list.add(new Contact(e, kind, d));
        }
        list.sort(Comparator.comparingDouble(Contact::dist));
        if (list.size() > 64) list.subList(64, list.size()).clear();
        contacts.clear();
        contacts.addAll(list);

        int n = 0;
        nearestHostile = -1;
        boolean ignite = false;
        for (Contact c : contacts) {
            if (c.kind() != HOSTILE) continue;
            if (c.dist() <= 32) n++;
            if (nearestHostile < 0) nearestHostile = c.dist();
            if (c.e() instanceof Creeper cr && cr.getSwellDir() > 0 && c.dist() < 10) ignite = true;
        }
        threats = n;
        computeLocks(p, level);
        if (ignite) {
            Alerts.fire("creeper", "EXPLOSIVE IGNITION", "DETONATION IMMINENT", Level.CRIT, 40, 30, Sfx.CRITICAL);
        } else if (nearestHostile >= 0 && nearestHostile < 6) {
            Alerts.fire("prox", "PROXIMITY ALERT",
                    String.format("HOSTILE %.1f M", nearestHostile), Level.WARN, 40, 120, Sfx.ALERT);
        }
    }

    // ---------------------------------------------------------------- target

    private static void scanTarget(LocalPlayer p, ClientLevel level) {
        double range = scanRange();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));

        // Don't see through walls: limit the entity search to the first solid block.
        BlockHitResult bh = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double maxSq = bh.getType() == HitResult.Type.MISS ? range * range : bh.getLocation().distanceToSqr(eye);

        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                p, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity,
                maxSq);

        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            Entity e = hit.getEntity();
            boolean isNew = e.getId() != targetId;
            hasTarget = true;
            targetHostile = le instanceof Enemy;
            targetDead = le.isDeadOrDying();
            targetName = le.getDisplayName().getString();
            targetDist = Math.sqrt(eye.distanceToSqr(hit.getLocation()));
            targetHp = Math.max(0f, le.getHealth());
            targetMaxHp = Math.max(1f, le.getMaxHealth());
            lockTicks = isNew ? 0 : lockTicks + 1;
            targetId = e.getId();
            targetEntity = le;
            if (isNew && targetHostile && !targetDead) {
                Sfx.LOCK.play();
                Modus.onNewTarget(le);
                SysLog.add("TARGET ACQUIRED: " + targetName.toUpperCase(java.util.Locale.ROOT));
            }
            if (targetDead && targetHostile && e.getId() != lastKillId) {
                lastKillId = e.getId();
                kills++;
                Alerts.fire("kill", "TARGET ELIMINATED", targetName.toUpperCase(java.util.Locale.ROOT),
                        Level.INFO, 50, 0, Sfx.KILL);
            }
        } else {
            hasTarget = false;
            lockTicks = 0;
            targetId = -1;
            targetEntity = null;
        }
    }

    // ---------------------------------------------------------------- motion

    private static void motion(LocalPlayer p, ClientLevel level) {
        Vec3 vel = p.getDeltaMovement();
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        speed += ((float) (Math.sqrt(dx * dx + dz * dz) * 20.0) - speed) * 0.25f;
        float vs = (float) ((p.getY() - p.yo) * 20.0);
        vSpeed += (vs - vSpeed) * 0.25f;
        flightSpeed += ((float) (vel.length() * 20.0) - flightSpeed) * 0.25f;

        if (age % 3 == 0) {
            altIdx = (altIdx + 1) % altHist.length;
            altHist[altIdx] = (float) p.getY();
        }

        if (age % 5 == 0) {
            Vec3 from = p.position();
            BlockHitResult gh = level.clip(new ClipContext(from, from.add(0, -320, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            groundDist = gh.getType() == HitResult.Type.MISS ? -1 : from.y - gh.getLocation().y;
        }

        // ---- collision / terrain-impact prediction
        collisionTtc = -1;
        collisionIsFall = false;
        predictedFallDmg = 0f;
        boolean flying = p.isFallFlying();
        boolean falling = !flying && p.fallDistance > 4f && vel.y < -0.4;
        double sp = vel.length() * 20.0;
        if ((flying || falling) && sp > 6.0) {
            Vec3 dir = vel.normalize();
            Vec3 from = p.getEyePosition();
            Vec3 to = from.add(dir.scale(sp * 2.5));
            BlockHitResult bh = level.clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
            if (bh.getType() == HitResult.Type.BLOCK && level.getFluidState(bh.getBlockPos()).isEmpty()) {
                double dist = from.distanceTo(bh.getLocation());
                double ttc = dist / sp;
                if (ttc < 2.0) {
                    collisionTtc = ttc;
                    collisionIsFall = falling;
                    if (falling) {
                        predictedFallDmg = (float) Math.max(0.0, p.fallDistance + dist - 3.0);
                    }
                    if (flying || predictedFallDmg >= 3f) {
                        boolean lethal = falling && predictedFallDmg >= p.getHealth();
                        Level lv = (ttc < 1.0 || lethal) ? Level.CRIT : Level.WARN;
                        String sub = falling
                                ? String.format("T-%.1fS  EST -%.0f HP", ttc, predictedFallDmg)
                                : String.format("T-%.1fS", ttc);
                        Alerts.fire("coll", falling ? (lethal ? "FATAL IMPACT PREDICTED" : "TERRAIN IMPACT")
                                        : "COLLISION WARNING", sub, lv, 25, 25, Sfx.COLLISION);
                        Alerts.keep("coll", falling ? (lethal ? "FATAL IMPACT PREDICTED" : "TERRAIN IMPACT")
                                : "COLLISION WARNING", sub, lv, 12);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ zoom

    private static void zoom() {
        float goal = (zoomHeld || zoomLatched) ? zoomTarget : 1f;
        zoomCur += (goal - zoomCur) * 0.35f;
        if (Math.abs(goal - zoomCur) < 0.005f) zoomCur = goal;
    }

    public static void adjustZoom(double scroll) {
        float max = zoomMax();
        float next = Mth.clamp(zoomTarget * (scroll > 0 ? 1.25f : 0.8f), 1.5f, max);
        if (Math.abs(next - zoomTarget) > 0.001f) {
            zoomTarget = next;
            Sfx.TICK.play(0.8f + 0.4f * (zoomTarget / max));
        }
    }

    // ------------------------------------------------------------------ misc

    public static void startScan() {
        if (age - scanStart < 100) return;
        scanStart = age;
        Sfx.PING.play(0.7f);
        Alerts.fire("scan", "WIDE-AREA SCAN", "SWEEPING " + radarRange() + " M",
                Level.INFO, 40, 0, null);
    }

    /** Pressing again within 6 blocks of the current waypoint clears it. */
    public static void toggleWaypoint(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (waypoint != null && p.position().distanceTo(waypoint) < 6.0) {
            waypoint = null;
            Alerts.fire("wp", "WAYPOINT CLEARED", "", Level.INFO, 30, 0, Sfx.TICK);
        } else {
            waypoint = p.position();
            Alerts.fire("wp", "WAYPOINT SET", String.format("%d  %d  %d", p.getBlockX(), p.getBlockY(), p.getBlockZ()),
                    Level.INFO, 40, 0, Sfx.PING);
        }
    }

    public static int altIndex() {
        return altIdx;
    }
}
