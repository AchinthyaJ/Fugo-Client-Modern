package io.github.fugoclient.mcefmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.fugoclient.mcefmod.WebUIManager;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright for MC 1.21.11.
 *
 * The lightmap is now generated on the GPU, so the old approach of overwriting a
 * NativeImage-backed texture no longer works (those fields don't exist anymore).
 * Instead we override the gamma value that {@code update(F)V} feeds into the
 * lightmap computation. Returning a very large gamma washes the whole lightmap
 * to full brightness, which is the standard, version-stable way to do fullbright.
 *
 * The gamma read is the third and final {@code SimpleOption.getValue()} call in
 * {@code update} (immediately after {@code GameOptions.getGamma()}), hence ordinal = 2.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @ModifyExpressionValue(
        method = "update(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
            ordinal = 2
        )
    )
    private Object fugo$fullbrightGamma(Object original) {
        if (WebUIManager.isFullbrightEnabled()) {
            return Double.valueOf(15.0);
        }
        return original;
    }
}
