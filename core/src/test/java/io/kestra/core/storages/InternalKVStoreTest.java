package io.kestra.core.storages;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.utils.IdUtils;
import io.kestra.storage.local.LocalStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalKVStoreTest {
    private static final Instant date = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final Map<String, Object> complexValue = Map.of("some", "complex", "object", Map.of("with", "nested", "values", date));
    static final String TEST_KV_KEY = "my-key";

    LocalStorage storageInterface;

    @BeforeEach
    public void setUp() throws IOException {
        Path basePath = Files.createTempDirectory("unit");
        storageInterface = new LocalStorage();
        storageInterface.setBasePath(basePath);
        storageInterface.init();
    }

    @Test
    void list() throws IOException {
        Instant now = Instant.now();
        InternalKVStore kv = kv();

        assertThat(kv.list().size()).isZero();

        String description = "myDescription";
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(new KVMetadata(description, Duration.ofMinutes(5)), complexValue));
        kv.put("my-second-key", new KVValueAndMetadata(new KVMetadata(null, Duration.ofMinutes(10)), complexValue));
        kv.put("expired-key", new KVValueAndMetadata(new KVMetadata(null, Duration.ofMillis(1)), complexValue));

        List<KVEntry> list = kv.list();
        assertThat(list.size()).isEqualTo(2);

        list.forEach(kvEntry -> {
            assertThat(kvEntry.creationDate()).isCloseTo(now, within(1, ChronoUnit. SECONDS));
            assertThat(kvEntry.updateDate()).isCloseTo(now, within(1, ChronoUnit. SECONDS));
        });

        Map<String, KVEntry> map = list.stream().collect(Collectors.toMap(KVEntry::key, Function.identity()));
        // Check that we don't list expired keys
        assertThat(map.size()).isEqualTo(2);

        KVEntry myKeyValue = map.get(TEST_KV_KEY);
        assertThat(myKeyValue.creationDate().plus(Duration.ofMinutes(4)).isBefore(myKeyValue.expirationDate()) &&
            myKeyValue.creationDate().plus(Duration.ofMinutes(6)).isAfter(myKeyValue.expirationDate())).isTrue();
        assertThat(myKeyValue.description()).isEqualTo(description);

        KVEntry mySecondKeyValue = map.get("my-second-key");
        assertThat(mySecondKeyValue.creationDate().plus(Duration.ofMinutes(9)).isBefore(mySecondKeyValue.expirationDate()) &&
            mySecondKeyValue.creationDate().plus(Duration.ofMinutes(11)).isAfter(mySecondKeyValue.expirationDate())).isTrue();
        assertThat(mySecondKeyValue.description()).isNull();
    }

    @Test
    void listAll() throws IOException {
        Instant now = Instant.now();
        InternalKVStore kv = kv();

        assertThat(kv.list().size()).isZero();

        String description = "myDescription";
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(new KVMetadata(description, Duration.ofMinutes(5)), complexValue));
        kv.put("key-without-expiration", new KVValueAndMetadata(new KVMetadata(null, null), complexValue));
        kv.put("expired-key", new KVValueAndMetadata(new KVMetadata(null, Duration.ofMillis(1)), complexValue));

        List<KVEntry> list = kv.listAll();
        assertThat(list.size()).isEqualTo(3);

        list.forEach(kvEntry -> {
            assertThat(kvEntry.creationDate()).isCloseTo(now, within(1, ChronoUnit. SECONDS));
            assertThat(kvEntry.updateDate()).isCloseTo(now, within(1, ChronoUnit. SECONDS));
        });

        List<String> keys = list.stream().map(KVEntry::key).toList();
        assertThat(keys).containsExactlyInAnyOrder(TEST_KV_KEY, "key-without-expiration", "expired-key");
    }

    @Test
    void put() throws IOException {
        // Given
        final InternalKVStore kv = kv();

        // When
        Instant before = Instant.now();
        String description = "myDescription";
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(new KVMetadata(description, Duration.ofMinutes(5)), complexValue));

        // Then
        StorageObject withMetadata = storageInterface.getWithMetadata(MAIN_TENANT, kv.namespace(), URI.create("/" + kv.namespace().replace(".", "/") + "/_kv/my-key.ion"));
        String valueFile = new String(withMetadata.inputStream().readAllBytes());
        Instant expirationDate = Instant.parse(withMetadata.metadata().get("expirationDate"));
        assertThat(expirationDate.isAfter(before.plus(Duration.ofMinutes(4))) && expirationDate.isBefore(before.plus(Duration.ofMinutes(6)))).isTrue();
        assertThat(valueFile).isEqualTo(JacksonMapper.ofIon().writeValueAsString(complexValue));
        assertThat(withMetadata.metadata().get("description")).isEqualTo(description);

        // Re-When
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(new KVMetadata(null, Duration.ofMinutes(10)), "some-value"));

        // Then
        withMetadata = storageInterface.getWithMetadata(MAIN_TENANT, kv.namespace(), URI.create("/" + kv.namespace().replace(".", "/") + "/_kv/my-key.ion"));
        valueFile = new String(withMetadata.inputStream().readAllBytes());
        expirationDate = Instant.parse(withMetadata.metadata().get("expirationDate"));
        assertThat(expirationDate.isAfter(before.plus(Duration.ofMinutes(9))) && expirationDate.isBefore(before.plus(Duration.ofMinutes(11)))).isTrue();
        assertThat(valueFile).isEqualTo("\"some-value\"");
    }

    @Test
    void should_delete_with_metadata() throws IOException {
        final InternalKVStore kv = kv();

        kv.put(TEST_KV_KEY, new KVValueAndMetadata(new KVMetadata("description", Duration.ofMinutes(5)), complexValue));
        URI uri = kv.storageUri(TEST_KV_KEY);
        URI metadataURI = URI.create(uri.getPath() + ".metadata");
        assertThat(storageInterface.exists(MAIN_TENANT, kv.namespace(), uri)).isTrue();
        assertThat(storageInterface.exists(MAIN_TENANT, kv.namespace(), metadataURI)).isTrue();

        boolean deleted = kv.delete(TEST_KV_KEY);
        assertTrue(deleted);
        assertThat(storageInterface.exists(MAIN_TENANT, kv.namespace(), uri)).isFalse();
        assertThat(storageInterface.exists(MAIN_TENANT, kv.namespace(), metadataURI)).isFalse();
    }

    @Test
    void should_delete_without_metadata() throws IOException {
        final InternalKVStore kv = kv();

        kv.put(TEST_KV_KEY, new KVValueAndMetadata(null, complexValue));
        URI uri = kv.storageUri(TEST_KV_KEY);
        URI metadataURI = URI.create(uri.getPath() + ".metadata");
        assertThat(storageInterface.exists(MAIN_TENANT, kv.namespace(), uri)).isTrue();
        assertThat(storageInterface.exists(MAIN_TENANT, kv.namespace(), metadataURI)).isFalse();

        boolean deleted = kv.delete(TEST_KV_KEY);
        assertTrue(deleted);
        assertThat(storageInterface.exists(MAIN_TENANT, kv.namespace(), uri)).isFalse();
    }

    @Test
    void shouldGetGivenEntryWithNullValue() throws IOException, ResourceExpiredException {
        // Given
        final InternalKVStore kv = kv();
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(new KVMetadata(null, Duration.ofMinutes(5)), null));

        // When
        Optional<KVValue> value = kv.getValue(TEST_KV_KEY);

        // Then
        assertThat(value).isEqualTo(Optional.of(new KVValue(null)));
    }

    @Test
    void shouldGetGivenEntryWithComplexValue() throws IOException, ResourceExpiredException {
        // Given
        final InternalKVStore kv = kv();
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(new KVMetadata(null, Duration.ofMinutes(5)), complexValue));

        // When
        Optional<KVValue> value = kv.getValue(TEST_KV_KEY);

        // Then
        assertThat(value.get()).isEqualTo(new KVValue(complexValue));
    }

    @Test
    void shouldGetEmptyGivenNonExistingKey() throws IOException, ResourceExpiredException {
        // Given
        final InternalKVStore kv = kv();

        // When
        Optional<KVValue> value = kv.getValue(TEST_KV_KEY);

        // Then
        assertThat(value.isEmpty()).isTrue();
    }

    @Test
    void shouldThrowGivenExpiredEntry() throws IOException {
        // Given
        final InternalKVStore kv = kv();
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(new KVMetadata(null, Duration.ofNanos(1)), complexValue));

        // When
        Assertions.assertThrows(ResourceExpiredException.class, () -> kv.getValue(TEST_KV_KEY));
    }

    @Test
    void illegalKey() {
        InternalKVStore kv = kv();
        String expectedErrorMessage = "Key must start with an alphanumeric character (uppercase or lowercase) and can contain alphanumeric characters (uppercase or lowercase), dots (.), underscores (_), and hyphens (-) only.";

        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> KVStore.validateKey("a/b"));
        assertThat(illegalArgumentException.getMessage()).isEqualTo(expectedErrorMessage);
        illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> kv.getValue("a/b"));
        assertThat(illegalArgumentException.getMessage()).isEqualTo(expectedErrorMessage);
        illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> kv.put("a/b", new KVValueAndMetadata(new KVMetadata(null, Duration.ofMinutes(5)), "content")));
        assertThat(illegalArgumentException.getMessage()).isEqualTo(expectedErrorMessage);
        illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> kv.delete("a/b"));
        assertThat(illegalArgumentException.getMessage()).isEqualTo(expectedErrorMessage);

        Assertions.assertDoesNotThrow(() -> KVStore.validateKey("AN_UPPER.CASE-key"));
    }

    private InternalKVStore kv() {
        final String namespaceId = "io.kestra." + IdUtils.create();
        return new InternalKVStore(MAIN_TENANT, namespaceId, storageInterface);
    }
}
