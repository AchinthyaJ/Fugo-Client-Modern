package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightTexture.class)
public interface LightmapTextureManagerAccessor {
    @Accessor("updateLightTexture")
    void setDirty(boolean dirty);
}
