/*
 * Copyright 2024 The Compress4J Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.compress4j.archive.compression;

import static io.github.compress4j.utils.FileUtils.DOS_HIDDEN;
import static io.github.compress4j.utils.FileUtils.DOS_READ_ONLY;

import io.github.compress4j.utils.PosixFilePermissionsMapper;
import io.github.compress4j.utils.StringUtil;
import io.github.compress4j.utils.SystemUtils;
import jakarta.annotation.Nullable;
import java.beans.Statement;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Map;
import java.util.function.BiPredicate;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Compressor<T extends ArchiveOutputStream<? extends ArchiveEntry>> implements Closeable {
    /** Compression-level for the archive file. Only values in [0-9] are allowed. */
    protected static final String COMPRESSION_LEVEL = "compression-level";

    private static final Logger LOGGER = LoggerFactory.getLogger(Compressor.class);
    private @Nullable BiPredicate<? super String, ? super Path> entryFilter;

    private static String entryName(String name) {
        StringUtils.stripEnd(name.replace('\\', '/'), "/");
        String entryName = StringUtil.trimLeading(StringUtil.trimTrailing(name.replace('\\', '/'), '/'), '/');
        if (entryName.isEmpty()) throw new IllegalArgumentException("Invalid entry name: " + name);
        return entryName;
    }

    private static long timestamp(long timestamp) {
        return timestamp == -1 ? System.currentTimeMillis() : timestamp;
    }

    private static int mode(Path file) throws IOException {
        if (SystemUtils.IS_OS_WINDOWS) {
            DosFileAttributeView attrs = Files.getFileAttributeView(file, DosFileAttributeView.class);
            if (attrs != null) {
                DosFileAttributes dosAttrs = attrs.readAttributes();
                int mode = 0;
                if (dosAttrs.isReadOnly()) mode |= DOS_READ_ONLY;
                if (dosAttrs.isHidden()) mode |= DOS_HIDDEN;
                return mode;
            }
        } else {
            PosixFileAttributeView attrs = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (attrs != null) {
                return PosixFilePermissionsMapper.toUnixMode(
                        attrs.readAttributes().permissions());
            }
        }
        return 0;
    }

    /**
     * Filtering entries being added to the archive. Please note that the second parameter of a filter ({@code Path})
     * <b>might be {@code null}</b> when it is applied to an entry not present on a disk - e.g., via
     * {@link #addFile(String, byte[])}.
     */
    public Compressor<T> filter(@Nullable BiPredicate<? super String, ? super Path> filter) {
        entryFilter = filter;
        return this;
    }

    public final void addFile(String entryName, Path file) throws IOException {
        addFile(entryName, file, -1);
    }

    public final void addFile(String entryName, Path file, long timestamp) throws IOException {
        entryName = entryName(entryName);
        if (accept(entryName, file)) {
            addFile(file, Files.readAttributes(file, BasicFileAttributes.class), entryName, timestamp);
        }
    }

    public final void addFile(String entryName, byte[] content) throws IOException {
        addFile(entryName, content, -1);
    }

    public final void addFile(String entryName, byte[] content, long timestamp) throws IOException {
        entryName = entryName(entryName);
        if (accept(entryName, null)) {
            writeFileEntry(entryName, new ByteArrayInputStream(content), content.length, timestamp(timestamp), 0, null);
        }
    }

    public final void addFile(String entryName, InputStream content) throws IOException {
        addFile(entryName, content, -1);
    }

    public final void addFile(String entryName, InputStream content, long timestamp) throws IOException {
        entryName = entryName(entryName);
        if (accept(entryName, null)) {
            writeFileEntry(entryName, content, -1, timestamp(timestamp), 0, null);
        }
    }

    public final void addDirectory(String entryName) throws IOException {
        addDirectory(entryName, -1);
    }

    public final void addDirectory(String entryName, long timestamp) throws IOException {
        entryName = entryName(entryName);
        if (accept(entryName, null)) {
            writeDirectoryEntry(entryName, timestamp(timestamp));
        }
    }

    public final void addDirectory(Path directory) throws IOException {
        addDirectory("", directory);
    }

    public final void addDirectory(String prefix, Path directory) throws IOException {
        addDirectory(prefix, directory, -1);
    }

    public final void addDirectory(String prefix, Path directory, long timestampInMillis) throws IOException {
        prefix = prefix.isEmpty() ? "" : entryName(prefix);
        addRecursively(prefix, directory, timestampInMillis);
    }

    private boolean accept(String entryName, @Nullable Path file) {
        return entryFilter == null || entryFilter.test(entryName, file);
    }

    private void addFile(Path file, BasicFileAttributes attrs, String name, long explicitTimestamp) throws IOException {
        try (InputStream source = Files.newInputStream(file)) {
            long timestamp = explicitTimestamp == -1 ? attrs.lastModifiedTime().toMillis() : explicitTimestamp;
            String symlinkTarget =
                    attrs.isSymbolicLink() ? Files.readSymbolicLink(file).toString() : null;
            writeFileEntry(name, source, attrs.size(), timestamp, mode(file), symlinkTarget);
        }
    }

    private void addRecursively(String prefix, Path root, long timestampMs) throws IOException {
        LOGGER.atTrace().log("dir={} prefix={}", root, prefix);

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = dir == root ? prefix : entryName(dir);
                if (name.isEmpty()) {
                    return FileVisitResult.CONTINUE;
                } else if (accept(name, dir)) {
                    LOGGER.atTrace().log("  {} -> {}/", dir, name);
                    writeDirectoryEntry(
                            name, timestampMs == -1 ? attrs.lastModifiedTime().toMillis() : timestampMs);
                    return FileVisitResult.CONTINUE;
                } else {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = entryName(file);
                if (accept(name, file)) {
                    LOGGER.atTrace()
                            .log(
                                    "  {} -> {}{}",
                                    file,
                                    name,
                                    attrs.isSymbolicLink() ? " symlink" : " size=" + attrs.size());
                    addFile(file, attrs, name, timestampMs);
                }
                return FileVisitResult.CONTINUE;
            }

            private String entryName(Path fileOrDir) {
                String relativeName =
                        Compressor.entryName(root.relativize(fileOrDir).toString());
                return prefix.isEmpty() ? relativeName : prefix + '/' + relativeName;
            }
        });

        LOGGER.atTrace().log(".");
    }

    /**
     * Apply options to archive output stream
     *
     * @param stream stream to apply options to
     * @param options options map
     * @return stream with option applied
     * @throws IOException if an IO error occurred
     */
    protected T applyFormatOptions(T stream, Map<String, Object> options) throws IOException {
        for (Map.Entry<String, Object> option : options.entrySet()) {
            try {
                if (option.getKey().equals(COMPRESSION_LEVEL)) {
                    continue;
                }
                new Statement(stream, "set" + StringUtils.capitalize(option.getKey()), new Object[] {option.getValue()})
                        .execute();
            } catch (Exception e) {
                throw new IOException("Cannot set option " + option.getKey(), e);
            }
        }
        return stream;
    }

    /**
     * Removes and returns the {@link #COMPRESSION_LEVEL} key from the input map parameter if it exists, or -1 if this
     * key does not exist.
     *
     * @param o options map
     * @return The compression level if it exists in the map, or -1 instead.
     * @throws IllegalArgumentException if the {@link #COMPRESSION_LEVEL} option does not parse to an Integer.
     */
    protected int getCompressionLevel(Map<String, Object> o) {
        if (!o.containsKey(COMPRESSION_LEVEL)) {
            return -1;
        }
        Object option = o.get(COMPRESSION_LEVEL);
        try {
            return (Integer) option;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Cannot set compression level " + option, e);
        }
    }

    protected abstract void writeDirectoryEntry(String name, long timestamp) throws IOException;

    protected abstract void writeFileEntry(String name, InputStream source, long length, long timestamp, int mode)
            throws IOException;

    protected abstract void writeFileEntry(
            String name, InputStream source, long length, long timestamp, int mode, String symlinkTarget)
            throws IOException;

    /**
     * Start a new archive. Entries can be included in the archive using the putEntry method, and then the archive
     * should be closed using its close method.
     *
     * @param s underlying output stream to which to write the archive.
     * @return new archive object for use in putEntry
     * @throws IOException thrown by the underlying output stream for I/O errors
     */
    protected abstract T createArchiveOutputStream(OutputStream s) throws IOException;

    /**
     * Start a new archive. Entries can be included in the archive using the putEntry method, and then the archive
     * should be closed using its close method. In addition options can be applied to the underlying stream. E.g.
     * compression level.
     *
     * @param s underlying output stream to which to write the archive.
     * @param o options to apply to the underlying output stream. Keys are option names and values are option values.
     * @return new archive object for use in putEntry
     * @throws IOException thrown by the underlying output stream for I/O errors
     */
    protected abstract T createArchiveOutputStream(OutputStream s, Map<String, Object> o) throws IOException;
}
