package io.github.fugoclient.mcefmod;

import net.montoyo.mcef.api.IJSQueryHandler;
import net.montoyo.mcef.api.IBrowser;
import net.montoyo.mcef.api.IJSQueryCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.Util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import java.io.File;
import java.util.UUID;

public class MinecraftJSBridge implements IJSQueryHandler {

    @Override
    public boolean onQuery(IBrowser b, long queryId, String query, boolean persistent, IJSQueryCallback cb) {
        try {
            System.out.println("[Fugo Bridge] Received query: " + query);
            JsonObject json = JsonParser.parseString(query).getAsJsonObject();
            String action = json.get("action").getAsString();
            System.out.println("[Fugo Bridge] Action: " + action);
            
            Minecraft client = Minecraft.getInstance();

            switch (action) {
                case "singleplayer":
                    client.execute(() -> client.setScreen(new SelectWorldScreen(client.screen)));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "multiplayer":
                    client.execute(() -> client.setScreen(new JoinMultiplayerScreen(client.screen)));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "options":
                    client.execute(() -> client.setScreen(new OptionsScreen(client.screen, client.options)));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "quit":
                    client.execute(() -> client.stop());
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "toggle_vanilla_crosshair":
                    try {
                        boolean disable = json.get("payload").getAsJsonObject().get("disable").getAsBoolean();
                        WebUIManager.vanillaCrosshairDisabled = disable;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "toggle_fullbright":
                    try {
                        boolean enabled = json.get("payload").getAsJsonObject().get("enabled").getAsBoolean();
                        System.out.println("[Fugo Bridge] Fullbright toggle: " + enabled);
                        WebUIManager.fullbrightEnabled = enabled;
                        client.execute(() -> {
                            System.out.println("[Fugo Bridge] Applying fullbright: " + enabled);
                            if (client.gameRenderer != null && client.gameRenderer.lightTexture() != null) {
                                ((io.github.fugoclient.mcefmod.mixin.LightmapTextureManagerAccessor) client.gameRenderer.lightTexture()).setDirty(true);
                                System.out.println("[Fugo Bridge] Marked lightmap as dirty");
                            } else {
                                System.out.println("[Fugo Bridge] ERROR: gameRenderer or lightTexture is null");
                            }
                        });
                    } catch (Exception ex) {
                        System.out.println("[Fugo Bridge] ERROR in toggle_fullbright:");
                        ex.printStackTrace();
                    }
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "close_overlay":
                    client.execute(() -> {
                        if (client.level == null) {
                            client.setScreen(new WebTitleScreen(false, false));
                        } else {
                            client.setScreen(null);
                        }
                    });
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "enter_edit_mode":
                    client.execute(() -> client.setScreen(new WebTitleScreen(true, true)));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "enter_mods_mode":
                    client.execute(() -> client.setScreen(new WebTitleScreen(true, false)));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "profiles:get": {
                    JsonObject resp = new JsonObject();
                    resp.add("profiles", new com.google.gson.Gson().toJsonTree(ProfileManager.getInstance().getProfiles()));
                    resp.addProperty("current", client.getUser().getName());
                    cb.success(resp.toString());
                    return true;
                }
                case "profiles:add_offline": {
                    String username = json.get("payload").getAsJsonObject().get("username").getAsString();
                    UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    ProfileManager.Profile p = new ProfileManager.Profile(username, offlineUuid.toString(), "0", "offline");
                    ProfileManager.getInstance().addProfile(p);
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "profiles:add_alt": {
                    JsonObject payload = json.get("payload").getAsJsonObject();
                    String username = payload.get("username").getAsString();
                    String uuid = payload.has("uuid") ? payload.get("uuid").getAsString() : "";
                    String token = payload.has("token") ? payload.get("token").getAsString() : "0";
                    if (uuid.isEmpty()) {
                        uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                    }
                    ProfileManager.Profile p = new ProfileManager.Profile(username, uuid, token, "alt");
                    ProfileManager.getInstance().addProfile(p);
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "profiles:select": {
                    String username = json.get("payload").getAsJsonObject().get("username").getAsString();
                    boolean success = ProfileManager.getInstance().selectProfile(username);
                    if (success) {
                        cb.success("{\"status\":\"success\"}");
                    } else {
                        cb.failure(400, "Profile not found: " + username);
                    }
                    return true;
                }
                case "profiles:delete": {
                    String username = json.get("payload").getAsJsonObject().get("username").getAsString();
                    ProfileManager.getInstance().deleteProfile(username);
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "profiles:microsoft_login": {
                    client.execute(() -> {
                        String authUrl = "https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&response_type=code&scope=service::user.auth.xboxlive.com::MBI_SSL&redirect_uri=https://login.live.com/oauth20_desktop.srf";
                        b.loadURL(authUrl);
                    });
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "open_mod_config": {
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "boost:toggle":
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "packs:toggle":
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "customization:change":
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "customization:accent_color":
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "update_autoclicker":
                    try {
                        JsonObject payload = json.get("payload").getAsJsonObject();
                        boolean enabled = payload.get("enabled").getAsBoolean();
                        int cps = payload.get("cps").getAsInt();
                        String button = payload.get("button").getAsString();
                        String keybind = payload.get("keybind").getAsString();
                        Autoclicker.setSettings(enabled, cps, button, keybind);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "quick_join":
                    try {
                        String ip = json.get("payload").getAsJsonObject().get("ip").getAsString();
                        System.out.println("[Fugo Bridge] Quick joining server: " + ip);
                        client.execute(() -> {
                            ServerAddress address = ServerAddress.parseString(ip);
                            ServerData serverData = new ServerData("Quick Join", ip, false);
                            ConnectScreen.startConnecting(client.screen, client, address, serverData, false);
                        });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "set_time": {
                    try {
                        int ticks = json.get("payload").getAsJsonObject().get("ticks").getAsInt();
                        client.execute(() -> {
                            if (client.getSingleplayerServer() != null) {
                                for (net.minecraft.server.level.ServerLevel world : client.getSingleplayerServer().getAllLevels()) {
                                    world.setDayTime(ticks);
                                }
                            } else if (client.player != null && client.getConnection() != null) {
                                client.player.connection.sendChatCommand("time set " + ticks);
                            }
                        });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "set_weather": {
                    try {
                        String weatherType = json.get("payload").getAsJsonObject().get("type").getAsString();
                        client.execute(() -> {
                            if (client.getSingleplayerServer() != null) {
                                for (net.minecraft.server.level.ServerLevel world : client.getSingleplayerServer().getAllLevels()) {
                                    switch (weatherType) {
                                        case "clear":
                                            world.setWeatherParameters(6000, 0, false, false);
                                            break;
                                        case "rain":
                                            world.setWeatherParameters(0, 6000, true, false);
                                            break;
                                        case "thunder":
                                            world.setWeatherParameters(0, 6000, true, true);
                                            break;
                                    }
                                }
                            } else if (client.player != null && client.getConnection() != null) {
                                client.player.connection.sendChatCommand("weather " + weatherType);
                            }
                        });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "get_state":
                    JsonObject state = new JsonObject();
                    state.addProperty("username", client.getUser().getName());
                    state.addProperty("version", client.getLaunchedVersion());
                    state.addProperty("inWorld", client.level != null);
                    boolean isWebTitle = client.screen instanceof WebTitleScreen;
                    boolean isOverlayScreen = false;
                    boolean isEditMode = false;
                    if (isWebTitle) {
                        isOverlayScreen = ((WebTitleScreen) client.screen).isOverlay();
                        isEditMode = ((WebTitleScreen) client.screen).isEditMode();
                    }
                    state.addProperty("isWebTitle", isWebTitle);
                    state.addProperty("isOverlay", isOverlayScreen);
                    state.addProperty("editMode", isEditMode);
                    cb.success(state.toString());
                    return true;
                case "gallery:get_items": {
                    JsonObject resp = new JsonObject();
                    resp.addProperty("status", "success");
                    
                    JsonArray screenshots = new JsonArray();
                    File screenshotsDir = new File(client.gameDirectory, "screenshots");
                    if (screenshotsDir.exists() && screenshotsDir.isDirectory()) {
                        File[] files = screenshotsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
                        if (files != null) {
                            java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                            for (File f : files) {
                                JsonObject s = new JsonObject();
                                s.addProperty("name", f.getName());
                                s.addProperty("date", sdf.format(new java.util.Date(f.lastModified())));
                                s.addProperty("size", String.format("%.2f MB", (double) f.length() / (1024 * 1024)));
                                s.addProperty("url", "../screenshots/" + f.getName());
                                screenshots.add(s);
                            }
                        }
                    }
                    resp.add("screenshots", screenshots);
                    
                    JsonArray replays = new JsonArray();
                    File replaysDir = new File(client.gameDirectory, "flashback/replays");
                    if (replaysDir.exists() && replaysDir.isDirectory()) {
                        File[] files = replaysDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
                        if (files != null) {
                            java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                            for (File f : files) {
                                JsonObject r = new JsonObject();
                                r.addProperty("name", f.getName());
                                r.addProperty("date", sdf.format(new java.util.Date(f.lastModified())));
                                r.addProperty("size", String.format("%.2f MB", (double) f.length() / (1024 * 1024)));
                                r.addProperty("url", "../flashback/replays/" + f.getName());
                                replays.add(r);
                            }
                        }
                    }
                    resp.add("replays", replays);
                    
                    cb.success(resp.toString());
                    return true;
                }
                case "gallery:open_file": {
                    try {
                        String type = json.get("payload").getAsJsonObject().get("type").getAsString();
                        String name = json.get("payload").getAsJsonObject().get("name").getAsString();
                        File dir = type.equals("screenshot") ? new File(client.gameDirectory, "screenshots") : new File(client.gameDirectory, "flashback/replays");
                        File file = new File(dir, name);
                        if (file.exists()) {
                            Util.getPlatform().open(file);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "gallery:open_folder": {
                    try {
                        String type = json.get("payload").getAsJsonObject().get("type").getAsString();
                        File dir = type.equals("screenshot") ? new File(client.gameDirectory, "screenshots") : new File(client.gameDirectory, "flashback/replays");
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        Util.getPlatform().open(dir);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "gallery:delete_file": {
                    try {
                        String type = json.get("payload").getAsJsonObject().get("type").getAsString();
                        String name = json.get("payload").getAsJsonObject().get("name").getAsString();
                        File dir = type.equals("screenshot") ? new File(client.gameDirectory, "screenshots") : new File(client.gameDirectory, "flashback/replays");
                        File file = new File(dir, name);
                        if (file.exists()) {
                            file.delete();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "recipes:get": {
                    cb.success("[]");
                    return true;
                }
                default:
                    cb.failure(404, "Unknown action: " + action);
                    return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            cb.failure(500, e.getMessage());
            return false;
        }
    }

    @Override
    public void cancelQuery(IBrowser browser, long queryId) {
    }
}
