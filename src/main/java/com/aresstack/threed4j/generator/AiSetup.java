package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetSpec;
import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * What a setup screen needs from the generator, step by step: the settings, the
 * entries of KeePass, the models of the service, and the generation of one asset.
 * All methods that talk to KeePass or the network block; call them off the game
 * thread. Messages of the exceptions are meant for the user.
 */
public final class AiSetup {

    /** The model suggested when the service offers it. */
    public static final String PREFERRED_MODEL = GeneratorConfig.DEFAULT_MODEL;

    private AiSetup() {
    }

    /**
     * @param baseUrl      base of the OpenAI-compatible API, e.g. {@code https://ki.intern/v1}; empty if
     *                     the address comes from the KeePass entry
     * @param keePassEntry title of the KeePass entry of the service (its address is the base of the
     *                     API, its password the API key); empty if access is typed in by hand
     */
    public record Settings(String baseUrl, String model, String keePassEntry) {
    }

    private static Path home() {
        return Path.of(System.getProperty("user.home"), ".3d4j");
    }

    private static Path settingsFile() {
        return home().resolve("generator.properties");
    }

    /**
     * The remembered settings; what is missing is taken from the configuration of
     * the Enterprise AI Client ({@code chat.baseUrl}, {@code chat.model},
     * {@code chat.apiKeyRef}) if that is set up on this machine.
     */
    public static Settings load() {
        Properties own = read(settingsFile());
        String appData = System.getenv("APPDATA");
        Properties client = read(Path.of(appData != null && !appData.isBlank() ? appData : System.getProperty("user.home"),
                ".enterprise-ai-client", "enterprise-ai-client.properties"));
        String entry = first(own.getProperty("chat.apiKeyRef"), client.getProperty("chat.apiKeyRef"), "");
        if (entry.startsWith("keepass:")) {
            entry = entry.substring("keepass:".length());
        }
        return new Settings(
                first(own.getProperty("chat.baseUrl"), client.getProperty("chat.baseUrl"), ""),
                first(own.getProperty("chat.model"), client.getProperty("chat.model"), PREFERRED_MODEL),
                entry);
    }

    /**
     * Upper limit of tokens per answer, sent as {@code max_tokens}: {@code chat.maxTokens}
     * from {@code .3d4j/generator.properties}, else from the Enterprise AI Client, else 8192.
     */
    public static int maxTokens() {
        Properties own = read(settingsFile());
        String appData = System.getenv("APPDATA");
        Properties client = read(Path.of(appData != null && !appData.isBlank() ? appData : System.getProperty("user.home"),
                ".enterprise-ai-client", "enterprise-ai-client.properties"));
        try {
            return Math.max(256, Integer.parseInt(first(own.getProperty("chat.maxTokens"),
                    client.getProperty("chat.maxTokens"), "8192")));
        } catch (NumberFormatException e) {
            return 8192;
        }
    }

    /**
     * How much the model thinks before it answers, sent as {@code reasoning_effort}:
     * {@code low}, {@code medium} or {@code high} from {@code chat.reasoningEffort} in
     * {@code .3d4j/generator.properties}.
     *
     * @return the level, or {@code null} to send nothing and leave it to the model's default
     */
    public static String reasoningEffort() {
        String value = read(settingsFile()).getProperty("chat.reasoningEffort", "").trim().toLowerCase(Locale.ROOT);
        return value.equals("low") || value.equals("medium") || value.equals("high") ? value : null;
    }

    /**
     * Remembers how answers are requested from now on.
     *
     * @param reasoningEffort {@code low}, {@code medium}, {@code high}, or {@code null} for the model's default
     */
    public static void saveRequestOptions(int maxTokens, String reasoningEffort) throws IOException {
        Properties own = read(settingsFile());
        own.setProperty("chat.maxTokens", String.valueOf(Math.max(256, maxTokens)));
        set(own, "chat.reasoningEffort", reasoningEffort);
        Files.createDirectories(home());
        try (Writer out = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            own.store(out, "3d4j asset generator");
        }
    }

