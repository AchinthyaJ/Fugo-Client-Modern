package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.fugoclient.mcefmod.WebUIManager;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "method_22676", at = @At("TAIL"))
    private void onKeyInput(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        WebUIManager.getInstance().updateHudKeysOnly();
        io.github.fugoclient.mcefmod.Autoclicker.onKey(key, action);
    }
}
