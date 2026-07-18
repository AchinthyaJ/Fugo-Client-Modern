package io.github.fugoclient.mcefmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class WebTitleScreen extends Screen {
    private final boolean isOverlay;
    private final boolean isEditMode;

    public WebTitleScreen(boolean isOverlay) {
        this(isOverlay, false);
    }

    public WebTitleScreen(boolean isOverlay, boolean isEditMode) {
        super(Component.literal(isOverlay ? "Fugo Overlay" : "Fugo Title Screen"));
        this.isOverlay = isOverlay;
        this.isEditMode = isEditMode;
    }

    public boolean isOverlay() {
        return isOverlay;
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    private double getScaleFactor() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    @Override
    protected void init() {
        super.init();
        double scale = getScaleFactor();
        WebUIManager.getInstance().resize((int) (this.width * scale), (int) (this.height * scale));

        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            WebUIManager.getInstance().resetStateCache();
            WebUIManager.getInstance().onStateChanged();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!isOverlay) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xFF0A0B10);
        }
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        int textureId = (browser != null) ? browser.getRenderer().getTextureID() : 0;

        if (textureId > 0) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, textureId);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);

            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tessellator.getBuilder();

            Matrix4f matrix = guiGraphics.pose().last().pose();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferBuilder.vertex(matrix, 0.0f, (float) height, 0.0f).uv(0.0f, 1.0f).endVertex();
            bufferBuilder.vertex(matrix, (float) width, (float) height, 0.0f).uv(1.0f, 1.0f).endVertex();
            bufferBuilder.vertex(matrix, (float) width, 0.0f, 0.0f).uv(1.0f, 0.0f).endVertex();
            bufferBuilder.vertex(matrix, 0.0f, 0.0f, 0.0f).uv(0.0f, 0.0f).endVertex();
            tessellator.end();
            RenderSystem.disableBlend();
        } else {
            guiGraphics.drawCenteredString(
                this.font,
                "Loading Fugo Web UI...",
                this.width / 2,
                this.height / 2 - 10,
                0xFFFFFF
            );
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            browser.sendMouseMove((int) (mouseX * scale), (int) (mouseY * scale));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            int scaledX = (int) (mouseX * scale);
            int scaledY = (int) (mouseY * scale);
            // GLFW mouse button: 0 = LEFT, 1 = RIGHT, 2 = MIDDLE (CinemaMod MCEF expects GLFW convention directly)
            browser.sendMousePress(scaledX, scaledY, button);
            browser.setFocus(true);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            int scaledX = (int) (mouseX * scale);
            int scaledY = (int) (mouseY * scale);
            browser.sendMouseRelease(scaledX, scaledY, button);
            browser.setFocus(true);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            int scaledX = (int) (mouseX * scale);
            int scaledY = (int) (mouseY * scale);
            int scrollAmount = (int) Math.round(amount * 120); // Convert to standard scroll units
            browser.sendMouseWheel(scaledX, scaledY, scrollAmount, 0);
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            browser.sendKeyPress(keyCode, scanCode, modifiers);
            browser.setFocus(true);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (isOverlay) {
                if (isEditMode) {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(new WebTitleScreen(true, false));
                    });
                    return true;
                } else {
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().level == null) {
                            Minecraft.getInstance().setScreen(new WebTitleScreen(false, false));
                        } else {
                            Minecraft.getInstance().setScreen(null);
                        }
                    });
                    return true;
                }
            } else {
                return true; // Consume escape key on main menu to prevent closing
            }
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT && isOverlay) {
            Minecraft.getInstance().execute(() -> {
                if (isEditMode) {
                    Minecraft.getInstance().setScreen(new WebTitleScreen(true, false));
                } else {
                    if (Minecraft.getInstance().level == null) {
                        Minecraft.getInstance().setScreen(new WebTitleScreen(false, false));
                    } else {
                        Minecraft.getInstance().setScreen(null);
                    }
                }
            });
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
            browser.setFocus(true);
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            browser.sendKeyTyped(codePoint, modifiers);
            browser.setFocus(true);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return isOverlay;
    }
}