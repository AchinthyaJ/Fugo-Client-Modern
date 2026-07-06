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
    private final java.util.List<Long> lmbClicks = new java.util.ArrayList<>();
    private final java.util.List<Long> rmbClicks = new java.util.ArrayList<>();

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

    private WebUIManager() {
    }

    public static WebUIManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        extractResources();

        // Trigger MCEF initialization (CefApp handler must be registered AFTER MCEF
        // sets up its classloader, but BEFORE CefApp is initialized)
        MCEFApi.initialize();

        // Register sandbox bypass handler now that JCEF classes are loaded on the classpath
        try {
            JCEFHelper.registerSandboxBypass();
        } catch (Throwable t) {
            System.err.println("[Fugo Client] Failed to add CefAppHandler: " + t.getMessage());
        }

        // Get API instance asynchronously when done
        MCEFApi.getInstanceFuture().thenAccept(api -> {
            try {
                // Register JS Query Handler using reflection to access CefClient
                java.lang.reflect.Field clientField = null;
                Class<?> clazz = api.getClass();
                while (clazz != null) {
                    try {
                        clientField = clazz.getDeclaredField("client");
                        break;
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                }
                if (clientField == null) {
                    throw new NoSuchFieldException("Field 'client' not found in " + api.getClass().getName());
                }
                clientField.setAccessible(true);
                org.cef.CefClient cefClient = (org.cef.CefClient) clientField.get(api);

                // Create the router configuration and handler first
                org.cef.browser.CefMessageRouter.CefMessageRouterConfig config =
                    new org.cef.browser.CefMessageRouter.CefMessageRouterConfig("mcefQuery", "mcefQueryCancel");
                org.cef.browser.CefMessageRouter msgRouter = org.cef.browser.CefMessageRouter.create(config);
                msgRouter.addHandler(new MinecraftJSBridge(), true);

                // Add the router to the client
                cefClient.addMessageRouter(msgRouter);

                // Log CEF console messages to System.out
                cefClient.addDisplayHandler(new org.cef.handler.CefDisplayHandlerAdapter() {
                    @Override
                    public boolean onConsoleMessage(org.cef.browser.CefBrowser b, org.cef.CefSettings.LogSeverity level, String message, String source, int line) {
                        System.out.println("[Fugo UI Console] (" + source + ":" + line + ") " + message);
                        return false;
                    }
                });

                // Add the load handler to catch Microsoft login redirects
                cefClient.addLoadHandler(new org.cef.handler.CefLoadHandlerAdapter() {
                    @Override
                    public void onLoadStart(org.cef.browser.CefBrowser b, org.cef.browser.CefFrame frame, org.cef.network.CefRequest.TransitionType transitionType) {
                        String url = frame.getURL();
                        if (url != null && url.startsWith("https://login.live.com/oauth20_desktop.srf")) {
                            System.out.println("[Fugo Client] Microsoft OAuth redirect detected: " + url);
                            
                            // Parse code parameter
                            String code = null;
                            int idx = url.indexOf("code=");
                            if (idx != -1) {
                                int end = url.indexOf("&", idx);
                                if (end != -1) {
                                    code = url.substring(idx + 5, end);
                                } else {
                                    code = url.substring(idx + 5);
                                }
                            }
                            
                            // Redirect browser back to our Web UI
                            boolean inWorld = MinecraftClient.getInstance().world != null;
                            File indexFile = new File(MinecraftClient.getInstance().runDirectory, "web-ui/" + (inWorld ? "hud" : "titlescreen") + ".html");
                            currentPage = inWorld ? "hud" : "titlescreen";
                            b.loadURL("file://" + indexFile.getAbsolutePath());
                            
                            if (code != null) {
                                final String authCode = code;
                                new Thread(() -> {
                                    try {
                                        ProfileManager.Profile profile = ProfileManager.getInstance().authenticateMicrosoft(authCode);
                                        if (profile != null) {
                                            ProfileManager.getInstance().addProfile(profile);
                                            System.out.println("[Fugo Client] Microsoft profile added successfully: " + profile.username);
                                            // Delay slightly to allow page load to finish before triggering
                                            Thread.sleep(1000);
                                            b.executeJavaScript("window.MinecraftBridge.trigger('profile:add_success', " + new com.google.gson.Gson().toJson(profile) + ");", "", 0);
                                        } else {
                                            b.executeJavaScript("window.MinecraftBridge.trigger('profile:add_failure', 'Authentication failed');", "", 0);
                                        }
                                    } catch (Exception e) {
                                        System.err.println("[Fugo Client] Microsoft Auth Error:");
                                        e.printStackTrace();
                                        b.executeJavaScript("window.MinecraftBridge.trigger('profile:add_failure', " + new com.google.gson.Gson().toJson(e.getMessage()) + ");", "", 0);
                                    }
                                }).start();
                            }
                        }
                    }
                });

                System.out.println("[Fugo Client] JCEF message router successfully registered!");
            } catch (Exception e) {
                System.err.println("[Fugo Client] Failed to register JCEF message router!");
                e.printStackTrace();
            }

            // Prepare local URL
            File indexFile = new File(MinecraftClient.getInstance().runDirectory, "web-ui/titlescreen.html");
            String url = "file://" + indexFile.getAbsolutePath();
            currentPage = "titlescreen";
            System.out.println("[Fugo Client] Loading URL: " + url);

            // Create browser (transparent = true)
            browser = api.createBrowser(url, true);

            try {
                browser.getCefBrowser().getClient().addRequestHandler(new org.cef.handler.CefRequestHandlerAdapter() {
                    @Override
                    public org.cef.handler.CefResourceRequestHandler getResourceRequestHandler(
                            org.cef.browser.CefBrowser browser,
                            org.cef.browser.CefFrame frame,
                            org.cef.network.CefRequest request,
                            boolean isNavigation,
                            boolean isDownload,
                            String requestInitiator,
                            org.cef.misc.BoolRef disableDefaultHandling) {
                        return new org.cef.handler.CefResourceRequestHandlerAdapter() {
                            @Override
                            public boolean onResourceResponse(
                                    org.cef.browser.CefBrowser browser,
                                    org.cef.browser.CefFrame frame,
                                    org.cef.network.CefRequest request,
                                    org.cef.network.CefResponse response) {
                                java.util.Map<String, String> headers = new java.util.HashMap<>();
                                response.getHeaderMap(headers);
                                boolean changed = headers.keySet().removeIf(key -> 
                                    key.equalsIgnoreCase("x-frame-options") || 
                                    key.equalsIgnoreCase("content-security-policy")
                                );
                                if (changed) {
                                    response.setHeaderMap(headers);
                                }
                                return false;
                            }
                        };
                    }
                });
                System.out.println("[Fugo Client] Registered X-Frame-Options bypass handler");
            } catch (Throwable t) {
                System.err.println("[Fugo Client] Failed to register X-Frame-Options bypass handler: " + t.getMessage());
            }

            // Set initial size
            MinecraftClient client = MinecraftClient.getInstance();
            double scale = client.getWindow().getScaleFactor();
            browser.resize(
                (int) (client.getWindow().getScaledWidth() * scale),
                (int) (client.getWindow().getScaledHeight() * scale)
            );
        });
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
        java.util.List<Long> clicks = right ? rmbClicks : lmbClicks;
        clicks.removeIf(time -> now - time > 1000);
        return clicks.size();
    }

    private String getArmorJson(net.minecraft.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return "{\"empty\":true}";
        }
        String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
        int maxDamage = stack.getMaxDamage();
        int damage = stack.getDamage();
        int durability = maxDamage - damage;
        float percent = (maxDamage > 0) ? ((float) durability / maxDamage) * 100.0f : 100.0f;
        int count = stack.getCount();

        // Get texture as data URI
        String textureUri = getItemTextureDataUri(stack);

        return String.format(java.util.Locale.US, "{\"empty\":false,\"id\":\"%s\",\"durability\":%d,\"max\":%d,\"percent\":%.1f,\"count\":%d,\"texture\":\"%s\"}",
            id, durability, maxDamage, percent, count, textureUri);
    }

    private String getItemTextureDataUri(net.minecraft.item.ItemStack stack) {
        return "";
    }

    public void updateHud() {
        MCEFBrowser browser = getBrowser();
        if (browser == null || browser.getCefBrowser() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        long now = System.currentTimeMillis();

        // Skip expensive HUD calcs during first 10s of startup (JIT warmup phase)
        boolean isWarmingUp = (now - STARTUP_TIME) < 10000;
        if (isWarmingUp && (now - lastCoordsTime) < 1000) {
            return;
        }

        net.minecraft.client.network.ClientPlayerEntity player = client.player;

        // 1. Keys & CPS - event-driven (lowest latency)
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
        lastLmbState = lmb;
        lastRmbState = rmb;

        int lmbCps = getCps(false);
        int rmbCps = getCps(true);

        boolean changed = (w != lastW || a != lastA || s != lastS || d != lastD || space != lastSpace ||
                           lmb != lastLmb || rmb != lastRmb || shift != lastShift || sprint != lastSprint ||
                           lmbCps != lastSentLmbCps || rmbCps != lastSentRmbCps);

        // Update keystroke state (always compute for data freshness)
        lastW = w; lastA = a; lastS = s; lastD = d; lastSpace = space;
        lastLmb = lmb; lastRmb = rmb; lastShift = shift; lastSprint = sprint;

        // Only send keystrokes to overlay if it's visible and state changed
        if (isOverlayVisible && changed && (now - lastKeystrokeTime >= 50)) {
            lastKeystrokeTime = now;
            lastSentLmbCps = lmbCps;
            lastSentRmbCps = rmbCps;
            StringBuilder sb = new StringBuilder(96);
            sb.append("{\"w\":").append(w).append(",\"a\":").append(a).append(",\"s\":").append(s)
              .append(",\"d\":").append(d).append(",\"space\":").append(space).append(",\"lmb\":").append(lmb)
              .append(",\"rmb\":").append(rmb).append(",\"shift\":").append(shift).append(",\"sprint\":").append(sprint)
              .append(",\"lmbCps\":").append(lmbCps).append(",\"rmbCps\":").append(rmbCps).append("}");
            browser.getCefBrowser().executeJavaScript("window.MinecraftBridge.trigger('hud:update_keys'," + sb.toString() + ");", "", 0);
        }

        // 2. Stats update - always calculate (fresh data), but only send if visible
        if (now - lastCoordsTime < 500) {
            // Still update state changes since those affect everything
            boolean interactive = client.currentScreen instanceof WebTitleScreen && ((WebTitleScreen) client.currentScreen).isOverlay();
            boolean editMode = interactive && ((WebTitleScreen) client.currentScreen).isEditMode();
            if (lastInteractive == null || lastEditMode == null || interactive != lastInteractive || editMode != lastEditMode) {
                lastInteractive = interactive;
                lastEditMode = editMode;
                if (isOverlayVisible) {
                    browser.getCefBrowser().executeJavaScript(
                        "window.MinecraftBridge.trigger('hud:update_interactive',{\"interactive\":" + interactive + ",\"editMode\":" + editMode + "});", "", 0);
                }
            }
            return;
        }
        lastCoordsTime = now;
        if (now - lastSessionTime >= 1000) {
            lastSessionTime = now;
            secondsElapsed++;
        }

        int fps = client.getCurrentFps();
        net.minecraft.client.network.PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
        int ping = entry != null ? entry.getLatency() : 0;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        String direction = player.getHorizontalFacing().asString().toUpperCase();
        double xSpeed = player.getX() - player.lastX, zSpeed = player.getZ() - player.lastZ;
        double bps = Math.sqrt(xSpeed * xSpeed + zSpeed * zSpeed) * 20.0;

        Runtime runtime = Runtime.getRuntime();
        long memUsed = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
        long memMax = runtime.maxMemory() >> 20;

        String biome = client.world.getBiome(player.getBlockPos()).getKey()
            .map(k -> k.getValue().getPath()).orElse("unknown");
        int cx = player.getBlockPos().getX() >> 4, cz = player.getBlockPos().getZ() >> 4;
        boolean isNether = client.world.getRegistryKey().getValue().getPath().contains("nether");
        double netherX = isNether ? x * 8.0 : x * 0.125, netherZ = isNether ? z * 8.0 : z * 0.125;

        net.minecraft.item.ItemStack mainHand = player.getMainHandStack();
        String itemName = mainHand.isEmpty() ? "" : mainHand.getName().getString();
        int itemMaxDmg = mainHand.getMaxDamage(), itemDmg = mainHand.isEmpty() ? 0 : itemMaxDmg - mainHand.getDamage();
        int itemCount = mainHand.getCount();
        float itemCooldown = mainHand.isEmpty() ? 0 : player.getItemCooldownManager().getCooldownProgress(mainHand, 0.0f);

        StringBuilder potions = new StringBuilder("[");
        boolean first = true;
        for (net.minecraft.entity.effect.StatusEffectInstance e : player.getActiveStatusEffects().values()) {
            if (!first) potions.append(",");
            int durationSecs = e.getDuration() / 20;
            boolean beneficial = !e.getEffectType().value().getCategory()
                .equals(net.minecraft.entity.effect.StatusEffectCategory.HARMFUL);
            potions.append("{\"name\":\"").append(e.getEffectType().value().getName().getString())
                  .append("\",\"duration\":\"").append(durationSecs / 60).append(":").append(String.format(java.util.Locale.US, "%02d", durationSecs % 60))
                  .append("\",\"amp\":").append(e.getAmplifier() + 1).append(",\"beneficial\":").append(beneficial)
                  .append(",\"color\":").append(e.getEffectType().value().getColor()).append("}");
            first = false;
        }
        potions.append("]");

        net.minecraft.scoreboard.Scoreboard sb2 = client.world.getScoreboard();
        net.minecraft.scoreboard.ScoreboardObjective obj = sb2.getObjectiveForSlot(
            net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
        StringBuilder scoreboard = new StringBuilder("[");
        first = true;
        if (obj != null) {
            for (net.minecraft.scoreboard.ScoreboardEntry e : sb2.getScoreboardEntries(obj)) {
                if (!first) scoreboard.append(",");
                scoreboard.append("\"").append(e.owner().replace("\"", "\\\"")).append("\"");
                first = false;
            }
        }
        scoreboard.append("]");

        String helmetJson = getArmorJson(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
        String chestJson = getArmorJson(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
        String leggingsJson = getArmorJson(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
        String bootsJson = getArmorJson(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
        String offhandJson = getArmorJson(player.getOffHandStack());

        String serverName = client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().name : "Singleplayer";
        String serverIp = client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : "127.0.0.1";
        int serverPlayers = client.getNetworkHandler() != null ? client.getNetworkHandler().getPlayerList().size() : 1;

        String[] targetArmor = PvPTracker.getTargetArmor();
        long worldTime = client.world.getTimeOfDay();

        StringBuilder json = new StringBuilder(1024);
        json.append("{\"fps\":").append(fps).append(",\"ping\":").append(ping)
            .append(",\"bps\":").append(String.format(java.util.Locale.US, "%.2f", bps)).append(",\"memUsed\":").append(memUsed)
            .append(",\"memMax\":").append(memMax).append(",\"frameTime\":").append(String.format(java.util.Locale.US, "%.2f", fps > 0 ? 1000.0/fps : 0))
            .append(",\"x\":").append(String.format(java.util.Locale.US, "%.1f", x)).append(",\"y\":").append(String.format(java.util.Locale.US, "%.1f", y))
            .append(",\"z\":").append(String.format(java.util.Locale.US, "%.1f", z)).append(",\"facing\":\"").append(direction)
            .append("\",\"yaw\":").append(String.format(java.util.Locale.US, "%.1f", player.getYaw())).append(",\"biome\":\"").append(biome)
            .append("\",\"cx\":").append(cx).append(",\"cz\":").append(cz)
            .append(",\"netherX\":").append(String.format(java.util.Locale.US, "%.1f", netherX)).append(",\"netherZ\":").append(String.format(java.util.Locale.US, "%.1f", netherZ))
            .append(",\"health\":").append(String.format(java.util.Locale.US, "%.1f", player.getHealth()))
            .append(",\"maxHealth\":").append(String.format(java.util.Locale.US, "%.1f", player.getMaxHealth()))
            .append(",\"hunger\":").append(player.getHungerManager().getFoodLevel())
            .append(",\"absorption\":").append(String.format(java.util.Locale.US, "%.1f", player.getAbsorptionAmount()))
            .append(",\"air\":").append(player.getAir()).append(",\"maxAir\":").append(player.getMaxAir())
            .append(",\"xp\":").append(String.format(java.util.Locale.US, "%.2f", player.experienceProgress))
            .append(",\"xpLevel\":").append(player.experienceLevel)
            .append(",\"sneaking\":").append(player.isSneaking()).append(",\"sprinting\":").append(player.isSprinting())
            .append(",\"combo\":").append(PvPTracker.getCombo()).append(",\"reach\":").append(String.format(java.util.Locale.US, "%.2f", PvPTracker.getReach()))
            .append(",\"targetName\":\"").append(PvPTracker.getTargetName().replace("\"", "\\\""))
            .append("\",\"targetHealth\":").append(String.format(java.util.Locale.US, "%.1f", PvPTracker.getTargetHealth()))
            .append(",\"targetMaxHealth\":").append(String.format(java.util.Locale.US, "%.1f", PvPTracker.getTargetMaxHealth()))
            .append(",\"targetDistance\":").append(String.format(java.util.Locale.US, "%.2f", PvPTracker.getTargetDistance()))
            .append(",\"targetArmor\":[\"").append(targetArmor[0] != null ? targetArmor[0] : "").append("\",\"")
            .append(targetArmor[1] != null ? targetArmor[1] : "").append("\",\"")
            .append(targetArmor[2] != null ? targetArmor[2] : "").append("\",\"")
            .append(targetArmor[3] != null ? targetArmor[3] : "").append("\"]")
            .append(",\"sessionTime\":").append(secondsElapsed).append(",\"kills\":").append(PvPTracker.getKills())
            .append(",\"deaths\":").append(PvPTracker.getDeaths()).append(",\"winStreak\":").append(PvPTracker.getWinStreak())
            .append(",\"currentStreak\":").append(PvPTracker.getCurrentStreak())
            .append(",\"serverName\":\"").append(serverName.replace("\"", "\\\""))
            .append("\",\"serverIp\":\"").append(serverIp.replace("\"", "\\\"")).append("\",\"serverPlayers\":").append(serverPlayers)
            .append(",\"scoreboard\":").append(scoreboard).append(",\"potions\":").append(potions)
            .append(",\"item\":{\"name\":\"").append(itemName.replace("\"", "\\\"")).append("\",\"durability\":").append(itemDmg)
            .append(",\"maxDurability\":").append(itemMaxDmg).append(",\"count\":").append(itemCount)
            .append(",\"cooldown\":").append(String.format(java.util.Locale.US, "%.2f", itemCooldown)).append("}")
            .append(",\"armor\":{\"helmet\":").append(helmetJson).append(",\"chest\":").append(chestJson)
            .append(",\"leggings\":").append(leggingsJson).append(",\"boots\":").append(bootsJson)
            .append(",\"offhand\":").append(offhandJson).append("}")
            .append(",\"utility\":{\"day\":").append(worldTime / 24000)
            .append(",\"weather\":\"").append(client.world.isThundering() ? "Thunder" : (client.world.isRaining() ? "Rain" : "Clear"))
            .append("\",\"time\":\"").append(String.format(java.util.Locale.US, "%02d:%02d", (worldTime / 1000 + 6) % 24, (worldTime % 1000) * 60 / 1000))
            .append("\"}}");

        // Only send to browser if overlay visible (calculations still happen for fresh data)
        if (isOverlayVisible) {
            browser.getCefBrowser().executeJavaScript("window.MinecraftBridge.trigger('hud:update_stats'," + json.toString() + ");", "", 0);
        }
    }

    public void updateHudKeysOnly() {
        MCEFBrowser browser = getBrowser();
        if (browser == null || browser.getCefBrowser() == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        boolean w = client.options.forwardKey.isPressed();
        boolean a = client.options.leftKey.isPressed();
        boolean s = client.options.backKey.isPressed();
        boolean d = client.options.rightKey.isPressed();
        boolean space = client.options.jumpKey.isPressed();
        boolean lmb = client.options.attackKey.isPressed();
        boolean rmb = client.options.useKey.isPressed();
        boolean shift = client.options.sneakKey.isPressed();
        boolean sprint = client.options.sprintKey.isPressed();

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
        
        String json = String.format(java.util.Locale.US, "{\"w\":%b,\"a\":%b,\"s\":%b,\"d\":%b,\"space\":%b,\"lmb\":%b,\"rmb\":%b,\"shift\":%b,\"sprint\":%b,\"lmbCps\":%d,\"rmbCps\":%d}",
            w, a, s, d, space, lmb, rmb, shift, sprint, lmbCps, rmbCps);
        browser.getCefBrowser().executeJavaScript("window.MinecraftBridge.trigger('hud:update_keys', " + json + ");", "", 0);
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
            File htmlFile = new File(MinecraftClient.getInstance().runDirectory, "web-ui/" + targetPage + ".html");
            System.out.println("[Fugo Client] Switching page to: " + htmlFile.getAbsolutePath());
            browser.getCefBrowser().loadURL("file://" + htmlFile.getAbsolutePath());
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
            browser.getCefBrowser().executeJavaScript("window.MinecraftBridge.trigger('ui:state_change'," + json.toString() + ");", "", 0);

            if (isWebTitle) {
                browser.getCefBrowser().executeJavaScript(
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
        File webDir = new File(MinecraftClient.getInstance().runDirectory, "web-ui");
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
