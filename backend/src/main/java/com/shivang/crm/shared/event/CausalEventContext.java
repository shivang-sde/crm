package com.shivang.crm.shared.event;

import java.util.UUID;

/**
 * Thread-local causal lineage for canonical events published while a workflow
 * action executes on this thread. The workflow graph runtime sets lineage
 * around action node execution; {@link CanonicalCrmEventPublisher} appends it
 * to event metadata so the trigger matcher can bound cross-workflow recursion.
 *
 * Always empty for manual/ingestion/system domain operations.
 */
public final class CausalEventContext {

    public record Lineage(UUID executionId, UUID workflowId, int chainDepth) {
    }

    /** Metadata keys used to carry lineage inside canonical event payloads. */
    public static final String METADATA_CAUSED_BY_EXECUTION_ID = "causedByWorkflowExecutionId";
    public static final String METADATA_CAUSED_BY_WORKFLOW_ID = "causedByWorkflowId";
    public static final String METADATA_CHAIN_DEPTH = "causedByChainDepth";

    private static final ThreadLocal<Lineage> LINEAGE = new ThreadLocal<>();

    private CausalEventContext() {
    }

    public static void set(Lineage lineage) {
        LINEAGE.set(lineage);
    }

    public static Lineage get() {
        return LINEAGE.get();
    }

    public static void clear() {
        LINEAGE.remove();
    }
}
