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

    private static final float MOUSE_SENSITIVITY = 0.15f;
    private static final float PITCH_CLAMP = 90.0f;

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

        boolean isAltPressed = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_ALT) || 
                               InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);

        if (client.currentScreen != null) {
            isAltPressed = false;
        }

        if (isAltPressed) {
            if (!wasAltPressed) {
                active = true;
                savedPerspective = client.options.getPerspective();
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                freelookYaw = client.player.getYaw();
                freelookPitch = client.player.getPitch();
            }
        } else if (wasAltPressed) {
            disable(client);
        }
        wasAltPressed = isAltPressed;
    }

    private static void disable(MinecraftClient client) {
        active = false;
        client.options.setPerspective(savedPerspective);
    }

    public static void handleMouseInput(double dx, double dy) {
        if (!active) return;
        
        freelookYaw += (float) dx * MOUSE_SENSITIVITY;
        freelookPitch += (float) dy * MOUSE_SENSITIVITY;

        if (freelookPitch < -PITCH_CLAMP) {
            freelookPitch = -PITCH_CLAMP;
        } else if (freelookPitch > PITCH_CLAMP) {
            freelookPitch = PITCH_CLAMP;
        }
    }
}