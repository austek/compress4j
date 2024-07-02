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
package io.github.compress4j.archivers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.compress.archivers.ArchiveEntry;

/** Utility class for I/O operations. */
public final class IOUtils {

    private IOUtils() {}

    /**
     * Copies all bytes from an input stream to a file. On return, the input stream will be at end of stream.
     *
     * <p>By default, the copy fails if the target file already exists or is a symbolic link.
     *
     * <p>If an I/O error occurs reading from the input stream or writing to the file, then it may do so after the
     * target file has been created and after some bytes have been read or written. Consequently, the input stream may
     * not be at end of stream and may be in an inconsistent state. It is strongly recommended that the input stream be
     * promptly closed if an I/O error occurs.
     *
     * <p>This method may block indefinitely reading from the input stream (or writing to the file). The behavior for
     * the case that the input stream is <i>asynchronously closed</i> or the thread interrupted during the copy is
     * highly input stream and file system provider specific and therefore not specified.
     *
     * @param in the input stream to read from
     * @param destination the directory to copy the file to
     * @param entry the path to the file
     * @param options options specifying how the copy should be done
     * @return the number of bytes read or written
     * @param <A> ArchiveEntry to be used
     * @throws IOException if an I/O error occurs when reading or writing
     * @throws FileAlreadyExistsException if the target file exists but cannot be replaced because the
     *     {@code REPLACE_EXISTING} option is not specified <i>(optional specific exception)</i>
     * @throws DirectoryNotEmptyException the {@code REPLACE_EXISTING} option is specified but the file cannot be
     *     replaced because it is a non-empty directory <i>(optional specific exception)</i> *
     * @throws UnsupportedOperationException if {@code options} contains a copy option that is not supported
     */
    public static <A extends ArchiveEntry> File copy(InputStream in, File destination, A entry, CopyOption... options)
            throws IOException {
        File file = createResourceInDestination(destination, entry.getName());

        if (entry.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
        } else {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            Files.copy(in, file.toPath(), options);
        }

        FileModeMapper.map(entry, file);

        return file;
    }

    /**
     * Given a source File, return its direct descendants if the File is a directory. Otherwise, return the File itself.
     *
     * @param source File or folder to be examined
     * @return a File[] array containing the files inside this folder, or a size-1 array containing the file itself.
     */
    public static File[] filesContainedIn(File source) {
        if (source.isDirectory()) {
            return source.listFiles();
        } else {
            return new File[] {source};
        }
    }

    /**
     * Makes sure that the given {@link File} is either a writable directory, or that it does not exist and a directory
     * can be created at its path. <br>
     * Will throw an exception if the given {@link File} is actually an existing file, or the directory is not writable
     *
     * @param destination the directory which to ensure its existence for
     * @throws IllegalArgumentException if the destination is an existing file, or the directory is not writable
     */
    public static void requireDirectory(File destination) throws IllegalArgumentException {
        if (destination.isFile()) {
            throw new IllegalArgumentException(destination + " exists and is a file, directory or path expected.");
        } else if (!destination.exists()) {
            //noinspection ResultOfMethodCallIgnored
            destination.mkdirs();
        }
        if (!destination.canWrite()) {
            throw new IllegalArgumentException("Can not write to destination " + destination);
        }
    }
    /**
     * Returns a resource after guaranteeing that it is created inside the destination directory
     *
     * @param destination the destination directory to place the resource in
     * @param entryName the name of the resource to create in the destination
     * @return the created resource after it is placed in the destination directory
     * @throws IOException if an I/O error occurs when creating the resource
     */
    public static File createResourceInDestination(File destination, String entryName) throws IOException {
        return createResourceInDestination(destination, entryName, destination.getCanonicalPath());
    }

    /**
     * Returns a resource after guaranteeing that it is created inside the destination directory
     *
     * @param destination the destination directory to place the resource in
     * @param entryName the name of the resource to create in the destination
     * @param destinationCanonicalPath the canonical path of the destination
     * @return the created resource after it is placed in the destination directory
     * @throws IOException if an I/O error occurs when creating the resource
     */
    public static File createResourceInDestination(File destination, String entryName, String destinationCanonicalPath)
            throws IOException {
        File file = new File(destination, entryName);
        if (!file.getCanonicalPath().startsWith(destinationCanonicalPath)) {
            file = new File(destination, cleanEntryName(entryName));
        }
        return file;
    }

    /**
     * Cleans up a path by normalizing it and removing any leading.
     *
     * @param entry a file path entry to clean
     * @return the cleaned path
     */
    public static String cleanEntryName(String entry) {
        Iterable<Path> iterable = Paths.get(entry).normalize();
        String list = StreamSupport.stream(iterable.spliterator(), false)
                .map(Path::toString)
                .filter(string -> !"..".equals(string))
                .collect(Collectors.joining("/"));
        return Paths.get(list).toString();
    }
}
