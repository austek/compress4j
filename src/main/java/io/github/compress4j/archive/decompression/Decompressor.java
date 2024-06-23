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
package io.github.compress4j.archive.decompression;

import io.github.compress4j.utils.PosixFilePermissionsMapper;
import io.github.compress4j.utils.StringUtil;
import io.github.compress4j.utils.SystemUtils;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Decompressor {
    private static final Logger LOGGER = LoggerFactory.getLogger(Decompressor.class);

    public static final class Entry {
        public enum Type {
            FILE,
            DIR,
            SYMLINK
        }

        public static final int DOS_READ_ONLY = 0b01;
        public static final int DOS_HIDDEN = 0b010;

        /** An entry name with separators converted to '/' and trimmed; handle with care */
        public final String name;

        public final Type type;
        /** Depending on the source, could be POSIX permissions, DOS attributes, or just {@code 0} */
        public final int mode;

        public final long size;
        public final @Nullable String linkTarget;

        Entry(String name, boolean isDirectory, long size) {
            this(name, isDirectory ? Type.DIR : Type.FILE, 0, null, size);
        }

        Entry(String name, Type type, int mode, @Nullable String linkTarget, long size) {
            name = name.trim().replace('\\', '/');
            int s = 0;
            int e = name.length() - 1;
            while (s < e && name.charAt(s) == '/') s++;
            while (e >= s && name.charAt(e) == '/') e--;
            this.name = name.substring(s, e + 1);
            this.type = type;
            this.mode = mode;
            this.linkTarget = linkTarget;
            this.size = size;
        }
    }

    /**
     * Policy for handling symbolic links which point to outside of archive.
     *
     * <p>Example: {@code foo -> /opt/foo}
     *
     * <p>or {@code foo -> ../foo}
     */
    public enum EscapingSymlinkPolicy {
        /**
         * Extract as is with no modification or check. Potentially can point to a completely different object if the
         * archive is transferred from some other host.
         */
        ALLOW,

        /** Check during extraction and throw exception. See {@link Decompressor#verifySymlinkTarget} */
        DISALLOW,

        /**
         * Make absolute symbolic links relative from the extraction directory. For example, when archive contains link
         * to {@code /opt/foo} and archive is extracted to {@code /foo/bar} then the resulting link will be
         * {@code /foo/bar/opt/foo}
         */
        RELATIVIZE_ABSOLUTE
    }

    private @Nullable Predicate<? super Entry> myFilter = null;
    private BiFunction<? super Entry, ? super IOException, ErrorHandlerChoice> myErrorHandler =
            (x, y) -> ErrorHandlerChoice.BAIL_OUT;
    private boolean myIgnoreIOExceptions = false;
    private @Nullable List<String> myPathPrefix = null;
    private boolean myOverwrite = true;
    private EscapingSymlinkPolicy myEscapingSymlinkPolicy = EscapingSymlinkPolicy.ALLOW;
    private BiConsumer<? super Entry, ? super Path> myPostProcessor;

    @SuppressWarnings("java:S3358")
    public Decompressor filter(@Nullable Predicate<? super String> filter) {
        myFilter = filter != null ? e -> filter.test(e.type == Entry.Type.DIR ? e.name + '/' : e.name) : null;
        return this;
    }

    public Decompressor entryFilter(@Nullable Predicate<? super Entry> filter) {
        myFilter = filter;
        return this;
    }

    public Decompressor errorHandler(BiFunction<? super Entry, ? super IOException, ErrorHandlerChoice> errorHandler) {
        myErrorHandler = errorHandler;
        return this;
    }

    public Decompressor overwrite(boolean overwrite) {
        myOverwrite = overwrite;
        return this;
    }

    public Decompressor escapingSymlinkPolicy(EscapingSymlinkPolicy policy) {
        myEscapingSymlinkPolicy = policy;
        return this;
    }

    public Decompressor postProcessor(@Nullable Consumer<? super Path> consumer) {
        myPostProcessor = consumer != null ? (entry, path) -> consumer.accept(path) : null;
        return this;
    }

    public Decompressor postProcessor(@Nullable BiConsumer<? super Entry, ? super Path> consumer) {
        myPostProcessor = consumer;
        return this;
    }

    /**
     * Extracts only items whose path starts with the normalized prefix of {@code prefix + '/'}. Paths are normalized
     * before comparison. The prefix test is applied after {@link #filter} predicate is tested. Some entries may clash,
     * so use {@link #overwrite} to control it. Some items with a path that does not start from the prefix could be
     * ignored.
     *
     * @param prefix a prefix to remove from every archive entry path
     * @return self
     */
    public Decompressor removePrefixPath(@Nullable String prefix) throws IOException {
        myPathPrefix = prefix != null ? normalizePathAndSplit(prefix) : null;
        return this;
    }

    public final void extract(Path outputDir) throws IOException {
        openStream();
        try {
            Deque<Path> extractedPaths = new ArrayDeque<>();

            // we'd like to keep a contact to invoke filter once per entry
            // since it was something implicit, and the introduction of
            // retry breaks the contract
            boolean proceedToNext = true;

            Entry entry = null;
            while (!proceedToNext || (entry = nextEntry()) != null) {
                if (proceedToNext && myFilter != null && !myFilter.test(entry)) {
                    continue;
                }

                proceedToNext = true; // will be set to false if EH returns RETRY
                try {
                    Path processedEntry = processEntry(outputDir, entry);
                    if (processedEntry != null) {
                        extractedPaths.push(processedEntry);
                    }
                } catch (IOException ioException) {
                    if (myIgnoreIOExceptions) {
                        LOGGER.debug(
                                "Skipped exception because {} was selected earlier",
                                ErrorHandlerChoice.SKIP_ALL,
                                ioException);
                    } else {
                        switch (myErrorHandler.apply(entry, ioException)) {
                            case ABORT:
                                while (!extractedPaths.isEmpty()) {
                                    Files.delete(extractedPaths.pop());
                                }
                                return;
                            case BAIL_OUT:
                                throw ioException;
                            case RETRY:
                                proceedToNext = false;
                                break;
                            case SKIP:
                                LOGGER.debug("Skipped exception", ioException);
                                break;
                            case SKIP_ALL:
                                myIgnoreIOExceptions = true;
                                LOGGER.debug("SKIP_ALL is selected", ioException);
                        }
                    }
                }
            }
        } finally {
            closeStream();
        }
    }

    /** @return Path to an extracted entity */
    private @Nullable Path processEntry(Path outputDir, Entry entry) throws IOException {
        if (myPathPrefix != null) {
            entry = mapPathPrefix(entry, myPathPrefix);
            if (entry == null) return null;
        }

        Path outputFile = entryFile(outputDir, entry.name);
        switch (entry.type) {
            case DIR:
                //noinspection ResultOfMethodCallIgnored
                outputFile.toFile().mkdirs();
                break;

            case FILE:
                if (myOverwrite || !Files.exists(outputFile)) {
                    InputStream inputStream = openEntryStream(entry);
                    try {
                        //noinspection ResultOfMethodCallIgnored
                        outputFile.getParent().toFile().mkdirs();
                        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
                            inputStream.transferTo(outputStream);
                        }
                        if (entry.mode != 0) {
                            setAttributes(entry.mode, outputFile);
                        }
                    } finally {
                        closeEntryStream(inputStream);
                    }
                }
                break;

            case SYMLINK:
                if (entry.linkTarget == null || entry.linkTarget.isEmpty()) {
                    throw new IOException("Invalid symlink entry: " + entry.name + " (empty target)");
                }

                String target = entry.linkTarget;

                switch (myEscapingSymlinkPolicy) {
                    case DISALLOW: {
                        verifySymlinkTarget(entry.name, entry.linkTarget, outputDir, outputFile);
                        break;
                    }
                    case RELATIVIZE_ABSOLUTE: {
                        if (Paths.get(target).isAbsolute()) {
                            target = Paths.get(outputDir.toString(), entry.linkTarget.substring(1))
                                    .toString();
                        }
                        break;
                    }
                    case ALLOW:
                        break;
                }

                if (myOverwrite || !Files.exists(outputFile, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        Path outputTarget = Paths.get(target);
                        //noinspection ResultOfMethodCallIgnored
                        outputFile.getParent().toFile().mkdirs();
                        Files.deleteIfExists(outputFile);
                        Files.createSymbolicLink(outputFile, outputTarget);
                    } catch (InvalidPathException e) {
                        throw new IOException("Invalid symlink entry: " + entry.name + " -> " + target, e);
                    }
                }
                break;
        }

        if (myPostProcessor != null) {
            myPostProcessor.accept(entry, outputFile);
        }

        return outputFile;
    }

    private static void verifySymlinkTarget(String entryName, String linkTarget, Path outputDir, Path outputFile)
            throws IOException {
        try {
            Path outputTarget = Paths.get(linkTarget);
            if (outputTarget.isAbsolute()) {
                throw new IOException("Invalid symlink (absolute path): " + entryName + " -> " + linkTarget);
            }
            Path linkTargetNormalized =
                    outputFile.getParent().resolve(outputTarget).normalize();
            if (!linkTargetNormalized.startsWith(outputDir.normalize())) {
                throw new IOException(
                        "Invalid symlink (points outside of output directory): " + entryName + " -> " + linkTarget);
            }
        } catch (InvalidPathException e) {
            throw new IOException("Failed to verify symlink entry scope: " + entryName + " -> " + linkTarget, e);
        }
    }

    private static @Nullable Entry mapPathPrefix(Entry e, List<String> prefix) throws IOException {
        List<String> ourPathSplit = normalizePathAndSplit(e.name);
        if (prefix.size() >= ourPathSplit.size()
                || !ourPathSplit.subList(0, prefix.size()).equals(prefix)) {
            return null;
        }
        String newName = String.join("/", ourPathSplit.subList(prefix.size(), ourPathSplit.size()));
        return new Entry(newName, e.type, e.mode, e.linkTarget, e.size);
    }

    private static List<String> normalizePathAndSplit(String path) throws IOException {
        ensureValidPath(path);
        String canonicalPath = Paths.get(path).toFile().getCanonicalPath();
        return Arrays.asList(StringUtil.trimLeading(canonicalPath, '/').split("/"));
    }

    private static void setAttributes(int mode, Path outputFile) throws IOException {
        if (SystemUtils.IS_OS_WINDOWS) {
            DosFileAttributeView attrs = Files.getFileAttributeView(outputFile, DosFileAttributeView.class);
            if (attrs != null) {
                if ((mode & Entry.DOS_READ_ONLY) != 0) attrs.setReadOnly(true);
                if ((mode & Entry.DOS_HIDDEN) != 0) attrs.setHidden(true);
            }
        } else {
            PosixFileAttributeView attrs = Files.getFileAttributeView(outputFile, PosixFileAttributeView.class);
            if (attrs != null) {
                attrs.setPermissions(PosixFilePermissionsMapper.fromUnixMode(mode));
            }
        }
    }

    // <editor-fold desc="Internal interface">
    protected Decompressor() {}

    protected abstract void openStream() throws IOException;

    protected abstract @Nullable Entry nextEntry() throws IOException;

    protected abstract InputStream openEntryStream(Entry entry) throws IOException;

    protected abstract void closeEntryStream(InputStream stream) throws IOException;

    protected abstract void closeStream() throws IOException;
    // </editor-fold>

    private static void ensureValidPath(String entryName) throws IOException {
        if (entryName.contains("..")
                && Arrays.asList(entryName.split("[/\\\\]")).contains("..")) {
            throw new IOException("Invalid entry name: " + entryName);
        }
    }

    public static Path entryFile(Path outputDir, String entryName) throws IOException {
        ensureValidPath(entryName);
        return outputDir.resolve(StringUtil.trimLeading(entryName, '/'));
    }

    /** Specifies action to be taken from the {@code com.intellij.util.io.Decompressor#errorHandler} */
    public enum ErrorHandlerChoice {
        /** Extraction should be aborted and already extracted entities should be cleaned */
        ABORT,

        /** Do not handle error, just rethrow the exception */
        BAIL_OUT,

        /** Retry failed entry extraction */
        RETRY,

        /** Skip this entry from extraction */
        SKIP,

        /** Skip this entry for extraction and ignore any further IOExceptions during this archive extraction */
        SKIP_ALL
    }
}
