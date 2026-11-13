package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetSpec;
import com.aresstack.threed4j.assets.AssetStore;

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
 * What a setup screen needs from the generator, step by step: the settings, the API
 * key from KeePass, the models of the service, and the generation of one asset.
 * All methods that talk to KeePass or the network block; call them off the game
 * thread. Messages of the exceptions are meant for the user.
 */
public final class AiSetup {

    /** The model suggested when the service offers it. */
    public static final String PREFERRED_MODEL = GeneratorConfig.DEFAULT_MODEL;

    private AiSetup() {
    }

    /**
     * @param baseUrl      base of the OpenAI-compatible API, e.g. {@code https://ki.intern/v1}; may be empty
     * @param keePassEntry title of the KeePass entry whose password is the API key; may be empty
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

    public static void save(Settings settings) throws IOException {
        Properties own = read(settingsFile());
        own.setProperty("chat.baseUrl", settings.baseUrl());
        own.setProperty("chat.model", settings.model());
        own.setProperty("chat.apiKeyRef", settings.keePassEntry());
        Files.createDirectories(home());
        try (Writer out = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            own.store(out, "3d4j asset generator");
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
     * Reads the API key from KeePass, pairing first if no pairing is stored.
     *
     * @param pairingCode called while KeePass shows its one-time code; blocks until
     *                    the user has typed it and returns it
     * @throws IllegalStateException if KeePass does not hand out the key
     */
    public static String readApiKey(String keePassEntry, Supplier<String> pairingCode) throws IOException {
        return KeePassApiKey.read(keePassEntry, home(), pairingCode);
    }

    /** @return the ids of the models the service offers, sorted */
    public static List<String> listModels(String baseUrl, String apiKey) throws IOException, InterruptedException {
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

    /** A connection to the service for generating assets; safe to use from several threads. */
    public static final class Session {
        private final ChatClient chat;

        public Session(String baseUrl, String model, String apiKey) {
            this.chat = new ChatClient(URI.create(baseUrl), model, apiKey);
        }

        /**
         * Lets the model draw one asset and stores it; asks again with a hint when
         * the answer is unusable.
         *
         * @return {@code null} on success, otherwise why it failed
         */
        public String generate(AssetStore store, AssetSpec spec) {
            return AssetGenerator.generate(chat, store, spec);
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
