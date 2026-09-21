package com.funchole.backend.controlplane.config;

import com.funchole.backend.certificate.CertificateBundle;
import com.funchole.backend.certificate.CertificateProvider;
import com.funchole.backend.certificate.CertificateReference;
import com.funchole.backend.certificate.generator.AcmeAccountKeyPairs;
import com.funchole.backend.certificate.generator.AcmeCertificateGenerator;
import com.funchole.backend.certificate.generator.CertificateGenerator;
import com.funchole.backend.certificate.generator.SelfSignedCertificateGenerator;
import com.funchole.backend.certificate.store.CertificateStore;
import com.funchole.backend.certificate.store.Http01ChallengeStore;
import java.net.URI;
import java.security.KeyPair;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CertificateConfig {

    private static final CertificateReference ACME_ACCOUNT_KEY_REFERENCE = new CertificateReference("acme/account-key");

    @Bean
    CertificateGenerator certificateGenerator(
            CertificateProperties properties,
            CertificateStore certificateStore,
            Http01ChallengeStore http01ChallengeStore
    ) {
        if (properties.provider() == CertificateProvider.SELF_SIGNED) {
            return new SelfSignedCertificateGenerator(Duration.ofDays(properties.selfSignedValidityDays()));
        }

        if (properties.provider() == CertificateProvider.LETS_ENCRYPT) {
            return new AcmeCertificateGenerator(
                    URI.create(properties.acmeServerUri()),
                    loadOrCreateAccountKeyPair(certificateStore),
                    http01ChallengeStore,
                    Duration.ofSeconds(properties.acmePollIntervalSeconds()),
                    properties.acmeMaxPollAttempts()
            );
        }

        throw new IllegalStateException("Certificate provider is not implemented yet: " + properties.provider());
    }

    private KeyPair loadOrCreateAccountKeyPair(CertificateStore certificateStore) {
        try {
            CertificateBundle existing = certificateStore.load(ACME_ACCOUNT_KEY_REFERENCE);
            return AcmeAccountKeyPairs.parse(existing);
        } catch (Exception loadFailure) {
            KeyPair generated = AcmeAccountKeyPairs.generate();
            certificateStore.save(ACME_ACCOUNT_KEY_REFERENCE, AcmeAccountKeyPairs.toBundle(generated));
            return generated;
        }
    }
}
