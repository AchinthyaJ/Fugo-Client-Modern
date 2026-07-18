package io.github.fugoclient.mcefmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WebUIManager {
    private static final WebUIManager INSTANCE = new WebUIManager();
    private static final long STARTUP_TIME = System.currentTimeMillis();
    public static boolean vanillaCrosshairDisabled = false;
    public static boolean fullbrightEnabled = false;
    public static boolean isDisconnecting = false;

    public static boolean isVanillaCrosshairDisabled() {
        return vanillaCrosshairDisabled;
    }

    public static boolean isFullbrightEnabled() {
        return fullbrightEnabled;
    }
    
    private MCEFBrowser browser;
    private WebTitleScreen titleScreen;
    private boolean isOverlayVisible = false;
    private String currentPage = "";
    
    private Boolean lastUiStateInWorld = null;
    private Boolean lastUiStateIsWebTitle = null;
    private Boolean lastUiStateIsOverlay = null;
    private Boolean lastUiStateIsEditMode = null;

    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean lastLmbState = false;
    private boolean lastRmbState = false;
    private final List<Long> lmbClicks = new ArrayList<>();
    private final List<Long> rmbClicks = new ArrayList<>();

    private long lastCoordsTime = 0;
    private long lastKeystrokeTime = 0;
    private long lastFpsTime = 0;
    private long lastPingTime = 0;
    private long lastArmorTime = 0;
    private long lastSessionTime = 0;
    private int secondsElapsed = 0;

    private boolean lastW = false;
    private boolean lastA = false;
    private boolean lastS = false;
    private boolean lastD = false;
    private boolean lastSpace = false;
    private boolean lastLmb = false;
    private boolean lastRmb = false;
    private boolean lastShift = false;
    private boolean lastSprint = false;
    private Boolean lastInteractive = null;
    private Boolean lastEditMode = null;
    private int lastSentLmbCps = -1;
    private int lastSentRmbCps = -1;

    private static final long STATS_MIN_INTERVAL_MS = 500;
    private long lastStatsSentTime = 0;
    private long lastHudKeysOnlyTime = 0;
    private long lastStatsHash = 0;
    private final StringBuilder statsSb = new StringBuilder(1024);
    private final StringBuilder reusableSb = new StringBuilder(256);

    private WebUIManager() {
    }

    public static WebUIManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        extractResources();
        try { JCEFHelper.registerSandboxBypass(); } catch (Throwable ignored) {}
    }

    /**
     * Lazily create the browser once MCEF is initialized (called from the render thread).
     * MCEF auto-initializes via its own mixins during game startup, so creating the
     * browser must be deferred until then (and until the Minecraft window exists).
     */
    public void ensureBrowser() {
        if (browser != null) return;
        if (!MCEF.isInitialized()) return;

        try {
            org.cef.CefClient cefClient = MCEF.getClient().getHandle();
            var config = new org.cef.browser.CefMessageRouter.CefMessageRouterConfig("mcefQuery", "mcefQueryCancel");
            var msgRouter = org.cef.browser.CefMessageRouter.create(config);
            msgRouter.addHandler(new MinecraftJSBridge(), true);
            cefClient.addMessageRouter(msgRouter);
            System.out.println("[Fugo Client] JS Query Handler registered successfully!");
        } catch (Exception e) {
            System.err.println("[Fugo Client] Failed to register JS query handler!");
            e.printStackTrace();
        }

        File indexFile = new File(Minecraft.getInstance().gameDirectory, "web-ui/titlescreen.html");
        String url = "file://" + indexFile.getAbsolutePath();
        currentPage = "titlescreen";
        System.out.println("[Fugo Client] Loading URL: " + url);

        browser = MCEF.createBrowser(url, true);
        resizeToWindow();
    }

    private void resizeToWindow() {
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null || browser == null) return;
        double scale = client.getWindow().getGuiScale();
        browser.resize(
            (int) (client.getWindow().getGuiScaledWidth() * scale),
            (int) (client.getWindow().getGuiScaledHeight() * scale)
        );
    }

    public MCEFBrowser getBrowser() {
        return browser;
    }

    public WebTitleScreen getOrCreateTitleScreen() {
        if (titleScreen == null) {
            titleScreen = new WebTitleScreen(false);
        }
        return titleScreen;
    }

    public void resize(int width, int height) {
        if (width == lastWidth && height == lastHeight) {
            return;
        }
        lastWidth = width;
        lastHeight = height;
        if (browser != null) {
            browser.resize(width, height);
        }
    }

    public void registerClick(boolean right) {
        long now = System.currentTimeMillis();
        if (right) {
            rmbClicks.add(now);
        } else {
            lmbClicks.add(now);
        }
    }

    public int getCps(boolean right) {
        long now = System.currentTimeMillis();
        List<Long> clicks = right ? rmbClicks : lmbClicks;
        clicks.removeIf(time -> now - time > 1000);
        return clicks.size();
    }

    private String getArmorJson(ItemStack stack) {
        if (stack.isEmpty()) {
            return "{\"empty\":true}";
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int maxDamage = stack.getMaxDamage();
        int damage = stack.getDamageValue();
        int durability = maxDamage - damage;
        float percent = (maxDamage > 0) ? ((float) durability / maxDamage) * 100.0f : 100.0f;
        int count = stack.getCount();

        return String.format(Locale.US, "{\"empty\":false,\"id\":\"%s\",\"durability\":%d,\"max\":%d,\"percent\":%.1f,\"count\":%d,\"texture\":\"\"}",
            id, durability, maxDamage, percent, count);
    }

    public void updateHud() {
        MCEFBrowser browser = getBrowser();
        if (browser == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        long now = System.currentTimeMillis();
        LocalPlayer player = client.player;

        // 1. Keys & CPS - throttled to 100ms to prevent JS flood
        boolean w = client.options.keyUp.isDown();
        boolean a = client.options.keyLeft.isDown();
        boolean s = client.options.keyDown.isDown();
        boolean d = client.options.keyRight.isDown();
        boolean space = client.options.keyJump.isDown();
        boolean lmb = client.options.keyAttack.isDown();
        boolean rmb = client.options.keyUse.isDown();
        boolean shift = client.options.keyShift.isDown();
        boolean sprint = client.options.keySprint.isDown();

        if (lmb && !lastLmbState) registerClick(false);
        if (rmb && !lastRmbState) registerClick(true);
        lastLmbState = lmb;
        lastRmbState = rmb;

        int lmbCps = getCps(false);
        int rmbCps = getCps(true);

        boolean keysChanged = (w != lastW || a != lastA || s != lastS || d != lastD || space != lastSpace ||
                           lmb != lastLmb || rmb != lastRmb || shift != lastShift || sprint != lastSprint ||
                           lmbCps != lastSentLmbCps || rmbCps != lastSentRmbCps);

        lastW = w; lastA = a; lastS = s; lastD = d; lastSpace = space;
        lastLmb = lmb; lastRmb = rmb; lastShift = shift; lastSprint = sprint;

        if (isOverlayVisible && keysChanged && (now - lastKeystrokeTime >= 100)) {
            lastKeystrokeTime = now;
            lastSentLmbCps = lmbCps;
            lastSentRmbCps = rmbCps;
            reusableSb.setLength(0);
            reusableSb.append("window.MinecraftBridge.trigger('hud:update_keys',{\"w\":").append(w)
                      .append(",\"a\":").append(a).append(",\"s\":").append(s).append(",\"d\":").append(d)
                      .append(",\"space\":").append(space).append(",\"lmb\":").append(lmb).append(",\"rmb\":").append(rmb)
                      .append(",\"shift\":").append(shift).append(",\"sprint\":").append(sprint)
                      .append(",\"lmbCps\":").append(lmbCps).append(",\"rmbCps\":").append(rmbCps).append("});");
            browser.executeJavaScript(reusableSb.toString(), "", 0);
        }

        // 2. Stats update - throttled to 500ms (2Hz) to eliminate rendering stutter
        if (now - lastStatsSentTime < STATS_MIN_INTERVAL_MS) return;
        lastStatsSentTime = now;

        // Interactive state check
        boolean interactive = client.screen instanceof WebTitleScreen && ((WebTitleScreen) client.screen).isOverlay();
        boolean editMode = interactive && ((WebTitleScreen) client.screen).isEditMode();
        if (lastInteractive == null || lastEditMode == null || interactive != lastInteractive || editMode != lastEditMode) {
            lastInteractive = interactive;
            lastEditMode = editMode;
            if (isOverlayVisible) {
                reusableSb.setLength(0);
                reusableSb.append("window.MinecraftBridge.trigger('hud:update_interactive',{\"interactive\":")
                          .append(interactive).append(",\"editMode\":").append(editMode).append("});");
                browser.executeJavaScript(reusableSb.toString(), "", 0);
            }
        }

        if (now - lastSessionTime >= 1000) {
            lastSessionTime = now;
            secondsElapsed++;
        }

        if (!isOverlayVisible) return;

        int fps = client.getFps();
        int ping = 0;
        if (client.getConnection() != null && player.getUUID() != null) {
            net.minecraft.client.multiplayer.PlayerInfo entry = client.getConnection().getPlayerInfo(player.getUUID());
            if (entry != null) {
                ping = entry.getLatency();
            }
        }
        
        double x = player.getX(), y = player.getY(), z = player.getZ();
        String direction = player.getDirection().name().toUpperCase();
        double xSpeed = player.getX() - player.xo, zSpeed = player.getZ() - player.zo;
        double bps = Math.sqrt(xSpeed * xSpeed + zSpeed * zSpeed) * 20.0;
        int bpsInt = (int)(bps * 100 + 0.5);

        int healthVal = Math.round(player.getHealth());
        int maxHealthVal = Math.round(player.getMaxHealth());
        int hungerVal = player.getFoodData().getFoodLevel();
        int xpLevelVal = player.experienceLevel;
        int px = (int) x;
        int py = (int) y;
        int pz = (int) z;
        int facingOrdinal = player.getDirection().ordinal();
        int targetHealthVal = Math.round(PvPTracker.getTargetHealth());
        int targetDistInt = (int)(PvPTracker.getTargetDistance() * 100 + 0.5);
        int comboVal = PvPTracker.getCombo();
        int killsVal = PvPTracker.getKills();
        int deathsVal = PvPTracker.getDeaths();

        // Calculate numeric hash to check if any data has changed before building the payload
        long hash = fps;
        hash = hash * 31 + ping;
        hash = hash * 31 + secondsElapsed;
        hash = hash * 31 + killsVal;
        hash = hash * 31 + deathsVal;
        hash = hash * 31 + comboVal;
        hash = hash * 31 + healthVal;
        hash = hash * 31 + maxHealthVal;
        hash = hash * 31 + hungerVal;
        hash = hash * 31 + xpLevelVal;
        hash = hash * 31 + px;
        hash = hash * 31 + py;
        hash = hash * 31 + pz;
        hash = hash * 31 + facingOrdinal;
        hash = hash * 31 + bpsInt;
        hash = hash * 31 + targetHealthVal;
        hash = hash * 31 + targetDistInt;

        if (hash == lastStatsHash) return;
        lastStatsHash = hash;

        Runtime runtime = Runtime.getRuntime();
        long memUsed = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
        long memMax = runtime.maxMemory() >> 20;

        String biome = client.level.getBiome(player.blockPosition()).unwrapKey()
            .map(k -> k.location().getPath()).orElse("unknown");
        int cx = player.blockPosition().getX() >> 4, cz = player.blockPosition().getZ() >> 4;
        boolean isNether = client.level.dimension().location().getPath().contains("nether");
        double netherX = isNether ? x * 8.0 : x * 0.125, netherZ = isNether ? z * 8.0 : z * 0.125;

        ItemStack mainHand = player.getMainHandItem();
        String itemName = mainHand.isEmpty() ? "" : mainHand.getHoverName().getString();
        int itemMaxDmg = mainHand.getMaxDamage(), itemDmg = mainHand.isEmpty() ? 0 : itemMaxDmg - mainHand.getDamageValue();
        int itemCount = mainHand.getCount();
        float itemCooldown = mainHand.isEmpty() ? 0 : player.getCooldowns().getCooldownPercent(mainHand.getItem(), 0.0f);

        statsSb.setLength(0);
        statsSb.append("window.MinecraftBridge.trigger('hud:update_stats',{")
            .append("\"fps\":").append(fps).append(",\"ping\":").append(ping)
            .append(",\"bps\":").append(bpsInt / 100.0).append(",\"memUsed\":").append(memUsed)
            .append(",\"memMax\":").append(memMax).append(",\"frameTime\":").append(String.format(Locale.US, "%.2f", fps > 0 ? 1000.0/fps : 0))
            .append(",\"x\":").append(String.format(Locale.US, "%.1f", x)).append(",\"y\":").append(String.format(Locale.US, "%.1f", y))
            .append(",\"z\":").append(String.format(Locale.US, "%.1f", z)).append(",\"facing\":\"").append(direction)
            .append("\",\"yaw\":").append(String.format(Locale.US, "%.1f", player.getYRot())).append(",\"biome\":\"").append(biome)
            .append("\",\"cx\":").append(cx).append(",\"cz\":").append(cz)
            .append(",\"netherX\":").append(String.format(Locale.US, "%.1f", netherX)).append(",\"netherZ\":").append(String.format(Locale.US, "%.1f", netherZ))
            .append(",\"health\":").append(healthVal)
            .append(",\"maxHealth\":").append(maxHealthVal)
            .append(",\"hunger\":").append(hungerVal)
            .append(",\"absorption\":").append(String.format(Locale.US, "%.1f", player.getAbsorptionAmount()))
            .append(",\"air\":").append(player.getAirSupply()).append(",\"maxAir\":").append(player.getMaxAirSupply())
            .append(",\"xp\":").append(String.format(Locale.US, "%.2f", player.experienceProgress))
            .append(",\"xpLevel\":").append(xpLevelVal)
            .append(",\"sneaking\":").append(player.isShiftKeyDown()).append(",\"sprinting\":").append(player.isSprinting())
            .append(",\"combo\":").append(comboVal).append(",\"reach\":").append(String.format(Locale.US, "%.2f", PvPTracker.getReach()))
            .append(",\"targetName\":\"").append(PvPTracker.getTargetName().replace("\"", "\\\""))
            .append("\",\"targetHealth\":").append(targetHealthVal)
            .append(",\"targetMaxHealth\":").append(String.format(Locale.US, "%.1f", PvPTracker.getTargetMaxHealth()))
            .append(",\"targetDistance\":").append(targetDistInt / 100.0);

        String[] targetArmor = PvPTracker.getTargetArmor();
        statsSb.append(",\"targetArmor\":[\"").append(targetArmor[0] != null ? targetArmor[0] : "").append("\",\"")
            .append(targetArmor[1] != null ? targetArmor[1] : "").append("\",\"")
            .append(targetArmor[2] != null ? targetArmor[2] : "").append("\",\"")
            .append(targetArmor[3] != null ? targetArmor[3] : "").append("\"]")
            .append(",\"sessionTime\":").append(secondsElapsed).append(",\"kills\":").append(killsVal)
            .append(",\"deaths\":").append(deathsVal).append(",\"winStreak\":").append(PvPTracker.getWinStreak())
            .append(",\"currentStreak\":").append(PvPTracker.getCurrentStreak());

        String serverName = client.getCurrentServer() != null ? client.getCurrentServer().name : "Singleplayer";
        String serverIp = client.getCurrentServer() != null ? client.getCurrentServer().ip : "127.0.0.1";
        int serverPlayers = client.getConnection() != null ? client.getConnection().getOnlinePlayers().size() : 1;

        statsSb.append(",\"serverName\":\"").append(serverName.replace("\"", "\\\""))
            .append("\",\"serverIp\":\"").append(serverIp.replace("\"", "\\\"")).append("\",\"serverPlayers\":").append(serverPlayers);

        Scoreboard sb2 = client.level.getScoreboard();
        Objective obj = sb2.getDisplayObjective(1);
        statsSb.append(",\"scoreboard\":[");
        boolean first = true;
        if (obj != null) {
            for (Score e : sb2.getPlayerScores(obj)) {
                if (!first) statsSb.append(",");
                statsSb.append("\"").append(e.getOwner().replace("\"", "\\\"")).append("\"");
                first = false;
            }
        }
        statsSb.append("],\"potions\":[");

        first = true;
        for (MobEffectInstance e : player.getActiveEffects()) {
            if (!first) statsSb.append(",");
            int durationSecs = e.getDuration() / 20;
            boolean beneficial = e.getEffect().getCategory().equals(MobEffectCategory.BENEFICIAL);
            statsSb.append("{\"name\":\"").append(e.getEffect().getDisplayName().getString())
                   .append("\",\"duration\":\"").append(durationSecs / 60).append(":").append(String.format(Locale.US, "%02d", durationSecs % 60))
                   .append("\",\"amp\":").append(e.getAmplifier() + 1).append(",\"beneficial\":").append(beneficial)
                   .append(",\"color\":").append(e.getEffect().getColor()).append("}");
            first = false;
        }
        statsSb.append("]");

        String helmetJson = getArmorJson(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        String chestJson = getArmorJson(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        String leggingsJson = getArmorJson(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
        String bootsJson = getArmorJson(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
        String offhandJson = getArmorJson(player.getOffhandItem());

        statsSb.append(",\"item\":{\"name\":\"").append(itemName.replace("\"", "\\\"")).append("\",\"durability\":").append(itemDmg)
            .append(",\"maxDurability\":").append(itemMaxDmg).append(",\"count\":").append(itemCount)
            .append(",\"cooldown\":").append(String.format(Locale.US, "%.2f", itemCooldown)).append("}")
            .append(",\"armor\":{\"helmet\":").append(helmetJson).append(",\"chest\":").append(chestJson)
            .append(",\"leggings\":").append(leggingsJson).append(",\"boots\":").append(bootsJson)
            .append(",\"offhand\":").append(offhandJson).append("}");

        long worldTime = client.level.getDayTime();
        statsSb.append(",\"utility\":{\"day\":").append(worldTime / 24000)
            .append(",\"weather\":\"").append(client.level.isThundering() ? "Thunder" : (client.level.isRaining() ? "Rain" : "Clear"))
            .append("\",\"time\":\"").append(String.format(Locale.US, "%02d:%02d", (worldTime / 1000 + 6) % 24, (worldTime % 1000) * 60 / 1000))
            .append("\"}});");

        browser.executeJavaScript(statsSb.toString(), "", 0);
    }

    public void updateHudKeysOnly() {
        MCEFBrowser browser = getBrowser();
        if (browser == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        boolean w = client.options.keyUp.isDown();
        boolean a = client.options.keyLeft.isDown();
        boolean s = client.options.keyDown.isDown();
        boolean d = client.options.keyRight.isDown();
        boolean space = client.options.keyJump.isDown();
        boolean lmb = client.options.keyAttack.isDown();
        boolean rmb = client.options.keyUse.isDown();
        boolean shift = client.options.keyShift.isDown();
        boolean sprint = client.options.keySprint.isDown();

        if (lmb && !lastLmbState) {
            registerClick(false);
        }
        if (rmb && !lastRmbState) {
            registerClick(true);
        }
        lastLmbState = lmb;
        lastRmbState = rmb;

        int lmbCps = getCps(false);
        int rmbCps = getCps(true);

        lastW = w;
        lastA = a;
        lastS = s;
        lastD = d;
        lastSpace = space;
        lastLmb = lmb;
        lastRmb = rmb;
        lastShift = shift;
        lastSprint = sprint;
        
        String json = String.format(Locale.US, "{\"w\":%b,\"a\":%b,\"s\":%b,\"d\":%b,\"space\":%b,\"lmb\":%b,\"rmb\":%b,\"shift\":%b,\"sprint\":%b,\"lmbCps\":%d,\"rmbCps\":%d}",
            w, a, s, d, space, lmb, rmb, shift, sprint, lmbCps, rmbCps);
        browser.executeJavaScript("window.MinecraftBridge.trigger('hud:update_keys', " + json + ");", "", 0);
    }

    public boolean isOverlayVisible() {
        return isOverlayVisible;
    }

    public void toggleOverlay() {
        isOverlayVisible = !isOverlayVisible;
    }

    public void resetStateCache() {
        lastUiStateInWorld = null;
        lastUiStateIsWebTitle = null;
        lastUiStateIsOverlay = null;
        lastUiStateIsEditMode = null;
    }

    public void onStateChanged() {
        MCEFBrowser browser = getBrowser();
        if (browser == null) return;

        Minecraft client = Minecraft.getInstance();
        boolean inWorld = client.level != null;
        boolean isWebTitle = client.screen instanceof WebTitleScreen;
        boolean isOverlay = isWebTitle && ((WebTitleScreen) client.screen).isOverlay();
        boolean isEditMode = isWebTitle && ((WebTitleScreen) client.screen).isEditMode();

        isOverlayVisible = isOverlay || inWorld;

        String targetPage = inWorld ? "hud" : "titlescreen";
        if (!currentPage.equals(targetPage)) {
            currentPage = targetPage;
            File htmlFile = new File(client.gameDirectory, "web-ui/" + targetPage + ".html");
            System.out.println("[Fugo Client] Switching page to: " + htmlFile.getAbsolutePath());
            browser.loadURL("file://" + htmlFile.getAbsolutePath());
            resetStateCache();
            return;
        }

        if (lastUiStateInWorld == null || lastUiStateIsWebTitle == null || lastUiStateIsOverlay == null || lastUiStateIsEditMode == null ||
            inWorld != lastUiStateInWorld || isWebTitle != lastUiStateIsWebTitle || isOverlay != lastUiStateIsOverlay || isEditMode != lastUiStateIsEditMode) {

            lastUiStateInWorld = inWorld;
            lastUiStateIsWebTitle = isWebTitle;
            lastUiStateIsOverlay = isOverlay;
            lastUiStateIsEditMode = isEditMode;

            StringBuilder json = new StringBuilder();
            json.append("{\"inWorld\":").append(inWorld).append(",\"isWebTitle\":").append(isWebTitle)
                .append(",\"isOverlay\":").append(isOverlay).append(",\"editMode\":").append(isEditMode).append("}");
            browser.executeJavaScript("window.MinecraftBridge.trigger('ui:state_change'," + json.toString() + ");", "", 0);

            if (isWebTitle) {
                browser.executeJavaScript(
                    "window.MinecraftBridge.trigger('overlay:toggle',{\"visible\":" + isOverlay + ",\"editMode\":" + isEditMode + "});", "", 0);
            }
        }
    }

    public void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    private void extractResources() {
        File webDir = new File(Minecraft.getInstance().gameDirectory, "web-ui");
        if (!webDir.exists()) {
            webDir.mkdirs();
        }
        
        // Extract files
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
        try (InputStream in = getClass().getResourceAsStream(resourcePath);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) {
                System.err.println("[Fugo Client] Resource not found: " + resourcePath);
                return;
            }
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            System.out.println("[Fugo Client] Extracted resource to: " + destFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
