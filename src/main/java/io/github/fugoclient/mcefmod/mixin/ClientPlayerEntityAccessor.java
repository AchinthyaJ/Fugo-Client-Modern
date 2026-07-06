package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface ClientPlayerEntityAccessor {
    @Accessor("ticksSinceLastAttack")
    void setTicksSinceLastAttack(int time);

    @Accessor("ticksSinceLastAttack")
    int getTicksSinceLastAttack();
}
