package io.github.fugoclient.mcefmod;

import net.minecraft.client.User;

public interface MinecraftClientAccessor {
    void setSession(User session);
    boolean invokeDoAttack();
    void invokeDoItemUse();
}
