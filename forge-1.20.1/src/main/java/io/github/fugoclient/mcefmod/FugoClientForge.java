package io.github.fugoclient.mcefmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

@Mod("fugoclient")
public class FugoClientForge {
    private static KeyMapping modsKeybind;
    private static KeyMapping editKeybind;
    private static KeyMapping zoomKeybind;

    // Throttle counters
    private int tickCounter = 0;
    private int pvpTickCounter = 0;
    private static final int PVP_TICK_INTERVAL = 5;

    public FugoClientForge() {
        WebUIManager.getInstance().init();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerKeyMappings);
        MinecraftForge.EVENT_BUS.register(this);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ProcessHandle.allProcesses()
                    .filter(p -> p.info().command().orElse("").contains("jcef_helper"))
                    .forEach(p -> p.destroyForcibly());
            } catch (Throwable ignore) {}
        }));
    }

    @SubscribeEvent
    public void registerKeyMappings(RegisterKeyMappingsEvent event) {
        modsKeybind = new KeyMapping(
            "key.fugoclient.toggle_mods",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "key.categories.fugoclient"
        );
        editKeybind = new KeyMapping(
            "key.fugoclient.toggle_edit",
            GLFW.GLFW_KEY_H,
            "key.categories.fugoclient"
        );
        zoomKeybind = new KeyMapping(
            "key.fugoclient.zoom",
            GLFW.GLFW_KEY_C,
            "key.categories.fugoclient"
        );

        event.register(modsKeybind);
        event.register(editKeybind);
        event.register(zoomKeybind);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft client = Minecraft.getInstance();
        tickCounter++;

        WebUIManager.getInstance().ensureBrowser();

        // CustomTextureManager - throttle to every 2 ticks
        if ((tickCounter & 1) == 0) {
            CustomTextureManager.tick();
        }

        if (modsKeybind != null && editKeybind != null) {
            while (modsKeybind.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new WebTitleScreen(true, false));
                }
            }

            while (editKeybind.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new WebTitleScreen(true, true));
                }
            }
        }

        if (client.level != null) {
            // PvPTracker - throttle to every 5 ticks
            pvpTickCounter++;
            if (pvpTickCounter >= PVP_TICK_INTERVAL) {
                pvpTickCounter = 0;
                PvPTracker.tick(client.player);
            }
            Autoclicker.tick(client);
            
            // WebUI updates - every tick for zero delay
            if (client.getWindow() != null) {
                double scale = client.getWindow().getGuiScale();
                int width = client.getWindow().getGuiScaledWidth();
                int height = client.getWindow().getGuiScaledHeight();
                WebUIManager.getInstance().resize((int) (width * scale), (int) (height * scale));
                WebUIManager.getInstance().updateHud();
            }
        }

        // State changes - every tick
        WebUIManager.getInstance().onStateChanged();
    }

    @SubscribeEvent
    public void onScreenInitPost(ScreenEvent.Init.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && event.getScreen() instanceof WebTitleScreen && !WebUIManager.isDisconnecting) {
            if (!((WebTitleScreen) event.getScreen()).isOverlay()) {
                client.setScreen(null);
            }
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide() && event.getEntity().equals(Minecraft.getInstance().player)) {
            PvPTracker.onAttack(event.getTarget());
        }
    }

    @SubscribeEvent
    public void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type() && WebUIManager.isVanillaCrosshairDisabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderGuiOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.screen instanceof WebTitleScreen) return;

        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser == null) return;

        int textureId = browser.getRenderer().getTextureID();
        if (textureId <= 0) return;

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        Matrix4f matrix = event.getGuiGraphics().pose().last().pose();

        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix, 0.0f, (float) height, 0.0f).uv(0.0f, 1.0f).endVertex();
        bufferBuilder.vertex(matrix, (float) width, (float) height, 0.0f).uv(1.0f, 1.0f).endVertex();
        bufferBuilder.vertex(matrix, (float) width, 0.0f, 0.0f).uv(1.0f, 0.0f).endVertex();
        bufferBuilder.vertex(matrix, 0.0f, 0.0f, 0.0f).uv(0.0f, 0.0f).endVertex();
        tessellator.end();
        RenderSystem.disableBlend();
    }

    public static boolean isZooming() {
        return zoomKeybind != null && zoomKeybind.isDown() && Minecraft.getInstance().screen == null;
    }
}