package com.funchole.backend.controlplane.config;

import com.funchole.backend.controlplane.service.LocalSourceStore;
import com.funchole.backend.controlplane.service.S3SourceStore;
import com.funchole.backend.controlplane.service.SourceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the {@link SourceStore} implementation, same pattern as
 * {@code CertificateConfig} for {@code CertificateGenerator}. Defaults to
 * {@code local} (today's behavior, zero config change for existing
 * deployments/dev); set {@code SOURCE_STORE_TYPE=s3} in production to store
 * submitted Function source durably instead of on ephemeral container disk -
 * see {@link S3SourceStore}'s own javadoc for why that matters.
 */
@Configuration
public class SourceStoreConfig {

    @Bean
    SourceStore sourceStore(
            SourceStorageProperties sourceStorageProperties,
            ArtifactPublisherProperties artifactPublisherProperties
    ) {
        if ("s3".equalsIgnoreCase(sourceStorageProperties.storeType())) {
            return new S3SourceStore(artifactPublisherProperties);
        }
        if ("local".equalsIgnoreCase(sourceStorageProperties.storeType())) {
            return new LocalSourceStore(sourceStorageProperties);
        }
        throw new IllegalStateException(
                "Unsupported SOURCE_STORE_TYPE: " + sourceStorageProperties.storeType() + " (expected 'local' or 's3')");
    }
}
