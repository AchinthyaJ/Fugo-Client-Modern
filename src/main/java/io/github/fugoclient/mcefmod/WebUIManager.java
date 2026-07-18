package io.github.fugoclient.mcefmod;

import net.minecraft.client.MinecraftClient;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class WebUIManager {
    private static final WebUIManager INSTANCE = new WebUIManager();
    private static final long STARTUP_TIME = System.currentTimeMillis();
    public static boolean vanillaCrosshairDisabled = false;
    public static boolean fullbrightEnabled = false;
    public static boolean isDisconnecting = false;
    
    // Browser frame rate constants - REDUCED to absolute minimum
    public static final int BROWSER_FPS_ACTIVE = 10;   // 10 FPS when visible - still enough for HUD text
    public static final int BROWSER_FPS_IDLE = 1;      // 1 FPS when minimized/hidden

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final String WEB_UI_PATH;

    static {
        WEB_UI_PATH = new File(MinecraftClient.getInstance().runDirectory, "web-ui").getAbsolutePath().replace('\\', '/');
    }

    public static boolean isVanillaCrosshairDisabled() { return vanillaCrosshairDisabled; }
    public static boolean isFullbrightEnabled() { return fullbrightEnabled; }
    
    private MCEFBrowser browser;
    private WebTitleScreen titleScreen;
    private boolean isOverlayVisible = false;
    private String currentPage = "";

    // GC-safe state tracking: use primitive booleans + sentinel
    private boolean lastUiStateInWorld_set = false;
    private boolean lastUiStateInWorld = false;
    private boolean lastUiStateIsWebTitle_set = false;
    private boolean lastUiStateIsWebTitle = false;
    private boolean lastUiStateIsOverlay_set = false;
    private boolean lastUiStateIsOverlay = false;
    private boolean lastUiStateIsEditMode_set = false;
    private boolean lastUiStateIsEditMode = false;

    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean lastLmbState = false;
    private boolean lastRmbState = false;
    private final java.util.ArrayDeque<Long> lmbClicks = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Long> rmbClicks = new java.util.ArrayDeque<>();

    private long lastCoordsTime = 0;
    private long lastKeystrokeTime = 0;
    private long lastCpsCleanupTime = 0;
    private int secondsElapsed = 0;

    private boolean lastW = false, lastA = false, lastS = false, lastD = false;
    private boolean lastSpace = false, lastLmb = false, lastRmb = false;
    private boolean lastShift = false, lastSprint = false;
    private boolean lastInteractive_set = false;
    private boolean lastInteractive = false;
    private boolean lastEditMode_set = false;
    private boolean lastEditMode = false;
    private int lastSentLmbCps = -1;
    private int lastSentRmbCps = -1;

    // Pre-allocated StringBuilders - ZERO allocations in hot path
    private final StringBuilder reusableSb = new StringBuilder(320);
    private final StringBuilder statsSb = new StringBuilder(1024);
    private final StringBuilder stateSb = new StringBuilder(160);
    private long lastStatsHash = 0;
    private long lastHudKeysOnlyTime = 0;

    // Throttle: only send stats every 500ms minimum
    private static final long STATS_MIN_INTERVAL_MS = 500;
    private long lastStatsSentTime = 0;

    private WebUIManager() {}

    public static WebUIManager getInstance() { return INSTANCE; }

    public void init() {
        extractResources();
        MCEFApi.initialize();
        try { JCEFHelper.registerSandboxBypass(); } catch (Throwable ignored) {}

        MCEFApi.getInstanceFuture().thenAccept(api -> {
            try {
                java.lang.reflect.Field clientField = null;
                Class<?> clazz = api.getClass();
                while (clazz != null) {
                    try { clientField = clazz.getDeclaredField("client"); break; }
                    catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
                }
                if (clientField == null) throw new NoSuchFieldException("Field 'client' not found");
                clientField.setAccessible(true);
                org.cef.CefClient cefClient = (org.cef.CefClient) clientField.get(api);

                var config = new org.cef.browser.CefMessageRouter.CefMessageRouterConfig("mcefQuery", "mcefQueryCancel");
                var msgRouter = org.cef.browser.CefMessageRouter.create(config);
                msgRouter.addHandler(new MinecraftJSBridge(), true);
                cefClient.addMessageRouter(msgRouter);

                cefClient.addDisplayHandler(new org.cef.handler.CefDisplayHandlerAdapter() {
                    @Override public boolean onConsoleMessage(org.cef.browser.CefBrowser b, org.cef.CefSettings.LogSeverity level, String message, String source, int line) {
                        return false;
                    }
                });

                cefClient.addLoadHandler(new org.cef.handler.CefLoadHandlerAdapter() {
                    @Override public void onLoadStart(org.cef.browser.CefBrowser b, org.cef.browser.CefFrame frame, org.cef.network.CefRequest.TransitionType transitionType) {
                        String url = frame.getURL();
                        if (url != null && url.startsWith("https://login.live.com/oauth20_desktop.srf")) {
                            String code = null;
                            int idx = url.indexOf("code=");
                            if (idx != -1) {
                                int end = url.indexOf("&", idx);
                                code = (end != -1) ? url.substring(idx + 5, end) : url.substring(idx + 5);
                            }
                            boolean inWorld = MinecraftClient.getInstance().world != null;
                            currentPage = inWorld ? "hud" : "titlescreen";
                            b.loadURL("file://" + WEB_UI_PATH + "/" + currentPage + ".html");
                            if (code != null) {
                                final String authCode = code;
                                new Thread(() -> {
                                    try {
                                        var profile = ProfileManager.getInstance().authenticateMicrosoft(authCode);
                                        if (profile != null) {
                                            ProfileManager.getInstance().addProfile(profile);
                                            Thread.sleep(1000);
                                            b.executeJavaScript("window.MinecraftBridge.trigger('profile:add_success', " + GSON.toJson(profile) + ");", "", 0);
                                        } else {
                                            b.executeJavaScript("window.MinecraftBridge.trigger('profile:add_failure', 'Authentication failed');", "", 0);
                                        }
                                    } catch (Exception e) {
                                        b.executeJavaScript("window.MinecraftBridge.trigger('profile:add_failure', " + GSON.toJson(e.getMessage()) + ");", "", 0);
                                    }
                                }).start();
                            }
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("[Fugo Client] Failed to register JCEF message router!");
            }

            currentPage = "titlescreen";
            browser = api.createBrowser("file://" + WEB_UI_PATH + "/titlescreen.html", true);

            try {
                browser.getCefBrowser().getClient().addRequestHandler(new org.cef.handler.CefRequestHandlerAdapter() {
                    @Override public org.cef.handler.CefResourceRequestHandler getResourceRequestHandler(org.cef.browser.CefBrowser b, org.cef.browser.CefFrame f, org.cef.network.CefRequest r, boolean isNav, boolean isDl, String init, org.cef.misc.BoolRef def) {
                        return new org.cef.handler.CefResourceRequestHandlerAdapter() {
                            @Override public boolean onResourceResponse(org.cef.browser.CefBrowser b, org.cef.browser.CefFrame f, org.cef.network.CefRequest r, org.cef.network.CefResponse resp) {
                                java.util.Map<String, String> headers = new java.util.HashMap<>();
                                resp.getHeaderMap(headers);
                                if (headers.keySet().removeIf(k -> k.equalsIgnoreCase("x-frame-options") || k.equalsIgnoreCase("content-security-policy"))) {
                                    resp.setHeaderMap(headers);
                                }
                                return false;
                            }
                        };
                    }
                });
            } catch (Throwable ignored) {}

            var client = MinecraftClient.getInstance();
            double scale = client.getWindow().getScaleFactor();
            browser.resize((int)(client.getWindow().getScaledWidth() * scale), (int)(client.getWindow().getScaledHeight() * scale));

            // Cap CEF rendering to 10 FPS — HUD text doesn't need more, saves massive CPU/GPU
            try {
                browser.getCefBrowser().setWindowlessFrameRate(BROWSER_FPS_ACTIVE);
            } catch (Throwable ignored) {}
        });
    }

    public MCEFBrowser getBrowser() { return browser; }

    /**
     * Dynamically adjust CEF's internal rendering frame rate.
     * Call with BROWSER_FPS_IDLE (1) when overlay is hidden to save CPU.
     * Call with BROWSER_FPS_ACTIVE (10) when overlay becomes visible.
     */
    public void setBrowserFrameRate(int fps) {
        if (browser != null && browser.getCefBrowser() != null) {
            try {
                browser.getCefBrowser().setWindowlessFrameRate(fps);
            } catch (Throwable ignored) {}
        }
    }

    public WebTitleScreen getOrCreateTitleScreen() {
        if (titleScreen == null) titleScreen = new WebTitleScreen(false);
        return titleScreen;
    }

    public void resize(int width, int height) {
        if (width == lastWidth && height == lastHeight) return;
        lastWidth = width; lastHeight = height;
        if (browser != null) browser.resize(width, height);
    }

    public void registerClick(boolean right) {
        long now = System.currentTimeMillis();
        var clicks = right ? rmbClicks : lmbClicks;
        if (now - lastCpsCleanupTime > 1000) {
            while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
            lastCpsCleanupTime = now;
        }
        clicks.addLast(now);
    }

    public int getCps(boolean right) {
        long now = System.currentTimeMillis();
        var clicks = right ? rmbClicks : lmbClicks;
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
        return clicks.size();
    }

    public void updateHud() {
        MCEFBrowser browser = getBrowser();
        if (browser == null || browser.getCefBrowser() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        long now = System.currentTimeMillis();
        if ((now - STARTUP_TIME) < 10000 && (now - lastCoordsTime) < 1000) return;

        var player = client.player;

        // Keys - always computed (lightweight, no allocations)
        boolean w = client.options.forwardKey.isPressed();
        boolean a = client.options.leftKey.isPressed();
        boolean s = client.options.backKey.isPressed();
        boolean d = client.options.rightKey.isPressed();
        boolean space = client.options.jumpKey.isPressed();
        boolean lmb = client.options.attackKey.isPressed();
        boolean rmb = client.options.useKey.isPressed();
        boolean shift = client.options.sneakKey.isPressed();
        boolean sprint = client.options.sprintKey.isPressed();

        if (lmb && !lastLmbState) registerClick(false);
        if (rmb && !lastRmbState) registerClick(true);
        lastLmbState = lmb; lastRmbState = rmb;

        int lmbCps = getCps(false);
        int rmbCps = getCps(true);

        boolean keysChanged = (w != lastW || a != lastA || s != lastS || d != lastD || space != lastSpace ||
            lmb != lastLmb || rmb != lastRmb || shift != lastShift || sprint != lastSprint ||
            lmbCps != lastSentLmbCps || rmbCps != lastSentRmbCps);

        lastW = w; lastA = a; lastS = s; lastD = d; lastSpace = space;
        lastLmb = lmb; lastRmb = rmb; lastShift = shift; lastSprint = sprint;

        // Throttle key updates to 100ms (10Hz) instead of 50ms (20Hz)
        if (isOverlayVisible && keysChanged && (now - lastKeystrokeTime >= 100)) {
            lastKeystrokeTime = now;
            lastSentLmbCps = lmbCps; lastSentRmbCps = rmbCps;
            StringBuilder sb = reusableSb;
            sb.setLength(0);
            sb.append("window.MinecraftBridge.trigger('hud:update_keys',{\"w\":").append(w)
              .append(",\"a\":").append(a).append(",\"s\":").append(s).append(",\"d\":").append(d)
              .append(",\"space\":").append(space).append(",\"lmb\":").append(lmb).append(",\"rmb\":").append(rmb)
              .append(",\"shift\":").append(shift).append(",\"sprint\":").append(sprint)
              .append(",\"lmbCps\":").append(lmbCps).append(",\"rmbCps\":").append(rmbCps).append("});");
            browser.getCefBrowser().executeJavaScript(sb.toString(), "", 0);
        }

        // Check interactive state (every 1000ms - was 500ms)
        if (now - lastCoordsTime < 1000) {
            boolean interactive = client.currentScreen instanceof WebTitleScreen && ((WebTitleScreen) client.currentScreen).isOverlay();
            boolean editMode = interactive && ((WebTitleScreen) client.currentScreen).isEditMode();
            if (!lastInteractive_set || !lastEditMode_set || interactive != lastInteractive || editMode != lastEditMode) {
                lastInteractive_set = true; lastInteractive = interactive;
                lastEditMode_set = true; lastEditMode = editMode;
                if (isOverlayVisible) {
                    StringBuilder sb = reusableSb;
                    sb.setLength(0);
                    sb.append("window.MinecraftBridge.trigger('hud:update_interactive',{\"interactive\":")
                      .append(interactive).append(",\"editMode\":").append(editMode).append("});");
                    browser.getCefBrowser().executeJavaScript(sb.toString(), "", 0);
                }
            }
            return;
        }
        lastCoordsTime = now;

        int newSeconds = (int)((now - STARTUP_TIME) / 1000);
        if (newSeconds > secondsElapsed) secondsElapsed = newSeconds;

        if (!isOverlayVisible) return;

        // Throttle stats updates to minimum 500ms interval
        if (now - lastStatsSentTime < STATS_MIN_INTERVAL_MS) return;
        lastStatsSentTime = now;

        // Compute cheap numeric hash BEFORE building the JSON string to skip unchanged frames
        int fps = client.getCurrentFps();
        var entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
        int ping = entry != null ? entry.getLatency() : 0;
        int kills = PvPTracker.getKills();
        int deaths = PvPTracker.getDeaths();
        int combo = PvPTracker.getCombo();
        int health = Math.round(player.getHealth());
        int maxHealth = Math.round(player.getMaxHealth());
        int hunger = player.getHungerManager().getFoodLevel();
        int xpLevel = player.experienceLevel;
        int px = (int) player.getX();
        int py = (int) player.getY();
        int pz = (int) player.getZ();
        int facingOrdinal = player.getHorizontalFacing().ordinal();

        double xSpeed = player.getX() - player.lastX;
        double zSpeed = player.getZ() - player.lastZ;
        double bps = Math.sqrt(xSpeed * xSpeed + zSpeed * zSpeed) * 20.0;
        int bpsInt = (int)(bps * 100 + 0.5);

        int targetHealthVal = Math.round(PvPTracker.getTargetHealth());
        int targetDistInt = (int)(PvPTracker.getTargetDistance() * 100 + 0.5);

        long worldTime = client.world.getTimeOfDay();
        int day = (int)(worldTime / 24000);
        int weatherCode = client.world.isThundering() ? 2 : (client.world.isRaining() ? 1 : 0);
        int hour24 = (int)((worldTime / 1000 + 6) % 24);
        int minute = (int)((worldTime % 1000) * 60 / 1000);

        // Compute fast hash from primitives - ZERO string allocations
        long hash = fps;
        hash = hash * 31 + ping;
        hash = hash * 31 + secondsElapsed;
        hash = hash * 31 + kills;
        hash = hash * 31 + deaths;
        hash = hash * 31 + combo;
        hash = hash * 31 + health;
        hash = hash * 31 + maxHealth;
        hash = hash * 31 + hunger;
        hash = hash * 31 + xpLevel;
        hash = hash * 31 + px;
        hash = hash * 31 + py;
        hash = hash * 31 + pz;
        hash = hash * 31 + facingOrdinal;
        hash = hash * 31 + bpsInt;
        hash = hash * 31 + targetHealthVal;
        hash = hash * 31 + targetDistInt;
        hash = hash * 31 + day;
        hash = hash * 31 + weatherCode;
        hash = hash * 31 + hour24;
        hash = hash * 31 + minute;

        if (hash == lastStatsHash) return;
        lastStatsHash = hash;

        // Build stats JSON using pre-allocated StringBuilder - only when data changed
        String facingStr = player.getHorizontalFacing().asString().toUpperCase();
        String targetName = PvPTracker.getTargetName();
        String[] targetArmor = PvPTracker.getTargetArmor();

        StringBuilder json = statsSb;
        json.setLength(0);
        json.append("window.MinecraftBridge.trigger('hud:update_stats',{")
            .append("\"fps\":").append(fps)
            .append(",\"ping\":").append(ping)
            .append(",\"sessionTime\":").append(secondsElapsed)
            .append(",\"kills\":").append(kills)
            .append(",\"deaths\":").append(deaths)
            .append(",\"combo\":").append(combo)
            .append(",\"health\":").append(health)
            .append(",\"maxHealth\":").append(maxHealth)
            .append(",\"hunger\":").append(hunger)
            .append(",\"xpLevel\":").append(xpLevel)
            .append(",\"x\":").append(px)
            .append(",\"y\":").append(py)
            .append(",\"z\":").append(pz)
            .append(",\"facing\":\"").append(facingStr).append('"')
            .append(",\"bps\":").append(bpsInt / 100.0);

        json.append(",\"targetName\":\"").append(targetName.replace("\"", "\\\"")).append('"');
        json.append(",\"targetHealth\":").append(targetHealthVal)
            .append(",\"targetDistance\":").append(targetDistInt / 100.0);

        json.append(",\"targetArmor\":[\"")
            .append(nullToEmpty(targetArmor[0])).append("\",\"")
            .append(nullToEmpty(targetArmor[1])).append("\",\"")
            .append(nullToEmpty(targetArmor[2])).append("\",\"")
            .append(nullToEmpty(targetArmor[3])).append("\"]");

        json.append(",\"utility\":{\"day\":").append(day)
            .append(",\"weather\":\"").append(weatherCode == 2 ? "Thunder" : (weatherCode == 1 ? "Rain" : "Clear"))
            .append("\",\"time\":\"");
        if (hour24 < 10) json.append('0');
        json.append(hour24).append(':');
        if (minute < 10) json.append('0');
        json.append(minute).append("\"}});");

        browser.getCefBrowser().executeJavaScript(json.toString(), "", 0);
    }

    public void updateHudKeysOnly() {
        // Throttle to max ~20Hz (was 60Hz) to avoid JS flooding from rapid input events
        long now = System.currentTimeMillis();
        if (now - lastHudKeysOnlyTime < 50) return;
        lastHudKeysOnlyTime = now;

        MCEFBrowser browser = getBrowser();
        if (browser == null || browser.getCefBrowser() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (!isOverlayVisible) return;

        boolean w = client.options.forwardKey.isPressed();
        boolean a = client.options.leftKey.isPressed();
        boolean s = client.options.backKey.isPressed();
        boolean d = client.options.rightKey.isPressed();
        boolean space = client.options.jumpKey.isPressed();
        boolean lmb = client.options.attackKey.isPressed();
        boolean rmb = client.options.useKey.isPressed();
        boolean shift = client.options.sneakKey.isPressed();
        boolean sprint = client.options.sprintKey.isPressed();

        if (lmb && !lastLmbState) registerClick(false);
        if (rmb && !lastRmbState) registerClick(true);
        lastLmbState = lmb; lastRmbState = rmb;

        // Skip JS call if no keys actually changed
        int lmbCps = getCps(false);
        int rmbCps = getCps(true);
        if (w == lastW && a == lastA && s == lastS && d == lastD && space == lastSpace &&
            lmb == lastLmb && rmb == lastRmb && shift == lastShift && sprint == lastSprint &&
            lmbCps == lastSentLmbCps && rmbCps == lastSentRmbCps) return;

        lastW = w; lastA = a; lastS = s; lastD = d; lastSpace = space;
        lastLmb = lmb; lastRmb = rmb; lastShift = shift; lastSprint = sprint;
        lastSentLmbCps = lmbCps; lastSentRmbCps = rmbCps;

        StringBuilder sb = reusableSb;
        sb.setLength(0);
        sb.append("window.MinecraftBridge.trigger('hud:update_keys',{\"w\":").append(w)
          .append(",\"a\":").append(a).append(",\"s\":").append(s).append(",\"d\":").append(d)
          .append(",\"space\":").append(space).append(",\"lmb\":").append(lmb).append(",\"rmb\":").append(rmb)
          .append(",\"shift\":").append(shift).append(",\"sprint\":").append(sprint)
          .append(",\"lmbCps\":").append(lmbCps).append(",\"rmbCps\":").append(rmbCps).append("});");
        browser.getCefBrowser().executeJavaScript(sb.toString(), "", 0);
    }

    public boolean isOverlayVisible() { return isOverlayVisible; }
    public void toggleOverlay() { isOverlayVisible = !isOverlayVisible; }
    
    public void resetStateCache() {
        lastUiStateInWorld_set = false;
        lastUiStateIsWebTitle_set = false;
        lastUiStateIsOverlay_set = false;
        lastUiStateIsEditMode_set = false;
    }

    public void onStateChanged() {
        MCEFBrowser browser = getBrowser();
        if (browser == null || browser.getCefBrowser() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        boolean inWorld = client.world != null;
        boolean isWebTitle = client.currentScreen instanceof WebTitleScreen;
        boolean isOverlay = isWebTitle && ((WebTitleScreen) client.currentScreen).isOverlay();
        boolean isEditMode = isWebTitle && ((WebTitleScreen) client.currentScreen).isEditMode();

        isOverlayVisible = isOverlay || inWorld;

        String targetPage = inWorld ? "hud" : "titlescreen";
        if (!currentPage.equals(targetPage)) {
            currentPage = targetPage;
            browser.getCefBrowser().loadURL("file://" + WEB_UI_PATH + "/" + targetPage + ".html");
            resetStateCache();
            return;
        }

        boolean stateChanged = !lastUiStateInWorld_set || !lastUiStateIsWebTitle_set ||
            !lastUiStateIsOverlay_set || !lastUiStateIsEditMode_set ||
            inWorld != lastUiStateInWorld || isWebTitle != lastUiStateIsWebTitle ||
            isOverlay != lastUiStateIsOverlay || isEditMode != lastUiStateIsEditMode;

        if (stateChanged) {
            lastUiStateInWorld_set = true; lastUiStateInWorld = inWorld;
            lastUiStateIsWebTitle_set = true; lastUiStateIsWebTitle = isWebTitle;
            lastUiStateIsOverlay_set = true; lastUiStateIsOverlay = isOverlay;
            lastUiStateIsEditMode_set = true; lastUiStateIsEditMode = isEditMode;

            StringBuilder sb = stateSb;
            sb.setLength(0);
            sb.append("window.MinecraftBridge.trigger('ui:state_change',{")
                .append("\"inWorld\":").append(inWorld)
                .append(",\"isWebTitle\":").append(isWebTitle)
                .append(",\"isOverlay\":").append(isOverlay)
                .append(",\"editMode\":").append(isEditMode).append("});");
            browser.getCefBrowser().executeJavaScript(sb.toString(), "", 0);

            if (isWebTitle) {
                browser.getCefBrowser().executeJavaScript(
                    "window.MinecraftBridge.trigger('overlay:toggle',{\"visible\":" + isOverlay + ",\"editMode\":" + isEditMode + "});", "", 0);
            }
        }
    }

    public void close() {
        if (browser != null) { browser.close(); browser = null; }
    }

    private void extractResources() {
        File webDir = new File(MinecraftClient.getInstance().runDirectory, "web-ui");
        if (!webDir.exists()) webDir.mkdirs();
        extractFile("/assets/fugoclient/web/titlescreen.html", new File(webDir, "titlescreen.html"));
        extractFile("/assets/fugoclient/web/hud.html", new File(webDir, "hud.html"));
        extractFile("/assets/fugoclient/web/style.css", new File(webDir, "style.css"));
        extractFile("/assets/fugoclient/web/script.js", new File(webDir, "script.js"));
        extractFile("/assets/fugoclient/web/minecraft.ico", new File(webDir, "minecraft.ico"));
        extractFile("/assets/fugoclient/web/launcherbackground.png", new File(webDir, "launcherbackground.png"));
        extractFile("/assets/fugoclient/web/editor.html", new File(webDir, "editor.html"));
        extractFile("/assets/fugoclient/web/editor.css", new File(webDir, "editor.css"));
        extractFile("/assets/fugoclient/web/editor.js", new File(webDir, "editor.js"));
    }

    private void extractFile(String resourcePath, File destFile) {
        if (destFile.exists() && destFile.lastModified() > STARTUP_TIME - 60000) return;
        try (InputStream in = getClass().getResourceAsStream(resourcePath);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) return;
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) out.write(buffer, 0, length);
        } catch (Exception ignored) {}
    }

    private static String nullToEmpty(String s) { return s != null ? s : ""; }
}