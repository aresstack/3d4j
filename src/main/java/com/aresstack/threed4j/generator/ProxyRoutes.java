package com.aresstack.threed4j.generator;

import com.aresstack.winproxy.ProxyConfiguration;
import com.aresstack.winproxy.ProxyDiagnostics;
import com.aresstack.winproxy.ProxyMode;
import com.aresstack.winproxy.ProxyResult;
import com.aresstack.winproxy.WindowsProxyResolver;

import java.io.IOException;
import java.io.Reader;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The way to a web address: directly or through the proxy of the company network.
 * The decision comes from {@code win-proxy-java}, the library the Enterprise AI
 * Client and Space Harvester use: it finds the PAC script of Windows and evaluates
 * it for the address.
 *
 * <p>Settings, first match wins: system properties {@code threed4j.proxy.mode},
 * {@code .pacUrl}, {@code .host}, {@code .port}; then {@code network.proxy.*} in
 * {@code .3d4j/generator.properties}; then the same keys in the configuration of
 * the Enterprise AI Client. Without any setting the mode is
 * {@code PAC_URL_POWERSHELL} on Windows (the default of the Enterprise AI Client)
 * and {@code DISABLED} elsewhere. Loopback addresses and
 * {@code network.proxy.nonProxyHosts} always go directly.</p>
 */
final class ProxyRoutes {

    private static volatile ProxyRoutes shared;

