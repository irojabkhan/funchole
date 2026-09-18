package com.funchole.backend.gateway.staticsite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaticContentTypesTests {

    @Test
    void resolvesKnownExtensionsToTheirContentType() {
        assertEquals("text/html; charset=UTF-8", StaticContentTypes.forPath(Path.of("index.html")));
        assertEquals("text/css; charset=UTF-8", StaticContentTypes.forPath(Path.of("assets/app.css")));
        assertEquals("text/javascript; charset=UTF-8", StaticContentTypes.forPath(Path.of("assets/app.js")));
        assertEquals("image/svg+xml", StaticContentTypes.forPath(Path.of("logo.svg")));
    }

    @Test
    void isCaseInsensitiveOnTheExtension() {
        assertEquals("image/png", StaticContentTypes.forPath(Path.of("logo.PNG")));
    }

    @Test
    void fallsBackToOctetStreamForUnknownOrMissingExtensions() {
        assertEquals("application/octet-stream", StaticContentTypes.forPath(Path.of("README")));
        assertEquals("application/octet-stream", StaticContentTypes.forPath(Path.of("weird.xyz")));
        assertEquals("application/octet-stream", StaticContentTypes.forPath(Path.of("trailing.")));
    }
}
