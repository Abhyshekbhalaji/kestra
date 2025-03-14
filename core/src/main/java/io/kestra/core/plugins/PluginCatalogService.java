package io.kestra.core.plugins;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Services for retrieving available plugin artifacts for Kestra.
 */
public class PluginCatalogService {

    private static final Logger log = LoggerFactory.getLogger(PluginCatalogService.class);

    private static final Duration MAX_CACHE_DURATION = Duration.ofHours(1);

    private final HttpClient httpClient;

    private CompletableFuture<List<PluginManifest>> plugins;

    private List<PluginManifest> loaded = List.of();

    private Instant cacheLastLoaded = Instant.now();
    private final AtomicBoolean isLoaded = new AtomicBoolean(false);

    private final boolean icons;
    private final boolean oss;

    /**
     * Creates a new {@link PluginCatalogService} instance.
     *
     * @param httpClient    the HTTP Client to connect to Kestra API.
     * @param icons         specifies whether icons must be loaded for plugins.
     * @param oss         specifies whether only OSS plugins must be returned.
     */
    public PluginCatalogService(final HttpClient httpClient,
                                final boolean icons,
                                final boolean oss) {
        this.httpClient = httpClient;
        this.icons = icons;
        this.oss = oss;

        // Immediately trigger an async load of plugin artifacts.
        this.isLoaded.set(true);
        this.plugins = CompletableFuture.supplyAsync(this::load);
    }

    public synchronized List<PluginManifest> get() {
        try {
            List<PluginManifest> artifacts = this.plugins.get();
            if (!artifacts.isEmpty()) {
                loaded = artifacts;
            }
            if (cacheLastLoaded.plus(MAX_CACHE_DURATION).isBefore(Instant.now())) {
                if (isLoaded.compareAndSet(false, true)) {
                    // trigger an async load of plugin artifacts for refreshing local cache.
                    this.plugins = CompletableFuture.supplyAsync(this::load);
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                log.warn("Failed to retrieve available plugins from Kestra API. Cause: Interrupted");
            } else {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.warn("Failed to retrieve available plugins from Kestra API. Cause: {}", cause.getMessage());
            }
        }
        return loaded;
    }

    private List<PluginManifest> load() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("(Re)loading available plugin artifacts from configured Kestra API.");
            }
            List<Map<String, Object>> plugins = httpClient
                .toBlocking()
                .exchange(
                    HttpRequest.create(HttpMethod.GET, "/v1/plugins"),
                    Argument.listOf(Argument.mapOf(String.class, Object.class))
                )
                .body();

            List<PluginManifest> artifacts = plugins
                .parallelStream()
                .filter(plugin -> !plugin.get("name").equals("core"))
                .filter(plugin -> !oss || !"EE".equals(plugin.get("license")))
                .map(plugin -> {
                    // Get artifact
                    String groupId = "EE".equals(plugin.get("license")) ? "io.kestra.plugin.ee" : "io.kestra.plugin";
                    String artifactId = (String) plugin.get("name");

                    String icon = null;
                    if (icons) {
                        // Get icon
                        HttpResponse<String> response = httpClient
                            .toBlocking()
                            .exchange(
                                HttpRequest.create(HttpMethod.GET, "/v1/plugins/icons/" + plugin.get("group")),
                                String.class
                            );
                        icon = response.getBody()
                            .map(svg -> Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8)))
                            .orElse(null);
                    }

                    return new PluginManifest(
                        (String) plugin.get("title"),
                        icon,
                        groupId,
                        artifactId
                    );
                })
                .sorted(Comparator.comparing(PluginManifest::title))
                .toList();

            if (!artifacts.isEmpty()) {
                cacheLastLoaded = Instant.now();
            }
            if (log.isDebugEnabled()) {
                log.debug("Available plugin artifacts loaded (count={})", artifacts.size());
            }
            return artifacts;
        } finally {
            isLoaded.set(false);
        }
    }

    public record PluginManifest(
        String title,
        String icon,
        String groupId,
        String artifactId
    ) {

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":LATEST";
        }
    }
}
