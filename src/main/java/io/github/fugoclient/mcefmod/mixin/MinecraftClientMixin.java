package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.client.option.GameOptions;
import io.github.fugoclient.mcefmod.MinecraftClientAccessor;
import io.github.fugoclient.mcefmod.FreeLookHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements MinecraftClientAccessor {
    @Shadow
    @Mutable
    private Session session;

    @Shadow
    public @Final GameOptions options;

    @Shadow
    private boolean doAttack() {
        return false;
    }

    @Shadow
    private void doItemUse() {}

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public boolean invokeDoAttack() {
        return this.doAttack();
    }

    @Override
    public void invokeDoItemUse() {
        this.doItemUse();
    }

    @Inject(method = "render(Z)V", at = @At("HEAD"))
    private void onRender(boolean tick, CallbackInfo ci) {
        FreeLookHandler.update((MinecraftClient) (Object) this);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V", at = @At("HEAD"))
    private void onDisconnectHead(net.minecraft.client.gui.screen.Screen screen, boolean transfer, boolean isMultiplayer, CallbackInfo ci) {
        io.github.fugoclient.mcefmod.WebUIManager.isDisconnecting = true;
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V", at = @At("RETURN"))
    private void onDisconnectReturn(net.minecraft.client.gui.screen.Screen screen, boolean transfer, boolean isMultiplayer, CallbackInfo ci) {
        io.github.fugoclient.mcefmod.WebUIManager.isDisconnecting = false;
    }

    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onDisconnectTextHead(net.minecraft.text.Text reason, CallbackInfo ci) {
        io.github.fugoclient.mcefmod.WebUIManager.isDisconnecting = true;
    }

    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("RETURN"))
    private void onDisconnectTextReturn(net.minecraft.text.Text reason, CallbackInfo ci) {
        io.github.fugoclient.mcefmod.WebUIManager.isDisconnecting = false;
    }
}
