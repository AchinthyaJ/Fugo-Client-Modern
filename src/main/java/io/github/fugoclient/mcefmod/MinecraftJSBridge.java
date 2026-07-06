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
import net.minecraft.registry.RegistryWrapper;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.io.File;
import com.google.gson.JsonArray;

public class MinecraftJSBridge extends CefMessageRouterHandlerAdapter {
    private static Double originalGamma = null;

    @Override
    public boolean onQuery(CefBrowser b, CefFrame frame, long queryId, String query, boolean persistent, CefQueryCallback cb) {
        try {
            System.out.println("[Fugo Bridge] Received query: " + query);
            JsonObject json = JsonParser.parseString(query).getAsJsonObject();
            String action = json.get("action").getAsString();
            System.out.println("[Fugo Bridge] Action: " + action);
            
            System.out.println("[Fugo Client] Received bridge action: " + action);

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
                    client.execute(() -> client.scheduleStop());
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
                            if (client.gameRenderer != null && client.gameRenderer.getLightmapTextureManager() != null) {
                                ((io.github.fugoclient.mcefmod.mixin.LightmapTextureManagerAccessor) client.gameRenderer.getLightmapTextureManager()).setDirty(true);
                                System.out.println("[Fugo Bridge] Marked lightmap as dirty");
                            } else {
                                System.out.println("[Fugo Bridge] ERROR: gameRenderer or lightmapTextureManager is null");
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
                    resp.add("profiles", new com.google.gson.Gson().toJsonTree(ProfileManager.getInstance().getProfiles()));
                    resp.addProperty("current", client.getSession().getUsername());
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
                    String modId = json.get("payload").getAsJsonObject().get("modId").getAsString();
                    client.execute(() -> {
                        net.minecraft.client.gui.screen.Screen parent = client.currentScreen;
                        net.minecraft.client.gui.screen.Screen configScreen = null;
                        
                        try {
                            Class<?> modMenuClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
                            java.lang.reflect.Method getConfigScreenMethod = modMenuClass.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screen.Screen.class);
                            configScreen = (net.minecraft.client.gui.screen.Screen) getConfigScreenMethod.invoke(null, modId, parent);
                        } catch (Throwable t) {
                            System.out.println("[Fugo Bridge] ModMenu getConfigScreen failed for " + modId + ": " + t.getMessage());
                        }
                        
                        if (configScreen == null) {
                            try {
                                if (modId.equals("flashback")) {
                                    Class<?> clazz = Class.forName("com.moulberry.lattice.LatticeConfigScreen");
                                    java.lang.reflect.Constructor<?> ctor = clazz.getConstructor(net.minecraft.client.gui.screen.Screen.class);
                                    configScreen = (net.minecraft.client.gui.screen.Screen) ctor.newInstance(parent);
                                } else if (modId.equals("xaeros-minimap")) {
                                    Class<?> clazz = null;
                                    try {
                                        clazz = Class.forName("xaero.minimap.gui.GuiMinimapSettings");
                                    } catch (ClassNotFoundException e) {
                                        clazz = Class.forName("xaero.minimap.gui.GuiMinimapSystem");
                                    }
                                    java.lang.reflect.Constructor<?> ctor = clazz.getConstructor(net.minecraft.client.gui.screen.Screen.class);
                                    configScreen = (net.minecraft.client.gui.screen.Screen) ctor.newInstance(parent);
                                }
                            } catch (Throwable t) {
                                System.err.println("[Fugo Bridge] Direct fallback failed for " + modId);
                                t.printStackTrace();
                            }
                        }
                        
                        if (configScreen != null) {
                            client.setScreen(configScreen);
                        } else {
                            System.err.println("[Fugo Bridge] Could not find or instantiate config screen for " + modId);
                        }
                    });
                    cb.success("{\"status\":\"success\"}");
                    return true;
                }
                case "boost:toggle":
                    System.out.println("[Fugo Bridge] Performance boost setting updated: " + json.toString());
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "packs:toggle":
                    System.out.println("[Fugo Bridge] Resource pack configuration changed: " + json.toString());
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "customization:change":
                    System.out.println("[Fugo Bridge] Customization setting updated: " + json.toString());
                    cb.success("{\"status\":\"success\"}");
                    return true;
                case "customization:accent_color":
                    System.out.println("[Fugo Bridge] Customization accent color updated: " + json.toString());
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
                        System.out.println("[Fugo Bridge] Autoclicker updated: enabled=" + enabled + ", cps=" + cps + ", button=" + button + ", keybind=" + keybind);
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
                            ServerAddress address = ServerAddress.parse(ip);
                            ServerInfo serverInfo = new ServerInfo("Quick Join", ip, ServerInfo.ServerType.OTHER);
                            ConnectScreen.connect(client.currentScreen, client, address, serverInfo, false, null);
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
                            if (client.isIntegratedServerRunning() && client.getServer() != null) {
                                // Singleplayer: directly set time on the integrated server world
                                for (net.minecraft.server.world.ServerWorld world : client.getServer().getWorlds()) {
                                    world.setTimeOfDay(ticks);
                                }
                                System.out.println("[Fugo] Time set to " + ticks + " via integrated server");
                            } else if (client.player != null && client.getNetworkHandler() != null) {
                                // Multiplayer: send command (requires op)
                                client.getNetworkHandler().sendChatCommand("time set " + ticks);
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
                            if (client.isIntegratedServerRunning() && client.getServer() != null) {
                                // Singleplayer: directly set weather on the integrated server world
                                for (net.minecraft.server.world.ServerWorld world : client.getServer().getWorlds()) {
                                    switch (weatherType) {
                                        case "clear":
                                            world.setWeather(6000, 0, false, false);
                                            break;
                                        case "rain":
                                            world.setWeather(0, 6000, true, false);
                                            break;
                                        case "thunder":
                                            world.setWeather(0, 6000, true, true);
                                            break;
                                    }
                                }
                                System.out.println("[Fugo] Weather set to " + weatherType + " via integrated server");
                            } else if (client.player != null && client.getNetworkHandler() != null) {
                                // Multiplayer: send command (requires op)
                                client.getNetworkHandler().sendChatCommand("weather " + weatherType);
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
                case "gallery:get_items": {
                    JsonObject resp = new JsonObject();
                    resp.addProperty("status", "success");
                    
                    JsonArray screenshots = new JsonArray();
                    File screenshotsDir = new File(client.runDirectory, "screenshots");
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
                    File replaysDir = new File(client.runDirectory, "flashback/replays");
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
                        File dir = type.equals("screenshot") ? new File(client.runDirectory, "screenshots") : new File(client.runDirectory, "flashback/replays");
                        File file = new File(dir, name);
                        if (file.exists()) {
                            net.minecraft.util.Util.getOperatingSystem().open(file);
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
                        File dir = type.equals("screenshot") ? new File(client.runDirectory, "screenshots") : new File(client.runDirectory, "flashback/replays");
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        net.minecraft.util.Util.getOperatingSystem().open(dir);
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
                        File dir = type.equals("screenshot") ? new File(client.runDirectory, "screenshots") : new File(client.runDirectory, "flashback/replays");
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
                    JsonObject resp = new JsonObject();
                    resp.addProperty("status", "success");
                    resp.add("recipes", JsonParser.parseString(getRecipesJson()).getAsJsonArray());
                    cb.success(resp.toString());
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

    private static List<net.minecraft.recipe.display.SlotDisplay> getRecipeDisplayIngredients(net.minecraft.recipe.display.RecipeDisplay display) {
        List<net.minecraft.recipe.display.SlotDisplay> list = new java.util.ArrayList<>();
        if (display instanceof net.minecraft.recipe.display.ShapedCraftingRecipeDisplay shaped) {
            list.addAll(shaped.ingredients());
        } else if (display instanceof net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay shapeless) {
            list.addAll(shapeless.ingredients());
        } else if (display instanceof net.minecraft.recipe.display.FurnaceRecipeDisplay furnace) {
            list.add(furnace.ingredient());
        } else if (display instanceof net.minecraft.recipe.display.StonecutterRecipeDisplay stonecutter) {
            list.add(stonecutter.input());
        } else if (display instanceof net.minecraft.recipe.display.SmithingRecipeDisplay smithing) {
            list.add(smithing.template());
            list.add(smithing.base());
            list.add(smithing.addition());
        }
        return list;
    }

    private String getRecipesJson() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return "[]";
        }
        com.google.gson.JsonArray recipesArray = new com.google.gson.JsonArray();
        net.minecraft.util.context.ContextParameterMap context = net.minecraft.recipe.display.SlotDisplayContexts.createParameters(client.world);
        
        List<net.minecraft.client.gui.screen.recipebook.RecipeResultCollection> collections = client.player.getRecipeBook().getOrderedResults();
        for (net.minecraft.client.gui.screen.recipebook.RecipeResultCollection collection : collections) {
            List<net.minecraft.recipe.RecipeDisplayEntry> entries = collection.getAllRecipes();
            for (net.minecraft.recipe.RecipeDisplayEntry entry : entries) {
                try {
                    net.minecraft.recipe.display.RecipeDisplay display = entry.display();
                    ItemStack resultStack = display.result().getFirst(context);
                    if (resultStack.isEmpty()) continue;
                    
                    JsonObject recipeJson = new JsonObject();
                    String resultId = net.minecraft.registry.Registries.ITEM.getId(resultStack.getItem()).getPath();
                    recipeJson.addProperty("result", resultId);
                    recipeJson.addProperty("name", resultStack.getName().getString());
                    
                    // Categorize based on item type
                    String category = "blocks"; // default fallback
                    net.minecraft.item.Item item = resultStack.getItem();
                    String itemId = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
                    
                    if (itemId.contains("helmet") || itemId.contains("chestplate") ||
                        itemId.contains("leggings") || itemId.contains("boots") ||
                        itemId.contains("sword") || itemId.contains("shield") ||
                        itemId.equals("bow") || itemId.equals("crossbow") || itemId.equals("trident")) {
                        category = "combat";
                    } else if (itemId.contains("shovel") || itemId.contains("pickaxe") ||
                               itemId.contains("axe") || itemId.contains("hoe") ||
                               itemId.equals("fishing_rod") || itemId.equals("compass") ||
                               itemId.equals("clock") || itemId.equals("spyglass") ||
                               itemId.equals("shears") || itemId.equals("flint_and_steel")) {
                        category = "tools";
                    } else if (itemId.contains("redstone") || itemId.contains("repeater") ||
                               itemId.contains("comparator") || itemId.contains("piston") ||
                               itemId.contains("observer") || itemId.contains("dispenser") ||
                               itemId.contains("dropper") || itemId.contains("hopper") ||
                               itemId.contains("lever") || itemId.contains("button") ||
                               itemId.contains("pressure_plate") || itemId.contains("wire") ||
                               itemId.contains("lamp") || itemId.contains("rail") ||
                               itemId.equals("target") || itemId.contains("door") ||
                               itemId.contains("trapdoor") || itemId.equals("daylight_detector")) {
                        category = "redstone";
                    } else if (item instanceof net.minecraft.item.BlockItem) {
                        category = "blocks";
                    } else {
                        category = "tools"; // Default fallback for misc items
                    }
                    recipeJson.addProperty("category", category);
                    recipeJson.addProperty("icon", resultId);
                    
                    // Ingredients and Grid
                    com.google.gson.JsonArray tempIngredients = new com.google.gson.JsonArray();
                    com.google.gson.JsonArray gridArray = new com.google.gson.JsonArray();
                    
                    if (display instanceof net.minecraft.recipe.display.ShapedCraftingRecipeDisplay shaped) {
                        int width = shaped.width();
                        int height = shaped.height();
                        // Initialize 3x3 grid with empty strings
                        for (int i = 0; i < 9; i++) {
                            gridArray.add("");
                        }
                        
                        List<net.minecraft.recipe.display.SlotDisplay> ingredients = shaped.ingredients();
                        for (int r = 0; r < height; r++) {
                            for (int c = 0; c < width; c++) {
                                int idx = r * width + c;
                                if (idx < ingredients.size()) {
                                    net.minecraft.recipe.display.SlotDisplay ingDisplay = ingredients.get(idx);
                                    ItemStack matchingStack = ingDisplay.getFirst(context);
                                    if (!matchingStack.isEmpty()) {
                                        String ingId = net.minecraft.registry.Registries.ITEM.getId(matchingStack.getItem()).getPath();
                                        gridArray.set(r * 3 + c, new com.google.gson.JsonPrimitive(ingId));
                                        if (!ingId.isEmpty()) {
                                            tempIngredients.add(matchingStack.getName().getString());
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        List<net.minecraft.recipe.display.SlotDisplay> ingredients = getRecipeDisplayIngredients(display);
                        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
                            net.minecraft.recipe.display.SlotDisplay ingDisplay = ingredients.get(i);
                            ItemStack matchingStack = ingDisplay.getFirst(context);
                            if (!matchingStack.isEmpty()) {
                                String ingId = net.minecraft.registry.Registries.ITEM.getId(matchingStack.getItem()).getPath();
                                gridArray.add(ingId);
                                if (!ingId.isEmpty()) {
                                    tempIngredients.add(matchingStack.getName().getString());
                                }
                            } else {
                                gridArray.add("");
                            }
                        }
                        while (gridArray.size() < 9) {
                            gridArray.add("");
                        }
                    }
                    
                    // Deduplicate and count ingredients
                    java.util.Map<String, Integer> counts = new java.util.HashMap<>();
                    for (int i = 0; i < tempIngredients.size(); i++) {
                        String name = tempIngredients.get(i).getAsString();
                        counts.put(name, counts.getOrDefault(name, 0) + 1);
                    }
                    com.google.gson.JsonArray dedupedIngredients = new com.google.gson.JsonArray();
                    for (java.util.Map.Entry<String, Integer> ent : counts.entrySet()) {
                        dedupedIngredients.add(ent.getValue() + "x " + ent.getKey());
                    }
                    
                    recipeJson.add("ingredients", dedupedIngredients);
                    recipeJson.add("grid", gridArray);
                    recipesArray.add(recipeJson);
                } catch (Exception e) {
                    // Ignore recipes that fail to process
                }
            }
        }
        return recipesArray.toString();
    }
}
