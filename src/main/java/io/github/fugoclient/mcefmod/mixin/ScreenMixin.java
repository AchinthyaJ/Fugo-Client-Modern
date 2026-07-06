package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "init()V", at = @At("HEAD"))
    private void onInitScreen(CallbackInfo ci) {
        // Parameterless init hook for Minecraft 1.21.11 compatibility
    }
}
