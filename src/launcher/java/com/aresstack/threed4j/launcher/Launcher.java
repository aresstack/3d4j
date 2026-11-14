package com.aresstack.threed4j.launcher;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
 * JVM that was started without that flag, and they only run on the Java version they
 * were compiled for. This class is therefore compiled <em>for Java 8</em>, without
 * preview features and without a compile-time dependency on the engine: a class file
 * carries its own version, and a JVM only minds the version of the classes it loads.
 * Started by Java 21, it checks whether the engine can be loaded and, if not, starts
 * the same JVM again with the required flags. Started by any other Java from 8 on, it
 * looks for a Java 21 on this computer ({@link JavaRuntimeFinder}) and starts that one
 * with the flags and this same JAR. That makes the JAR runnable with a plain
 * {@code java -jar} or a double-click, whichever Java the computer starts JARs with.</p>
 *
 * <p>System properties:</p>
 * <ul>
 *   <li>{@code threed4j.main} – fully qualified name of the class to start
 *       (default: the plaza demo)</li>
 *   <li>{@code threed4j.java} – the Java 21 to use (its folder or its {@code java}
 *       executable); the environment variable {@code THREED4J_JAVA} does the same</li>
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
        int java = JavaRuntimeFinder.majorOf(System.getProperty("java.specification.version"));
        if (java != REQUIRED_JAVA) {
            if (Boolean.getBoolean(RELAUNCHED_PROPERTY)) {
                throw new IllegalStateException("The Java found for the restart is Java " + java);
            }
            File memory = new File(new File(System.getProperty("user.home"), ".3d4j"), "launcher.properties");
            File found = new JavaRuntimeFinder(REQUIRED_JAVA, memory).find();
            if (found == null) {
                fail("3d4j braucht genau Java " + REQUIRED_JAVA + ", gestartet wurde es mit Java " + java + ",\n"
                        + "und auf diesem Rechner wurde kein Java " + REQUIRED_JAVA + " gefunden.\n\n"
                        + "Gesucht wurde in JAVA_HOME, im PATH und in den üblichen Installationsordnern.\n"
                        + "Ein Java " + REQUIRED_JAVA + " an anderer Stelle nennt die Umgebungsvariable THREED4J_JAVA\n"
                        + "(oder -Dthreed4j.java=...).", false);
            }
            // Errors of the second JVM are reported by that JVM; only its exit code is passed on.
            System.exit(relaunch(sameKind(found), false, args));
        }

        if (!previewEnabled()) {
            if (Boolean.getBoolean(RELAUNCHED_PROPERTY)) {
                throw new IllegalStateException("Relaunch with --enable-preview did not enable preview features");
            }
            System.exit(relaunch(javaExecutable(), true, args));
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
        if (windowless() && !GraphicsEnvironment.isHeadless()) {
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

    /**
     * Starts a JVM with preview features enabled and this JAR, and waits for it to finish.
     *
     * @param sameJava whether it is the Java this launcher runs on, so that its options fit
     */
    private static int relaunch(String executable, boolean sameJava, String[] args) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(executable);
        for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            // A debug agent would try to bind the port the parent already holds. Another Java
            // may not know this one's options; system properties it always takes.
            boolean debugAgent = jvmArg.startsWith("-agentlib:jdwp") || jvmArg.startsWith("-Xrunjdwp");
            if (!debugAgent && (sameJava || jvmArg.startsWith("-D"))) {
                command.add(jvmArg);
            }
        }
        command.add("--enable-preview");
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-D" + RELAUNCHED_PROPERTY + "=true");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Launcher.class.getName());
        command.addAll(Arrays.asList(args));

        final Process child = new ProcessBuilder(command).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                child.destroy();
            }
        }, "3d4j-launcher-cleanup"));
        return child.waitFor();
    }

    /** The executable this JVM was started with, so that javaw.exe stays javaw.exe. */
    private static String javaExecutable() {
        String command = ownCommand();
        return command != null ? command
                : new File(new File(System.getProperty("java.home"), "bin"), "java").getPath();
    }

    /** For a found installation: javaw.exe if this JVM runs without a console, so that none opens. */
    private static String sameKind(File java) {
        if (windowless()) {
            File javaw = new File(java.getParentFile(), "javaw.exe");
            if (javaw.isFile()) {
                return javaw.getPath();
            }
        }
        return java.getPath();
    }

    /** Whether this JVM was started without a console, as a double-click on a JAR does. */
    private static boolean windowless() {
        String command = ownCommand();
        if (command != null) {
            return command.toLowerCase(Locale.ROOT).matches(".*javaw(\\.exe)?");
        }
        // Java 8 cannot say what it was started with: on Windows, no console is the sign.
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") && System.console() == null;
    }

    /** The path of this JVM's executable; {@code null} on Java 8, which has no ProcessHandle. */
    private static String ownCommand() {
        try {
            Class<?> handleClass = Class.forName("java.lang.ProcessHandle");
            Object info = handleClass.getMethod("info").invoke(handleClass.getMethod("current").invoke(null));
            Object command = Class.forName("java.lang.ProcessHandle$Info").getMethod("command").invoke(info);
            Object path = command.getClass().getMethod("orElse", Object.class).invoke(command, (Object) null);
            return path == null ? null : path.toString();
        } catch (Throwable notAvailable) {
            return null;
        }
    }
}
