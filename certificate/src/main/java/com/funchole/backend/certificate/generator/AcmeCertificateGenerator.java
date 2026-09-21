package com.funchole.backend.certificate.generator;

import com.funchole.backend.certificate.CertificateBundle;
import com.funchole.backend.certificate.CertificateRequest;
import com.funchole.backend.certificate.GeneratedCertificate;
import com.funchole.backend.certificate.store.Http01ChallengeStore;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

/**
 * Real Let's Encrypt (ACME v2) issuance over the HTTP-01 challenge type.
 * Blocking/synchronous by design - {@link #generate} isn't called on a
 * request thread (see {@code GatewayCertificateService}, which runs
 * provisioning as a background job), and ACME's own challenge-validation
 * protocol requires polling for a status change anyway, so there is no
 * value in an async API here.
 */
public class AcmeCertificateGenerator implements CertificateGenerator {

    private static final int DOMAIN_KEY_SIZE = 2048;

    private final Session session;
    private final KeyPair accountKeyPair;
    private final Http01ChallengeStore challengeStore;
    private final Duration pollInterval;
    private final int maxPollAttempts;

    public AcmeCertificateGenerator(
            URI acmeServerUri,
            KeyPair accountKeyPair,
            Http01ChallengeStore challengeStore,
            Duration pollInterval,
            int maxPollAttempts
    ) {
        this.session = new Session(acmeServerUri);
        this.accountKeyPair = accountKeyPair;
        this.challengeStore = challengeStore;
        this.pollInterval = pollInterval;
        this.maxPollAttempts = maxPollAttempts;
    }

    @Override
    public GeneratedCertificate generate(CertificateRequest request) {
        try {
            Account account = new AccountBuilder()
                    .agreeToTermsOfService()
                    .useKeyPair(accountKeyPair)
                    .create(session);

            Order order = account.newOrder()
                    .domains(request.subjectAlternativeNames())
                    .create();

            for (Authorization authorization : order.getAuthorizations()) {
                authorizeViaHttp01(authorization);
            }

            KeyPair domainKeyPair = KeyPairUtils.createKeyPair(DOMAIN_KEY_SIZE);
            CSRBuilder csrBuilder = new CSRBuilder();
            csrBuilder.addDomains(request.subjectAlternativeNames());
            csrBuilder.sign(domainKeyPair);
            order.execute(csrBuilder.getEncoded());

            waitForStatus(order::getStatus, order::update, "order");

            Certificate certificate = order.getCertificate();
            if (certificate == null) {
                throw new IllegalStateException("ACME order for " + request.commonName() + " completed without a certificate");
            }

            List<X509Certificate> chain = certificate.getCertificateChain();
            X509Certificate leaf = chain.get(0);

            CertificateBundle bundle = new CertificateBundle(
                    toPem(chain).getBytes(StandardCharsets.UTF_8),
                    toPem(domainKeyPair.getPrivate()).getBytes(StandardCharsets.UTF_8)
            );

            return new GeneratedCertificate(
                    bundle,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(leaf.getNotAfter().toInstant(), ZoneOffset.UTC)
            );
        } catch (AcmeException exception) {
            throw new IllegalStateException("ACME certificate issuance failed for " + request.commonName(), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build ACME certificate signing request for " + request.commonName(), exception);
        }
    }

    private void authorizeViaHttp01(Authorization authorization) throws AcmeException {
        if (authorization.getStatus() == Status.VALID) {
            return;
        }

        Http01Challenge challenge = authorization.findChallenge(Http01Challenge.class)
                .orElseThrow(() -> new AcmeException(
                        "No HTTP-01 challenge offered for " + authorization.getIdentifier().getDomain()));

        challengeStore.put(challenge.getToken(), challenge.getAuthorization());
        try {
            challenge.trigger();
            waitForStatus(challenge::getStatus, challenge::update, "challenge");
        } finally {
            challengeStore.remove(challenge.getToken());
        }
    }

    private void waitForStatus(Supplier<Status> statusSupplier, StatusUpdater updater, String what) throws AcmeException {
        for (int attempt = 0; attempt < maxPollAttempts; attempt++) {
            Status status = statusSupplier.get();
            if (status == Status.VALID) {
                return;
            }
            if (status == Status.INVALID) {
                throw new IllegalStateException("ACME " + what + " reached INVALID status");
            }
            sleep(what);
            updater.update();
        }
        throw new IllegalStateException("Timed out waiting for ACME " + what + " to become VALID");
    }

    private void sleep(String what) {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for ACME " + what, interruptedException);
        }
    }

    private String toPem(List<X509Certificate> chain) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            for (X509Certificate certificate : chain) {
                pemWriter.writeObject(certificate);
            }
        }
        return stringWriter.toString();
    }

    private String toPem(Object value) throws IOException {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(value);
            pemWriter.flush();
            return stringWriter.toString();
        }
    }

    @FunctionalInterface
    private interface StatusUpdater {
        void update() throws AcmeException;
    }
}