    public static void save(Settings settings) throws IOException {
        Properties own = read(settingsFile());
        // An empty value is removed, so that the generator falls back as documented.
        set(own, "chat.baseUrl", settings.baseUrl());
        set(own, "chat.model", settings.model());
        set(own, "chat.apiKeyRef", settings.keePassEntry());
        Files.createDirectories(home());
        try (Writer out = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            own.store(out, "3d4j asset generator");
        }
    }

    private static void set(Properties properties, String name, String value) {
        if (value == null || value.isBlank()) {
            properties.remove(name);
        } else {
            properties.setProperty(name, value);
        }
    }

    /** @return the address without a trailing slash, or {@code null} if it is not an http(s) address */
    public static String normalizeBaseUrl(String text) {
        String trimmed = text == null ? "" : text.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                return null;
            }
            return trimmed;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The API key from the environment variable {@code THREED4J_API_KEY}, or {@code null}. */
    public static String apiKeyFromEnvironment() {
        String key = System.getenv(AssetGenerator.API_KEY_VARIABLE);
        return key == null || key.isBlank() ? null : key;
    }

    public static boolean isPaired() {
        return KeePassApiKey.isPaired(home());
    }

    public static void unpair() throws IOException {
        KeePassApiKey.unpair(home());
    }

    /**
     * Lists the KeePass entries (title and address, without passwords), pairing first
     * if no pairing is stored. An AI service is an entry whose address is the base
     * address of the API and whose password is the API key.
     *
     * @param pairingCode called while KeePass shows its one-time code; blocks until
     *                    the user has typed it and returns it
     * @throws IllegalStateException if KeePass cannot be reached
     */
    public static List<KeePassEntry> keePassEntries(Supplier<String> pairingCode) throws IOException {
        return KeePassApiKey.entries(home(), pairingCode);
    }

    /**
     * Reads one KeePass entry including its password.
     *
     * @return the entry, or {@code null} if KeePass has none with this title
     * @throws IllegalStateException if KeePass cannot be reached
     */
    public static KeePassEntry keePassEntry(String title, Supplier<String> pairingCode) throws IOException {
        return KeePassApiKey.entry(home(), title, pairingCode);
    }

    /** @return the ids of the models the service offers, sorted */
    public static List<String> listModels(String baseUrl, String apiKey) throws IOException {
        return new ChatClient(URI.create(baseUrl), "", apiKey).models();
    }

