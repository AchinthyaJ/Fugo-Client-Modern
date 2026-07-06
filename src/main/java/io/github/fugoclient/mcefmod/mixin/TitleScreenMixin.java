package io.github.fugoclient.mcefmod.mixin;

import io.github.fugoclient.mcefmod.WebUIManager;
import io.github.fugoclient.mcefmod.WebTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class TitleScreenMixin {

    @Shadow public abstract void setScreen(Screen screen);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        // Intercept vanilla TitleScreen initialization and redirect to WebTitleScreen
        if (screen != null && screen.getClass() == TitleScreen.class) {
            this.setScreen(WebUIManager.getInstance().getOrCreateTitleScreen());
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void onSetScreenReturn(Screen screen, CallbackInfo ci) {
        WebUIManager.getInstance().onStateChanged();
    }
}

