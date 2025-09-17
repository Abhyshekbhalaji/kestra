package io.kestra.core.repositories;

import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class AbstractFlowTopologyRepositoryTest {
    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    protected FlowTopology createSimpleFlowTopology(String tenantId, String flowA, String flowB, String namespace) {
        return FlowTopology.builder()
            .relation(FlowRelation.FLOW_TASK)
            .source(FlowNode.builder()
                .id(flowA)
                .namespace(namespace)
                .tenantId(tenantId)
                .uid(tenantId + flowA)
                .build()
            )
            .destination(FlowNode.builder()
                .id(flowB)
                .namespace(namespace)
                .tenantId(tenantId)
                .uid(tenantId + flowB)
                .build()
            )
            .build();
    }

    @Test
    void findByFlow() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow(tenant, "io.kestra.tests", "flow-a", false);

        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    void findByNamespace() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
        );
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-c", "flow-d", "io.kestra.tests")
        );

        List<FlowTopology> list = flowTopologyRepository.findByNamespace(tenant, "io.kestra.tests");

        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    void findAll() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
        );
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-c", "flow-d", "io.kestra.tests")
        );
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-e", "flow-f", "io.kestra.tests.2")
        );

        List<FlowTopology> list = flowTopologyRepository.findAll(tenant);

        assertThat(list.size()).isEqualTo(3);
    }
}
