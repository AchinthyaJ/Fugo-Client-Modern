package io.github.fugoclient.mcefmod;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.callback.CefQueryCallback;

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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

public class MinecraftJSBridge extends CefMessageRouterHandlerAdapter {

    private static HttpServer editorServer = null;
    private static final int EDITOR_PORT = 8642;

    @Override
    public boolean onQuery(CefBrowser b, CefFrame frame, long queryId, String query, boolean persistent, CefQueryCallback cb) {
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
                                client.player.connection.sendCommand("time set " + ticks);
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
                                client.player.connection.sendCommand("weather " + weatherType);
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
                            Util.getPlatform().openUrl(file.toURI().toURL());
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
                        Util.getPlatform().openUrl(dir.toURI().toURL());
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
                case "theme:get": {
                    File themeFile = new File(client.gameDirectory, "fugo-theme.json");
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
                    cb.success("[]");
                    return true;
                }
                case "launch_editor": {
                    new Thread(() -> {
                        try {
                            startEditorServer();
                            // Open system browser
                            String url = "http://localhost:" + EDITOR_PORT + "/editor.html";
                            Util.getPlatform().openUri(url);
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
            e.printStackTrace();
            cb.failure(500, e.getMessage());
            return false;
        }
    }

    /**
     * Starts a lightweight HTTP server that serves the web UI directory
     * so the theme editor can be accessed in the system browser.
     */
    private static synchronized void startEditorServer() throws Exception {
        if (editorServer != null) {
            System.out.println("[Fugo Editor] Server already running on port " + EDITOR_PORT);
            return;
        }

        // Find the web assets directory - check common locations
        File webDir = findWebAssetsDir();
        if (webDir == null || !webDir.exists()) {
            throw new RuntimeException("Could not find web assets directory");
        }

        System.out.println("[Fugo Editor] Serving from: " + webDir.getAbsolutePath());

        editorServer = HttpServer.create(new InetSocketAddress("127.0.0.1", EDITOR_PORT), 0);
        final File serveDir = webDir;

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
                File themeFile = new File(Minecraft.getInstance().gameDirectory, "fugo-theme.json");
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
                    File themeFile = new File(Minecraft.getInstance().gameDirectory, "fugo-theme.json");
                    Files.write(themeFile.toPath(), bodyBytes);
                    
                    // Apply to in-game CEF browser
                    Minecraft.getInstance().execute(() -> {
                        try {
                            if (WebUIManager.getInstance().getBrowser() != null) {
                                WebUIManager.getInstance().getBrowser().executeJavaScript(
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
                    String notFound = "404 Not Found";
                    exchange.sendResponseHeaders(404, notFound.length());
                    exchange.getResponseBody().write(notFound.getBytes());
                    exchange.getResponseBody().close();
                    return;
                }
            } catch (Exception ex) {
                String notFound = "500 Internal Server Error";
                exchange.sendResponseHeaders(500, notFound.length());
                exchange.getResponseBody().write(notFound.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            // Determine content type
            String contentType = "application/octet-stream";
            String name = file.getName().toLowerCase();
            if (name.endsWith(".html")) contentType = "text/html; charset=utf-8";
            else if (name.endsWith(".css")) contentType = "text/css; charset=utf-8";
            else if (name.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
            else if (name.endsWith(".json")) contentType = "application/json; charset=utf-8";
            else if (name.endsWith(".png")) contentType = "image/png";
            else if (name.endsWith(".ico")) contentType = "image/x-icon";
            else if (name.endsWith(".svg")) contentType = "image/svg+xml";

            byte[] data = Files.readAllBytes(file.toPath());

            // CORS headers so the iframe works
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, data.length);
            OutputStream os = exchange.getResponseBody();
            os.write(data);
            os.close();
        });

        editorServer.setExecutor(null);
        editorServer.start();
        System.out.println("[Fugo Editor] HTTP server started on http://127.0.0.1:" + EDITOR_PORT);
    }

    /**
     * Locates the web assets directory on disk.
     * Checks dev environment paths and extracted resource locations.
     */
    private static File findWebAssetsDir() {
        // 1. Check dev environment (source tree)
        String[] devPaths = {
            "src/main/resources/assets/fugoclient/web",
            "../src/main/resources/assets/fugoclient/web",
            "forge-1.20.1/src/main/resources/assets/fugoclient/web"
        };
        for (String p : devPaths) {
            File f = new File(p);
            if (f.exists() && new File(f, "editor.html").exists()) return f;
        }

        // 2. Check game directory for extracted assets
        Minecraft mc = Minecraft.getInstance();
        File gameDir = mc.gameDirectory;
        File extracted = new File(gameDir, "web-ui");
        if (extracted.exists() && new File(extracted, "editor.html").exists()) return extracted;

        // 3. Try to extract from classpath to game directory
        try {
            String[] webFiles = {"editor.html", "editor.css", "editor.js", "hud.html", "titlescreen.html", "style.css", "script.js", "launcherbackground.png", "minecraft.ico"};
            extracted.mkdirs();
            for (String fileName : webFiles) {
                InputStream is = MinecraftJSBridge.class.getClassLoader().getResourceAsStream("assets/fugoclient/web/" + fileName);
                if (is != null) {
                    Files.copy(is, new File(extracted, fileName).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    is.close();
                }
            }
            if (new File(extracted, "editor.html").exists()) return extracted;
        } catch (Exception ex) {
            System.out.println("[Fugo Editor] Failed to extract web assets: " + ex.getMessage());
        }

        return null;
    }

    @Override
    public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
    }
}