package io.github.fugoclient.mcefmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class FreeLookHandler {
    private static boolean active = false;
    private static CameraType savedPerspective = CameraType.FIRST_PERSON;
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

    public static void update(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (active) {
                disable(client);
            }
            return;
        }

        boolean isAltPressed = InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) || 
                              InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);

        if (client.screen != null) {
            isAltPressed = false;
        }

        if (isAltPressed) {
            if (!wasAltPressed) {
                active = true;
                savedPerspective = client.options.getCameraType();
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                
                freelookYaw = client.player.getYRot();
                freelookPitch = client.player.getXRot();
                wasAltPressed = true;
            }
        } else {
            if (wasAltPressed) {
                disable(client);
                wasAltPressed = false;
            }
        }
    }

    private static void disable(Minecraft client) {
        active = false;
        client.options.setCameraType(savedPerspective);
    }

    public static void handleMouseInput(double dx, double dy) {
        if (!active) return;
        
        freelookYaw += (float) dx * 0.15f;
        freelookPitch += (float) dy * 0.15f;

        if (freelookPitch < -90.0f) {
            freelookPitch = -90.0f;
        } else if (freelookPitch > 90.0f) {
            freelookPitch = 90.0f;
        }
    }
}
