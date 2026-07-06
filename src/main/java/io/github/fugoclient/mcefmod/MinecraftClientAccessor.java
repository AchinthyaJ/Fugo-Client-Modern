package io.github.fugoclient.mcefmod;

import net.minecraft.client.session.Session;

public interface MinecraftClientAccessor {
    void setSession(Session session);
    boolean invokeDoAttack();
    void invokeDoItemUse();
}
