package com.aresstack.threed4j.generator;

import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassRpcConfig;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassRpcLink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Reads entries (API key and address of the AI service) from KeePass through the
 * KeePassRPC plugin, with the KeePassRPC code of the Enterprise AI Client and Space
 * Harvester (package {@code com.aresstack.enterpriseai...keepassrpc}).
 *
 * <p>The first call pairs this program with KeePass: KeePass shows a one-time
 * password, the user types it into this program. The key is stored in
 * {@code .3d4j/keepass.properties}; later calls connect without asking. If KeePass
 * no longer accepts the stored key, the pairing is repeated once.</p>
 */
final class KeePassApiKey {

    private static final String CLIENT_ID = "3d4j";
    private static final String ORIGIN = "chrome-extension://3d4j";

    private KeePassApiKey() {
    }

    static Path pairingFile(Path home) {
        return home.resolve("keepass.properties");
    }

    /** {@code true} if an earlier pairing is stored. Whether KeePass still accepts it shows only when connecting. */
    static boolean isPaired(Path home) {
        Path file = pairingFile(home);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        Properties stored = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            stored.load(in);
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
        return !stored.getProperty("srpKey", "").isBlank();
    }

    /** Forgets the stored pairing; the entry in KeePass stays. */
    static void unpair(Path home) throws IOException {
        Files.deleteIfExists(pairingFile(home));
    }

    /** Reads the pairing code from the console. */
    static String codeFromConsole() {
        System.out.print("KeePass now shows a code. Type it here: ");
        System.out.flush();
        try {
            String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            return line == null ? "" : line.trim();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Lists the entries KeePass hands out (title and address, no passwords), sorted by
     * title; pairs first if no pairing is stored.
     *
     * @param codeProvider called if pairing is needed, while KeePass shows its one-time
     *                     password; returns what the user typed (may block until then)
     * @throws IllegalStateException with a message for the user if KeePass cannot be used
     */
    static List<KeePassEntry> entries(Path home, Supplier<String> codeProvider) throws IOException {
        return connected(home, codeProvider, (config, key) -> {
            List<KeePassEntry> entries = new ArrayList<>();
            for (KeePassRpcLink.Entry entry : KeePassRpcLink.entries(config, key)) {
                entries.add(new KeePassEntry(entry.title(), entry.url(), ""));
            }
            entries.sort(Comparator.comparing(KeePassEntry::title, String.CASE_INSENSITIVE_ORDER));
            return entries;
        });
    }

    /**
     * Reads the entry with exactly this title, including its password.
     *
     * @return the entry, or {@code null} if KeePass has none with this title
     */
    static KeePassEntry entry(Path home, String title, Supplier<String> codeProvider) throws IOException {
        return connected(home, codeProvider, (config, key) -> {
            KeePassRpcLink.Entry entry = KeePassRpcLink.entry(config, key, title);
            return entry == null ? null : new KeePassEntry(entry.title(), entry.url(), entry.password());
        });
    }

    private interface Call<T> {
        T run(KeePassRpcConfig config, String key) throws KeePassRpcLink.Failure;
    }

    private static <T> T connected(Path home, Supplier<String> codeProvider, Call<T> call) throws IOException {
        Path file = pairingFile(home);
        Properties stored = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                stored.load(in);
            }
        }
        KeePassRpcConfig config = KeePassRpcConfig.builder()
                .host(stored.getProperty("host", KeePassRpcConfig.DEFAULT_HOST))
                .port(Integer.parseInt(stored.getProperty("port", String.valueOf(KeePassRpcConfig.DEFAULT_PORT))))
                .origin(ORIGIN)
                .clientId(CLIENT_ID)
                .clientDisplayName("3d4j")
                .build();
        String key = stored.getProperty("srpKey");
        boolean fresh = key == null || key.isBlank();
        try {
            if (fresh) {
                key = pair(config, codeProvider, stored, home);
            }
            try {
                return call.run(config, key);
            } catch (KeePassRpcLink.Failure e) {
                if (fresh || !e.rejected()) {
                    throw e;
                }
                // The pairing was removed in KeePass: pair again, exactly once.
                Files.deleteIfExists(file);
                return call.run(config, pair(config, codeProvider, stored, home));
            }
        } catch (KeePassRpcLink.Failure e) {
            throw new IllegalStateException("KeePass cannot be used (" + e.getMessage() + ").\n"
                    + "Check that KeePass is running with the KeePassRPC plugin and the database is unlocked.", e);
        }
    }

    private static String pair(KeePassRpcConfig config, Supplier<String> codeProvider, Properties stored, Path home)
            throws KeePassRpcLink.Failure, IOException {
        String key;
        try {
            key = KeePassRpcLink.pair(config, name -> {
                String code = codeProvider.get();
                return code == null || code.isBlank() ? null : code.trim().toCharArray();
            });
        } catch (KeePassRpcLink.Failure e) {
            if (e.rejected()) {
                throw new IllegalStateException("KeePass rejected the one-time password. Pair again.", e);
            }
            throw e;
        }
        if (key == null) {
            throw new IllegalStateException("Pairing with KeePass was cancelled.");
        }
        stored.setProperty("host", config.host());
        stored.setProperty("port", String.valueOf(config.port()));
        stored.setProperty("srpKey", key);
        Files.createDirectories(home);
        try (Writer out = Files.newBufferedWriter(pairingFile(home), StandardCharsets.UTF_8)) {
            stored.store(out, "3d4j pairing with KeePassRPC");
        }
        return key;
    }
}
