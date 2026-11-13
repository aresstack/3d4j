package com.aresstack.threed4j.launcher;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.swing.JOptionPane;

/**
 * Entry point of the runnable JAR.
 *
 * <p>The engine uses the Foreign Function &amp; Memory API, which is a preview API in
 * Java 21. Classes compiled with {@code --enable-preview} cannot even be loaded by a
 * JVM that was started without that flag. This class is therefore compiled
 * <em>without</em> preview features and has no compile-time dependency on the engine.
 * It checks whether the engine can be loaded and, if not, starts the same JVM again
 * with the required flags and waits for it. That makes the JAR runnable with a plain
 * {@code java -jar} or a double-click.</p>
 *
 * <p>System properties:</p>
 * <ul>
 *   <li>{@code threed4j.main} – fully qualified name of the class to start
 *       (default: the plaza demo)</li>
 * </ul>
 */
public final class Launcher {

    /** Java feature version the engine is compiled for. Preview class files are bound to it. */
    private static final int REQUIRED_JAVA = 21;

    private static final String MAIN_PROPERTY = "threed4j.main";
    private static final String DEFAULT_MAIN = "com.aresstack.threed4j.demo.PlazaDemo";
    private static final String RELAUNCHED_PROPERTY = "threed4j.relaunched";
    private static final String GENERATE_COMMAND = "generate";
    private static final String GENERATOR_MAIN = "com.aresstack.threed4j.generator.AssetGenerator";
    /** An engine class that uses the FFM API and therefore only loads with preview enabled. */
    private static final String PREVIEW_PROBE = "com.aresstack.threed4j.ffm.Native";

    private Launcher() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            t.printStackTrace();
            fail(t.toString(), true);
        }
    }

    private static void run(String[] args) throws Throwable {
        int java = Runtime.version().feature();
        if (java != REQUIRED_JAVA) {
            fail("3d4j needs exactly Java " + REQUIRED_JAVA + " (running on Java " + java + ").\n"
                    + "The FFM API is a preview API there, and preview class files only run on the\n"
                    + "Java version they were compiled for.", false);
        }

        if (!previewEnabled()) {
            if (Boolean.getBoolean(RELAUNCHED_PROPERTY)) {
                throw new IllegalStateException("Relaunch with --enable-preview did not enable preview features");
            }
            // Errors of the second JVM are reported by that JVM; only its exit code is passed on.
            System.exit(relaunch(args));
        }

        // "java -jar 3d4j.jar generate ..." starts the asset generator instead of the game.
        String mainName = System.getProperty(MAIN_PROPERTY, DEFAULT_MAIN);
        if (args.length > 0 && args[0].equals(GENERATE_COMMAND)) {
            mainName = GENERATOR_MAIN;
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        Class<?> mainClass = Class.forName(mainName, true, Launcher.class.getClassLoader());
        Method main = mainClass.getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Reports a fatal error and exits. A JAR started by double-click runs on
     * {@code javaw}, which has no console, so the message is also shown in a dialog there.
     */
    private static void fail(String message, boolean alreadyPrinted) {
        if (!alreadyPrinted) {
            System.err.println(message);
        }
        if (javaExecutable().toLowerCase(Locale.ROOT).matches(".*javaw(\\.exe)?")) {
            try {
                JOptionPane.showMessageDialog(null, message, "3d4j", JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignored) {
                // No display available: the message on stderr has to do.
            }
        }
        System.exit(1);
    }

    /**
     * Finds out whether this JVM accepts preview class files by loading (not
     * initializing) an engine class that is known to use the FFM API.
     *
     * <p>The class to start cannot be used for this: javac marks only those class
     * files as "preview" that use a preview API themselves, and a game's main class
     * usually does not.</p>
     */
    private static boolean previewEnabled() throws ClassNotFoundException {
        try {
            Class.forName(PREVIEW_PROBE, false, Launcher.class.getClassLoader());
            return true;
        } catch (UnsupportedClassVersionError previewDisabled) {
            return false;
        }
    }

    /** Starts this JVM again with preview features enabled and waits for it to finish. */
    private static int relaunch(String[] args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            // A debug agent would try to bind the port the parent already holds.
            if (!jvmArg.startsWith("-agentlib:jdwp") && !jvmArg.startsWith("-Xrunjdwp")) {
                command.add(jvmArg);
            }
        }
        command.add("--enable-preview");
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-D" + RELAUNCHED_PROPERTY + "=true");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Launcher.class.getName());
        command.addAll(List.of(args));

        Process child = new ProcessBuilder(command).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(child::destroy, "3d4j-launcher-cleanup"));
        return child.waitFor();
    }

    /** The executable this JVM was started with, so that javaw.exe stays javaw.exe. */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }
}
