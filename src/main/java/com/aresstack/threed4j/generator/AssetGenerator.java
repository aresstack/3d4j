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

    /** The rules for a ground texture; the opposite of a figure: it fills its square and repeats. */
    static final String TILE_STYLE = """
            You draw ground textures for a video game as SVG. The game lays your drawing like a floor \
            tile, edge to edge in all directions, in a pastel-coloured cartoon town seen from above.

            Rules:
            - Answer with one complete SVG document and nothing else.
            - Root element: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">.
            - The first element is <rect x="0" y="0" width="256" height="256"> filled with the base colour.
            - Then 20 to 45 small, simple shapes spread evenly and irregularly over the square. \
            Every shape stays at least 12 units away from all four edges, so that the tile repeats \
            without a visible seam.
            - It is a floor: flat friendly colours close to the base colour, low contrast, no \
            dark outlines, nothing that looks like a single object.
            - Use only basic shapes and paths with solid fills. No text, no images, no filters, \
            no gradients, no scripts, no external references.""";

    private AssetGenerator() {
    }

    public static void main(String[] args) throws Exception {
        // "generate --route=https://host/" shows how that address is reached (proxy or directly) and stops.
        for (String arg : args) {
            if (arg.startsWith("--route=")) {
                ProxyRoutes.shared().diagnose(java.net.URI.create(arg.substring("--route=".length())))
                        .forEach(System.out::println);
                System.exit(0);
            }
        }
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
        java.net.URI baseUrl = config.baseUrl();
        if (apiKey == null || apiKey.isBlank()) {
            if (config.keePassEntry() == null) {
                throw new IllegalArgumentException("No API key: pass the title of the KeePass entry once with "
                        + "--entry=TITLE, or set the environment variable " + API_KEY_VARIABLE + ".");
            }
            System.out.println("Reading the entry \"" + config.keePassEntry() + "\" from KeePass (pairs once if needed) ...");
            KeePassEntry entry = KeePassApiKey.entry(config.home(), config.keePassEntry(), KeePassApiKey::codeFromConsole);
            if (entry == null) {
                throw new IllegalStateException("KeePass has no entry titled \"" + config.keePassEntry() + "\".");
            }
            apiKey = entry.password();
            if (baseUrl == null && entry.hasUrl()) {
                baseUrl = java.net.URI.create(AiSetup.normalizeBaseUrl(entry.url()));
            }
        }
        if (baseUrl == null) {
            throw new IllegalArgumentException("The address of the AI service is unknown: put it into the URL "
                    + "field of the KeePass entry, or pass it with --base-url=https://.../v1.");
        }

        System.out.println("Asking " + config.model() + " at " + baseUrl + " for " + wanted.size()
                + " asset(s), " + config.parallel() + " at a time");
        ChatClient chat = new ChatClient(baseUrl, config.model(), apiKey);
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
    static String generate(ChatClient chat, AssetStore store, AssetSpec spec) {
        if (spec.recipe() == AssetSpec.Recipe.FLOWER_BUSH) {
            return FlowerComposer.generate(chat::ask, store, spec, chat.model());
        }
        boolean tile = spec.recipe() == AssetSpec.Recipe.TILE;
        String problem = null;
        String lastAnswer = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String prompt = "Draw: " + spec.description() + "."
                    + (problem == null ? "" : "\n\nYour previous answer was unusable: " + problem
                    + ". Follow the rules exactly and answer with the corrected SVG only.");
            try {
                lastAnswer = chat.ask(tile ? TILE_STYLE : STYLE, prompt);
            } catch (IOException e) {
                return e.getMessage(); // a network or service problem does not get better by asking again
            }
            String svg = SvgRasterizer.extract(lastAnswer);
            if (svg.isEmpty()) {
                problem = "it contained no <svg> element";
                continue;
            }
            try {
                BufferedImage image = SvgRasterizer.render(svg, spec.width(), spec.height());
                problem = tile ? tileProblem(image) : figureProblem(image);
                if (problem == null) {
                    store.save(spec, svg, image, chat.model());
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

    /** A figure stands free: something is drawn, and the background stayed transparent. */
    static String figureProblem(BufferedImage image) {
        double coverage = SvgRasterizer.coverage(image);
        if (coverage < 0.03) {
            return "the drawing is empty or tiny";
        }
        return coverage > 0.93 ? "the background is not transparent" : null;
    }

    /**
     * A tile covers its square completely, and its outermost pixels all have the same
     * colour: then it repeats without a visible seam.
     */
    static String tileProblem(BufferedImage image) {
        if (SvgRasterizer.coverage(image) < 0.995) {
            return "the tile does not cover the whole square; start with a rectangle that fills it";
        }
        int width = image.getWidth();
        int height = image.getHeight();
        // The colour of the top left corner is the reference; every pixel of the outermost ring must be close to it.
        int reference = image.getRGB(0, 0);
        int ring = 3;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x >= ring && x < width - ring && y >= ring && y < height - ring) {
                    continue;
                }
                int rgb = image.getRGB(x, y);
                for (int shift = 0; shift <= 16; shift += 8) {
                    if (Math.abs(((rgb >> shift) & 0xFF) - ((reference >> shift) & 0xFF)) > 24) {
                        return "shapes touch the edge of the square, so the tile would not repeat seamlessly; "
                                + "keep every shape at least 12 units away from all four edges";
                    }
                }
            }
        }
        return null;
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