    private final Properties[] sources;
    private final Path home;
    private final String modeName;
    private final ProxyMode mode;
    private final List<String> nonProxyHosts = new ArrayList<>();
    private final long timeoutMillis;
    private final Map<String, Proxy> cache = new ConcurrentHashMap<>();
    private final ExecutorService resolverThreads = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "3d4j-proxy");
        thread.setDaemon(true);
        return thread;
    });
    private WindowsProxyResolver resolver;
    private String configurationProblem;
    private volatile PasswordAuthentication proxyLogin;

    /** The routes for this machine, read once. */
    static ProxyRoutes shared() {
        ProxyRoutes routes = shared;
        if (routes == null) {
            synchronized (ProxyRoutes.class) {
                routes = shared;
                if (routes == null) {
                    Path home = Path.of(System.getProperty("user.home"), ".3d4j");
                    String appData = System.getenv("APPDATA");
                    Path client = Path.of(appData != null && !appData.isBlank() ? appData : System.getProperty("user.home"),
                            ".enterprise-ai-client", "enterprise-ai-client.properties");
                    routes = new ProxyRoutes(home, systemProperties(), read(home.resolve("generator.properties")), read(client));
                    shared = routes;
                }
            }
        }
        return routes;
    }

    /** @param sources settings with the keys {@code network.proxy.*}; the first that has a key wins */
    ProxyRoutes(Path home, Properties... sources) {
        this.home = home;
        this.sources = sources;
        String pacUrl = setting("network.proxy.pacUrl");
        String raw = setting("network.proxy.mode");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        String name = raw == null ? (windows ? "PAC_URL_POWERSHELL" : "DISABLED")
                : raw.toUpperCase(Locale.ROOT).replace('-', '_');
        // The names used by the Enterprise AI Client up to 0.1.3.
        name = switch (name) {
            case "NONE" -> "DISABLED";
            case "MANUAL" -> "MANUAL_PROXY";
            case "SYSTEM" -> "WINDOWS_STATIC_PROXY";
            case "AUTO" -> pacUrl == null ? "PAC_URL_POWERSHELL" : "PAC_URL_MANUAL";
            default -> name;
        };
        this.modeName = name;
        ProxyMode parsed = null;
        try {
            parsed = ProxyMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            configurationProblem = "unknown proxy mode " + name;
        }
        this.mode = parsed;
        String hosts = setting("network.proxy.nonProxyHosts");
        if (hosts != null) {
            for (String host : hosts.split("[,;|\\s]+")) {
                if (!host.isBlank()) {
                    nonProxyHosts.add(host.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        long timeout = 20_000;
        try {
            String configured = setting("network.proxy.resolveTimeoutMillis");
            if (configured != null) {
                timeout = Math.max(1000, Long.parseLong(configured));
            }
        } catch (NumberFormatException e) {
            // keep the default
        }
        this.timeoutMillis = timeout;

        if (mode != null && mode != ProxyMode.DISABLED) {
            try {
                // The test URL is always set, as in Space Harvester: otherwise the library uses one of its own.
                ProxyConfiguration.Builder builder = ProxyConfiguration.builder().mode(mode).testUrl("http://127.0.0.1/");
                if (pacUrl != null) {
                    builder.pacUrl(pacUrl);
                }
                String host = setting("network.proxy.host");
                if (host != null) {
                    builder.manualProxyHost(host);
                }
                String port = setting("network.proxy.port");
                if (port != null) {
                    builder.manualProxyPort(Integer.parseInt(port));
                }
                String script = setting("network.proxy.pacDiscoveryScript");
                if (script != null) {
                    builder.pacUrlDiscoveryScript(script);
                }
                ProxyConfiguration configuration = builder.build();
                List<String> problems = configuration.validationProblems();
                if (problems.isEmpty()) {
                    resolver = new WindowsProxyResolver(configuration);
                } else {
                    configurationProblem = String.join("; ", problems);
                }
            } catch (RuntimeException e) {
                configurationProblem = String.valueOf(e.getMessage());
            }
            installProxyLogin();
        }
    }

    /** For example "PAC_URL_POWERSHELL". */
    String describe() {
        return modeName;
    }

    /**
     * @return {@link Proxy#NO_PROXY} or the HTTP proxy for this address
     * @throws IOException if the way cannot be determined; nothing is sent directly in that case
     */
    Proxy proxyFor(URI target) throws IOException {
        String host = target.getHost();
        if (mode == ProxyMode.DISABLED || isLoopback(host) || isNonProxyHost(host)) {
            return Proxy.NO_PROXY;
        }
        if (resolver == null) {
            throw new IOException("proxy route for " + host + " unavailable (" + modeName + "): " + configurationProblem);
        }
        String url = resolutionUrl(target);
        Proxy known = cache.get(url);
        if (known != null) {
            return known;
        }
        ProxyResult result;
        try {
            result = withTimeout(() -> resolver.resolve(url));
        } catch (TimeoutException e) {
            throw new IOException("proxy route for " + host + " unavailable (" + modeName + "): no answer within "
                    + timeoutMillis + " ms");
        } catch (Exception e) {
            throw new IOException("proxy route for " + host + " unavailable (" + modeName + "): " + e, e);
        }
        Proxy proxy;
        if (result != null && result.isDirect()) {
            proxy = Proxy.NO_PROXY;
        } else if (result != null && result.isProxy()) {
            // Unresolved: the JDK looks the proxy up when it connects.
            proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(result.getHost(), result.getPort()));
        } else {
            throw new IOException("proxy route for " + host + " unavailable (" + modeName + "): "
                    + (result == null ? "no result" : result.getReason() + " " + result.getDetail()));
        }
        cache.put(url, proxy);
        return proxy;
    }

    /** What the library did to find the way to this address, line by line; for the command line. */
    List<String> diagnose(URI target) {
        List<String> lines = new ArrayList<>();
        lines.add("Target: " + target);
        lines.add("Mode: " + modeName);
        String host = target.getHost();
        if (mode == ProxyMode.DISABLED || isLoopback(host) || isNonProxyHost(host)) {
            lines.add("Result: DIRECT (" + (mode == ProxyMode.DISABLED ? "mode" : isLoopback(host) ? "loopback"
                    : "network.proxy.nonProxyHosts") + ")");
            return lines;
        }
        if (resolver == null) {
            lines.add("Result: unavailable, " + configurationProblem);
            return lines;
        }
        try {
            ProxyDiagnostics diagnostics = withTimeout(() -> resolver.diagnose(resolutionUrl(target)));
            for (String step : diagnostics.getSteps()) {
                lines.add("- " + step);
            }
            if (diagnostics.getPacUrl() != null) {
                lines.add("PAC URL: " + diagnostics.getPacUrl()
                        + (diagnostics.getPacUrlSource() == null ? "" : " (" + diagnostics.getPacUrlSource() + ")"));
            }
            if (diagnostics.getPacScriptLength() > 0) {
                lines.add("PAC script: " + diagnostics.getPacScriptLength() + " bytes");
            }
            if (diagnostics.getFailureDetail() != null && !diagnostics.getFailureDetail().isEmpty()) {
                lines.add("Failure: " + diagnostics.getFailureDetail());
            }
            lines.add("Result: " + diagnostics.getResult());
        } catch (TimeoutException e) {
            lines.add("Result: unavailable, no answer within " + timeoutMillis + " ms");
        } catch (Exception e) {
            lines.add("Result: unavailable, " + e);
        }
        return lines;
    }

    /**
     * What browsers give a PAC script: for https only {@code https://host[:port]/}
     * (path and query are encrypted and none of the script's business).
     */
    private static String resolutionUrl(URI target) {
        String port = target.getPort() < 0 ? "" : ":" + target.getPort();
        if ("https".equalsIgnoreCase(target.getScheme())) {
            return "https://" + target.getHost() + port + "/";
        }
        String path = target.getRawPath() == null || target.getRawPath().isEmpty() ? "/" : target.getRawPath();
        return target.getScheme() + "://" + target.getHost() + port + path;
    }

    private interface Work<T> {
        T run() throws Exception;
    }

    private <T> T withTimeout(Work<T> work) throws Exception {
        Future<T> future = resolverThreads.submit(work::run);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private static boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        String name = host.toLowerCase(Locale.ROOT);
        if (name.equals("localhost") || name.endsWith(".localhost")) {
            return true;
        }
        // Only literal addresses: no name lookup just to decide the route.
        if (name.matches("[0-9.]+") || name.contains(":")) {
            try {
                return InetAddress.getByName(name.replace("[", "").replace("]", "")).isLoopbackAddress();
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    private boolean isNonProxyHost(String host) {
        if (host == null) {
            return false;
        }
        String name = host.toLowerCase(Locale.ROOT);
        for (String pattern : nonProxyHosts) {
            if (pattern.startsWith("*") ? name.endsWith(pattern.substring(1)) : name.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Login at the proxy with BASIC from a KeePass entry, as the Enterprise AI Client
     * configures it ({@code network.proxy.auth.mode=BASIC},
     * {@code network.proxy.auth.credentialRef}). Without it Windows logs in by itself
     * where the proxy accepts that (NTLM, Negotiate).
     */
    private void installProxyLogin() {
        String authentication = setting("network.proxy.auth.mode");
        String reference = setting("network.proxy.auth.credentialRef");
        if (authentication == null || !authentication.equalsIgnoreCase("BASIC") || reference == null
                || Authenticator.getDefault() != null) {
            return;
        }
        String title = reference.startsWith("keepass:") ? reference.substring("keepass:".length()) : reference;
        // Since JDK 8u111 BASIC is switched off for the tunnel to https addresses unless this is set.
        if (System.getProperty("jdk.http.auth.tunneling.disabledSchemes") == null) {
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        }
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() != RequestorType.PROXY) {
                    return null;
                }
                PasswordAuthentication login = proxyLogin;
                if (login == null) {
                    try {
                        // No pairing from here: nobody could be asked for the one-time password.
                        String[] credentials = KeePassApiKey.credentials(home, title, () -> null);
                        if (credentials != null) {
                            login = new PasswordAuthentication(credentials[0], credentials[1].toCharArray());
                            proxyLogin = login;
                        }
                    } catch (IOException | RuntimeException e) {
                        return null;
                    }
                }
                return login;
            }
        });
    }

    private String setting(String name) {
        for (Properties source : sources) {
            String value = source.getProperty(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /** {@code -Dthreed4j.proxy.mode=...} and friends, under the names of the files. */
    private static Properties systemProperties() {
        Properties properties = new Properties();
        for (String name : new String[] {"mode", "pacUrl", "host", "port", "nonProxyHosts"}) {
            String value = System.getProperty("threed4j.proxy." + name);
            if (value != null) {
                properties.setProperty("network.proxy." + name, value);
            }
        }
        return properties;
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                // an unreadable file is treated like a missing one
            }
        }
        return properties;
    }
}
