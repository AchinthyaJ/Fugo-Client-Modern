package io.github.fugoclient.mcefmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;

public class PvPTracker {
    private static int combo = 0;
    private static double reach = 0.0;
    private static long lastHitTime = 0;
    
    private static int targetId = -1;
    private static String targetName = "";
    private static float targetHealth = 0.0f;
    private static float targetMaxHealth = 0.0f;
    private static double targetDistance = 0.0;
    private static final String[] targetArmor = new String[4];
    private static long lastTargetTime = 0;

    private static int kills = 0;
    private static int deaths = 0;
    private static int winStreak = 0;
    private static int currentStreak = 0;
    private static boolean wasDead = false;
    
    // Cached timestamp to avoid repeated System.currentTimeMillis() calls
    private static long cachedNow = 0;
    private static boolean comboExpired = false;
    private static boolean targetExpired = false;

    public static void onAttack(Entity target) {
        if (!(target instanceof LivingEntity)) return;
        
        LivingEntity living = (LivingEntity) target;
        long now = System.currentTimeMillis();
        
        combo = (now - lastHitTime <= 3000) ? combo + 1 : 1;
        lastHitTime = now;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            reach = client.player.distanceTo(target);
        }
        
        targetId = target.getId();
        targetName = target.getName().getString();
        targetHealth = living.getHealth();
        targetMaxHealth = living.getMaxHealth();
        targetDistance = reach;
        
        // Pre-allocate equipment slot array for faster iteration
        targetArmor[0] = armorToId(living.getEquippedStack(EquipmentSlot.FEET));
        targetArmor[1] = armorToId(living.getEquippedStack(EquipmentSlot.LEGS));
        targetArmor[2] = armorToId(living.getEquippedStack(EquipmentSlot.CHEST));
        targetArmor[3] = armorToId(living.getEquippedStack(EquipmentSlot.HEAD));
        
        lastTargetTime = now;
    }

    private static String armorToId(net.minecraft.item.ItemStack stack) {
        return stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).getPath();
    }

    public static void onDeath() {
        deaths++;
        combo = 0;
        currentStreak = 0;
    }

    public static void tick(net.minecraft.client.network.ClientPlayerEntity player) {
        if (player == null) return;
        
        cachedNow = System.currentTimeMillis();
        comboExpired = cachedNow - lastHitTime > 3000;
        targetExpired = cachedNow - lastTargetTime > 5000;
        
        // 1. Detect death
        boolean isDead = player.isDead() || player.getHealth() <= 0;
        if (isDead && !wasDead) {
            onDeath();
        }
        wasDead = isDead;

        // 2. Simple kill detector (skip if no target or expired)
        if (lastTargetTime > 0 && targetId != -1 && !targetExpired) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                Entity e = client.world.getEntityById(targetId);
                if (e instanceof LivingEntity) {
                    LivingEntity living = (LivingEntity) e;
                    if (living.isDead() || living.getHealth() <= 0) {
                        kills++;
                        currentStreak++;
                        if (currentStreak > winStreak) {
                            winStreak = currentStreak;
                        }
                        lastTargetTime = 0;
                        targetId = -1;
                    }
                }
            }
        }
    }

    public static void resetSession() {
        kills = 0;
        deaths = 0;
        winStreak = 0;
        currentStreak = 0;
        combo = 0;
        reach = 0.0;
        targetName = "";
    }

    public static int getCombo() {
        if (comboExpired) {
            combo = 0;
        }
        return combo;
    }

    public static double getReach() {
        return reach;
    }

    public static long getLastHitTime() {
        return lastHitTime;
    }

    public static String getTargetName() {
        if (targetExpired) {
            targetName = "";
        }
        return targetName;
    }

    public static float getTargetHealth() {
        return targetHealth;
    }

    public static float getTargetMaxHealth() {
        return targetMaxHealth;
    }

    public static double getTargetDistance() {
        return targetDistance;
    }

    public static String[] getTargetArmor() {
        return targetArmor;
    }

    public static int getKills() {
        return kills;
    }

    public static int getDeaths() {
        return deaths;
    }

    public static int getWinStreak() {
        return winStreak;
    }

    public static int getCurrentStreak() {
        return currentStreak;
    }
}