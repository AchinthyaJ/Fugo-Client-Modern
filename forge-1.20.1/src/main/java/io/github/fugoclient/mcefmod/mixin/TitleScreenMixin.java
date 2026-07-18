package io.github.fugoclient.mcefmod.mixin;

import io.github.fugoclient.mcefmod.WebUIManager;
import io.github.fugoclient.mcefmod.WebTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class TitleScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        // Intercept vanilla TitleScreen initialization and redirect to WebTitleScreen
        if (screen != null && screen.getClass() == TitleScreen.class) {
            ((Minecraft) (Object) this).setScreen(WebUIManager.getInstance().getOrCreateTitleScreen());
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void onSetScreenReturn(Screen screen, CallbackInfo ci) {
        WebUIManager.getInstance().onStateChanged();
    }
}
