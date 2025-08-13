package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.LocalPath;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.annotation.Property;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Map;

import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.NAMESPACE;
import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariables;
import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariablesWithExecution;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(rebuildContext = true)
@Property(name="kestra.server-type", value="WORKER")
class ReadFileFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Inject
    StorageInterface storageInterface;

    @Test
    void readNamespaceFile() throws IllegalVariableEvaluationException, IOException {
        String filePath = "file.txt";
        storageInterface.createDirectory(MAIN_TENANT, NAMESPACE, URI.create(StorageContext.namespaceFilePrefix(NAMESPACE)));
        storageInterface.put(MAIN_TENANT, NAMESPACE, URI.create(StorageContext.namespaceFilePrefix(NAMESPACE) + "/" + filePath), new ByteArrayInputStream("Hello from {{ flow.namespace }}".getBytes()));

        String render = variableRenderer.render("{{ render(read('" + filePath + "')) }}", getVariables());
        assertThat(render).isEqualTo("Hello from " + NAMESPACE);
    }

    @Test
    void readNamespaceFileFromURI() throws IllegalVariableEvaluationException, IOException {
        String filePath = "file.txt";
        storageInterface.createDirectory(MAIN_TENANT, NAMESPACE, URI.create(StorageContext.namespaceFilePrefix(NAMESPACE)));
        storageInterface.put(MAIN_TENANT, NAMESPACE, URI.create(StorageContext.namespaceFilePrefix(NAMESPACE) + "/" + filePath), new ByteArrayInputStream("Hello from {{ flow.namespace }}".getBytes()));

        Map<String, Object> variables = getVariablesWithExecution(NAMESPACE);

        String render = variableRenderer.render("{{ render(read(fileURI('" + filePath + "'))) }}", variables);
        assertThat(render).isEqualTo("Hello from " + NAMESPACE);
    }

    @Test
    void readNamespaceFileWithNamespace() throws IllegalVariableEvaluationException, IOException {
        String filePath = "file.txt";
        storageInterface.createDirectory(MAIN_TENANT, NAMESPACE, URI.create(StorageContext.namespaceFilePrefix(NAMESPACE)));
        storageInterface.put(MAIN_TENANT, NAMESPACE, URI.create(StorageContext.namespaceFilePrefix(NAMESPACE) + "/" + filePath), new ByteArrayInputStream("Hello but not from flow.namespace".getBytes()));

        String render = variableRenderer.render("{{ read('" + filePath + "', namespace='" + NAMESPACE + "') }}", getVariables("different.namespace"));
        assertThat(render).isEqualTo("Hello but not from flow.namespace");
    }

    @Test
    void readUnknownNamespaceFile() {
        IllegalVariableEvaluationException illegalVariableEvaluationException = assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ read('unknown.txt') }}", getVariables()));
        assertThat(illegalVariableEvaluationException.getCause().getCause().getClass()).isEqualTo(FileNotFoundException.class);
    }

    @Test
    void readInternalStorageFile() throws IOException, IllegalVariableEvaluationException {
        // task output URI format: 'kestra:///$namespace/$flowId/executions/$executionId/tasks/$taskName/$taskRunId/$random.ion'
        String namespace = "my.namespace";
        String flowId = "flow";
        String executionId = IdUtils.create();
        URI internalStorageURI = URI.create("/" + namespace.replace(".", "/") + "/" + flowId + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
        URI internalStorageFile = storageInterface.put(MAIN_TENANT, namespace, internalStorageURI, new ByteArrayInputStream("Hello from a task output".getBytes()));

        // test for an authorized execution
        Map<String, Object> variables = getVariablesWithExecution(namespace, executionId);

        String render = variableRenderer.render("{{ read('" + internalStorageFile + "') }}", variables);
        assertThat(render).isEqualTo("Hello from a task output");

        // test for an authorized parent execution (execution trigger)
        variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", flowId,
                "namespace", namespace,
                "executionId", executionId,
                "tenantId", MAIN_TENANT
            )
        );

        render = variableRenderer.render("{{ read('" + internalStorageFile + "') }}", variables);
        assertThat(render).isEqualTo("Hello from a task output");
    }

    @Test
    void readInternalStorageURI() throws IOException, IllegalVariableEvaluationException {
        // task output URI format: 'kestra:///$namespace/$flowId/executions/$executionId/tasks/$taskName/$taskRunId/$random.ion'
        String namespace = "my.namespace";
        String flowId = "flow";
        String executionId = IdUtils.create();
        URI internalStorageURI = URI.create("/" + namespace.replace(".", "/") + "/" + flowId + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
        URI internalStorageFile = storageInterface.put(MAIN_TENANT, namespace, internalStorageURI, new ByteArrayInputStream("Hello from a task output".getBytes()));

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", flowId,
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId),
            "file", internalStorageFile
        );

        String render = variableRenderer.render("{{ read(file) }}", variables);
        assertThat(render).isEqualTo("Hello from a task output");

        // test for an authorized parent execution (execution trigger)
        variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", flowId,
                "namespace", namespace,
                "executionId", executionId,
                "tenantId", MAIN_TENANT
            )
        );

        render = variableRenderer.render("{{ read('" + internalStorageFile + "') }}", variables);
        assertThat(render).isEqualTo("Hello from a task output");
    }

    @Test
    void readInternalStorageFileFromAnotherExecution() throws IOException, IllegalVariableEvaluationException {
        String namespace = "my.namespace";
        String flowId = "flow";
        String executionId = IdUtils.create();
        URI internalStorageURI = URI.create("/" + namespace.replace(".", "/") + "/" + flowId + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
        URI internalStorageFile = storageInterface.put(MAIN_TENANT, namespace, internalStorageURI, new ByteArrayInputStream("Hello from a task output".getBytes()));

        Map<String, Object> variables = getVariablesWithExecution("notme", "notme");

        String render = variableRenderer.render("{{ read('" + internalStorageFile + "') }}", variables);
        assertThat(render).isEqualTo("Hello from a task output");
    }

    @Test
    @Property(name="kestra.server-type", value="EXECUTOR")
    @Disabled("Moved on the next release")
    void readFailOnNonWorkerNodes() {
        IllegalVariableEvaluationException exception = assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ read('unknown.txt') }}", Map.of("flow", Map.of("namespace", "io.kestra.tests"))));
        assertThat(exception.getMessage()).contains("The 'read' function can only be used in the Worker as it access the internal storage.");
    }

    @Test
    void shouldFailProcessingUnsupportedScheme() {
        Map<String, Object> variables = getVariablesWithExecution("notme", "notme");

        assertThrows(IllegalArgumentException.class, () -> variableRenderer.render("{{ read('unsupported://path-to/file.txt') }}", variables));
    }

    @Test
    void shouldFailProcessingNotAllowedPath() throws IOException {
        URI file = createFile();
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "notme"),
            "file", file.toString()
        );

        assertThrows(SecurityException.class, () -> variableRenderer.render("{{ read(file) }}", variables));
    }

    @Test
    @Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
    void shouldSucceedProcessingAllowedFile() throws IllegalVariableEvaluationException, IOException {
        URI file = createFile();
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "notme"),
            "file", file.toString()
        );

        assertThat(variableRenderer.render("{{ read(file) }}", variables)).isEqualTo("Hello World");
    }

    @Test
    @Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
    @Property(name = LocalPath.ENABLE_FILE_FUNCTIONS_CONFIG, value = "false")
    void shouldFailProcessingAllowedFileIfFileFunctionDisabled() throws IOException {
        URI file = createFile();
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "notme"),
            "file", file.toString()
        );

        assertThrows(SecurityException.class, () -> variableRenderer.render("{{ read(file) }}", variables));
    }

    @Test
    void shouldProcessNamespaceFile() throws IOException, IllegalVariableEvaluationException {
        URI file = createNsFile(false);
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "flow",
                "namespace", "io.kestra.tests",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "execution"),
            "nsfile", file.toString()
        );

        assertThat(variableRenderer.render("{{ read(nsfile) }}", variables)).isEqualTo("Hello World");
    }

    @Test
    void shouldProcessNamespaceFileFromAnotherNamespace() throws IOException, IllegalVariableEvaluationException {
        URI file = createNsFile(true);
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "flow",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "execution"),
            "nsfile", file.toString()
        );

        assertThat(variableRenderer.render("{{ read(nsfile) }}", variables)).isEqualTo("Hello World");
    }

    private URI createFile() throws IOException {
        File tempFile = File.createTempFile("file", ".txt");
        Files.write(tempFile.toPath(), "Hello World".getBytes());
        return tempFile.toPath().toUri();
    }

    private URI createNsFile(boolean nsInAuthority) throws IOException {
        String namespace = "io.kestra.tests";
        String filePath = "file.txt";
        storageInterface.createDirectory(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace)));
        storageInterface.put(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace) + "/" + filePath), new ByteArrayInputStream("Hello World".getBytes()));
        return URI.create("nsfile://" + (nsInAuthority ? namespace : "") + "/" + filePath);
    }
}
