package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.client.render.Camera;
import io.github.fugoclient.mcefmod.FreeLookHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Unique
    private boolean mcef$isSettingRotation = false;

    @Inject(method = "setRotation(FF)V", at = @At("HEAD"), cancellable = true)
    private void onSetRotation(float yaw, float pitch, CallbackInfo ci) {
        if (FreeLookHandler.isActive() && !mcef$isSettingRotation) {
            mcef$isSettingRotation = true;
            setRotation(FreeLookHandler.getFreelookYaw(), FreeLookHandler.getFreelookPitch());
            mcef$isSettingRotation = false;
            ci.cancel();
        }
    }
}
