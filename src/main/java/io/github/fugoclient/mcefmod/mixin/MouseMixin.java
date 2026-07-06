package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.fugoclient.mcefmod.WebUIManager;
import io.github.fugoclient.mcefmod.FreeLookHandler;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "method_22684", at = @At("TAIL"))
    private void onMouseInput(long window, int button, int action, int modifiers, CallbackInfo ci) {
        WebUIManager.getInstance().updateHudKeysOnly();
    }

    @Redirect(method = "updateMouse(D)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void redirectChangeLookDirection(net.minecraft.client.network.ClientPlayerEntity player, double dx, double dy) {
        if (FreeLookHandler.isActive()) {
            FreeLookHandler.handleMouseInput(dx, dy);
        } else {
            player.changeLookDirection(dx, dy);
        }
    }
}
