package io.github.fugoclient.mcefmod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import io.github.fugoclient.mcefmod.MinecraftClientAccessor;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ProfileManager {
    private static final ProfileManager INSTANCE = new ProfileManager();
    private final File profilesFile;
    private List<Profile> profiles = new ArrayList<>();
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public static ProfileManager getInstance() {
        return INSTANCE;
    }

    private ProfileManager() {
        this.profilesFile = new File(Minecraft.getInstance().gameDirectory, "fugo_profiles.json");
        loadProfiles();
    }

    public static class Profile {
        public String username;
        public String uuid;
        public String accessToken;
        public String type; // "offline", "alt", "microsoft"

        public Profile(String username, String uuid, String accessToken, String type) {
            this.username = username;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.type = type;
        }
    }

    public synchronized void loadProfiles() {
        if (!profilesFile.exists()) {
            profiles = new ArrayList<>();
            return;
        }
        try (Reader reader = new FileReader(profilesFile)) {
            profiles = gson.fromJson(reader, new TypeToken<List<Profile>>(){}.getType());
            if (profiles == null) {
                profiles = new ArrayList<>();
            }
        } catch (Exception e) {
            e.printStackTrace();
            profiles = new ArrayList<>();
        }
    }

    public synchronized void saveProfiles() {
        try (Writer writer = new FileWriter(profilesFile)) {
            gson.toJson(profiles, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Profile> getProfiles() {
        return profiles;
    }

    public synchronized void addProfile(Profile profile) {
        profiles.removeIf(p -> p.username.equalsIgnoreCase(profile.username));
        profiles.add(profile);
        saveProfiles();
    }

    public synchronized void deleteProfile(String username) {
        profiles.removeIf(p -> p.username.equalsIgnoreCase(username));
        saveProfiles();
    }

    public synchronized boolean selectProfile(String username) {
        Profile target = null;
        for (Profile p : profiles) {
            if (p.username.equalsIgnoreCase(username)) {
                target = p;
                break;
            }
        }

        if (target != null) {
            try {
                User.Type type = User.Type.byName(target.type);
                if (type == null) {
                    type = User.Type.MOJANG;
                }
                User session = new User(
                    target.username,
                    target.uuid,
                    target.accessToken,
                    Optional.empty(),
                    Optional.empty(),
                    type
                );
                ((MinecraftClientAccessor) Minecraft.getInstance()).setSession(session);
                System.out.println("[ProfileManager] Switched session to: " + target.username + " (" + target.type + ")");
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public Profile authenticateMicrosoft(String code) throws Exception {
        String tokenBody = String.format(
            "client_id=00000000402b5328&code=%s&grant_type=authorization_code&redirect_uri=https://login.live.com/oauth20_desktop.srf",
            code
        );
        HttpRequest tokenRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://login.live.com/oauth20_token.srf"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
            .build();

        HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> tokenMap = gson.fromJson(tokenResponse.body(), new TypeToken<Map<String, Object>>(){}.getType());
        String msAccessToken = (String) tokenMap.get("access_token");

        if (msAccessToken == null) {
            throw new RuntimeException("Microsoft Auth Failed: No access token returned.");
        }

        Map<String, Object> xblProps = new HashMap<>();
        xblProps.put("AuthMethod", "RPS");
        xblProps.put("SiteName", "user.auth.xboxlive.com");
        xblProps.put("RpsTicket", "d=" + msAccessToken);

        Map<String, Object> xblBody = new HashMap<>();
        xblBody.put("Properties", xblProps);
        xblBody.put("RelyingParty", "http://auth.xboxlive.com");
        xblBody.put("TokenType", "JWT");

        HttpRequest xblRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(xblBody)))
            .build();

        HttpResponse<String> xblResponse = httpClient.send(xblRequest, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> xblMap = gson.fromJson(xblResponse.body(), new TypeToken<Map<String, Object>>(){}.getType());
        String xblToken = (String) xblMap.get("Token");
        
        Map<String, Object> DisplayClaims = (Map<String, Object>) xblMap.get("DisplayClaims");
        List<Map<String, Object>> xui = (List<Map<String, Object>>) DisplayClaims.get("xui");
        String uhs = (String) xui.get(0).get("uhs");

        Map<String, Object> xstsProps = new HashMap<>();
        xstsProps.put("SandboxId", "RETAIL");
        xstsProps.put("UserTokens", Collections.singletonList(xblToken));

        Map<String, Object> xstsBody = new HashMap<>();
        xstsBody.put("Properties", xstsProps);
        xstsBody.put("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.put("TokenType", "JWT");

        HttpRequest xstsRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(xstsBody)))
            .build();

        HttpResponse<String> xstsResponse = httpClient.send(xstsRequest, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> xstsMap = gson.fromJson(xstsResponse.body(), new TypeToken<Map<String, Object>>(){}.getType());
        String xstsToken = (String) xstsMap.get("Token");

        Map<String, Object> mcBody = new HashMap<>();
        mcBody.put("identityToken", String.format("XBL3.0 x=%s;%s", uhs, xstsToken));

        HttpRequest mcRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(mcBody)))
            .build();

        HttpResponse<String> mcResponse = httpClient.send(mcRequest, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> mcMap = gson.fromJson(mcResponse.body(), new TypeToken<Map<String, Object>>(){}.getType());
        String mcToken = (String) mcMap.get("access_token");

        if (mcToken == null) {
            throw new RuntimeException("Mojang Xbox Auth Failed: No access token returned.");
        }

        HttpRequest profileRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
            .header("Authorization", "Bearer " + mcToken)
            .GET()
            .build();

        HttpResponse<String> profileResponse = httpClient.send(profileRequest, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> profileMap = gson.fromJson(profileResponse.body(), new TypeToken<Map<String, Object>>(){}.getType());
        
        String username = (String) profileMap.get("name");
        String uuidStr = (String) profileMap.get("id");
        
        if (uuidStr != null && uuidStr.length() == 32) {
            uuidStr = uuidStr.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
            );
        }

        return new Profile(username, uuidStr, mcToken, "microsoft");
    }
}
