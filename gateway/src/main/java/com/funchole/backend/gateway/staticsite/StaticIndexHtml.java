package com.funchole.backend.gateway.staticsite;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Any {@code index.html} in a STATIC site - the site's own root, or a
 * nested directory-style page like {@code about/index.html} (see
 * {@link StaticFileResolver}) - is served for more than one browser URL:
 * its own directory (with or without a trailing slash) and, for the site's
 * root {@code index.html} specifically, any SPA-fallback deep link the
 * resolver hands it for (e.g. {@code /app/dashboard/settings} for a Flow
 * mounted at {@code /app/*}). The page's own relative asset references
 * ({@code href="style.css"}, {@code src="app.js"}) resolve differently in
 * each case - the browser computes them against whatever URL is actually
 * in its address bar, not against the file's real directory - so the exact
 * same references can 404 from one URL and work from another.
 *
 * <p>{@link #withBaseHref} fixes this the standard way: injecting a
 * {@code <base href="...">} right after the page's own {@code <head>} tag
 * makes every relative reference in the page (including a relative
 * {@code fetch()} call) resolve against that {@code index.html}'s real
 * directory, regardless of the browser's current URL.
 */
public final class StaticIndexHtml {

    private static final String INDEX_HTML = "index.html";
    private static final Pattern HEAD_OPEN_TAG = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE);

    private StaticIndexHtml() {
    }

    public static boolean isIndexHtml(Path resolved) {
        Path fileName = resolved.getFileName();
        return fileName != null && INDEX_HTML.equals(fileName.toString());
    }

    /**
     * @param siteRoot  the site's own local artifact directory
     * @param resolved  the {@code index.html} file actually served (site
     *                  root or a nested directory's own index)
     * @param mountRoot the Flow's own mount root, always ending in "/"
     *                  (e.g. "/app/")
     * @return {@code mountRoot} itself for the site's root index.html, or
     * {@code mountRoot} plus {@code resolved}'s directory relative to
     * {@code siteRoot} (e.g. "/app/about/") for a nested index.html
     */
    public static String baseHrefFor(Path siteRoot, Path resolved, String mountRoot) {
        Path parent = resolved.getParent();
        if (parent == null) {
            return mountRoot;
        }
        Path relativeDirectory = siteRoot.normalize().relativize(parent);
        String relativeDirectoryText = relativeDirectory.toString().replace(File.separatorChar, '/');
        return relativeDirectoryText.isEmpty() ? mountRoot : mountRoot + relativeDirectoryText + "/";
    }

    /**
     * @param htmlBytes the page's raw bytes, assumed UTF-8 (matching the
     *                  {@code text/html; charset=UTF-8} this is always
     *                  served with)
     * @param baseHref  the value for the injected {@code <base href>}, see
     *                  {@link #baseHrefFor}
     * @return the page with a {@code <base>} tag injected right after its
     * first {@code <head>} tag, or the original bytes unchanged if no
     * {@code <head>} tag is found
     */
    public static byte[] withBaseHref(byte[] htmlBytes, String baseHref) {
        String html = new String(htmlBytes, StandardCharsets.UTF_8);
        Matcher matcher = HEAD_OPEN_TAG.matcher(html);
        if (!matcher.find()) {
            return htmlBytes;
        }
        String baseTag = "<base href=\"" + baseHref.replace("\"", "&quot;") + "\">";
        String withBase = html.substring(0, matcher.end()) + baseTag + html.substring(matcher.end());
        return withBase.getBytes(StandardCharsets.UTF_8);
    }
}
