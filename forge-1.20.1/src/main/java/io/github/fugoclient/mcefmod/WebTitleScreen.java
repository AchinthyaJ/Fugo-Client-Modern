package io.github.fugoclient.mcefmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.montoyo.mcef.api.IBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.awt.Canvas;
import java.awt.event.KeyEvent;

public class WebTitleScreen extends Screen {
    private final boolean isOverlay;
    private final boolean isEditMode;
    private static final Canvas DUMMY_CANVAS = new Canvas();

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

        IBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            // MCEF IBrowser doesn't have setFocus, we can ignore it or let MCEF handle it
            WebUIManager.getInstance().resetStateCache();
            WebUIManager.getInstance().onStateChanged();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!isOverlay) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xFF0A0B10);
        }
        IBrowser browser = WebUIManager.getInstance().getBrowser();
        int textureId = (browser != null) ? browser.getTextureID() : 0;

        if (textureId > 0) {
            RenderSystem.setShaderTexture(0, textureId);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferBuilder = tessellator.getBuilder();

            Matrix4f matrix = guiGraphics.pose().last().pose();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferBuilder.vertex(matrix, 0.0f, (float) height, 0.0f).uv(0.0f, 1.0f).endVertex();
            bufferBuilder.vertex(matrix, (float) width, (float) height, 0.0f).uv(1.0f, 1.0f).endVertex();
            bufferBuilder.vertex(matrix, (float) width, 0.0f, 0.0f).uv(1.0f, 0.0f).endVertex();
            bufferBuilder.vertex(matrix, 0.0f, 0.0f, 0.0f).uv(0.0f, 0.0f).endVertex();
            tessellator.end();
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
        IBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            browser.sendMouseMoveEvent((int) (mouseX * scale), (int) (mouseY * scale), false);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        IBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            int scaledX = (int) (mouseX * scale);
            int scaledY = (int) (mouseY * scale);
            // Map GLFW mouse button to JCEF mouse button:
            // GLFW: 0 = LEFT, 1 = RIGHT, 2 = MIDDLE
            // JCEF: 0 = LEFT, 1 = MIDDLE, 2 = RIGHT
            int jcefButton = 0;
            if (button == 1) jcefButton = 2; // Right
            else if (button == 2) jcefButton = 1; // Middle
            browser.sendMouseClickEvent(scaledX, scaledY, jcefButton, false, 1);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        IBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            int scaledX = (int) (mouseX * scale);
            int scaledY = (int) (mouseY * scale);
            int jcefButton = 0;
            if (button == 1) jcefButton = 2;
            else if (button == 2) jcefButton = 1;
            browser.sendMouseClickEvent(scaledX, scaledY, jcefButton, true, 1);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // Forward mouse wheel/scroll if supported by IBrowser, otherwise ignore
        // Many MCEF versions do not expose scroll, so we can ignore or let standard CEF handle
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private static int mapGlfwToAwt(int glfwKey) {
        switch (glfwKey) {
            case GLFW.GLFW_KEY_BACKSPACE: return KeyEvent.VK_BACK_SPACE;
            case GLFW.GLFW_KEY_TAB: return KeyEvent.VK_TAB;
            case GLFW.GLFW_KEY_ENTER: return KeyEvent.VK_ENTER;
            case GLFW.GLFW_KEY_ESCAPE: return KeyEvent.VK_ESCAPE;
            case GLFW.GLFW_KEY_SPACE: return KeyEvent.VK_SPACE;
            case GLFW.GLFW_KEY_LEFT: return KeyEvent.VK_LEFT;
            case GLFW.GLFW_KEY_UP: return KeyEvent.VK_UP;
            case GLFW.GLFW_KEY_RIGHT: return KeyEvent.VK_RIGHT;
            case GLFW.GLFW_KEY_DOWN: return KeyEvent.VK_DOWN;
            case GLFW.GLFW_KEY_DELETE: return KeyEvent.VK_DELETE;
            case GLFW.GLFW_KEY_HOME: return KeyEvent.VK_HOME;
            case GLFW.GLFW_KEY_END: return KeyEvent.VK_END;
            case GLFW.GLFW_KEY_PAGE_UP: return KeyEvent.VK_PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN: return KeyEvent.VK_PAGE_DOWN;
            default:
                return glfwKey;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        IBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            int awtKey = mapGlfwToAwt(keyCode);
            KeyEvent ev = new KeyEvent(DUMMY_CANVAS, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, awtKey, (char) 0);
            browser.sendKeyEvent(ev);
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
        IBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            int awtKey = mapGlfwToAwt(keyCode);
            KeyEvent ev = new KeyEvent(DUMMY_CANVAS, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), modifiers, awtKey, (char) 0);
            browser.sendKeyEvent(ev);
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        IBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            KeyEvent ev = new KeyEvent(DUMMY_CANVAS, KeyEvent.KEY_TYPED, System.currentTimeMillis(), modifiers, 0, codePoint);
            browser.sendKeyEvent(ev);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return isOverlay;
    }
}
