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
import java.util.Properties;

/**
 * Reads the API key from KeePass through the KeePassRPC plugin, using
 * {@code keepassrpc-java}.
 *
 * <p>The first call pairs this program with KeePass: KeePass shows a one-time code,
 * the user types it into the console. The result is stored in
 * {@code .3d4j/keepass.properties}; later calls connect without asking.</p>
 */
final class KeePassApiKey {

    private static final String CLIENT_ID = "3d4j";
    private static final String ORIGIN = "chrome-extension://3d4j";
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 12546;

    private KeePassApiKey() {
    }

    /** @throws IllegalStateException with a message for the user if KeePass does not hand out the key */
    static String read(String entryTitle, Path home) throws IOException {
        Path file = home.resolve("keepass.properties");
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
            KeePassRpcPairingResult result = pair(host, port);
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
            System.out.println("Paired with KeePass. The pairing is stored in " + file);
        }

        try (KeePassRpcCredentialClient client =
                     new DefaultKeePassRpcCredentialClient(host, port, CLIENT_ID, srpKey, ORIGIN)) {
            client.connect();
            String password = client.getPassword(entryTitle);
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("KeePass has no entry titled \"" + entryTitle
                        + "\" with a password. Is the database unlocked?");
            }
            return password;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("KeePass did not hand out the API key (" + e.getMessage() + ").\n"
                    + "Check that KeePass is running with the KeePassRPC plugin and the database is unlocked.\n"
                    + "If the pairing was removed in KeePass, delete " + file + " and start again.", e);
        }
    }

    private static KeePassRpcPairingResult pair(String host, int port) {
        System.out.println("Pairing with KeePass (once). KeePass now shows a code.");
        KeePassRpcPairingRequest request = KeePassRpcPairingRequest.builder()
                .host(host)
                .port(port)
                .origin(ORIGIN)
                .clientId(CLIENT_ID)
                .clientDisplayName("3d4j Asset Generator")
                .build();
        try {
            return new DefaultKeePassRpcPairingService().pair(request, session -> {
                System.out.print("Code from KeePass: ");
                System.out.flush();
                try {
                    String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
                    return line == null ? "" : line.trim();
                } catch (IOException e) {
                    return "";
                }
            });
        } catch (RuntimeException e) {
            throw new IllegalStateException("Pairing with KeePass failed (" + e.getMessage() + ").\n"
                    + "Check that KeePass is running with the KeePassRPC plugin and the database is unlocked.", e);
        }
    }
}
