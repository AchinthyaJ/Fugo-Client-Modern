package io.github.fugoclient.mcefmod.mixin;

import io.github.fugoclient.mcefmod.MinecraftClientAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftMixin extends MinecraftClientAccessor {
    @Accessor("user")
    @Override
    void setSession(User session);

    @Invoker("startAttack")
    @Override
    boolean invokeDoAttack();

    @Invoker("startUseItem")
    @Override
    void invokeDoItemUse();
}
