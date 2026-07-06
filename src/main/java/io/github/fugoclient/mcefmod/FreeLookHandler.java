package io.github.fugoclient.mcefmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class FreeLookHandler {
    private static boolean active = false;
    private static Perspective savedPerspective = Perspective.FIRST_PERSON;
    private static float freelookYaw = 0.0f;
    private static float freelookPitch = 0.0f;
    private static boolean wasAltPressed = false;

    public static boolean isActive() {
        return active;
    }

    public static float getFreelookYaw() {
        return freelookYaw;
    }

    public static float getFreelookPitch() {
        return freelookPitch;
    }

    public static void update(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            if (active) {
                disable(client);
            }
            return;
        }

        // Check if ALT is pressed (both Left and Right Alt)
        boolean isAltPressed = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_ALT) || 
                              InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);

        // Do not activate/maintain freelook if a screen is open
        if (client.currentScreen != null) {
            isAltPressed = false;
        }

        if (isAltPressed) {
            if (!wasAltPressed) {
                // Activate freelook
                active = true;
                savedPerspective = client.options.getPerspective();
                // Force perspective to THIRD_PERSON_BACK
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                
                // Initialize freelook yaw/pitch to player's current view angles
                freelookYaw = client.player.getYaw();
                freelookPitch = client.player.getPitch();
                wasAltPressed = true;
            }
        } else {
            if (wasAltPressed) {
                // Deactivate freelook
                disable(client);
                wasAltPressed = false;
            }
        }
    }

    private static void disable(MinecraftClient client) {
        active = false;
        client.options.setPerspective(savedPerspective);
    }

    public static void handleMouseInput(double dx, double dy) {
        if (!active) return;
        
        // Accumulate rotation using same scale factor (0.15) as Entity.changeLookDirection
        freelookYaw += (float) dx * 0.15f;
        freelookPitch += (float) dy * 0.15f;

        // Clamp pitch to [-90, 90]
        if (freelookPitch < -90.0f) {
            freelookPitch = -90.0f;
        } else if (freelookPitch > 90.0f) {
            freelookPitch = 90.0f;
        }
    }
}
