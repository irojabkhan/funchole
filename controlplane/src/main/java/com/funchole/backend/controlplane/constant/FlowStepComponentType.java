package com.funchole.backend.controlplane.constant;

/**
 * The component types the Dispatcher's ExecutionPlanner can actually progress
 * today. FUNCTION, RESPONSE and MIDDLEWARE all reference a real, owned,
 * READY FunctionVersion (validated in FlowStepService); SUB_FLOW references a
 * real, owned, ADOPTED FlowVersion instead. MAPPING/LOGICAL remain
 * unimplemented - widen this further only once the Dispatcher gains support.
 */
public enum FlowStepComponentType {
    FUNCTION,
    RESPONSE,
    MIDDLEWARE,
    SUB_FLOW
}
