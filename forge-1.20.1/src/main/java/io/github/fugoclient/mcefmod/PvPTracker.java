package io.github.fugoclient.mcefmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

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
            
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                reach = client.player.distanceTo(target);
            }
            
            targetId = target.getId();
            targetName = target.getName().getString();
            targetHealth = living.getHealth();
            targetMaxHealth = living.getMaxHealth();
            targetDistance = reach;
            
            for (int i = 0; i < 4; i++) {
                EquipmentSlot slot;
                switch (i) {
                    case 0: slot = EquipmentSlot.FEET; break;
                    case 1: slot = EquipmentSlot.LEGS; break;
                    case 2: slot = EquipmentSlot.CHEST; break;
                    default: slot = EquipmentSlot.HEAD; break;
                }
                ItemStack armorStack = living.getItemBySlot(slot);
                targetArmor[i] = armorStack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(armorStack.getItem()).getPath();
            }
            lastTargetTime = now;
        }
    }

    public static void onDeath() {
        deaths++;
        combo = 0;
        currentStreak = 0;
    }

    public static void tick(LocalPlayer player) {
        if (player == null) return;
        
        boolean isDead = !player.isAlive() || player.getHealth() <= 0.0f;
        if (isDead && !wasDead) {
            onDeath();
        }
        wasDead = isDead;

        if (lastTargetTime > 0 && System.currentTimeMillis() - lastTargetTime < 5000 && targetId != -1) {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null) {
                Entity e = client.level.getEntity(targetId);
                if (e instanceof LivingEntity) {
                    LivingEntity living = (LivingEntity) e;
                    if (!living.isAlive() || living.getHealth() <= 0.0f) {
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
