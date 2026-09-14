package com.funchole.backend.controlplane.config;

import com.funchole.backend.artifact.ArtifactPublisher;
import com.funchole.backend.artifact.S3ArtifactPublisher;
import com.funchole.backend.artifact.S3ArtifactStoreConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArtifactPublisherConfig {

    @Bean
    ArtifactPublisher artifactPublisher(ArtifactPublisherProperties properties) {
        S3ArtifactStoreConfig config = new S3ArtifactStoreConfig(
                properties.endpoint(),
                properties.bucket(),
                properties.accessKey(),
                properties.secretKey(),
                properties.region(),
                properties.pathStyleAccess()
        );
        return new S3ArtifactPublisher(config);
    }
}
