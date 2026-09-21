package com.funchole.backend.certificate.generator;

import com.funchole.backend.certificate.CertificateBundle;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.shredzone.acme4j.util.KeyPairUtils;

/**
 * Persists an ACME account's key pair (not a certificate) through the same
 * {@link com.funchole.backend.certificate.store.CertificateStore} used for
 * certificate material, since it's the only durable, OpenBao-backed storage
 * this module already has - the account key isn't a real cert/chain, so its
 * PEM-encoded public and private halves are carried in
 * {@link CertificateBundle}'s two fields instead of a real chain.
 */
public final class AcmeAccountKeyPairs {

    private static final int KEY_SIZE = 2048;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private AcmeAccountKeyPairs() {
    }

    public static KeyPair generate() {
        return KeyPairUtils.createKeyPair(KEY_SIZE);
    }

    public static CertificateBundle toBundle(KeyPair keyPair) {
        return new CertificateBundle(
                toPem(keyPair.getPublic()).getBytes(StandardCharsets.UTF_8),
                toPem(keyPair.getPrivate()).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static KeyPair parse(CertificateBundle bundle) {
        PublicKey publicKey = parsePublicKey(new String(bundle.certificateChain(), StandardCharsets.UTF_8));
        PrivateKey privateKey = parsePrivateKey(new String(bundle.privateKey(), StandardCharsets.UTF_8));
        return new KeyPair(publicKey, privateKey);
    }

    private static PublicKey parsePublicKey(String pem) {
        Object parsed = readPem(pem);
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        try {
            if (parsed instanceof SubjectPublicKeyInfo publicKeyInfo) {
                return converter.getPublicKey(publicKeyInfo);
            }
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                return converter.getKeyPair(pemKeyPair).getPublic();
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to convert ACME account public key", exception);
        }
        throw new IllegalStateException("Unrecognized PEM content for ACME account public key: " + parsed.getClass());
    }

    private static PrivateKey parsePrivateKey(String pem) {
        Object parsed = readPem(pem);
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        try {
            if (parsed instanceof PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                return converter.getKeyPair(pemKeyPair).getPrivate();
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to convert ACME account private key", exception);
        }
        throw new IllegalStateException("Unrecognized PEM content for ACME account private key: " + parsed.getClass());
    }

    private static Object readPem(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object parsed = parser.readObject();
            if (parsed == null) {
                throw new IllegalStateException("No PEM object found in ACME account key material");
            }
            return parsed;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse ACME account key PEM", exception);
        }
    }

    private static String toPem(Object value) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(value);
            pemWriter.flush();
            return stringWriter.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode ACME account key as PEM", exception);
        }
    }
}
