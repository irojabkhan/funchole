package com.funchole.backend.gateway.flow;

import com.funchole.backend.gateway.GatewayRequestContext;
import com.funchole.backend.gateway.GatewayRuntimeEntry;
import java.util.Optional;

public interface FlowResolver {

    Optional<RouteMatch> resolve(GatewayRuntimeEntry gateway, GatewayRequestContext requestContext);
}
