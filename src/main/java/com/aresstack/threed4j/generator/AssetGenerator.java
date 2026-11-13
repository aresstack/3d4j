package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetCatalog;
import com.aresstack.threed4j.assets.AssetSpec;
import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.SvgRasterizer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Lets a chat model draw the game's artwork: asks for each asset as SVG, checks
 * that the answer can be drawn and looks like a sprite, and stores SVG and PNG in
 * the asset folder. The game picks the files up at its next start and needs no
 * network itself.
 *
 * <p>Start: {@code java -jar 3d4j.jar generate [--base-url=URL] [--model=NAME]
 * [--entry=KEEPASS-TITLE] [--parallel=N] [--only=ID,ID] [--force]}. The API key
 * comes from the environment variable {@code THREED4J_API_KEY} if it is set,
 * otherwise from KeePass.</p>
 */
public final class AssetGenerator {

    static final String API_KEY_VARIABLE = "THREED4J_API_KEY";
    private static final int ATTEMPTS = 3;

    static final String STYLE = """
            You draw artwork for a video game as SVG. The game shows your drawing as a flat cut-out \
            figure standing in a pastel-coloured cartoon town.

            Rules:
            - Answer with one complete SVG document and nothing else.
            - Root element: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 512">.
            - The background must stay transparent: no rectangle behind the subject.
            - Draw only the subject. It stands on the bottom edge (y = 512), is centered \
            horizontally and fills most of the height.
            - Cartoon style: flat, friendly colours, thick dark outlines \
            (stroke="#3B2A4A", stroke-width 6 to 8, round joins), a few lighter highlights.
            - Use only basic shapes and paths with solid fills. No text, no images, no filters, \
            no gradients, no scripts, no external references.""";

    private AssetGenerator() {
    }

    public static void main(String[] args) throws Exception {
        try {
            System.exit(run(args, AssetStore.standard(), System.getenv(API_KEY_VARIABLE)) ? 0 : 1);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    /** @return {@code true} if every requested asset was produced */
    static boolean run(String[] args, AssetStore store, String apiKeyFromEnvironment) throws Exception {
        GeneratorConfig config = GeneratorConfig.load(args);

        List<AssetSpec> wanted = new ArrayList<>();
        for (AssetSpec spec : AssetCatalog.ALL) {
            if (!config.only().isEmpty() && !config.only().contains(spec.id())) {
                continue;
            }
            if (store.has(spec) && !config.force()) {
                System.out.println("  kept   " + spec.id() + " (exists; --force regenerates)");
                continue;
            }
            wanted.add(spec);
        }
        if (wanted.isEmpty()) {
            System.out.println("Nothing to generate. Assets are in " + store.directory());
            return true;
        }

        String apiKey = apiKeyFromEnvironment;
        if (apiKey == null || apiKey.isBlank()) {
            if (config.keePassEntry() == null) {
                throw new IllegalArgumentException("No API key: pass the title of the KeePass entry once with "
                        + "--entry=TITLE, or set the environment variable " + API_KEY_VARIABLE + ".");
            }
            apiKey = KeePassApiKey.read(config.keePassEntry(), config.home());
        }

        System.out.println("Asking " + config.model() + " at " + config.baseUrl() + " for " + wanted.size()
                + " asset(s), " + config.parallel() + " at a time");
        ChatClient chat = new ChatClient(config.baseUrl(), config.model(), apiKey);
        ExecutorService executor = Executors.newFixedThreadPool(config.parallel());
        boolean allOk = true;
        try {
            List<Future<String>> results = new ArrayList<>();
            for (AssetSpec spec : wanted) {
                results.add(executor.submit(() -> generate(chat, store, spec)));
            }
            for (int i = 0; i < wanted.size(); i++) {
                String problem = results.get(i).get();
                if (problem == null) {
                    System.out.println("  ok     " + wanted.get(i).id());
                } else {
                    allOk = false;
                    System.out.println("  FAILED " + wanted.get(i).id() + ": " + problem);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        System.out.println("Assets are in " + store.directory() + ". Start the game to see them.");
        return allOk;
    }

    /**
     * Asks for one asset, telling the model what was wrong with its previous attempt.
     *
     * @return {@code null} on success, otherwise why the last attempt failed
     */
    private static String generate(ChatClient chat, AssetStore store, AssetSpec spec) {
        String problem = null;
        String lastAnswer = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String prompt = "Draw: " + spec.description() + "."
                    + (problem == null ? "" : "\n\nYour previous answer was unusable: " + problem
                    + ". Follow the rules exactly and answer with the corrected SVG only.");
            try {
                lastAnswer = chat.ask(STYLE, prompt);
            } catch (IOException e) {
                return e.getMessage(); // a network or service problem does not get better by asking again
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "interrupted";
            }
            String svg = SvgRasterizer.extract(lastAnswer);
            if (svg.isEmpty()) {
                problem = "it contained no <svg> element";
                continue;
            }
            try {
                BufferedImage image = SvgRasterizer.render(svg, spec.width(), spec.height());
                double coverage = SvgRasterizer.coverage(image);
                if (coverage < 0.03) {
                    problem = "the drawing is empty or tiny";
                } else if (coverage > 0.93) {
                    problem = "the background is not transparent";
                } else {
                    store.save(spec, svg, image);
                    return null;
                }
            } catch (SvgRasterizer.UnreadableSvgException e) {
                problem = e.getMessage();
            } catch (IOException e) {
                return "cannot write to " + store.directory() + ": " + e.getMessage();
            }
        }
        keepForInspection(store, spec, lastAnswer);
        return String.format(Locale.ROOT, "%s (after %d attempts; last answer saved as %s.rejected.txt)",
                problem, ATTEMPTS, spec.id());
    }

    private static void keepForInspection(AssetStore store, AssetSpec spec, String answer) {
        if (answer == null) {
            return;
        }
        try {
            Files.createDirectories(store.directory());
            Files.writeString(store.directory().resolve(spec.id() + ".rejected.txt"), answer, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // inspection aid only
        }
    }
}
