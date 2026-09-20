package com.funchole.backend.gateway.staticsite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticFileResolverTests {

    @TempDir
    Path siteRoot;

    @Test
    void resolvesAnExactFileThatExists() throws IOException {
        Files.writeString(siteRoot.resolve("index.html"), "<html>root</html>");
        Files.createDirectories(siteRoot.resolve("assets"));
        Files.writeString(siteRoot.resolve("assets/app.js"), "console.log('hi');");

        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "assets/app.js");

        assertTrue(resolved.isPresent());
        assertEquals(siteRoot.resolve("assets/app.js"), resolved.get());
    }

    @Test
    void fallsBackToIndexHtmlWhenRequestedFileIsMissing() throws IOException {
        Files.writeString(siteRoot.resolve("index.html"), "<html>root</html>");

        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "some/spa/route");

        assertTrue(resolved.isPresent());
        assertEquals(siteRoot.resolve("index.html"), resolved.get());
    }

    @Test
    void resolvesIndexHtmlForABlankRelativePath() throws IOException {
        Files.writeString(siteRoot.resolve("index.html"), "<html>root</html>");

        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "");

        assertTrue(resolved.isPresent());
        assertEquals(siteRoot.resolve("index.html"), resolved.get());
    }

    @Test
    void rejectsPathTraversalOutsideTheSiteRoot() throws IOException {
        Files.writeString(siteRoot.resolve("index.html"), "<html>root</html>");
        Path secretOutsideRoot = siteRoot.resolveSibling("secret.txt");
        Files.writeString(secretOutsideRoot, "top secret");

        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "../secret.txt");

        assertTrue(resolved.isEmpty());

        Files.deleteIfExists(secretOutsideRoot);
    }

    @Test
    void returnsEmptyWhenNeitherTheFileNorIndexHtmlExist() {
        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "missing.html");

        assertTrue(resolved.isEmpty());
    }

    @Test
    void resolvesACleanUrlToItsFlatHtmlFile() throws IOException {
        Files.writeString(siteRoot.resolve("index.html"), "<html>root</html>");
        Files.writeString(siteRoot.resolve("about.html"), "<html>about</html>");

        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "about");

        assertTrue(resolved.isPresent());
        assertEquals(siteRoot.resolve("about.html"), resolved.get());
    }

    @Test
    void resolvesACleanUrlToItsDirectoryIndexFile() throws IOException {
        Files.writeString(siteRoot.resolve("index.html"), "<html>root</html>");
        Files.createDirectories(siteRoot.resolve("about"));
        Files.writeString(siteRoot.resolve("about/index.html"), "<html>about</html>");

        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "about");

        assertTrue(resolved.isPresent());
        assertEquals(siteRoot.resolve("about/index.html"), resolved.get());
    }

    @Test
    void resolvesACleanUrlWithATrailingSlashToItsDirectoryIndexFile() throws IOException {
        Files.writeString(siteRoot.resolve("index.html"), "<html>root</html>");
        Files.createDirectories(siteRoot.resolve("about"));
        Files.writeString(siteRoot.resolve("about/index.html"), "<html>about</html>");

        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "about/");

        assertTrue(resolved.isPresent());
        assertEquals(siteRoot.resolve("about/index.html"), resolved.get());
    }

    @Test
    void anExactFileStillWinsOverACleanUrlCandidate() throws IOException {
        Files.writeString(siteRoot.resolve("index.html"), "<html>root</html>");
        Files.writeString(siteRoot.resolve("about"), "literal file named 'about', no extension");
        Files.writeString(siteRoot.resolve("about.html"), "<html>about</html>");

        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "about");

        assertTrue(resolved.isPresent());
        assertEquals(siteRoot.resolve("about"), resolved.get());
    }

    @Test
    void fallsBackToRootIndexWhenNoCleanUrlCandidateExistsEither() throws IOException {
        Files.writeString(siteRoot.resolve("index.html"), "<html>root</html>");

        Optional<Path> resolved = StaticFileResolver.resolve(siteRoot, "contact");

        assertTrue(resolved.isPresent());
        assertEquals(siteRoot.resolve("index.html"), resolved.get());
    }
}
