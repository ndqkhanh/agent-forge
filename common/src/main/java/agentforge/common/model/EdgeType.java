package agentforge.common.model;

/**
 * Types of edges in the workflow DAG.
 */
public enum EdgeType {
    /** Always fires when predecessor completes successfully. */
    UNCONDITIONAL,
    /** Fires only when a predicate on the predecessor's output is true. */
    CONDITIONAL,
    /** Fires immediately based on predicted outcome; rolled back if wrong. */
    SPECULATIVE
}
