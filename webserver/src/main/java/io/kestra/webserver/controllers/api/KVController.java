package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.*;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.NamespaceUtils;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;

import java.io.*;
import java.time.*;
import java.util.*;

@Validated
@Controller("/api/v1/{tenant}/namespaces/{namespace}/kv")
public class KVController {
    @Inject
    private StorageInterface storageInterface;
    @Inject
    protected TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"KV"}, summary = "List all keys for a namespace")
    public List<KVEntry> listKeys(
        @Parameter(description = "The namespace id") @PathVariable String namespace
    ) throws IOException {
        return kvStore(namespace).list();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get("/inheritance")
    @Operation(tags = {"KV"}, summary = "List all keys for ancestor namespaces")
    public List<KVEntry> listKeysWithInheritance(
        @Parameter(description = "The namespace id") @PathVariable String namespace
    ) throws IOException {
        int lastDotIndex = namespace.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return Collections.emptyList();
        }
        String result = namespace.substring(0, lastDotIndex);

        List<String> ancestorNamespaces = NamespaceUtils.asTree(result);
        return getKvEntriesWithInheritance(ancestorNamespaces);
    }

    protected List<KVEntry> getKvEntriesWithInheritance(List<String> namespaces) throws IOException {
        List<KVEntry> kvEntries = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        List<String> sortedNamespaces = namespaces.stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
        for (String ns : sortedNamespaces) {
            List<KVEntry> entries = kvStore(ns).list();
            entries.forEach(key -> {
                if (!keys.contains(key.key())) {
                    keys.add(key.key());
                    kvEntries.add(key);
                }
            });
        }
        return kvEntries;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{key}")
    @Operation(tags = {"KV"}, summary = "Get value for a key")
    public TypedValue getKeyValue(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key
    ) throws IOException, ResourceExpiredException {
        KVValue wrapper = kvStore(namespace)
            .getValue(key)
            .orElseThrow(() -> new NoSuchElementException("No value found for key '" + key + "' in namespace '" + namespace + "'"));
        Object value = wrapper.value();
        if (value instanceof byte[] bytesValue) {
            value = new String(bytesValue);
        }
        return new TypedValue(KVType.from(value), value);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{key}", consumes = {MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Operation(tags = {"KV"}, summary = "Puts a key-value pair in store")
    public void setKeyValue(
        HttpHeaders httpHeaders,
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key,
        @RequestBody(description = "The value of the key") @Body String value
    ) throws IOException {
        String description = httpHeaders.get("description");
        String ttl = httpHeaders.get("ttl");
        KVMetadata metadata = new KVMetadata(description, ttl == null ? null : Duration.parse(ttl));
        try {
            // use ION mapper to properly handle timestamp
            JsonNode jsonNode = JacksonMapper.ofIon().readTree(value);
            kvStore(namespace).put(key, new KVValueAndMetadata(metadata, jsonNode));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON value for: " + value);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{key}")
    @Operation(tags = {"KV"}, summary = "Delete a key-value pair")
    public boolean deleteKeyValue(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key
    ) throws IOException {
        return kvStore(namespace).delete(key);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete
    @Operation(tags = {"KV"}, summary = "Bulk-delete multiple key/value pairs from the given namespace.")
    public HttpResponse<ApiDeleteBulkResponse> deleteKeyValues(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @RequestBody(description = "The keys") @Body ApiDeleteBulkRequest request
    ) {
        KVStore kvStore = kvStore(namespace);
        List<String> deletedKeys = request.keys().stream()
            .map(key -> {
                try {
                    if (kvStore.delete(key)) {
                        return Optional.of(key);
                    }
                    return Optional.<String>empty();
                } catch (IOException e) {
                    // Ignore deletion error for bulk-operation
                    return Optional.<String>empty();
                }
            })
            .flatMap(Optional::stream)
            .toList();
        return HttpResponse.ok(new ApiDeleteBulkResponse(deletedKeys));
    }

    /**
     * API Response for the bulk-delete operation.
     *
     * @param keys
     */
    @Introspected
    public record ApiDeleteBulkResponse(
        @Parameter(description = "The list of keys deleted")
        List<String> keys
    ) {

        public List<String> keys() {
            return Optional.ofNullable(keys).orElse(List.of());
        }
    }

    /**
     * API Request for the bulk-delete operation.
     *
     * @param keys
     */
    public record ApiDeleteBulkRequest(
        @Parameter(description = "The list of keys to delete")
        List<String> keys
    ) {

        public List<String> keys() {
            return Optional.ofNullable(keys).orElse(List.of());
        }
    }
    
    /**
     * Create a new {@link KVStore} facade for the given namespace.
     *
     * @param namespace the namespace of the KV Store.
     * @return a new {@link KVStore}.
     */
    protected KVStore kvStore(final String namespace) {
        return new InternalKVStore(tenantService.resolveTenant(), namespace, storageInterface);
    }
    
    public record TypedValue(
        @Parameter(description = "The type of the KV entry.")
        KVType type,
        
        @Parameter(description = "The value of the KV entry.")
        Object value
    ) {
    }
}
