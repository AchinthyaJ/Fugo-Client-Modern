package io.github.fugoclient.mcefmod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class Autoclicker {
    private static boolean enabled = false;
    private static int cps = 12;
    private static String button = "left"; // "left" or "right"
    private static String keybind = "G";
    private static int keybindCode = GLFW.GLFW_KEY_G;
    
    private static boolean active = false; // toggle state
    private static long lastClickTime = 0;

    public static void setSettings(boolean enabledVal, int cpsVal, String buttonVal, String keybindVal) {
        enabled = enabledVal;
        cps = Math.max(1, Math.min(25, cpsVal));
        button = buttonVal != null ? buttonVal.toLowerCase() : "left";
        keybind = keybindVal != null ? keybindVal.toUpperCase() : "G";
        keybindCode = getGlfwKey(keybind);
        System.out.println("[Autoclicker] Settings updated: enabled=" + enabled + ", keybind=" + keybind + ", keybindCode=" + keybindCode);
        if (!enabled) {
            active = false;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isActive() {
        return active;
    }

    public static int getCps() {
        return cps;
    }

    public static String getButton() {
        return button;
    }

    public static String getKeybind() {
        return keybind;
    }

    public static void onKey(int key, int action) {
        if (!enabled) return;
        System.out.println("[Autoclicker] Key pressed: " + key + ", keybindCode: " + keybindCode + ", action: " + action);
        if (action == GLFW.GLFW_PRESS && key == keybindCode) {
            Minecraft client = Minecraft.getInstance();
            if (client.screen == null) {
                active = !active;
                System.out.println("[Autoclicker] Toggled: " + active);
                if (client.player != null) {
                    client.player.displayClientMessage(
                        Component.literal("§8[§6Aether§8] §fAutoclicker: " + (active ? "§aON" : "§cOFF")),
                        true // action bar message
                    );
                }
            }
        }
    }

    public static void tick(Minecraft client) {
        if (!enabled) {
            return;
        }
        if (!active) {
            return;
        }
        if (client.player == null || client.level == null) {
            return;
        }
        if (client.screen != null) {
            return;
        }

        MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;

        boolean isHeld = false;
        if ("left".equals(button)) {
            isHeld = client.options.keyAttack.isDown();
        } else if ("right".equals(button)) {
            isHeld = client.options.keyUse.isDown();
        }

        if (isHeld) {
            long now = System.currentTimeMillis();
            long interval = 1000 / cps;
            if (now - lastClickTime >= interval) {
                lastClickTime = now;
                System.out.println("[Autoclicker] Performing " + button + " click at " + now);
                try {
                    if ("left".equals(button)) {
                        boolean result = accessor.invokeDoAttack();
                        System.out.println("[Autoclicker] Attack returned: " + result);
                    } else {
                        accessor.invokeDoItemUse();
                        System.out.println("[Autoclicker] ItemUse invoked");
                    }
                } catch (Exception e) {
                    System.err.println("[Autoclicker] Error during click: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private static int getGlfwKey(String keyName) {
        if (keyName == null || keyName.isEmpty()) return -1;
        keyName = keyName.toUpperCase();
        if (keyName.length() == 1) {
            return keyName.charAt(0);
        }
        switch (keyName) {
            case "LSHIFT": return 340;
            case "RSHIFT": return 344;
            case "LCONTROL": return 341;
            case "RCONTROL": return 345;
            case "LALT": return 342;
            case "RALT": return 346;
            case "SPACE": return 32;
            case "ENTER": return 257;
            case "TAB": return 258;
            case "ESCAPE": return 256;
            case "BACKSPACE": return 259;
            case "INSERT": return 260;
            case "DELETE": return 261;
            case "RIGHT": return 262;
            case "LEFT": return 263;
            case "DOWN": return 264;
            case "UP": return 265;
            default: {
                System.out.println("[Autoclicker] Unknown key: " + keyName);
                return -1;
            }
        }
    }
}
