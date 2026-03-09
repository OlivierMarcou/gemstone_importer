package com.gemstone.viewer.etsy;

import java.awt.Desktop;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.Base64;

/**
 * Client Etsy API v3 avec OAuth2 PKCE pour application desktop.
 *
 * Flux d'authentification :
 *   1. generateAuthUrl()  → ouvre le navigateur
 *   2. startCallbackServer() → écoute sur localhost:8765
 *   3. exchangeCode()     → obtient access_token + refresh_token
 *   4. Les tokens sont sauvegardés dans java.util.prefs
 *
 * Scopes requis : listings_r
 * Redirect URI  : http://localhost:8765/callback
 */
public class EtsyApiClient {

    // ── Constantes API ──────────────────────────────────────────────
    public static final String API_BASE       = "https://openapi.etsy.com/v3";
    public static final String AUTH_URL       = "https://www.etsy.com/oauth/connect";
    public static final String TOKEN_URL      = "https://api.etsy.com/v3/public/oauth/token";
    public static final String REDIRECT_URI   = "http://localhost:8765/callback";
    public static final int    CALLBACK_PORT  = 8765;
    public static final int    PAGE_SIZE      = 100;
    public static final String[] SCOPES       = {"listings_r"};

    // ── Preferences keys ────────────────────────────────────────────
    private static final String PREF_NODE      = "com/gemstone/etsy";
    private static final String PREF_API_KEY   = "api_key";
    private static final String PREF_ACCESS    = "access_token";
    private static final String PREF_REFRESH   = "refresh_token";
    private static final String PREF_EXPIRY    = "token_expiry";
    private static final String PREF_SHOP_ID   = "shop_id";
    private static final String PREF_SHOP_NAME = "shop_name";

    // ── State ────────────────────────────────────────────────────────
    private String apiKey;
    private String accessToken;
    private String refreshToken;
    private long   tokenExpiry;   // epoch seconds
    private String shopId;
    private String shopName;

    private String codeVerifier;
    private ServerSocket callbackServer;
    private final CompletableFuture<String> authCodeFuture = new CompletableFuture<>();

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // ── Constructor ─────────────────────────────────────────────────
    public EtsyApiClient() { loadPrefs(); }

    public EtsyApiClient(String apiKey) {
        this.apiKey = apiKey;
        loadPrefs();
        this.apiKey = apiKey; // override loaded value
    }

    // ── Prefs ────────────────────────────────────────────────────────
    private void loadPrefs() {
        Preferences p = Preferences.userRoot().node(PREF_NODE);
        apiKey       = p.get(PREF_API_KEY,   null);
        accessToken  = p.get(PREF_ACCESS,    null);
        refreshToken = p.get(PREF_REFRESH,   null);
        tokenExpiry  = p.getLong(PREF_EXPIRY, 0);
        shopId       = p.get(PREF_SHOP_ID,   null);
        shopName     = p.get(PREF_SHOP_NAME, null);
    }

    public void savePrefs() {
        Preferences p = Preferences.userRoot().node(PREF_NODE);
        if (apiKey      != null) p.put(PREF_API_KEY,   apiKey);
        if (accessToken != null) p.put(PREF_ACCESS,    accessToken);
        if (refreshToken!= null) p.put(PREF_REFRESH,   refreshToken);
        p.putLong(PREF_EXPIRY, tokenExpiry);
        if (shopId   != null) p.put(PREF_SHOP_ID,   shopId);
        if (shopName != null) p.put(PREF_SHOP_NAME, shopName);
    }

    public void clearAuth() {
        Preferences p = Preferences.userRoot().node(PREF_NODE);
        p.remove(PREF_ACCESS); p.remove(PREF_REFRESH); p.remove(PREF_EXPIRY);
        p.remove(PREF_SHOP_ID); p.remove(PREF_SHOP_NAME);
        accessToken = null; refreshToken = null; tokenExpiry = 0;
        shopId = null; shopName = null;
    }

    // ── Getters ──────────────────────────────────────────────────────
    public String getApiKey()    { return apiKey; }
    public String getShopId()    { return shopId; }
    public String getShopName()  { return shopName; }
    public boolean hasApiKey()   { return apiKey != null && !apiKey.isBlank(); }
    public boolean hasToken()    { return accessToken != null && !accessToken.isBlank(); }
    public boolean isTokenValid(){ return hasToken() && System.currentTimeMillis()/1000 < tokenExpiry - 60; }

    // ── OAuth2 PKCE ──────────────────────────────────────────────────

