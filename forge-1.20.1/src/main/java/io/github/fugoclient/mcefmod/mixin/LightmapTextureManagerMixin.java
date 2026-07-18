package io.github.fugoclient.mcefmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.fugoclient.mcefmod.WebUIManager;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class LightmapTextureManagerMixin {

    @ModifyExpressionValue(
        method = "updateLightTexture",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"
        )
    )
    private Object fugo$fullbrightGamma(Object original) {
        if (WebUIManager.isFullbrightEnabled()) {
            return Double.valueOf(15.0);
        }
        return original;
    }
}
