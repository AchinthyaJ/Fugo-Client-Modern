package io.github.fugoclient.mcefmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.Identifier;
import net.dimaskama.mcef.api.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.gui.ScreenRect;

public class MCEFMod implements ClientModInitializer {

    private static KeyBinding modsKeybind;
    private static KeyBinding editKeybind;
    private static KeyBinding zoomKeybind;

    // === ZERO-ALLOCATION RENDER PATH ===
    private static final Matrix3x2f CACHED_MATRIX = new Matrix3x2f();
    private static TexturedQuadGuiElementRenderState cachedRenderState = null;
    private static GpuTextureView lastTextureView = null;
    private static int lastRenderWidth = 0;
    private static int lastRenderHeight = 0;
    
    // Throttle
    private static int tickCounter = 0;
    private static boolean wasInWorld = false;
    
    // Browser power saving - resize to 1x1 when overlay hidden to save GPU
    private static boolean browserWasMinimized = false;
    private static int fullWidth = 0;
    private static int fullHeight = 0;

    @Override
    public void onInitializeClient() {
        WebUIManager.getInstance().init();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WebUIManager.getInstance().close();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ProcessHandle.allProcesses()
                    .filter(p -> p.info().command().orElse("").contains("jcef_helper"))
                    .forEach(p -> p.destroyForcibly());
            } catch (Throwable ignore) {}
        }));

        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (client.world != null && screen instanceof WebTitleScreen && !WebUIManager.isDisconnecting) {
                if (!((WebTitleScreen) screen).isOverlay()) {
                    client.setScreen(null);
                }
            }
        });

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("fugoclient", "keys"));

        modsKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.fugoclient.toggle_mods", 
            InputUtil.Type.KEYSYM, 
            GLFW.GLFW_KEY_RIGHT_SHIFT, 
            category
        ));

        editKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.fugoclient.toggle_edit", 
            InputUtil.Type.KEYSYM, 
            GLFW.GLFW_KEY_H, 
            category
        ));

        zoomKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.fugoclient.zoom", 
            InputUtil.Type.KEYSYM, 
            GLFW.GLFW_KEY_C, 
            category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            boolean inWorld = client.world != null;
            
            CustomTextureManager.tick();

            while (modsKeybind.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new WebTitleScreen(true, false));
                }
            }

            while (editKeybind.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new WebTitleScreen(true, true));
                }
            }

            if (inWorld) {
                PvPTracker.tick(client.player);
                Autoclicker.tick(client);
                
                if ((tickCounter % 10) == 0) {
                    WebUIManager.getInstance().updateHud();
                    WebUIManager.getInstance().onStateChanged();
                } else if ((tickCounter % 5) == 0) {
                    WebUIManager.getInstance().onStateChanged();
                }
                
                // Browser power saving: minimize browser when overlay hidden
                MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
                boolean overlayVisible = WebUIManager.getInstance().isOverlayVisible();
                
                if (browser != null && client.getWindow() != null) {
                    if (!overlayVisible && !browserWasMinimized) {
                        // Save full size and minimize browser to 1x1 to save GPU/CPU
                        double scale = client.getWindow().getScaleFactor();
                        fullWidth = (int)(client.getWindow().getScaledWidth() * scale);
                        fullHeight = (int)(client.getWindow().getScaledHeight() * scale);
                        browser.resize(1, 1);
                        browserWasMinimized = true;
                    } else if (overlayVisible && browserWasMinimized) {
                        // Restore full size
                        if (fullWidth > 0 && fullHeight > 0) {
                            browser.resize(fullWidth, fullHeight);
                        }
                        browserWasMinimized = false;
                    }
                    
                    // Normal resize check every 20 ticks
                    if ((tickCounter % 20) == 0 && overlayVisible) {
                        double scale = client.getWindow().getScaleFactor();
                        int width = (int)(client.getWindow().getScaledWidth() * scale);
                        int height = (int)(client.getWindow().getScaledHeight() * scale);
                        WebUIManager.getInstance().resize(width, height);
                    }
                }
            }
            
            if (inWorld != wasInWorld) {
                wasInWorld = inWorld;
                WebUIManager.getInstance().onStateChanged();
            }
        });

        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && player.equals(MinecraftClient.getInstance().player)) {
                PvPTracker.onAttack(entity);
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        // ULTRA-LIGHTWEIGHT HUD RENDER - skips entirely when overlay hidden
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.currentScreen instanceof WebTitleScreen) return;

            WebUIManager wm = WebUIManager.getInstance();
            if (!wm.isOverlayVisible()) return;

            MCEFBrowser browser = wm.getBrowser();
            if (browser == null) return;

            GpuTextureView gpuTextureView = browser.getTextureView();
            if (gpuTextureView == null) return;

            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();

            if (gpuTextureView != lastTextureView || width != lastRenderWidth || height != lastRenderHeight || cachedRenderState == null) {
                lastTextureView = gpuTextureView;
                lastRenderWidth = width;
                lastRenderHeight = height;
                CACHED_MATRIX.set(context.getMatrices());

                cachedRenderState = new TexturedQuadGuiElementRenderState(
                    RenderPipelines.GUI_TEXTURED,
                    TextureSetup.of(gpuTextureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
                    CACHED_MATRIX,
                    0, 0, width, height, 0.0F, 1.0F, 0.0F, 1.0F, 0xFFFFFFFF,
                    new ScreenRect(0, 0, width, height)
                );
            }

            io.github.fugoclient.mcefmod.mixin.DrawContextAccessor accessor =
                (io.github.fugoclient.mcefmod.mixin.DrawContextAccessor) context;
            accessor.getGuiRenderState().addSimpleElementToCurrentLayer(cachedRenderState);
        });
    }

    public static boolean isZooming() {
        return zoomKeybind != null && zoomKeybind.isPressed() && MinecraftClient.getInstance().currentScreen == null;
    }
}