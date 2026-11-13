package com.aresstack.threed4j.generator;

import com.aresstack.keepassrpc.client.DefaultKeePassRpcCredentialClient;
import com.aresstack.keepassrpc.client.KeePassRpcCredentialClient;
import com.aresstack.keepassrpc.pairing.DefaultKeePassRpcPairingService;
import com.aresstack.keepassrpc.pairing.KeePassRpcPairingRequest;
import com.aresstack.keepassrpc.pairing.KeePassRpcPairingResult;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Supplier;

/**
 * Reads entries (API key and address of the AI service) from KeePass through the
 * KeePassRPC plugin, using {@code keepassrpc-java}.
 *
 * <p>The first call pairs this program with KeePass: KeePass shows a one-time code,
 * the user types it into this program. The result is stored in
 * {@code .3d4j/keepass.properties}; later calls connect without asking.</p>
 */
final class KeePassApiKey {

    private static final String CLIENT_ID = "3d4j";
    private static final String ORIGIN = "chrome-extension://3d4j";
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 12546;
    private static final Pattern HTTP_ADDRESS = Pattern.compile("https?://[^\\s\"'\\],]+");

    private KeePassApiKey() {
    }

    static Path pairingFile(Path home) {
        return home.resolve("keepass.properties");
    }

    /** {@code true} if an earlier pairing is stored. Whether KeePass still accepts it shows only when connecting. */
    static boolean isPaired(Path home) {
        return Files.isRegularFile(pairingFile(home));
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
     * Lists the entries KeePass hands out, pairing first if no pairing is stored.
     *
     * @param codeProvider called if pairing is needed, while KeePass shows its one-time
     *                     code; returns what the user typed (may block until then)
     * @throws IllegalStateException with a message for the user if KeePass cannot be reached
     */
    static List<KeePassEntry> entries(Path home, Supplier<String> codeProvider) throws IOException {
        Path file = pairingFile(home);
        Properties stored = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                stored.load(in);
            }
        }
        String host = stored.getProperty("host", DEFAULT_HOST);
        int port = Integer.parseInt(stored.getProperty("port", String.valueOf(DEFAULT_PORT)));
        String srpKey = stored.getProperty("srpKey");

        if (srpKey == null || srpKey.isBlank()) {
            KeePassRpcPairingResult result = pair(host, port, codeProvider);
            host = result.getEndpoint().getHost();
            port = result.getEndpoint().getPort();
            srpKey = result.getSrpKey();
            stored.setProperty("host", host);
            stored.setProperty("port", String.valueOf(port));
            stored.setProperty("srpKey", srpKey);
            Files.createDirectories(home);
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                stored.store(out, "3d4j pairing with KeePassRPC");
            }
        }

        try (KeePassRpcCredentialClient client =
                     new DefaultKeePassRpcCredentialClient(host, port, CLIENT_ID, srpKey, ORIGIN)) {
            client.connect();
            return parseListing(client.listEntries());
        } catch (Exception e) {
            throw new IllegalStateException("KeePass did not hand out its entries (" + e.getMessage() + ").\n"
                    + "Check that KeePass is running with the KeePassRPC plugin and the database is unlocked.\n"
                    + "If the pairing was removed in KeePass, delete " + file + " and start again.", e);
        }
    }

    /**
     * Reads the listing of {@code KeePassRpcCredentialClient.listEntries()}: one block
     * of {@code Name: value} lines per entry, blocks separated by an empty line.
     */
    static List<KeePassEntry> parseListing(String listing) {
        List<KeePassEntry> entries = new ArrayList<>();
        String title = null;
        String url = "";
        String password = "";
        for (String line : (listing + "\n\n").split("\n", -1)) {
            if (line.startsWith("Title: ")) {
                title = line.substring("Title: ".length()).trim();
            } else if (line.startsWith("Password: ")) {
                password = line.substring("Password: ".length());
            } else if (line.startsWith("URL: ")) {
                Matcher address = HTTP_ADDRESS.matcher(line);
                url = address.find() ? address.group() : "";
            } else if (line.isBlank() && title != null) {
                if (!title.isEmpty()) {
                    entries.add(new KeePassEntry(title, url, password));
                }
                title = null;
                url = "";
                password = "";
            }
        }
        entries.sort(Comparator.comparing(KeePassEntry::title, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private static KeePassRpcPairingResult pair(String host, int port, Supplier<String> codeProvider) {
        KeePassRpcPairingRequest request = KeePassRpcPairingRequest.builder()
                .host(host)
                .port(port)
                .origin(ORIGIN)
                .clientId(CLIENT_ID)
                .clientDisplayName("3d4j")
                .build();
        try {
            return new DefaultKeePassRpcPairingService().pair(request, session -> codeProvider.get());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Pairing with KeePass failed (" + e.getMessage() + ").\n"
                    + "Check that KeePass is running with the KeePassRPC plugin and the database is unlocked.", e);
        }
    }
}
