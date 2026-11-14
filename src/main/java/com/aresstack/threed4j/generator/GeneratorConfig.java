package com.aresstack.threed4j.generator;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Settings of the asset generator.
 *
 * <p>Each value is taken from the first place that has it: the command line, the
 * generator's own file ({@code .3d4j/generator.properties} in the home directory),
 * the configuration of the Enterprise AI Client. The keys are the client's keys
 * ({@code chat.baseUrl}, {@code chat.model}, {@code chat.apiKeyRef}), so a machine
 * on which the client is set up needs no further configuration. Values given on
 * the command line are remembered in the generator's own file.</p>
 *
 * @param baseUrl       base of the OpenAI-compatible API, e.g. {@code https://ki.intern/v1};
 *                      {@code null} to take the address of the KeePass entry
 * @param keePassEntry  title of the KeePass entry whose password is the API key
 * @param parallel      number of requests sent at the same time
 * @param force         regenerate assets that already exist
 * @param only          ids to generate; empty means all
 */
record GeneratorConfig(URI baseUrl, String model, String keePassEntry, int parallel, boolean force,
                       List<String> only, Path home) {

    static final String DEFAULT_MODEL = "google/gemma-4-31B-it";

    /** @throws IllegalArgumentException with a message for the user if something is missing or malformed */
    static GeneratorConfig load(String[] args) throws IOException {
        Path home = Path.of(System.getProperty("user.home"), ".3d4j");
        Path ownFile = home.resolve("generator.properties");
        Properties own = read(ownFile);
        Properties client = read(enterpriseAiClientConfig());

        String baseUrl = null;
        String model = null;
        String entry = null;
        int parallel = 4;
        boolean force = false;
        List<String> only = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--base-url=")) {
                baseUrl = arg.substring("--base-url=".length());
            } else if (arg.startsWith("--model=")) {
                model = arg.substring("--model=".length());
            } else if (arg.startsWith("--entry=")) {
                entry = arg.substring("--entry=".length());
            } else if (arg.startsWith("--parallel=")) {
                parallel = Math.max(1, Integer.parseInt(arg.substring("--parallel=".length())));
            } else if (arg.startsWith("--only=")) {
                only.addAll(List.of(arg.substring("--only=".length()).split(",")));
            } else if (arg.equals("--force")) {
                force = true;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg
                        + "\nKnown: --base-url=URL --model=NAME --entry=KEEPASS-TITLE --parallel=N --only=ID,ID --force");
            }
        }

        // Remember what was given explicitly.
        boolean changed = false;
        changed |= remember(own, "chat.baseUrl", baseUrl);
        changed |= remember(own, "chat.model", model);
        changed |= remember(own, "chat.apiKeyRef", entry);
        if (changed) {
            Files.createDirectories(home);
            try (Writer out = Files.newBufferedWriter(ownFile, StandardCharsets.UTF_8)) {
                own.store(out, "3d4j asset generator");
            }
        }

        baseUrl = first(baseUrl, own.getProperty("chat.baseUrl"), client.getProperty("chat.baseUrl"));
        model = first(model, own.getProperty("chat.model"), client.getProperty("chat.model"), DEFAULT_MODEL);
        entry = first(entry, own.getProperty("chat.apiKeyRef"), client.getProperty("chat.apiKeyRef"));
        if (entry != null && entry.startsWith("keepass:")) {
            entry = entry.substring("keepass:".length());
        }

        if (baseUrl == null && entry == null) {
            throw new IllegalArgumentException("The address of the AI service is unknown. Pass it once with "
                    + "--base-url=https://.../v1, or name a KeePass entry that carries it with --entry=TITLE "
                    + "(both are remembered in " + ownFile + ").");
        }
        URI uri = null;
        if (baseUrl != null) {
            String normalized = AiSetup.normalizeBaseUrl(baseUrl);
            if (normalized == null) {
                throw new IllegalArgumentException("Not an http(s) address: " + baseUrl);
            }
            uri = URI.create(normalized);
        }
        return new GeneratorConfig(uri, model, entry, parallel, force, List.copyOf(only), home);
    }

    /** Where the Enterprise AI Client keeps its settings: {@code %APPDATA%} on Windows, the home directory elsewhere. */
    private static Path enterpriseAiClientConfig() {
        String appData = System.getenv("APPDATA");
        String base = appData != null && !appData.isBlank() ? appData : System.getProperty("user.home");
        return Path.of(base, ".enterprise-ai-client", "enterprise-ai-client.properties");
    }

    private static Properties read(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(in);
            }
        }
        return properties;
    }

    private static boolean remember(Properties properties, String key, String value) {
        if (value == null || value.equals(properties.getProperty(key))) {
            return false;
        }
        properties.setProperty(key, value);
        return true;
    }

    private static String first(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
