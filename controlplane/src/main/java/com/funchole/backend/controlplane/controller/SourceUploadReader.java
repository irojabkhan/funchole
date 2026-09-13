package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.entity.SourceFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads an uploaded source submission into the plain {@link SourceFile} list
 * that {@code FunctionVersionSourceService} already accepts. Two multipart
 * shapes are supported side by side, since a real user's source can arrive
 * either way:
 *
 * <ul>
 *   <li>a single zip archive part ({@link #readZip}) - the whole
 *       project/folder packaged as one file, e.g. dragging a folder into a
 *       zip tool first;</li>
 *   <li>one or more individual file parts ({@link #readFiles}) - a direct,
 *       no-archive upload of exactly the files that make up the function
 *       (e.g. just {@code index.mjs} and {@code package.json}), with each
 *       part's filename carrying its path relative to the source root
 *       (subdirectories included, e.g. {@code src/handler.mjs}) so a real
 *       folder structure can be preserved without zipping it first.</li>
 * </ul>
 *
 * <p>This is purely a transport-layer concern (it depends on
 * {@link MultipartFile}) and is deliberately kept out of the
 * transport-neutral source service - only the REST multipart adapter needs
 * to know a zip or a raw file list was involved at all; both paths converge
 * on the exact same {@link SourceFile} shape before reaching the service, so
 * path-traversal/entrypoint/duplicate validation there applies identically
 * either way.
 *
 * <p>Entry/size caps here are a minimal, hardcoded guard against an
 * obviously-hostile upload (F269, full archive-bomb policy, is still open)
 * - not a substitute for it.
 */
final class SourceUploadReader {

    private static final int MAX_ENTRIES = 500;
    private static final long MAX_ENTRY_UNCOMPRESSED_BYTES = 5L * 1024 * 1024;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 20L * 1024 * 1024;

    private SourceUploadReader() {
    }

    static List<SourceFile> readZip(MultipartFile archive) {
        if (archive == null || archive.isEmpty()) {
            throw new IllegalArgumentException("A non-empty zip archive file is required");
        }

        List<SourceFile> files = new ArrayList<>();
        int entryCount = 0;
        long totalBytes = 0;

        try (ZipInputStream zip = new ZipInputStream(archive.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Archive contains more than " + MAX_ENTRIES + " entries");
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                long entryBytes = 0;
                int read;
                while ((read = zip.read(chunk)) != -1) {
                    entryBytes += read;
                    totalBytes += read;
                    if (entryBytes > MAX_ENTRY_UNCOMPRESSED_BYTES) {
                        throw new IllegalArgumentException("Archive entry exceeds the maximum allowed size: " + entry.getName());
                    }
                    if (totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw new IllegalArgumentException("Archive exceeds the maximum total uncompressed size");
                    }
                    buffer.write(chunk, 0, read);
                }
                files.add(new SourceFile(entry.getName(), buffer.toString(StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read the uploaded file as a zip archive", exception);
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException("Archive contains no files");
        }
        return files;
    }

    static List<SourceFile> readFiles(List<MultipartFile> uploadedFiles) {
        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one source file is required");
        }
        if (uploadedFiles.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("More than " + MAX_ENTRIES + " files were submitted");
        }

        List<SourceFile> files = new ArrayList<>();
        long totalBytes = 0;
        for (MultipartFile uploadedFile : uploadedFiles) {
            String relativePath = uploadedFile.getOriginalFilename();
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("Each uploaded file must carry a filename to use as its source-relative path");
            }
            if (uploadedFile.getSize() > MAX_ENTRY_UNCOMPRESSED_BYTES) {
                throw new IllegalArgumentException("Uploaded file exceeds the maximum allowed size: " + relativePath);
            }
            totalBytes += uploadedFile.getSize();
            if (totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw new IllegalArgumentException("Uploaded files exceed the maximum total allowed size");
            }
            try {
                files.add(new SourceFile(relativePath, new String(uploadedFile.getBytes(), StandardCharsets.UTF_8)));
            } catch (IOException exception) {
                throw new IllegalArgumentException("Failed to read uploaded file: " + relativePath, exception);
            }
        }
        return files;
    }
}
