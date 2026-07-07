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

import net.montoyo.mcef.api.IBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

@Mod("fugoclient")
public class FugoClientForge {
    private static KeyMapping modsKeybind;
    private static KeyMapping editKeybind;
    private static KeyMapping zoomKeybind;

    public FugoClientForge() {
        WebUIManager.getInstance().init();

        FMLJavaModLoadingContext.get().getModEventBus().register(this);
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
        CustomTextureManager.tick();

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
            PvPTracker.tick(client.player);
            Autoclicker.tick(client);
            if (client.getWindow() != null) {
                double scale = client.getWindow().getGuiScale();
                int width = client.getWindow().getGuiScaledWidth();
                int height = client.getWindow().getGuiScaledHeight();
                WebUIManager.getInstance().resize((int) (width * scale), (int) (height * scale));
                WebUIManager.getInstance().updateHud();
            }
        }

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

        IBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser == null) return;

        int textureId = browser.getTextureID();
        if (textureId <= 0) return;

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        Matrix4f matrix = event.getGuiGraphics().pose().last().pose();

        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix, 0.0f, (float) height, 0.0f).uv(0.0f, 1.0f).endVertex();
        bufferBuilder.vertex(matrix, (float) width, (float) height, 0.0f).uv(1.0f, 1.0f).endVertex();
        bufferBuilder.vertex(matrix, (float) width, 0.0f, 0.0f).uv(1.0f, 0.0f).endVertex();
        bufferBuilder.vertex(matrix, 0.0f, 0.0f, 0.0f).uv(0.0f, 0.0f).endVertex();
        tessellator.end();
    }

    public static boolean isZooming() {
        return zoomKeybind != null && zoomKeybind.isDown() && Minecraft.getInstance().screen == null;
    }
}