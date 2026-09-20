package com.funchole.backend.gateway.staticsite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaticIndexHtmlTests {

    @Test
    void isIndexHtmlMatchesAnyIndexHtmlRegardlessOfDepth() {
        assertTrue(StaticIndexHtml.isIndexHtml(Path.of("/sites/abc/index.html")));
        assertTrue(StaticIndexHtml.isIndexHtml(Path.of("/sites/abc/about/index.html")));
        assertFalse(StaticIndexHtml.isIndexHtml(Path.of("/sites/abc/style.css")));
        assertFalse(StaticIndexHtml.isIndexHtml(Path.of("/sites/abc/about.html")));
    }

    @Test
    void baseHrefForTheSiteRootIndexIsJustTheMountRoot() {
        Path siteRoot = Path.of("/sites/abc");
        Path resolved = Path.of("/sites/abc/index.html");

        assertEquals("/app/", StaticIndexHtml.baseHrefFor(siteRoot, resolved, "/app/"));
    }

    @Test
    void baseHrefForANestedIndexIncludesItsOwnDirectory() {
        Path siteRoot = Path.of("/sites/abc");
        Path resolved = Path.of("/sites/abc/about/index.html");

        assertEquals("/app/about/", StaticIndexHtml.baseHrefFor(siteRoot, resolved, "/app/"));
    }

    @Test
    void baseHrefForADeeplyNestedIndexIncludesTheFullDirectory() {
        Path siteRoot = Path.of("/sites/abc");
        Path resolved = Path.of("/sites/abc/blog/2026/post/index.html");

        assertEquals("/app/blog/2026/post/", StaticIndexHtml.baseHrefFor(siteRoot, resolved, "/app/"));
    }

    @Test
    void injectsABaseTagRightAfterAPlainHeadTag() {
        byte[] html = "<html><head><title>x</title></head><body></body></html>".getBytes(StandardCharsets.UTF_8);

        byte[] result = StaticIndexHtml.withBaseHref(html, "/app/");

        assertEquals(
                "<html><head><base href=\"/app/\"><title>x</title></head><body></body></html>",
                new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void injectsABaseTagAfterAHeadTagWithAttributes() {
        byte[] html = "<html><head lang=\"en\"><title>x</title></head></html>".getBytes(StandardCharsets.UTF_8);

        byte[] result = StaticIndexHtml.withBaseHref(html, "/app/");

        assertEquals(
                "<html><head lang=\"en\"><base href=\"/app/\"><title>x</title></head></html>",
                new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void isCaseInsensitiveToTheHeadTag() {
        byte[] html = "<HTML><HEAD><title>x</title></HEAD></HTML>".getBytes(StandardCharsets.UTF_8);

        byte[] result = StaticIndexHtml.withBaseHref(html, "/app/");

        assertTrue(new String(result, StandardCharsets.UTF_8).contains("<HEAD><base href=\"/app/\">"));
    }

    @Test
    void leavesHtmlWithNoHeadTagUnchanged() {
        byte[] html = "<html><body>no head here</body></html>".getBytes(StandardCharsets.UTF_8);

        byte[] result = StaticIndexHtml.withBaseHref(html, "/app/");

        assertArrayEquals(html, result);
    }

    @Test
    void escapesAQuoteInTheBaseHref() {
        byte[] html = "<head></head>".getBytes(StandardCharsets.UTF_8);

        byte[] result = StaticIndexHtml.withBaseHref(html, "/a\"b/");

        assertTrue(new String(result, StandardCharsets.UTF_8).contains("href=\"/a&quot;b/\""));
    }
}
