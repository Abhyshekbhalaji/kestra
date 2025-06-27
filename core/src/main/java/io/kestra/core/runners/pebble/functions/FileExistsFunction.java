package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.LocalPath;
import io.kestra.core.storages.StorageContext;
import io.pebbletemplates.pebble.template.EvaluationContext;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;

@Singleton
public class FileExistsFunction extends AbstractFileFunction {
    private static final String ERROR_MESSAGE = "The 'fileExists' function expects an argument 'path' that is a path to the internal storage URI.";

    @Override
    protected Object fileFunction(EvaluationContext context, URI path, String namespace, String tenantId) throws IOException {
        return switch (path.getScheme()) {
            case StorageContext.KESTRA_SCHEME -> storageInterface.exists(tenantId, namespace, path);
            case LocalPath.FILE_SCHEME -> localPathFactory.createLocalPath().exists(path);
            default -> throw new IllegalArgumentException(SCHEME_NOT_SUPPORTED_ERROR.formatted(path));
        };
    }

    @Override
    protected String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
