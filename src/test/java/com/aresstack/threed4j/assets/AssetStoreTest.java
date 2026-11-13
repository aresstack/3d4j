package com.aresstack.threed4j.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetStoreTest {

    private static final AssetSpec TREE = new AssetSpec("tree", 8, 16, "a tree");

    @TempDir
    Path directory;

    private static BufferedImage picture() {
        return new BufferedImage(8, 16, BufferedImage.TYPE_INT_ARGB);
    }

    private List<String> history(AssetStore store) throws Exception {
        if (!Files.isDirectory(store.historyDirectory(TREE))) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(store.historyDirectory(TREE))) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void theFirstArtworkLeavesNoHistoryAndRecordsItsOrigin() throws Exception {
        AssetStore store = new AssetStore(directory);
        store.save(TREE, "<svg>one</svg>", picture(), "openai/gpt-oss-120b");

        assertTrue(history(store).isEmpty());
        String info = Files.readString(store.infoFile(TREE));
        assertTrue(info.contains("\"model\": \"openai/gpt-oss-120b\""), info);
        assertTrue(info.contains("\"description\": \"a tree\""), info);
        assertTrue(info.contains("\"createdAt\""), info);
    }

    @Test
    void replacedArtworkMovesToTheHistoryWithItsOrigin() throws Exception {
        AssetStore store = new AssetStore(directory);
        store.save(TREE, "<svg>one</svg>", picture(), "model-one");
        Files.setLastModifiedTime(store.svgFile(TREE), FileTime.from(Instant.parse("2026-11-13T10:00:00Z")));
        store.save(TREE, "<svg>two</svg>", picture(), "model-two");

        List<String> kept = history(store);
        assertEquals(3, kept.size(), kept.toString());
        String base = kept.get(0).substring(0, kept.get(0).lastIndexOf('.'));
        assertEquals(List.of(base + ".json", base + ".png", base + ".svg"), kept);
        assertEquals("<svg>one</svg>", Files.readString(store.historyDirectory(TREE).resolve(base + ".svg")));
        assertTrue(Files.readString(store.historyDirectory(TREE).resolve(base + ".json")).contains("model-one"));

        assertEquals("<svg>two</svg>", Files.readString(store.svgFile(TREE)));
        assertTrue(Files.readString(store.infoFile(TREE)).contains("model-two"));
        assertTrue(store.has(TREE));
    }

    @Test
    void twoReplacementsOfTheSameMomentAreBothKept() throws Exception {
        AssetStore store = new AssetStore(directory);
        FileTime moment = FileTime.from(Instant.parse("2026-11-13T10:00:00Z"));
        store.save(TREE, "<svg>one</svg>", picture(), "m");
        Files.setLastModifiedTime(store.svgFile(TREE), moment);
        store.save(TREE, "<svg>two</svg>", picture(), "m");
        Files.setLastModifiedTime(store.svgFile(TREE), moment);
        store.save(TREE, "<svg>three</svg>", picture(), "m");

        assertEquals(6, history(store).size(), history(store).toString());
        assertEquals("<svg>three</svg>", Files.readString(store.svgFile(TREE)));
    }

    @Test
    void aFilePutThereByHandIsKeptTooAndTheHistoryIsNotAnAsset() throws Exception {
        AssetStore store = new AssetStore(directory);
        Files.writeString(store.svgFile(TREE), "<svg>by hand</svg>");
        store.save(TREE, "<svg>generated</svg>", picture(), "m");

        List<String> kept = history(store);
        assertEquals(1, kept.size(), kept.toString());
        assertTrue(kept.get(0).endsWith(".svg"));
        Files.delete(store.svgFile(TREE));
        Files.delete(store.pngFile(TREE));
        assertFalse(store.has(TREE), "only the files in the folder itself count");
    }
}
