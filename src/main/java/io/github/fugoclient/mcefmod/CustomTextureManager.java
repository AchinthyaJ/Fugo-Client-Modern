package io.github.fugoclient.mcefmod;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public class CustomTextureManager {
    private static Identifier customCape = null;
    
    private static NativeImage fullCapeImage = null;
    private static NativeImage frameImage = null;
    private static NativeImageBackedTexture capeTexture = null;
    private static int capeFrameHeight = 32;
    private static int capeFramesCount = 1;
    private static int currentCapeFrame = 0;
    private static int tickDivider = 0;
    
    private static boolean loaded = false;
    private static boolean loadAttempted = false;

    public static void loadCustomTextures() {
        if (loaded || loadAttempted) return;
        loadAttempted = true;
        
        try {
            File deathClientDir = new File(System.getProperty("user.home"), ".death-client");
            if (!deathClientDir.exists()) {
                return;
            }

            File capeFile = new File(deathClientDir, "skin-server/current-cape.png");
            if (!capeFile.exists()) {
                File capesDir = new File(deathClientDir, "capes");
                if (capesDir.exists() && capesDir.isDirectory()) {
                    File[] files = capesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
                    if (files != null && files.length > 0) {
                        capeFile = files[0];
                    }
                }
            }

            if (capeFile.exists()) {
                try (InputStream is = new FileInputStream(capeFile)) {
                    fullCapeImage = NativeImage.read(is);
                    int width = fullCapeImage.getWidth();
                    int height = fullCapeImage.getHeight();

                    if (width <= 0 || height <= 0) {
                        return;
                    }

                    if (width == 22 || width == 44) {
                        capeFrameHeight = 17;
                    } else if (width == 64 || width == 128) {
                        capeFrameHeight = 32;
                    } else if (width >= 64) {
                        capeFrameHeight = Math.min(width, height);
                    } else {
                        capeFrameHeight = Math.max(1, width / 2);
                    }

                    capeFrameHeight = Math.min(capeFrameHeight, height);
                    capeFramesCount = Math.max(1, height / capeFrameHeight);

                    try {
                        frameImage = new NativeImage(width, capeFrameHeight, false);
                        fullCapeImage.copyRect(frameImage, 0, 0, 0, 0, width, capeFrameHeight, false, false);

                        capeTexture = new NativeImageBackedTexture(() -> "custom_cape", frameImage);
                        customCape = Identifier.of("fugoclient", "custom_cape");
                        MinecraftClient.getInstance().getTextureManager().registerTexture(
                            Identifier.of("fugoclient", "textures/custom_cape.png"),
                            capeTexture
                        );
                        loaded = true;
                    } catch (Exception e) {
                        if (frameImage != null) frameImage.close();
                        if (fullCapeImage != null) {
                            fullCapeImage.close();
                            fullCapeImage = null;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    public static void tick() {
        if (!loaded) {
            if (!loadAttempted) loadCustomTextures();
            return;
        }

        // Fast path: single-frame capes need no animation work
        if (capeFramesCount <= 1) return;
        if (fullCapeImage == null || frameImage == null || capeTexture == null) return;

        tickDivider++;
        if (tickDivider < 4) return;
        tickDivider = 0;
        currentCapeFrame = (currentCapeFrame + 1) % capeFramesCount;

        try {
            int width = fullCapeImage.getWidth();
            int height = fullCapeImage.getHeight();
            int srcY = currentCapeFrame * capeFrameHeight;

            if (srcY + capeFrameHeight > height) {
                currentCapeFrame = 0;
                srcY = 0;
            }

            fullCapeImage.copyRect(frameImage, 0, srcY, 0, 0, width, capeFrameHeight, false, false);
            capeTexture.upload();
        } catch (Exception e) {
            capeFramesCount = 1;
        }
    }

    public static Identifier getCustomCape() {
        return customCape;
    }

    public static int getCapeWidth() {
        return frameImage != null ? frameImage.getWidth() : 64;
    }

    public static int getCapeHeight() {
        return frameImage != null ? frameImage.getHeight() : 32;
    }
}