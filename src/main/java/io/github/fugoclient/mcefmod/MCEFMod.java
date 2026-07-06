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
import net.minecraft.client.render.RenderTickCounter;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class MCEFMod implements ClientModInitializer {

    private static KeyBinding modsKeybind;
    private static KeyBinding editKeybind;
    private static KeyBinding zoomKeybind;

    @Override
    public void onInitializeClient() {
        System.out.println("[Fugo Client] Initializing mod...");

        // Initialize the browser manager
        WebUIManager.getInstance().init();

        // Close browser on client stop to free memory immediately
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            System.out.println("[Fugo Client] Closing browser and freeing memory...");
            WebUIManager.getInstance().close();
        });

        // Register JVM shutdown hook to kill jcef_helper processes cleanly
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("[Fugo Client] JVM shutting down. Terminating any remaining jcef_helper processes...");
                ProcessHandle.allProcesses()
                    .filter(p -> p.info().command().orElse("").contains("jcef_helper"))
                    .forEach(p -> {
                        try {
                            p.destroyForcibly();
                        } catch (Throwable ignore) {}
                    });
            } catch (Throwable ignore) {}
        }));

        // Auto-close title screen when entering a world
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (client.world != null && screen instanceof WebTitleScreen && !WebUIManager.isDisconnecting) {
                WebTitleScreen titleScreen = (WebTitleScreen) screen;
                if (!titleScreen.isOverlay()) {
                    client.setScreen(null);
                }
            }
        });

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("fugoclient", "keys"));

        // Register Right Shift keybind to toggle the mods/modules overlay
        modsKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.fugoclient.toggle_mods", 
            InputUtil.Type.KEYSYM, 
            GLFW.GLFW_KEY_RIGHT_SHIFT, 
            category
        ));

        // Register H keybind to toggle HUD layout edit mode
        editKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.fugoclient.toggle_edit", 
            InputUtil.Type.KEYSYM, 
            GLFW.GLFW_KEY_H, 
            category
        ));

        // Register C keybind for zoom
        zoomKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.fugoclient.zoom", 
            InputUtil.Type.KEYSYM, 
            GLFW.GLFW_KEY_C, 
            category
        ));

        // Register client tick handler to poll the keybinding and update HUD data
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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

            if (client.world != null) {
                PvPTracker.tick(client.player);
                io.github.fugoclient.mcefmod.Autoclicker.tick(client);
                if (client.getWindow() != null) {
                    double scale = client.getWindow().getScaleFactor();
                    int width = client.getWindow().getScaledWidth();
                    int height = client.getWindow().getScaledHeight();
                    WebUIManager.getInstance().resize((int) (width * scale), (int) (height * scale));
                    WebUIManager.getInstance().updateHud();
                }
            }

            WebUIManager.getInstance().onStateChanged();
        });

        // Register Attack Callback to count combos/reach/target info
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && player.equals(MinecraftClient.getInstance().player)) {
                PvPTracker.onAttack(entity);
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        // Register HUD rendering callback - only render if overlay is visible
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.currentScreen instanceof WebTitleScreen) return;

            MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
            if (browser == null) return;

            GpuTextureView gpuTextureView = browser.getTextureView();
            if (gpuTextureView == null) return;

            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();

            io.github.fugoclient.mcefmod.mixin.DrawContextAccessor accessor =
                (io.github.fugoclient.mcefmod.mixin.DrawContextAccessor) context;

            accessor.getGuiRenderState().addSimpleElementToCurrentLayer(new TexturedQuadGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.of(gpuTextureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
                new Matrix3x2f(context.getMatrices()),
                0, 0, width, height, 0.0F, 1.0F, 0.0F, 1.0F, 0xFFFFFFFF,
                new net.minecraft.client.gui.ScreenRect(0, 0, width, height)
            ));
        });
    }

    public static boolean isZooming() {
        return zoomKeybind != null && zoomKeybind.isPressed() && net.minecraft.client.MinecraftClient.getInstance().currentScreen == null;
    }
}
