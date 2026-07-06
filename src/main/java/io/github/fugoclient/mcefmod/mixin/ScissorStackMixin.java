package io.github.fugoclient.mcefmod.mixin;

import io.github.fugoclient.mcefmod.ScissorStackAccessor;
import net.minecraft.client.gui.ScreenRect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.gui.DrawContext$ScissorStack")
public abstract class ScissorStackMixin implements ScissorStackAccessor {
    @Shadow public abstract ScreenRect peekLast();

    @Override
    public ScreenRect peekScissor() {
        return peekLast();
    }
}