    /** Génère l'URL d'autorisation et lance le serveur de callback */
    public String startOAuthFlow() throws IOException {
        // PKCE
        codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        // Démarrer le serveur callback
        callbackServer = new ServerSocket(CALLBACK_PORT);
        new Thread(this::waitForCallback, "etsy-oauth-callback").start();

        // Construire l'URL — scope séparé par espace puis encodé une seule fois
        String state = UUID.randomUUID().toString().replace("-","").substring(0,16);
        String scopeParam = String.join(" ", SCOPES); // "listings_r" ou "listings_r email_r ..."
        return AUTH_URL +
            "?response_type=code" +
            "&client_id=" + encode(apiKey) +
            "&redirect_uri=" + encode(REDIRECT_URI) +
            "&scope=" + encode(scopeParam) +
            "&state=" + state +
            "&code_challenge=" + codeChallenge +
            "&code_challenge_method=S256";
    }

    /** Attend la redirection du navigateur et retourne le code */
    public String waitForAuthCode(long timeoutSeconds) throws Exception {
        return authCodeFuture.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private void waitForCallback() {
        try (Socket socket = callbackServer.accept()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String requestLine = reader.readLine();
            // GET /callback?code=xxx&state=yyy HTTP/1.1
            String code = null;
            if (requestLine != null && requestLine.contains("code=")) {
                String query = requestLine.split(" ")[1]; // /callback?code=xxx...
                if (query.contains("?")) query = query.split("\\?")[1];
                for (String param : query.split("&")) {
                    if (param.startsWith("code=")) { code = param.substring(5); break; }
                }
            }

            // Réponse HTTP au navigateur
            String html = code != null
                ? "<html><body style='font-family:sans-serif;background:#1a1a2e;color:#e2e8f0;" +
                  "display:flex;align-items:center;justify-content:center;height:100vh;margin:0'>" +
                  "<div style='text-align:center'><h1 style='color:#f59e0b'>✓ Authentification réussie</h1>" +
                  "<p>Vous pouvez fermer cet onglet et retourner dans Gemstone Viewer.</p></div></body></html>"
                : "<html><body><h1>❌ Erreur</h1><p>Code manquant.</p></body></html>";

            PrintWriter writer = new PrintWriter(socket.getOutputStream());
            writer.println("HTTP/1.1 200 OK");
            writer.println("Content-Type: text/html; charset=utf-8");
            writer.println("Connection: close");
            writer.println();
            writer.println(html);
            writer.flush();

            if (code != null) authCodeFuture.complete(code);
            else authCodeFuture.completeExceptionally(new IOException("Code OAuth manquant dans la réponse"));
        } catch (Exception e) {
            authCodeFuture.completeExceptionally(e);
        } finally {
            try { if (callbackServer != null) callbackServer.close(); } catch (IOException ignored) {}
        }
    }

    /** Échange le code contre un access_token */
    public void exchangeCode(String code) throws IOException, InterruptedException {
        String body = "grant_type=authorization_code" +
            "&client_id=" + encode(apiKey) +
            "&redirect_uri=" + encode(REDIRECT_URI) +
            "&code=" + encode(code) +
            "&code_verifier=" + encode(codeVerifier);

        JsonObject json = postForm(TOKEN_URL, body, null);
        applyTokenResponse(json);
        savePrefs();
    }

    /** Renouvelle l'access token avec le refresh token */
    public void refreshAccessToken() throws IOException, InterruptedException {
        if (refreshToken == null) throw new IOException("Pas de refresh token disponible. Reconnectez-vous.");
        String body = "grant_type=refresh_token" +
            "&client_id=" + encode(apiKey) +
            "&refresh_token=" + encode(refreshToken);
        JsonObject json = postForm(TOKEN_URL, body, null);
        applyTokenResponse(json);
        savePrefs();
    }

    private void applyTokenResponse(JsonObject json) {
        accessToken  = json.getString("access_token");
        refreshToken = json.getString("refresh_token");
        long expiresIn = json.getLong("expires_in", 3600);
        tokenExpiry = System.currentTimeMillis()/1000 + expiresIn;
    }

    /** S'assure que le token est valide, le renouvelle si nécessaire */
    public void ensureValidToken() throws IOException, InterruptedException {
        if (!isTokenValid() && refreshToken != null) refreshAccessToken();
        if (!isTokenValid()) throw new IOException("Token expiré. Reconnectez-vous via OAuth.");
    }

    // ── API Calls ────────────────────────────────────────────────────

    /** Récupère les infos de l'utilisateur connecté et son shop */
    public void loadShopInfo(Consumer<String> log) throws IOException, InterruptedException {
        ensureValidToken();
        // User info
        JsonObject user = getJson(API_BASE + "/application/users/me");
        String userId = user.getString("user_id");
        if (log != null) log.accept("Utilisateur Etsy : " + user.getString("login_name",""));

        // Shop info
        JsonObject shop = getJson(API_BASE + "/application/users/" + userId + "/shops");
        // Response is a Shop object
        shopId   = shop.getString("shop_id");
        shopName = shop.getString("shop_name", "");
        if (log != null) log.accept("Boutique : " + shopName + " (ID: " + shopId + ")");
        savePrefs();
    }

    /**
     * Télécharge toutes les fiches produit de la boutique.
     * @param states  Liste de states : "active", "inactive", "draft", "expired", "sold_out" ou null = tous
     * @param onBatch Callback appelé pour chaque lot (liste de JsonObject)
     * @param log     Callback pour messages de progression
     * @return Nombre total de fiches importées
     */
    public int fetchAllListings(List<String> states, Consumer<List<JsonObject>> onBatch,
            Consumer<String> log) throws IOException, InterruptedException {
        ensureValidToken();
        if (shopId == null) loadShopInfo(log);

        int total = 0;
        // Si states == null → on fait 2 passes : "active|inactive|draft|expired" puis "sold_out"
        // L'API Etsy ne supporte pas "all" directement, il faut filtrer par state
        List<String> statesToFetch = states != null ? states
            : Arrays.asList("active","inactive","draft","expired","sold_out");

        for (String state : statesToFetch) {
            if (log != null) log.accept("Récupération des fiches « " + state + " »…");
            int offset = 0;
            int count = 0;
            while (true) {
                String url = API_BASE + "/application/shops/" + shopId + "/listings" +
                    "?state=" + encode(state) +
                    "&limit=" + PAGE_SIZE +
                    "&offset=" + offset +
                    "&includes=Images,MainImage";
                JsonObject resp = getJson(url);
                List<JsonObject> results = resp.getArray("results");
                if (results == null || results.isEmpty()) break;

                onBatch.accept(results);
                count += results.size();
                total += results.size();
                offset += results.size();

                int apiCount = (int) resp.getLong("count", 0);
                if (log != null) log.accept("  " + state + " : " + count + "/" + apiCount + " chargées");
                if (results.size() < PAGE_SIZE || count >= apiCount) break;
                Thread.sleep(200); // rate limit
            }
        }
        return total;
    }

    // ── JSON Helpers ─────────────────────────────────────────────────

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("x-api-key", apiKey)
            .header("Accept", "application/json")
            .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 401) {
            refreshAccessToken();
            return getJson(url); // retry once
        }
        if (resp.statusCode() != 200)
            throw new IOException("Etsy API " + resp.statusCode() + " : " + resp.body().substring(0, Math.min(200, resp.body().length())));
        return new JsonObject(resp.body());
    }

    private JsonObject postForm(String url, String body, String bearerToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearerToken != null) req.header("Authorization", "Bearer " + bearerToken);
        HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IOException("Token error " + resp.statusCode() + " : " + resp.body());
        return new JsonObject(resp.body());
    }

    // ── PKCE Utils ───────────────────────────────────────────────────

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ── Mapping Etsy JSON → EtsyListing fields ───────────────────────

    /** Convertit le state Etsy en notre enum DB */
    public static String mapState(String etsyState) {
        return switch (etsyState != null ? etsyState.toLowerCase() : "") {
            case "active"    -> "active";
            case "inactive"  -> "inactive";
            case "draft"     -> "draft";
            case "expired"   -> "expired";
            case "sold_out"  -> "sold";   // sold_out mappé sur sold
            default          -> "draft";
        };
    }

    /** Extrait l'URL de la première image */
    public static String extractMainImageUrl(JsonObject listing) {
        List<JsonObject> images = listing.getArray("images");
        if (images != null && !images.isEmpty()) {
            String full = images.get(0).getString("url_fullxfull");
            if (full != null) return full;
            return images.get(0).getString("url_570xN");
        }
        JsonObject main = listing.getObject("main_image");
        if (main != null) {
            String full = main.getString("url_fullxfull");
            return full != null ? full : main.getString("url_570xN");
        }
        return null;
    }

    /** Calcule le prix depuis price.amount / price.divisor */
    public static BigDecimal extractPrice(JsonObject listing) {
        JsonObject price = listing.getObject("price");
        if (price == null) return null;
        long amount   = price.getLong("amount",   0);
        long divisor  = price.getLong("divisor",  100);
        if (divisor == 0) divisor = 100;
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor));
    }

    // ── Minimal JSON parser (no external deps) ───────────────────────

    /**
     * Parseur JSON minimal maison — évite toute dépendance externe.
     * Supporte : getString, getLong, getBoolean, getObject, getArray.
     */
    public static class JsonObject {
        private final String raw;
        private final Map<String, String> values = new LinkedHashMap<>();

        public JsonObject(String json) {
            this.raw = json != null ? json.trim() : "{}";
            parse();
        }

        private void parse() {
            String s = raw;
            if (s.startsWith("{")) s = s.substring(1);
            if (s.endsWith("}")) s = s.substring(0, s.lastIndexOf('}'));
            int i = 0;
            while (i < s.length()) {
                // skip whitespace
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
                if (i >= s.length() || s.charAt(i) != '"') break;
                // read key
                int ks = i+1;
                int ke = s.indexOf('"', ks);
                if (ke < 0) break;
                String key = s.substring(ks, ke);
                i = ke + 1;
                while (i < s.length() && s.charAt(i) != ':') i++;
                i++;
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
                if (i >= s.length()) break;
                // read value
                char c = s.charAt(i);
                String value;
                if (c == '"') {
                    // string
                    i++;
                    StringBuilder sb = new StringBuilder();
                    while (i < s.length()) {
                        char ch = s.charAt(i);
                        if (ch == '\\' && i+1 < s.length()) { sb.append(s.charAt(++i)); i++; continue; }
                        if (ch == '"') break;
                        sb.append(ch); i++;
                    }
                    value = sb.toString();
                    i++; // skip closing "
                } else if (c == '{' || c == '[') {
                    // nested object/array — find balanced end
                    char open = c; char close = c == '{' ? '}' : ']';
                    int depth = 0, start = i; boolean inStr = false;
                    while (i < s.length()) {
                        char ch = s.charAt(i);
                        if (ch == '"' && (i==0||s.charAt(i-1)!='\\')) inStr = !inStr;
                        if (!inStr) { if (ch == open) depth++; else if (ch == close) { depth--; if (depth == 0) { i++; break; } } }
                        i++;
                    }
                    value = s.substring(start, i);
                } else {
                    // number/boolean/null
                    int start = i;
                    while (i < s.length() && ",}\n\r ".indexOf(s.charAt(i)) < 0 && s.charAt(i) != ']') i++;
                    value = s.substring(start, i).trim();
                }
                values.put(key, value);
                while (i < s.length() && (s.charAt(i) == ',' || Character.isWhitespace(s.charAt(i)))) i++;
            }
        }

        public String getString(String key) { return getString(key, null); }
        public String getString(String key, String def) { String v = values.get(key); return (v == null || v.equals("null")) ? def : v; }
        public long   getLong(String key, long def) { String v = values.get(key); if (v==null||v.equals("null")) return def; try { return Long.parseLong(v.trim()); } catch (Exception e) { return def; } }
        public boolean getBoolean(String key, boolean def) { String v = values.get(key); if (v==null) return def; return "true".equalsIgnoreCase(v.trim()); }
        public JsonObject getObject(String key) { String v = values.get(key); if (v==null||v.equals("null")||!v.startsWith("{")) return null; return new JsonObject(v); }
        public List<JsonObject> getArray(String key) {
            String v = values.get(key);
            if (v == null || v.equals("null") || !v.startsWith("[")) return null;
            List<JsonObject> out = new ArrayList<>();
            String inner = v.substring(1, v.lastIndexOf(']'));
            // Split on top-level comma
            int depth = 0; int start = 0; boolean inStr = false;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == '"' && (i==0||inner.charAt(i-1)!='\\')) inStr = !inStr;
                if (!inStr) {
                    if (c=='{' || c=='[') depth++;
                    else if (c=='}' || c==']') depth--;
                    else if (c==',' && depth==0) {
                        String chunk = inner.substring(start, i).trim();
                        if (chunk.startsWith("{")) out.add(new JsonObject(chunk));
                        start = i+1;
                    }
                }
            }
            String last = inner.substring(start).trim();
            if (last.startsWith("{")) out.add(new JsonObject(last));
            return out;
        }
        public List<String> getStringArray(String key) {
            String v = values.get(key);
            if (v == null || v.equals("null") || !v.startsWith("[")) return new ArrayList<>();
            List<String> out = new ArrayList<>();
            String inner = v.substring(1, v.lastIndexOf(']'));
            for (String part : inner.split(",")) {
                String s = part.trim().replaceAll("^\"|\"$", "");
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        public String raw() { return raw; }
    }
}
