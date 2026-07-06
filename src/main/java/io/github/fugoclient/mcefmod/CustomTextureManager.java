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

    public static void loadCustomTextures() {
        if (loaded) return;
        loaded = true;
        
        try {
            File deathClientDir = new File(System.getProperty("user.home"), ".death-client");
            if (!deathClientDir.exists()) {
                System.out.println("[CustomTextureManager] .death-client folder not found at " + deathClientDir.getAbsolutePath());
                return;
            }

            // 2. Load Cape (Prioritize launcher-provided skin-server, fallback to capes directory)
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

                    // Validate dimensions
                    if (width <= 0 || height <= 0) {
                        System.err.println("[CustomTextureManager] Invalid cape dimensions: " + width + "x" + height);
                        return;
                    }

                    // Detect frame dimensions - support various spritesheet formats
                    if (width == 22 || width == 44) {
                        capeFrameHeight = 17;
                    } else if (width == 64 || width == 128) {
                        capeFrameHeight = 32;
                    } else if (width >= 64) {
                        // For custom widths, assume square-ish frames (width = frame width)
                        capeFrameHeight = Math.min(width, height);
                    } else {
                        capeFrameHeight = Math.max(1, width / 2);
                    }

                    // Ensure frame height doesn't exceed total height
                    capeFrameHeight = Math.min(capeFrameHeight, height);

                    capeFramesCount = Math.max(1, height / capeFrameHeight);

                    System.out.println("[CustomTextureManager] Cape dimensions: " + width + "x" + height +
                        ", frame height: " + capeFrameHeight + ", frames: " + capeFramesCount);

                    // Create texture for rendering (one frame)
                    try {
                        frameImage = new NativeImage(width, capeFrameHeight, false);
                        fullCapeImage.copyRect(frameImage, 0, 0, 0, 0, width, capeFrameHeight, false, false);

                        capeTexture = new NativeImageBackedTexture(() -> "custom_cape", frameImage);
                        customCape = Identifier.of("fugoclient", "custom_cape");
                        MinecraftClient.getInstance().getTextureManager().registerTexture(
                            Identifier.of("fugoclient", "textures/custom_cape.png"),
                            capeTexture
                        );
                        System.out.println("[CustomTextureManager] ✓ Loaded cape: " + capeFile.getName() +
                            " (" + capeFramesCount + " frames)");
                    } catch (Exception e) {
                        System.err.println("[CustomTextureManager] Failed to create cape texture:");
                        e.printStackTrace();
                        if (frameImage != null) {
                            frameImage.close();
                        }
                        if (fullCapeImage != null) {
                            fullCapeImage.close();
                            fullCapeImage = null;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[CustomTextureManager] Failed to read cape file: " + capeFile.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("[CustomTextureManager] Error loading custom textures:");
            e.printStackTrace();
        }
    }

    public static void tick() {
        if (!loaded) {
            loadCustomTextures();
        }

        // Animate cape only every 4 ticks (200ms) on low-end hw to save GPU bandwidth
        if (capeFramesCount > 1 && fullCapeImage != null && frameImage != null && capeTexture != null) {
            tickDivider++;
            if (tickDivider >= 4) {
                tickDivider = 0;
                currentCapeFrame = (currentCapeFrame + 1) % capeFramesCount;

                try {
                    int width = fullCapeImage.getWidth();
                    int height = fullCapeImage.getHeight();
                    int srcY = currentCapeFrame * capeFrameHeight;

                    // Bounds check to prevent crashes
                    if (srcY + capeFrameHeight > height) {
                        System.err.println("[CustomTextureManager] Frame out of bounds: srcY=" + srcY +
                            ", frameHeight=" + capeFrameHeight + ", totalHeight=" + height);
                        currentCapeFrame = 0; // Reset to first frame on error
                        srcY = 0;
                    }

                    fullCapeImage.copyRect(frameImage, 0, srcY, 0, 0, width, capeFrameHeight, false, false);
                    capeTexture.upload();
                } catch (Exception e) {
                    System.err.println("[CustomTextureManager] Error updating cape animation:");
                    e.printStackTrace();
                    // Disable animation on error to prevent repeated crashes
                    capeFramesCount = 1;
                }
            }
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
