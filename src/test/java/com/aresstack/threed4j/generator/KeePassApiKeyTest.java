package com.aresstack.threed4j.generator;

import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.FakeKeePassRpcServer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pairing and reading against the stand-in KeePassRPC server of the Enterprise AI Client. */
class KeePassApiKeyTest {

    @TempDir
    Path home;
    private FakeKeePassRpcServer keePass;
    private final AtomicInteger asked = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        keePass = new FakeKeePassRpcServer().startAndWait();
        keePass.addEntry("Wiki", "angelo", "geheim", false);
        keePass.addEntry("KI-Dienst", "", "sk-123", false);
        setUrl("KI-Dienst", "https://ki.intern/v1");
        Files.writeString(KeePassApiKey.pairingFile(home), "host=127.0.0.1\nport=" + keePass.boundPort() + "\n");
    }

    @AfterEach
    void stop() throws Exception {
        keePass.stop(1000);
    }

    private String code() {
        asked.incrementAndGet();
        return keePass.pairingPassword();
    }

    @Test
    void pairsWithTheCodeKeePassShowsThenListsEntriesWithoutAskingAgain() throws Exception {
        List<KeePassEntry> entries = KeePassApiKey.entries(home, this::code);

        assertEquals(List.of("KI-Dienst", "Wiki"), entries.stream().map(KeePassEntry::title).toList());
        assertEquals("https://ki.intern/v1", entries.get(0).url());
        assertEquals("", entries.get(0).password(), "listings carry no passwords");
        assertEquals("", entries.get(1).url());
        assertTrue(Files.readString(KeePassApiKey.pairingFile(home)).contains("srpKey="), "the pairing is stored");

        KeePassEntry service = KeePassApiKey.entry(home, "ki-dienst", this::code);
        assertEquals("sk-123", service.password());
        assertEquals("https://ki.intern/v1", service.url());
        assertNull(KeePassApiKey.entry(home, "Gibt es nicht", this::code));
        assertEquals(1, asked.get(), "the code is needed once");
    }

    @Test
    void aWrongCodeIsReportedAndNothingIsStored() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> KeePassApiKey.entries(home, () -> "falsch"));

        assertTrue(failure.getMessage().contains("rejected the one-time password"), failure.getMessage());
        assertFalse(Files.exists(KeePassApiKey.pairingFile(home))
                && readPairing().contains("srpKey="), "no key without a pairing");
    }

    @Test
    void pairsAgainOnceWhenKeePassNoLongerKnowsTheStoredKey() throws Exception {
        KeePassApiKey.entries(home, this::code);
        keePass.revokeAllPairings();

        assertEquals(2, KeePassApiKey.entries(home, this::code).size());
        assertEquals(2, asked.get());
    }

    private String readPairing() {
        try {
            return Files.readString(KeePassApiKey.pairingFile(home));
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** The stand-in server creates entries without an address; add one the way KeePassRPC sends it. */
    @SuppressWarnings("unchecked")
    private void setUrl(String title, String url) throws Exception {
        Field field = FakeKeePassRpcServer.class.getDeclaredField("entries");
        field.setAccessible(true);
        List<JsonObject> entries = (List<JsonObject>) field.get(keePass);
        synchronized (entries) {
            for (JsonObject entry : entries) {
                if (entry.get("title").getAsString().equals(title)) {
                    JsonArray urls = new JsonArray();
                    urls.add(url);
                    entry.add("uRLs", urls);
                }
            }
        }
    }
}
