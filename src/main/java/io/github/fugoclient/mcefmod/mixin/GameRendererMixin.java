package io.github.fugoclient.mcefmod.mixin;

import io.github.fugoclient.mcefmod.MCEFMod;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Unique
    private float currentZoomProgress = 1.0f;

    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)F", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        boolean zooming = MCEFMod.isZooming();
        float targetProgress = zooming ? 0.125f : 1.0f; // 8x zoom at full zoom
        
        // Smooth exponential interpolation
        float speed = zooming ? 0.18f : 0.22f;
        currentZoomProgress += (targetProgress - currentZoomProgress) * speed;
        
        // Clamp close values to target
        if (Math.abs(currentZoomProgress - targetProgress) < 0.005f) {
            currentZoomProgress = targetProgress;
        }

        if (currentZoomProgress < 1.0f) {
            cir.setReturnValue(cir.getReturnValue() * currentZoomProgress);
        }
    }
}
