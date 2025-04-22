package io.kestra.core.models.tasks.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.runners.TaskLogLineMatcher.TaskLogMatch;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwConsumer;

abstract public class PluginUtilsService {

    private static final TypeReference<Map<String, String>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

    public static Map<String, String> createOutputFiles(
        Path tempDirectory,
        List<String> outputFiles,
        Map<String, Object> additionalVars
    ) throws IOException {
        return PluginUtilsService.createOutputFiles(tempDirectory, outputFiles, additionalVars, false);
    }

    public static Map<String, String> createOutputFiles(
        Path tempDirectory,
        List<String> outputFiles,
        Map<String, Object> additionalVars,
        Boolean isDir
    ) throws IOException {
        List<String> outputs = new ArrayList<>();

        if (outputFiles != null && !outputFiles.isEmpty()) {
            outputs.addAll(outputFiles);
        }

        Map<String, String> result = new HashMap<>();
        if (!outputs.isEmpty()) {
            outputs
                .forEach(throwConsumer(s -> {
                    PluginUtilsService.validFilename(s);
                    File tempFile;

                    if (isDir) {
                        tempFile = Files.createTempDirectory(tempDirectory, s + "_").toFile();
                    } else {
                        String prefix = StringUtils.leftPad(s + "_", 3, "_");
                        tempFile = File.createTempFile(prefix, null, tempDirectory.toFile());
                    }

                    result.put(s, additionalVars.get("workingDir") + "/" + tempFile.getName());
                }));

            if (!isDir) {
                additionalVars.put("temp", result);
            }
            additionalVars.put(isDir ? "outputDirs": "outputFiles", result);
        }

        return result;
    }

    private static void validFilename(String s) {
        if (s.startsWith("./") || s.startsWith("..") || s.startsWith("/")) {
            throw new IllegalArgumentException("Invalid outputFile (only relative path is supported) " +
                "for path '" + s + "'"
            );
        }
    }

    public static Map<String, String> transformInputFiles(RunContext runContext, @NotNull Object inputFiles) throws IllegalVariableEvaluationException, JsonProcessingException {
        return PluginUtilsService.transformInputFiles(runContext, Collections.emptyMap(), inputFiles);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> transformInputFiles(RunContext runContext, Map<String, Object> additionalVars, @NotNull Object inputFiles) throws IllegalVariableEvaluationException, JsonProcessingException {
        if (inputFiles instanceof Map) {
            Map<String, String> castedInputFiles = (Map<String, String>) ((Map<?, ?>) inputFiles);
            Map<String, String> nullFilteredInputFiles = new HashMap<>();
            castedInputFiles.forEach((key, val) -> {
                if (val != null) {
                    nullFilteredInputFiles.put(key, val);
                }
            });
            return runContext.renderMap(nullFilteredInputFiles, additionalVars);
        } else if (inputFiles instanceof String inputFileString) {


            return JacksonMapper.ofJson(false).readValue(
                runContext.render(inputFileString, additionalVars),
                MAP_TYPE_REFERENCE
            );
        } else {
            throw new IllegalVariableEvaluationException("Invalid `files` properties with type '" + (inputFiles != null ? inputFiles.getClass() : "null") + "'");
        }
    }


    public static void createInputFiles(
        RunContext runContext,
        Path workingDirectory,
        Map<String, String> inputFiles,
        Map<String, Object> additionalVars
    ) throws IOException, IllegalVariableEvaluationException, URISyntaxException {
        if (inputFiles != null && inputFiles.size() > 0) {
            for (String fileName : inputFiles.keySet()) {
                String finalFileName = runContext.render(fileName);

                PluginUtilsService.validFilename(finalFileName);

                File file = new File(fileName);

                // path with "/", create the subfolders
                if (file.getParent() != null) {
                    Path subFolder = Paths.get(
                        workingDirectory.toAbsolutePath().toString(),
                        new File(finalFileName).getParent()
                    );

                    if (!subFolder.toFile().exists()) {
                        Files.createDirectories(subFolder);
                    }
                }

                String filePath = workingDirectory + "/" + finalFileName;
                String render = runContext.render(inputFiles.get(fileName), additionalVars);

                if (render.startsWith("kestra://")) {
                    try (
                        InputStream inputStream = runContext.storage().getFile(new URI(render));
                        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(filePath))
                    ) {
                        int byteRead;
                        while ((byteRead = inputStream.read()) != -1) {
                            outputStream.write(byteRead);
                        }
                        outputStream.flush();
                    }
                } else {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                        writer.write(render);
                    }
                }
            }
        }
    }

    public static Map<String, Object> parseOut(String line, Logger logger, RunContext runContext, boolean isStdErr, Instant customInstant) {

        TaskLogLineMatcher logLineMatcher = ((DefaultRunContext) runContext).getApplicationContext().getBean(TaskLogLineMatcher.class);

        Map<String, Object> outputs = new HashMap<>();
        try {
            Optional<TaskLogMatch> matches = logLineMatcher.matches(line, logger, runContext, customInstant);
            if (matches.isPresent()) {
                TaskLogMatch taskLogMatch = matches.get();
                outputs.putAll(taskLogMatch.outputs());
            } else if (isStdErr) {
                runContext.logger().error(line);
            } else {
                runContext.logger().info(line);
            }

        } catch (IOException e) {
            logger.warn("Invalid outputs '{}'", e.getMessage(), e);
        }
        return outputs;
    }

    /**
     * This helper method will allow gathering the execution information from a task parameters:
     * - If executionId is null, it is fetched from the runContext variables (a.k.a. current execution).
     * - If executionId is not null but namespace and flowId are null, namespace and flowId will be fetched from the runContext variables.
     * - Otherwise, all params must be set
     * It will then check that the namespace is allowed to access the target namespace.
     * <p>
     * It will throw IllegalArgumentException for any incompatible set of variables.
     */
    @SuppressWarnings("unchecked")
    public static ExecutionInfo executionFromTaskParameters(RunContext runContext, String namespace, String flowId, String executionId) throws IllegalVariableEvaluationException {
        var flowInfo = runContext.flowInfo();

        String realTenantId = flowInfo.tenantId();
        String realExecutionId;
        String realNamespace;
        String realFlowId;
        if (executionId != null) {
            realExecutionId = runContext.render(executionId);

            if (namespace != null && flowId != null) {
                realNamespace = runContext.render(namespace);
                realFlowId = runContext.render(flowId);
                // validate that the flow exists: a.k.a access is authorized by this namespace
                FlowService flowService = ((DefaultRunContext)runContext).getApplicationContext().getBean(FlowService.class);
                flowService.checkAllowedNamespace(flowInfo.tenantId(), realNamespace, flowInfo.tenantId(), flowInfo.namespace());
            } else if (namespace != null || flowId != null) {
                throw new IllegalArgumentException("Both `namespace` and `flowId` must be set when `executionId` is set.");
            } else {
                realNamespace = flowInfo.namespace();
                realFlowId = flowInfo.id();
            }

        } else {
            if (namespace != null || flowId != null) {
                throw new IllegalArgumentException("`namespace` and `flowId` should only be set when `executionId` is set.");
            }
            realExecutionId = (String) new HashMap<>((Map<String, Object>) runContext.getVariables().get("execution")).get("id");
            realNamespace = flowInfo.namespace();
            realFlowId = flowInfo.id();
        }

        return new ExecutionInfo(realTenantId, realNamespace, realFlowId, realExecutionId);
    }

    public record ExecutionInfo(String tenantId, String namespace, String flowId, String id) {}
}
