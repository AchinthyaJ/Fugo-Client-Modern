package io.github.fugoclient.mcefmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.MinecraftClient;

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

    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) target;
            long now = System.currentTimeMillis();
            
            if (now - lastHitTime <= 3000) {
                combo++;
            } else {
                combo = 1;
            }
            
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
            
            for (int i = 0; i < 4; i++) {
                net.minecraft.entity.EquipmentSlot slot;
                switch (i) {
                    case 0: slot = net.minecraft.entity.EquipmentSlot.FEET; break;
                    case 1: slot = net.minecraft.entity.EquipmentSlot.LEGS; break;
                    case 2: slot = net.minecraft.entity.EquipmentSlot.CHEST; break;
                    default: slot = net.minecraft.entity.EquipmentSlot.HEAD; break;
                }
                net.minecraft.item.ItemStack armorStack = living.getEquippedStack(slot);
                targetArmor[i] = armorStack.isEmpty() ? "" : net.minecraft.registry.Registries.ITEM.getId(armorStack.getItem()).getPath();
            }
            lastTargetTime = now;
        }
    }

    public static void onDeath() {
        deaths++;
        combo = 0;
        currentStreak = 0;
    }

    public static void tick(net.minecraft.client.network.ClientPlayerEntity player) {
        if (player == null) return;
        
        // 1. Detect death
        boolean isDead = player.isDead() || player.getHealth() <= 0;
        if (isDead && !wasDead) {
            onDeath();
        }
        wasDead = isDead;

        // 2. Simple kill detector
        if (lastTargetTime > 0 && System.currentTimeMillis() - lastTargetTime < 5000 && targetId != -1) {
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
                        lastTargetTime = 0; // reset to avoid double count
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
        if (System.currentTimeMillis() - lastHitTime > 3000) {
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
        if (System.currentTimeMillis() - lastTargetTime > 5000) {
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
