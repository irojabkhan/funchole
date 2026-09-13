package com.funchole.backend.controlplane.entity;

import java.util.List;

public record SourceBundle(
        String runtimeType, String runtimeVersion, String entrypoint, String handler, List<SourceFile> files
) {
}
