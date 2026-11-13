package com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc;

import java.util.ArrayList;
import java.util.List;

/**
 * Public entry to the KeePassRPC classes of this package for 3d4j.
 *
 * <p>Everything else in this package is an unchanged copy from
 * aresstack/enterprise-ai-client ({@code proasteion/platform/security-keepassrpc}),
 * the code that pairs Space Harvester and the Enterprise AI Client with KeePass.
 * Its transport is package-private and upstream reaches it through
 * {@code KeePassRpcSecretProvider}, which needs modules 3d4j does not have; this
 * class is the only file here that is not from upstream and does no more than
 * call the transport.</p>
 */
public final class KeePassRpcLink {

    private KeePassRpcLink() {
    }

    /**
     * @param url      the first address stored in the entry, or an empty string
     * @param password empty in listings; filled by {@link #entry}
     */
    public record Entry(String title, String url, String userName, String password) {
    }

    /** KeePass could not be used; {@link #rejected()} tells whether a new pairing would help. */
    public static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;
        private final boolean rejected;

        Failure(KeePassRpcException cause) {
            super(cause.getMessage(), cause);
            this.rejected = cause.kind() == KeePassRpcException.Kind.AUTH_FAILED;
        }

        /** {@code true} if KeePass refused the one-time password or the stored key. */
        public boolean rejected() {
            return rejected;
        }
    }

    /**
     * Pairs with KeePass: KeePass shows a one-time password, the callback asks the user for it.
     *
     * @return the key to store for later connections, or {@code null} if the callback returned {@code null}
     */
    public static String pair(KeePassRpcConfig config, KeePassPairingCallback callback) throws Failure {
        try {
            char[] key = new WebSocketKeePassRpcTransport(config).pair(callback);
            return key == null ? null : new String(key);
        } catch (KeePassRpcException e) {
            throw new Failure(e);
        }
    }

    /** All entries with title and address, without passwords. */
    public static List<Entry> entries(KeePassRpcConfig config, String key) throws Failure {
        try (KeePassRpcTransport.Session session = new WebSocketKeePassRpcTransport(config).open(key.toCharArray())) {
            List<Entry> entries = new ArrayList<>();
            for (KeePassEntry entry : session.allEntries()) {
                entries.add(new Entry(entry.title(), entry.url(), entry.userName(), ""));
            }
            return entries;
        } catch (KeePassRpcException e) {
            throw new Failure(e);
        }
    }

    /** @return the entry with exactly this title including its password, or {@code null} if there is none */
    public static Entry entry(KeePassRpcConfig config, String key, String title) throws Failure {
        try (KeePassRpcTransport.Session session = new WebSocketKeePassRpcTransport(config).open(key.toCharArray())) {
            try (KeePassEntry entry = session.findEntryByTitle(title)) {
                return entry == null ? null : new Entry(entry.title(), entry.url(), entry.userName(), new String(entry.password()));
            }
        } catch (KeePassRpcException e) {
            throw new Failure(e);
        }
    }
}
