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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import io.github.fugoclient.mcefmod.mixin.LightmapTextureManagerAccessor;

import com.sun.net.httpserver.HttpServer;

public class MinecraftJSBridge extends CefMessageRouterHandlerAdapter {
    private static final Gson GSON = new Gson();
    private static Double originalGamma = null;
    private static HttpServer editorServer = null;
    private static final int EDITOR_PORT = 8642;

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
                case "theme:get": {
                    File themeFile = new File(client.runDirectory, "fugo-theme.json");
                    String content = "{}";
                    if (themeFile.exists()) {
                        try {
                            content = new String(Files.readAllBytes(themeFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                        } catch (Exception ignored) {}
                    }
                    cb.success(content);
                    return true;
                }
                case "recipes:get": {
                    JsonObject resp = new JsonObject();
                    resp.addProperty("status", "success");
                    resp.add("recipes", getRecipesJson(client));
                    cb.success(resp.toString());
                    return true;
                }
                case "launch_editor": {
                    new Thread(() -> {
                        try {
                            startEditorServer();
                            String url = "http://localhost:" + EDITOR_PORT + "/editor.html";
                            net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(url));
                            System.out.println("[Fugo Bridge] Editor launched at: " + url);
                        } catch (Exception ex) {
                            System.out.println("[Fugo Bridge] Failed to launch editor: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }, "Fugo-Editor-Launcher").start();
                    cb.success("{\"status\":\"success\",\"port\":" + EDITOR_PORT + "}");
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

    private static synchronized void startEditorServer() throws Exception {
        if (editorServer != null) return;
        File webDir = findWebAssetsDir();
        if (webDir == null) throw new RuntimeException("Could not find web assets directory");
        final File serveDir = webDir;
        editorServer = HttpServer.create(new InetSocketAddress("127.0.0.1", EDITOR_PORT), 0);
        editorServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();

            // Allow CORS preflight OPTIONS requests
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                exchange.getResponseBody().close();
                return;
            }

            // API: get current theme
            if (exchange.getRequestMethod().equalsIgnoreCase("GET") && path.equals("/api/get-theme")) {
                File themeFile = new File(MinecraftClient.getInstance().runDirectory, "fugo-theme.json");
                String content = "{}";
                if (themeFile.exists()) {
                    try {
                        content = new String(Files.readAllBytes(themeFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception ignored) {}
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                byte[] data = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
                exchange.getResponseBody().close();
                return;
            }

            // API: apply and save theme
            if (exchange.getRequestMethod().equalsIgnoreCase("POST") && path.equals("/api/apply-theme")) {
                try {
                    InputStream is = exchange.getRequestBody();
                    byte[] bodyBytes = is.readAllBytes();
                    String body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
                    
                    // Save to config directory
                    File themeFile = new File(MinecraftClient.getInstance().runDirectory, "fugo-theme.json");
                    Files.write(themeFile.toPath(), bodyBytes);
                    
                    // Apply to in-game CEF browser
                    MinecraftClient.getInstance().execute(() -> {
                        try {
                            if (WebUIManager.getInstance().getBrowser() != null && WebUIManager.getInstance().getBrowser().getCefBrowser() != null) {
                                WebUIManager.getInstance().getBrowser().getCefBrowser().executeJavaScript(
                                    "if (window.applyThemeData) window.applyThemeData(" + body + ");", "", 0
                                );
                            }
                        } catch (Exception ignored) {}
                    });

                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    String resp = "{\"status\":\"success\"}";
                    exchange.sendResponseHeaders(200, resp.length());
                    exchange.getResponseBody().write(resp.getBytes());
                    exchange.getResponseBody().close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    String err = "{\"status\":\"error\",\"message\":\"" + ex.getMessage().replace("\"", "\\\"") + "\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(500, err.length());
                    exchange.getResponseBody().write(err.getBytes());
                    exchange.getResponseBody().close();
                }
                return;
            }

            if (path.equals("/")) path = "/editor.html";
            File file = new File(serveDir, path);
            try {
                File canonicalFile = file.getCanonicalFile();
                File canonicalServeDir = serveDir.getCanonicalFile();
                if (!canonicalFile.exists() || !canonicalFile.isFile() || !canonicalFile.getPath().startsWith(canonicalServeDir.getPath())) {
                    exchange.sendResponseHeaders(404, 0); exchange.getResponseBody().close(); return;
                }
            } catch (Exception ex) {
                exchange.sendResponseHeaders(500, 0); exchange.getResponseBody().close(); return;
            }
            String ct = "application/octet-stream";
            String n = file.getName().toLowerCase();
            if (n.endsWith(".html")) ct = "text/html; charset=utf-8";
            else if (n.endsWith(".css")) ct = "text/css; charset=utf-8";
            else if (n.endsWith(".js")) ct = "application/javascript; charset=utf-8";
            else if (n.endsWith(".png")) ct = "image/png";
            else if (n.endsWith(".ico")) ct = "image/x-icon";
            byte[] data = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", ct);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, data.length);
            OutputStream os = exchange.getResponseBody(); os.write(data); os.close();
        });
        editorServer.setExecutor(null);
        editorServer.start();
    }

    private static File findWebAssetsDir() {
        for (String p : new String[]{"src/main/resources/assets/fugoclient/web", "../src/main/resources/assets/fugoclient/web"}) {
            File f = new File(p);
            if (f.exists() && new File(f, "editor.html").exists()) return f;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        File extracted = new File(mc.runDirectory, "web-ui");
        if (extracted.exists() && new File(extracted, "editor.html").exists()) return extracted;
        try {
            extracted.mkdirs();
            for (String fn : new String[]{"editor.html","editor.css","editor.js","hud.html","titlescreen.html","style.css","script.js","launcherbackground.png","minecraft.ico"}) {
                InputStream is = MinecraftJSBridge.class.getClassLoader().getResourceAsStream("assets/fugoclient/web/" + fn);
                if (is != null) { Files.copy(is, new File(extracted, fn).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); is.close(); }
            }
            if (new File(extracted, "editor.html").exists()) return extracted;
        } catch (Exception ex) { ex.printStackTrace(); }
        return null;
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