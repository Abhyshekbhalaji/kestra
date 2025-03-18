package io.kestra.core.runners.pebble.functions;

import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVValue;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Singleton
public class KvFunction implements Function {

    private static final String KEY_ARGS = "key";
    private static final String ERROR_ON_MISSING_ARG = "errorOnMissing";
    private static final String NAMESPACE_ARG = "namespace";

    @Inject
    private KVStoreService kvStoreService;

    @Override
    public List<String> getArgumentNames() {
        return List.of(KEY_ARGS, NAMESPACE_ARG, ERROR_ON_MISSING_ARG);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        String key = getKey(args, self, lineNumber);
        String namespace = (String) args.get(NAMESPACE_ARG);

        Boolean errorOnMissing = Optional.ofNullable((Boolean) args.get(ERROR_ON_MISSING_ARG)).orElse(true);

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        String flowNamespace = flow.get(NAMESPACE_ARG);
        String flowTenantId = flow.get("tenantId");

        // we didn't check allowedNamespace here as it's checked in the kvStoreService itself
        if (namespace == null) {
            namespace = flowNamespace;
        }

        Optional<KVValue> value;
        try {
            value = kvStoreService.get(flowTenantId, namespace, flowNamespace).getValue(key);
        } catch (Exception e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }

        if (value.isEmpty() && errorOnMissing == Boolean.TRUE) {
            throw new PebbleException(null, "The key '" + key + "' does not exist in the namespace '" + namespace + "'.", lineNumber, self.getName());
        }

        return value.map(KVValue::value).orElse(null);
    }

    protected String getKey(Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        if (!args.containsKey(KEY_ARGS)) {
            throw new PebbleException(null, "The 'kv' function expects an argument 'key'.", lineNumber, self.getName());
        }

        return (String) args.get(KEY_ARGS);
    }
}