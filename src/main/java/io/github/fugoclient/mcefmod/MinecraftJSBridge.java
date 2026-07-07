package io.github.fugoclient.mcefmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.recipe.display.*;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import java.util.List;
import java.util.UUID;
import java.io.File;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import io.github.fugoclient.mcefmod.mixin.LightmapTextureManagerAccessor;

public class MinecraftJSBridge extends CefMessageRouterHandlerAdapter {
    private static final Gson GSON = new Gson();
    private static Double originalGamma = null;

    @Override
    public boolean onQuery(CefBrowser b, CefFrame frame, long queryId, String query, boolean persistent, CefQueryCallback cb) {
        try {
            JsonObject json = JsonParser.parseString(query).getAsJsonObject();
            String action = json.get("action").getAsString();
            MinecraftClient client = MinecraftClient.getInstance();

            switch (action) {
                case "singleplayer":
                    client.execute(() -> client.setScreen(new SelectWorldScreen(client.currentScreen)));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "multiplayer":
                    client.execute(() -> client.setScreen(new MultiplayerScreen(client.currentScreen)));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "options":
                    client.execute(() -> client.setScreen(new OptionsScreen(client.currentScreen, client.options)));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "quit":
                    client.execute(client::scheduleStop);
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "toggle_vanilla_crosshair":
                    try {
                        WebUIManager.vanillaCrosshairDisabled = json.get("payload").getAsJsonObject().get("disable").getAsBoolean();
                    } catch (Exception ignored) {}
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "toggle_fullbright":
                    try {
                        WebUIManager.fullbrightEnabled = json.get("payload").getAsJsonObject().get("enabled").getAsBoolean();
                        client.execute(() -> {
                            if (client.gameRenderer != null && client.gameRenderer.getLightmapTextureManager() != null) {
                                ((LightmapTextureManagerAccessor) client.gameRenderer.getLightmapTextureManager()).setDirty(true);
                            }
                        });
                    } catch (Exception ignored) {}
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "close_overlay":
                    client.execute(() -> {
                        if (client.world == null) {
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
                    resp.add("profiles", GSON.toJsonTree(ProfileManager.getInstance().getProfiles()));
                    resp.addProperty("current", client.getSession().getUsername());
                    cb.success(resp.toString());
                    return true;
                }
                case "profiles:add_offline": {
                    String username = json.get("payload").getAsJsonObject().get("username").getAsString();
                    UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    ProfileManager.getInstance().addProfile(new ProfileManager.Profile(username, offlineUuid.toString(), "0", "offline"));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "profiles:add_alt": {
                    JsonObject payload = json.get("payload").getAsJsonObject();
                    String username = payload.get("username").getAsString();
                    String uuid = payload.has("uuid") && !payload.get("uuid").getAsString().isEmpty()
                        ? payload.get("uuid").getAsString()
                        : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                    String token = payload.has("token") ? payload.get("token").getAsString() : "0";
                    ProfileManager.getInstance().addProfile(new ProfileManager.Profile(username, uuid, token, "alt"));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "profiles:select": {
                    String username = json.get("payload").getAsJsonObject().get("username").getAsString();
                    if (ProfileManager.getInstance().selectProfile(username)) {
                        cb.success("{\"status\":\"success\"}");
                    } else {
                        cb.failure(400, "Profile not found: " + username);
                    }
                    return true;
                }
                case "profiles:delete": {
                    ProfileManager.getInstance().deleteProfile(json.get("payload").getAsJsonObject().get("username").getAsString());
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "profiles:microsoft_login": {
                    client.execute(() -> b.loadURL("https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&response_type=code&scope=service::user.auth.xboxlive.com::MBI_SSL&redirect_uri=https://login.live.com/oauth20_desktop.srf"));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "open_mod_config": {
                    String modId = json.get("payload").getAsJsonObject().get("modId").getAsString();
                    client.execute(() -> openModConfig(client, modId));
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "update_autoclicker":
                    try {
                        JsonObject payload = json.get("payload").getAsJsonObject();
                        Autoclicker.setSettings(
                            payload.get("enabled").getAsBoolean(),
                            payload.get("cps").getAsInt(),
                            payload.get("button").getAsString(),
                            payload.get("keybind").getAsString()
                        );
                    } catch (Exception ignored) {}
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "quick_join":
                    try {
                        String ip = json.get("payload").getAsJsonObject().get("ip").getAsString();
                        client.execute(() -> {
                            ServerAddress address = ServerAddress.parse(ip);
                            ConnectScreen.connect(client.currentScreen, client, address, new ServerInfo("Quick Join", ip, ServerInfo.ServerType.OTHER), false, null);
                        });
                    } catch (Exception ignored) {}
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "set_time": {
                    try {
                        int ticks = json.get("payload").getAsJsonObject().get("ticks").getAsInt();
                        client.execute(() -> setTime(client, ticks));
                    } catch (Exception ignored) {}
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "set_weather": {
                    try {
                        String weatherType = json.get("payload").getAsJsonObject().get("type").getAsString();
                        client.execute(() -> setWeather(client, weatherType));
                    } catch (Exception ignored) {}
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "get_state": {
                    JsonObject state = new JsonObject();
                    state.addProperty("username", client.getSession().getUsername());
                    state.addProperty("version", client.getGameVersion());
                    state.addProperty("inWorld", client.world != null);
                    boolean isWebTitle = client.currentScreen instanceof WebTitleScreen;
                    boolean isOverlayScreen = false;
                    boolean isEditMode = false;
                    if (isWebTitle) {
                        isOverlayScreen = ((WebTitleScreen) client.currentScreen).isOverlay();
                        isEditMode = ((WebTitleScreen) client.currentScreen).isEditMode();
                    }
                    state.addProperty("isWebTitle", isWebTitle);
                    state.addProperty("isOverlay", isOverlayScreen);
                    state.addProperty("editMode", isEditMode);
                    cb.success(state.toString());
                    return true;
                }
                case "gallery:get_items": {
                    JsonObject resp = new JsonObject();
                    resp.addProperty("status", "success");
                    resp.add("screenshots", buildGallery(client.runDirectory, "screenshots", ".png"));
                    resp.add("replays", buildGallery(new File(client.runDirectory, "flashback"), "replays", ".zip"));
                    cb.success(resp.toString());
                    return true;
                }
                case "gallery:open_file":
                case "gallery:open_folder":
                case "gallery:delete_file":
                    handleGalleryAction(action, json, client, cb);
                    return true;
                case "recipes:get": {
                    JsonObject resp = new JsonObject();
                    resp.addProperty("status", "success");
                    resp.add("recipes", getRecipesJson(client));
                    cb.success(resp.toString());
                    return true;
                }
                default:
                    cb.failure(404, "Unknown action: " + action);
                    return false;
            }
        } catch (Exception e) {
            cb.failure(500, e.getMessage());
            return false;
        }
    }

    private void openModConfig(MinecraftClient client, String modId) {
        net.minecraft.client.gui.screen.Screen parent = client.currentScreen;
        net.minecraft.client.gui.screen.Screen configScreen = null;
        
        try {
            Class<?> modMenuClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
            configScreen = (net.minecraft.client.gui.screen.Screen) modMenuClass.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screen.Screen.class)
                .invoke(null, modId, parent);
        } catch (Throwable ignored) {}
        
        if (configScreen == null) {
            try {
                if (modId.equals("flashback")) {
                    configScreen = (net.minecraft.client.gui.screen.Screen) Class.forName("com.moulberry.lattice.LatticeConfigScreen")
                        .getConstructor(net.minecraft.client.gui.screen.Screen.class).newInstance(parent);
                } else if (modId.equals("xaeros-minimap")) {
                    Class<?> clazz;
                    try {
                        clazz = Class.forName("xaero.minimap.gui.GuiMinimapSettings");
                    } catch (ClassNotFoundException e) {
                        clazz = Class.forName("xaero.minimap.gui.GuiMinimapSystem");
                    }
                    configScreen = (net.minecraft.client.gui.screen.Screen) clazz.getConstructor(net.minecraft.client.gui.screen.Screen.class)
                        .newInstance(parent);
                }
            } catch (Throwable ignored) {}
        }
        
        if (configScreen != null) {
            client.setScreen(configScreen);
        }
    }

    private void setTime(MinecraftClient client, int ticks) {
        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            for (net.minecraft.server.world.ServerWorld world : client.getServer().getWorlds()) {
                world.setTimeOfDay(ticks);
            }
        } else if (client.player != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("time set " + ticks);
        }
    }

    private void setWeather(MinecraftClient client, String weatherType) {
        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            for (net.minecraft.server.world.ServerWorld world : client.getServer().getWorlds()) {
                switch (weatherType) {
                    case "clear": world.setWeather(6000, 0, false, false); break;
                    case "rain": world.setWeather(0, 6000, true, false); break;
                    case "thunder": world.setWeather(0, 6000, true, true); break;
                }
            }
        } else if (client.player != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("weather " + weatherType);
        }
    }

    private JsonArray buildGallery(File baseDir, String subDir, String extension) {
        JsonArray items = new JsonArray();
        File dir = new File(baseDir, subDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(extension));
            if (files != null) {
                java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                for (File f : files) {
                    JsonObject item = new JsonObject();
                    item.addProperty("name", f.getName());
                    item.addProperty("date", sdf.format(new java.util.Date(f.lastModified())));
                    item.addProperty("size", String.format("%.2f MB", (double) f.length() / (1024 * 1024)));
                    item.addProperty("url", "../" + subDir + "/" + f.getName());
                    items.add(item);
                }
            }
        }
        return items;
    }

    private void handleGalleryAction(String action, JsonObject json, MinecraftClient client, CefQueryCallback cb) {
        try {
            JsonObject payload = json.get("payload").getAsJsonObject();
            String type = payload.get("type").getAsString();
            File dir = type.equals("screenshot") ? new File(client.runDirectory, "screenshots") : new File(client.runDirectory, "flashback/replays");
            
            switch (action) {
                case "gallery:open_file": {
                    File file = new File(dir, payload.get("name").getAsString());
                    if (file.exists()) net.minecraft.util.Util.getOperatingSystem().open(file);
                    break;
                }
                case "gallery:open_folder": {
                    if (!dir.exists()) dir.mkdirs();
                    net.minecraft.util.Util.getOperatingSystem().open(dir);
                    break;
                }
                case "gallery:delete_file": {
                    File file = new File(dir, payload.get("name").getAsString());
                    if (file.exists()) file.delete();
                    break;
                }
            }
        } catch (Exception ignored) {}
        cb.success("{\"status\":\"success\"}");
    }

    private List<SlotDisplay> getRecipeDisplayIngredients(RecipeDisplay display) {
        List<SlotDisplay> list = new java.util.ArrayList<>();
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            list.addAll(shaped.ingredients());
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            list.addAll(shapeless.ingredients());
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            list.add(furnace.ingredient());
        } else if (display instanceof StonecutterRecipeDisplay stonecutter) {
            list.add(stonecutter.input());
        } else if (display instanceof SmithingRecipeDisplay smithing) {
            list.add(smithing.template());
            list.add(smithing.base());
            list.add(smithing.addition());
        }
        return list;
    }

    private JsonArray getRecipesJson(MinecraftClient client) {
        JsonArray recipesArray = new JsonArray();
        if (client.player == null || client.world == null) return recipesArray;
        
        net.minecraft.util.context.ContextParameterMap context = SlotDisplayContexts.createParameters(client.world);
        List<RecipeResultCollection> collections = client.player.getRecipeBook().getOrderedResults();
        
        for (RecipeResultCollection collection : collections) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                try {
                    RecipeDisplay display = entry.display();
                    ItemStack resultStack = display.result().getFirst(context);
                    if (resultStack.isEmpty()) continue;
                    
                    JsonObject recipeJson = new JsonObject();
                    String resultId = Registries.ITEM.getId(resultStack.getItem()).getPath();
                    recipeJson.addProperty("result", resultId);
                    recipeJson.addProperty("name", resultStack.getName().getString());
                    recipeJson.addProperty("category", categorizeItem(resultId, resultStack.getItem()));
                    recipeJson.addProperty("icon", resultId);

                    JsonArray gridArray = new JsonArray();
                    if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                        int width = shaped.width(), height = shaped.height();
                        for (int i = 0; i < 9; i++) gridArray.add("");
                        List<SlotDisplay> ingredients = shaped.ingredients();
                        for (int r = 0; r < height; r++) {
                            for (int c = 0; c < width; c++) {
                                int idx = r * width + c;
                                if (idx < ingredients.size()) {
                                    ItemStack matchingStack = ingredients.get(idx).getFirst(context);
                                    if (!matchingStack.isEmpty()) {
                                        gridArray.set(r * 3 + c, new com.google.gson.JsonPrimitive(Registries.ITEM.getId(matchingStack.getItem()).getPath()));
                                    }
                                }
                            }
                        }
                    } else {
                        List<SlotDisplay> ingredients = getRecipeDisplayIngredients(display);
                        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
                            ItemStack matchingStack = ingredients.get(i).getFirst(context);
                            gridArray.add(matchingStack.isEmpty() ? "" : Registries.ITEM.getId(matchingStack.getItem()).getPath());
                        }
                        while (gridArray.size() < 9) gridArray.add("");
                    }
                    recipeJson.add("grid", gridArray);
                    recipesArray.add(recipeJson);
                } catch (Exception ignored) {}
            }
        }
        return recipesArray;
    }

