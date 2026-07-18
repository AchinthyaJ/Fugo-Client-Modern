package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.fugoclient.mcefmod.Autoclicker;

@Mixin(ClientPlayerEntity.class)
public class LocalPlayerEntityMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        // Fast path: skip if autoclicker not active (99.9% of time)
        if (Autoclicker.isEnabled() && Autoclicker.isActive()) {
            ClientPlayerEntityAccessor accessor = (ClientPlayerEntityAccessor) this;
            // Reset attack cooldown to allow rapid clicks
            accessor.setTicksSinceLastAttack(0);
        }
    }
}