    /**
     * Which model to select before the user has chosen, by the rule of the setup
     * wizards of Space Harvester and the Enterprise AI Client: exactly the wanted
     * id; otherwise the only model whose id contains the part after the last "/";
     * otherwise the first one offered.
     *
     * @return the id, or {@code null} for an empty list
     */
    public static String preselect(List<String> models, String wanted) {
        if (models.isEmpty()) {
            return null;
        }
        for (String model : models) {
            if (model.equalsIgnoreCase(wanted)) {
                return model;
            }
        }
        String name = wanted.substring(wanted.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        List<String> similar = models.stream().filter(m -> m.toLowerCase(Locale.ROOT).contains(name)).toList();
        return similar.size() == 1 ? similar.get(0) : models.get(0);
    }

    /** The address Ollama listens at on the machine it runs on. */
    public static final String OLLAMA_DEFAULT = ChatClient.OLLAMA_DEFAULT;

    /** Whether the remembered access is an Ollama server ({@code chat.provider=ollama}) with {@code chat.baseUrl} as its address. */
    public static boolean ollama() {
        return "ollama".equalsIgnoreCase(read(settingsFile()).getProperty("chat.provider", "").trim());
    }

    /** Remembers whether the access is an Ollama server or an OpenAI-compatible service. */
    public static void saveOllama(boolean ollama) throws IOException {
        Properties own = read(settingsFile());
        set(own, "chat.provider", ollama ? "ollama" : null);
        Files.createDirectories(home());
        try (Writer out = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            own.store(out, "3d4j asset generator");
        }
    }

    /** @return the names of the models installed on an Ollama server, sorted */
    public static List<String> listOllamaModels(String baseUrl) throws IOException {
        return ChatClient.ollama(baseUrl, "").models();
    }

    /** Access to the service for generating artwork; safe to use from several threads. */
    public static final class Session {
        private final String baseUrl;
        private final String model;
        private final String apiKey;
        private final boolean ollama;

        public Session(String baseUrl, String model, String apiKey) {
            this(baseUrl, model, apiKey, false);
        }

        private Session(String baseUrl, String model, String apiKey, boolean ollama) {
            this.baseUrl = baseUrl;
            this.model = model;
            this.apiKey = apiKey;
            this.ollama = ollama;
        }

        /** Access to an Ollama server: no API key, Ollama's own API. */
        public static Session ollama(String baseUrl, String model) {
            return new Session(baseUrl, model, null, true);
        }

        private ChatClient client(String chosenModel) {
            return ollama ? ChatClient.ollama(baseUrl, chosenModel) : new ChatClient(URI.create(baseUrl), chosenModel, apiKey);
        }

        /** A client per piece of work, so that changed request options (answer length, thinking) apply at once. */
        private ChatClient chat() {
            return client(model);
        }

        /**
         * Lets the model draw one asset and stores it; asks again with a hint when
         * the answer is unusable.
         *
         * @return {@code null} on success, otherwise why it failed
         */
        public String generate(AssetStore store, AssetSpec spec) {
            return AssetGenerator.generate(chat(), store, spec);
        }

        /**
         * Lets the model draw one picture of this kind for a district and adds it to the collection.
         *
         * @return {@code null} on success, otherwise why it failed
         */
        public String draw(PartLibrary library, PartLibrary.Kind kind, int number, String districtStyle) {
            return draw(library, new District("district", "district", districtStyle), kind, number);
        }

        /**
         * Lets the model draw one picture of what this kind is in the district and adds it to the collection.
         *
         * @return {@code null} on success, otherwise why it failed
         */
        public String draw(PartLibrary library, District district, PartLibrary.Kind kind, int number) {
            String chosen = modelFor(kind);
            return Artist.draw(client(chosen != null ? chosen : model), library, district, kind, number);
        }

        /** Asks a model of the service a question, e.g. the one that plans districts. */
        public String ask(String chosenModel, String system, String user) throws IOException {
            return client(chosenModel).ask(system, user);
        }

        /** The model that draws when a kind has none of its own. */
        public String model() {
            return model;
        }

        /** @return the ids of the models the service offers, sorted; talks to the service */
        public List<String> models() throws IOException {
            return client("").models();
        }
    }

    private static volatile Session session;

    /** The access that worked last in this run of the program, or {@code null}. Kept in memory only. */
    public static Session session() {
        return session;
    }

    /** Remembers the access chosen in the world menu for everything that draws later in this run. */
    public static void rememberSession(Session chosen) {
        session = chosen;
    }

    /**
     * Fetches the access remembered from an earlier run without asking anybody: the
     * environment variable with the remembered address, or the remembered KeePass
     * entry if this program is paired. Blocks while it talks to KeePass.
     *
     * @return the access, also remembered as {@link #session()}; {@code null} if there is none
     */
    public static Session resumeSession() {
        try {
            Settings settings = load();
            if (ollama()) {
                // A local server needs no key and nobody's consent to be asked again.
                String server = settings.baseUrl().isBlank() ? OLLAMA_DEFAULT : settings.baseUrl();
                session = Session.ollama(server, settings.model());
                return session;
            }
            String key = apiKeyFromEnvironment();
            String address = normalizeBaseUrl(settings.baseUrl());
            if ((key == null || address == null) && isPaired() && !settings.keePassEntry().isBlank()) {
                // An empty code cancels a pairing nobody asked for.
                KeePassEntry entry = KeePassApiKey.entry(home(), settings.keePassEntry(), () -> "");
                if (entry != null && !entry.password().isBlank()) {
                    key = entry.password();
                    address = address != null ? address : normalizeBaseUrl(entry.url());
                }
            }
            if (key == null || address == null) {
                return null;
            }
            session = new Session(address, settings.model(), key);
            return session;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Whether access to the AI service is at hand without asking anybody: chosen in
     * this run, or remembered from an earlier one. Does not talk to KeePass.
     */
    public static boolean accessKnown() {
        if (session != null) {
            return true;
        }
        if (ollama()) {
            return true;
        }
        Settings settings = load();
        return apiKeyFromEnvironment() != null && normalizeBaseUrl(settings.baseUrl()) != null
                || isPaired() && !settings.keePassEntry().isBlank();
    }

    /**
     * The model that draws pictures of this kind: {@code chat.model.<kind>} in
     * {@code .3d4j/generator.properties}, e.g. {@code chat.model.visitor}.
     *
     * @return the model's id, or {@code null} if the kind has none of its own and the general model draws it
     */
    public static String modelFor(PartLibrary.Kind kind) {
        String value = read(settingsFile()).getProperty("chat.model." + kind.fileName(), "").trim();
        return value.isEmpty() ? null : value;
    }

    /** The model that draws when a kind has none of its own: the one last chosen, else {@link #PREFERRED_MODEL}. */
    public static String generalModel() {
        Session current = session;
        return current != null ? current.model() : load().model();
    }

    /** @param model the model's id, or {@code null} to let the general model draw this kind again */
    public static void saveModelFor(PartLibrary.Kind kind, String model) throws IOException {
        Properties own = read(settingsFile());
        set(own, "chat.model." + kind.fileName(), model);
        Files.createDirectories(home());
        try (Writer out = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            own.store(out, "3d4j asset generator");
        }
    }

    // ------------------------------------------------------- the town grows

    /** The model that plans districts unless another is set: planning is structured text, not drawing. */
    public static final String PLANNING_MODEL = "openai/gpt-oss-120b";

    /** Whether the town plans a further district by itself when the youngest is complete ({@code world.enabled}). */
    public static boolean worldEnabled() {
        return !"false".equalsIgnoreCase(read(settingsFile()).getProperty("world.enabled"));
    }

    /** The model that plans districts ({@code world.model}). */
    public static String worldModel() {
        String value = read(settingsFile()).getProperty("world.model", "").trim();
        if (value.isEmpty() && ollama()) {
            return generalModel(); // an Ollama server has the models installed there, not the default by name
        }
        return value.isEmpty() ? PLANNING_MODEL : value;
    }

    /** How many districts the town grows to by itself, the two that come with the game included ({@code world.maxDistricts}). */
    public static int worldMaxDistricts() {
        try {
            return Math.max(2, Math.min(24, Integer.parseInt(read(settingsFile()).getProperty("world.maxDistricts", "10").trim())));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public static void saveWorld(boolean enabled, String model, int maxDistricts) throws IOException {
        Properties own = read(settingsFile());
        own.setProperty("world.enabled", String.valueOf(enabled));
        set(own, "world.model", model == null || model.equals(ollama() ? generalModel() : PLANNING_MODEL) ? null : model);
        own.setProperty("world.maxDistricts", String.valueOf(Math.max(2, Math.min(24, maxDistricts))));
        Files.createDirectories(home());
        try (Writer out = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            own.store(out, "3d4j asset generator");
        }
    }

    /** Whether pictures are drawn in the background while the game runs ({@code background.enabled}). */
    public static boolean backgroundEnabled() {
        return !"false".equalsIgnoreCase(read(settingsFile()).getProperty("background.enabled"));
    }

    /** The most requests the background drawing may send at the same time. */
    public static final int MAX_PARALLEL = 8;

    /** How many pictures are drawn at the same time ({@code background.parallel}); one unless set. */
    public static int backgroundParallel() {
        try {
            return Math.max(1, Math.min(MAX_PARALLEL,
                    Integer.parseInt(read(settingsFile()).getProperty("background.parallel", "1").trim())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static void saveBackgroundParallel(int parallel) throws IOException {
        Properties own = read(settingsFile());
        own.setProperty("background.parallel", String.valueOf(Math.max(1, Math.min(MAX_PARALLEL, parallel))));
        Files.createDirectories(home());
        try (Writer out = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            own.store(out, "3d4j asset generator");
        }
    }

    public static void saveBackgroundEnabled(boolean enabled) throws IOException {
        Properties own = read(settingsFile());
        own.setProperty("background.enabled", String.valueOf(enabled));
        Files.createDirectories(home());
        try (Writer out = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            own.store(out, "3d4j asset generator");
        }
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                // an unreadable file is treated like a missing one
            }
        }
        return properties;
    }

    private static String first(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }
}
