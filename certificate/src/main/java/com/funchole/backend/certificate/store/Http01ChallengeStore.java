package com.funchole.backend.certificate.store;

/**
 * Handoff point between the process that talks to the ACME server (where a
 * certificate gets requested) and the process that actually answers Let's
 * Encrypt's HTTP-01 validation request on the gateway's public port 80 -
 * these are two separate JVMs/containers in FuncHole's topology, so the
 * challenge token and its expected response body have to be published
 * somewhere both sides can reach.
 */
public interface Http01ChallengeStore {

    void put(String token, String authorization);

    void remove(String token);
}
