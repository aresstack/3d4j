package com.aresstack.threed4j.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The artwork the game brings along: pictures drawn by models in earlier runs
 * (the people of the town, and the collections of the Blütenviertel and the
 * Werksviertel) and one planned district, the Wellenviertel. At the first start
 * they are copied from the program into the asset folder, so that the town is not
 * a town of placeholders until the model has drawn its first pictures.
 *
 * <p>Every file is offered once: {@code library/.defaults} lists what has been
 * offered. A file that is already there is left alone, and a picture the player
 * removed does not come back. Switched off with {@code -Dthreed4j.defaults=false}.</p>
 */
public final class DefaultAssets {

    private static final String ROOT = "/defaults/";
    private static final String OFFERED = ".defaults";

    private DefaultAssets() {
    }

    /** The files the program brings along, as paths below the asset folder, e.g. {@code library/stadt/visitor-001.png}. */
    public static List<String> files() {
        try (InputStream in = DefaultAssets.class.getResourceAsStream(ROOT + "index.txt")) {
            if (in == null) {
                return List.of();
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::trim)
                    .filter(line -> !line.isEmpty()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Copies what has not been offered yet into the asset folder.
     *
     * @return how many files were copied
     */
    public static int install(AssetStore store) {
        if ("false".equalsIgnoreCase(System.getProperty("threed4j.defaults"))) {
            return 0;
        }
        Path marker = store.directory().resolve("library").resolve(OFFERED);
        Set<String> offered = new LinkedHashSet<>();
        try {
            if (Files.isRegularFile(marker)) {
                offered.addAll(Files.readAllLines(marker, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // read as if nothing had been offered; existing files are still left alone
        }
        int copied = 0;
        List<String> now = new ArrayList<>();
        for (String file : files()) {
            if (offered.contains(file)) {
                continue;
            }
            Path target = store.directory().resolve(file);
            try (InputStream in = DefaultAssets.class.getResourceAsStream(ROOT + file)) {
                if (in == null) {
                    continue;
                }
                byte[] content = in.readAllBytes();
                if (!Files.exists(target) || samePlanReworked(target, content)) {
                    Files.createDirectories(target.getParent());
                    Files.write(target, content);
                    copied++;
                }
                now.add(file);
            } catch (IOException e) {
                System.out.println("Library: " + file + " could not be installed (" + e.getMessage() + ")");
            }
        }
        if (!now.isEmpty()) {
            offered.addAll(now);
            try {
                Files.createDirectories(marker.getParent());
                Files.write(marker, offered, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Library: " + marker + " could not be written (" + e.getMessage() + ")");
            }
        }
        return copied;
    }

    /**
     * Whether a district's plan in the asset folder is the very plan the program
     * brings along in a reworked form (same district, planned at the same moment):
     * then the reworked one takes its place, once.
     */
    private static boolean samePlanReworked(Path target, byte[] bundled) {
        if (!target.getFileName().toString().equals(DistrictFile.NAME)) {
            return false;
        }
        try {
            JsonObject there = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject ours = JsonParser.parseString(new String(bundled, StandardCharsets.UTF_8)).getAsJsonObject();
            return there.get("id").getAsString().equals(ours.get("id").getAsString())
                    && there.get("plannedAt").getAsString().equals(ours.get("plannedAt").getAsString());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
