package io.github.fugoclient.mcefmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.dimaskama.mcef.api.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;

public class WebTitleScreen extends Screen {
    private final boolean isOverlay;
    private final boolean isEditMode;

    // === ZERO-ALLOCATION RENDER CACHE ===
    private final Matrix3x2f cachedMatrix = new Matrix3x2f();
    private TexturedQuadGuiElementRenderState cachedRenderState = null;
    private GpuTextureView lastTextureView = null;
    private int lastRenderWidth = 0;
    private int lastRenderHeight = 0;

    public WebTitleScreen(boolean isOverlay) {
        this(isOverlay, false);
    }

    public WebTitleScreen(boolean isOverlay, boolean isEditMode) {
        super(Text.of(isOverlay ? "Fugo Overlay" : "Fugo Title Screen"));
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
        return MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    @Override
    protected void init() {
        super.init();
        // Resize browser to fill the screen physical size
        double scale = getScaleFactor();
        WebUIManager.getInstance().resize((int) (this.width * scale), (int) (this.height * scale));

        // Focus the browser and notify it of the screen mode
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            browser.setFocus(true);
            WebUIManager.getInstance().resetStateCache();
            WebUIManager.getInstance().onStateChanged();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isOverlay) {
            context.fill(0, 0, this.width, this.height, 0xFF0A0B10);
        }
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        GpuTextureView gpuTextureView = (browser != null) ? browser.getTextureView() : null;

        if (gpuTextureView != null) {
            // Rebuild render state only when texture or dimensions change - ZERO per-frame allocations
            if (gpuTextureView != lastTextureView || width != lastRenderWidth || height != lastRenderHeight || cachedRenderState == null) {
                lastTextureView = gpuTextureView;
                lastRenderWidth = width;
                lastRenderHeight = height;
                cachedMatrix.set(context.getMatrices());
                cachedRenderState = new TexturedQuadGuiElementRenderState(
                    RenderPipelines.GUI_TEXTURED,
                    TextureSetup.of(gpuTextureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
                    cachedMatrix,
                    0, 0, width, height,
                    0.0F, 1.0F, 0.0F, 1.0F,
                    0xFFFFFFFF,
                    new ScreenRect(0, 0, width, height)
                );
            }
            io.github.fugoclient.mcefmod.mixin.DrawContextAccessor accessor = (io.github.fugoclient.mcefmod.mixin.DrawContextAccessor) context;
            accessor.getGuiRenderState().addSimpleElementToCurrentLayer(cachedRenderState);
        } else {
            // Browser not ready, show loading text
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                "Loading Fugo Web UI...",
                this.width / 2,
                this.height / 2 - 10,
                0xFFFFFF
            );
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            browser.onMouseMoved((int) (mouseX * scale), (int) (mouseY * scale));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            Click scaledClick = new Click(click.x() * scale, click.y() * scale, click.buttonInfo());
            browser.onMouseClicked(scaledClick, doubled);
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            Click scaledClick = new Click(click.x() * scale, click.y() * scale, click.buttonInfo());
            browser.onMouseReleased(scaledClick);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            double scale = getScaleFactor();
            browser.onMouseScrolled((int) (mouseX * scale), (int) (mouseY * scale), verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            browser.onKeyPressed(event);
        }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (isOverlay) {
                if (isEditMode) {
                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient.getInstance().setScreen(new WebTitleScreen(true, false));
                    });
                    return true;
                } else {
                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().world == null) {
                            MinecraftClient.getInstance().setScreen(new WebTitleScreen(false, false));
                        } else {
                            MinecraftClient.getInstance().setScreen(null);
                        }
                    });
                    return true;
                }
            } else {
                return true; // Consume escape key on main menu to prevent closing
            }
        }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT && isOverlay) {
            MinecraftClient.getInstance().execute(() -> {
                if (isEditMode) {
                    MinecraftClient.getInstance().setScreen(new WebTitleScreen(true, false));
                } else {
                    if (MinecraftClient.getInstance().world == null) {
                        MinecraftClient.getInstance().setScreen(new WebTitleScreen(false, false));
                    } else {
                        MinecraftClient.getInstance().setScreen(null);
                    }
                }
            });
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyInput event) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            browser.onKeyReleased(event);
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharInput event) {
        MCEFBrowser browser = WebUIManager.getInstance().getBrowser();
        if (browser != null) {
            browser.onCharTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return isOverlay; // Escape closes overlay but not the main menu TitleScreen
    }
}
