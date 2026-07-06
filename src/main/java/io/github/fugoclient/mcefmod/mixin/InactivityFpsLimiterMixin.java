package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.client.option.InactivityFpsLimiter;
import net.minecraft.client.MinecraftClient;
import io.github.fugoclient.mcefmod.WebTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public class InactivityFpsLimiterMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onUpdate(CallbackInfoReturnable<Integer> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen instanceof WebTitleScreen) {
            cir.setReturnValue(client.options.getMaxFps().getValue());
        }
    }
}
