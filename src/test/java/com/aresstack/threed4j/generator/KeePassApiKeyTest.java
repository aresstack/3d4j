package com.aresstack.threed4j.generator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeePassApiKeyTest {

    /** The format of {@code KeePassRpcCredentialClient.listEntries()} in keepassrpc-java 0.1.0-beta.1. */
    private static final String LISTING = """
            Title: Wiki
            UserName: angelo
            Password: geheim: mit Doppelpunkt
            URL: https://wiki.intern/
            UniqueID: 0123456789ABCDEF
            MM_Priority: 0

            Title: KI-Dienst
            UserName:\s
            Password: sk-123
            URL: https://ki.intern/v1
            UniqueID: FEDCBA9876543210

            Title: Notiz
            UserName:\s
            Password:\s
            URL:\s
            UniqueID: 00

            OK: Operation completed successfully.
            """;

    @Test
    void readsTitleAddressAndPasswordOfEveryEntrySortedByTitle() {
        List<KeePassEntry> entries = KeePassApiKey.parseListing(LISTING);

        assertEquals(List.of("KI-Dienst", "Notiz", "Wiki"), entries.stream().map(KeePassEntry::title).toList());
        assertEquals(new KeePassEntry("KI-Dienst", "https://ki.intern/v1", "sk-123"), entries.get(0));
        assertEquals("", entries.get(1).url());
        assertEquals("", entries.get(1).password());
        assertEquals("geheim: mit Doppelpunkt", entries.get(2).password());
        assertEquals("https://wiki.intern/", entries.get(2).url());
    }

    @Test
    void anEmptyDatabaseHasNoEntries() {
        assertTrue(KeePassApiKey.parseListing("OK: Operation completed successfully.\n").isEmpty());
        assertTrue(KeePassApiKey.parseListing("").isEmpty());
    }
}