    private static String categorizeItem(String itemId, net.minecraft.item.Item item) {
        if (itemId.contains("helmet") || itemId.contains("chestplate") || itemId.contains("leggings") ||
            itemId.contains("boots") || itemId.contains("sword") || itemId.contains("shield") ||
            itemId.equals("bow") || itemId.equals("crossbow") || itemId.equals("trident")) {
            return "combat";
        }
        if (itemId.contains("shovel") || itemId.contains("pickaxe") || itemId.contains("axe") ||
            itemId.contains("hoe") || itemId.equals("fishing_rod") || itemId.equals("compass") ||
            itemId.equals("clock") || itemId.equals("spyglass") || itemId.equals("shears") ||
            itemId.equals("flint_and_steel")) {
            return "tools";
        }
        if (itemId.contains("redstone") || itemId.contains("repeater") || itemId.contains("comparator") ||
            itemId.contains("piston") || itemId.contains("observer") || itemId.contains("dispenser") ||
            itemId.contains("dropper") || itemId.contains("hopper") || itemId.contains("lever") ||
            itemId.contains("button") || itemId.contains("pressure_plate") || itemId.contains("wire") ||
            itemId.contains("lamp") || itemId.contains("rail") || itemId.equals("target") ||
            itemId.contains("door") || itemId.contains("trapdoor") || itemId.equals("daylight_detector")) {
            return "redstone";
        }
        if (item instanceof net.minecraft.item.BlockItem) return "blocks";
        return "tools";
    }
}