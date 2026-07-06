package io.github.fugoclient.mcefmod.mixin;

import io.github.fugoclient.mcefmod.CustomTextureManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerEntityMixin {

    private SkinTextures cachedSkinTextures;
    private SkinTextures lastOriginalSkinTextures;

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> info) {
        SkinTextures original = info.getReturnValue();
        if (original == null) return;

        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        // Only override skin/cape for the local player
        if (net.minecraft.client.MinecraftClient.getInstance().player != null && 
            player.getUuid().equals(net.minecraft.client.MinecraftClient.getInstance().player.getUuid())) {
            
            Identifier customCape = CustomTextureManager.getCustomCape();

            if (customCape != null) {
                if (this.cachedSkinTextures == null || this.lastOriginalSkinTextures != original) {
                    this.lastOriginalSkinTextures = original;
                    AssetInfo.TextureAsset skin = original.body();
                    AssetInfo.TextureAsset cape = new AssetInfo.TextureAssetInfo(customCape);
                    AssetInfo.TextureAsset elytra = new AssetInfo.TextureAssetInfo(customCape);
                    this.cachedSkinTextures = new SkinTextures(
                        skin,
                        cape,
                        elytra, 
                        original.model(),
                        original.secure()
                    );
                }
                info.setReturnValue(this.cachedSkinTextures);
            }
        }
    }
}
