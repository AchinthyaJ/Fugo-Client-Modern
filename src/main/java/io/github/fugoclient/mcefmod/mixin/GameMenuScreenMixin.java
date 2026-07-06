package io.github.fugoclient.mcefmod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isMultiplayer = !client.isInSingleplayer() && client.getCurrentServerEntry() != null;

        if (isMultiplayer) {
            // Reconnect button
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Reconnect"), btn -> {
                net.minecraft.client.network.ServerInfo serverInfo = client.getCurrentServerEntry();
                if (serverInfo != null) {
                    client.disconnect(new net.minecraft.client.gui.screen.TitleScreen(), false, false);
                    net.minecraft.client.network.ServerAddress address = net.minecraft.client.network.ServerAddress.parse(serverInfo.address);
                    net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(
                        new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(new net.minecraft.client.gui.screen.TitleScreen()), 
                        client, 
                        address, 
                        serverInfo, 
                        false, 
                        null
                    );
                }
            }).dimensions(10, 10, 90, 20).build());
            
            // Quit to Desktop button (positioned next to Reconnect)
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Quit to Desktop"), btn -> {
                client.scheduleStop();
            }).dimensions(105, 10, 100, 20).build());
        } else {
            // Quit to Desktop button (positioned at top left corner)
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Quit to Desktop"), btn -> {
                client.scheduleStop();
            }).dimensions(10, 10, 100, 20).build());
        }
    }
}
