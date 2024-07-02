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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.util.Objects;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * Implementation of an {@link Archiver} that uses {@link ArchiveStreamFactory} to generate archive streams by a given
 * archiver name passed when creating the {@code GenericArchiver}. Thus, it can be used for all archive formats the
 * {@code org.apache.commons.compress} library supports.
 */
class CommonsArchiver<E extends ArchiveEntry> implements Archiver {

    private final ArchiveFormat archiveFormat;

    CommonsArchiver(ArchiveFormat archiveFormat) {
        this.archiveFormat = archiveFormat;
    }

    private static String getRelativePath(File parent, File source) {
        return parent.toPath().toUri().relativize(source.toPath().toUri()).getPath();
    }

    public ArchiveFormat getArchiveFormat() {
        return archiveFormat;
    }

    @Override
    public File create(String archive, File destination, File source) throws IOException {
        return create(archive, destination, IOUtils.filesContainedIn(source));
    }

    @Override
    public File create(String archive, File destination, File... sources) throws IOException {

        IOUtils.requireDirectory(destination);

        File archiveFile = createNewArchiveFile(archive, getFilenameExtension(), destination);

        try (ArchiveOutputStream<E> outputStream = createArchiveOutputStream(archiveFile)) {
            writeToArchive(sources, outputStream);
            outputStream.flush();
        }

        return archiveFile;
    }

    @Override
    public void extract(File archive, File destination, CopyOption... options) throws IOException {
        assertExtractSource(archive);

        IOUtils.requireDirectory(destination);

        try (ArchiveInputStream<?> input = createArchiveInputStream(archive)) {
            extract(input, destination, options);
        }
    }

    @Override
    public void extract(InputStream archive, File destination, CopyOption... options) throws IOException {
        extract(createArchiveInputStream(archive), destination, options);
    }

    private <T extends ArchiveEntry> void extract(ArchiveInputStream<T> input, File destination, CopyOption... options)
            throws IOException {
        T entry;
        while ((entry = input.getNextEntry()) != null) {
            IOUtils.copy(input, destination, entry, options);
        }
    }

    @Override
    public ArchiveStream stream(File archive) throws IOException {
        return new CommonsArchiveStream<>(createArchiveInputStream(archive));
    }

    @Override
    public String getFilenameExtension() {
        return getArchiveFormat().getDefaultFileExtension();
    }

    /**
     * Returns a new ArchiveInputStream for reading archives. Subclasses can override this to return their own custom
     * implementation.
     *
     * @param archive the archive file to stream from
     * @return a new ArchiveInputStream for the given archive file
     * @throws IOException propagated IO exceptions
     */
    protected ArchiveInputStream<E> createArchiveInputStream(File archive) throws IOException {
        try {
            return CommonsStreamFactory.createArchiveInputStream(archive);
        } catch (ArchiveException e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns a new ArchiveInputStream for reading archives. Subclasses can override this to return their own custom
     * implementation.
     *
     * @param archive the archive contents to stream from
     * @return a new ArchiveInputStream for the given archive file
     * @throws IOException propagated IO exceptions
     */
    protected ArchiveInputStream<E> createArchiveInputStream(InputStream archive) throws IOException {
        try {
            return CommonsStreamFactory.createArchiveInputStream(archive);
        } catch (ArchiveException e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns a new ArchiveOutputStream for creating archives. Subclasses can override this to return their own custom
     * implementation.
     *
     * @param archiveFile the archive file to stream to
     * @return a new ArchiveOutputStream for the given archive file.
     * @throws IOException propagated IO exceptions
     */
    protected ArchiveOutputStream<E> createArchiveOutputStream(File archiveFile) throws IOException {
        try {
            ArchiveOutputStream<E> archiveOutputStream =
                    CommonsStreamFactory.createArchiveOutputStream(this, archiveFile);

            if (archiveOutputStream instanceof TarArchiveOutputStream tarArchiveOutputStream) {
                (tarArchiveOutputStream).setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            }

            return archiveOutputStream;
        } catch (ArchiveException e) {
            throw new IOException(e);
        }
    }

    /**
     * Asserts that the given File object is a readable file that can be used to extract from.
     *
     * @param archive the file to check
     * @throws FileNotFoundException if the file does not exist
     * @throws IllegalArgumentException if the file is a directory or not readable
     */
    protected void assertExtractSource(File archive) throws FileNotFoundException, IllegalArgumentException {
        if (archive.isDirectory()) {
            throw new IllegalArgumentException("Can not extract " + archive + ". Source is a directory.");
        } else if (!archive.exists()) {
            throw new FileNotFoundException(archive.getPath());
        } else if (!archive.canRead()) {
            throw new IllegalArgumentException("Can not extract " + archive + ". Can not read from source.");
        }
    }

    /**
     * Creates a new File in the given destination. The resulting name will always be "archive"."fileExtension". If the
     * archive name parameter already ends with the given file name extension, it is not additionally appended.
     *
     * @param archive the name of the archive
     * @param extension the file extension (e.g. ".tar")
     * @param destination the parent path
     * @return the newly created file
     * @throws IOException if an I/O error occurred while creating the file
     */
    @SuppressWarnings("java:S899")
    protected File createNewArchiveFile(String archive, String extension, File destination) throws IOException {
        if (!archive.endsWith(extension)) {
            archive += extension;
        }

        File file = new File(destination, archive);
        //noinspection ResultOfMethodCallIgnored
        file.createNewFile();

        return file;
    }

    /**
     * Recursion entry point for {@link #writeToArchive(File, File[], ArchiveOutputStream)}. <br>
     * Recursively writes all given source {@link File}s into the given {@link ArchiveOutputStream}.
     *
     * @param sources the files to write in to the archive
     * @param archive the archive to write into
     * @throws IOException when an I/O error occurs
     */
    protected void writeToArchive(File[] sources, ArchiveOutputStream<E> archive) throws IOException {
        for (File source : sources) {
            if (!source.exists()) {
                throw new FileNotFoundException(source.getPath());
            } else if (!source.canRead()) {
                throw new FileNotFoundException(source.getPath() + " (Permission denied)");
            }

            writeToArchive(source.getParentFile(), new File[] {source}, archive);
        }
    }

    /**
     * Recursively writes all given source {@link File}s into the given {@link ArchiveOutputStream}. The paths of the
     * sources in the archive will be relative to the given parent {@code File}.
     *
     * @param parent the parent file node for computing a relative path
     * @param sources the files to write in to the archive
     * @param archive the archive to write into
     * @throws IOException when an I/O error occurs
     */
    protected void writeToArchive(File parent, File[] sources, ArchiveOutputStream<E> archive) throws IOException {
        for (File source : sources) {
            String relativePath = getRelativePath(parent, source);

            createArchiveEntry(source, relativePath, archive);

            if (source.isDirectory()) {
                writeToArchive(parent, Objects.requireNonNull(source.listFiles()), archive);
            }
        }
    }

    /**
     * Creates a new {@link ArchiveEntry} in the given {@link ArchiveOutputStream}, and copies the given {@link File}
     * into the new entry.
     *
     * @param file the file to add to the archive
     * @param entryName the name of the archive entry
     * @param archive the archive to write to
     * @throws IOException when an I/O error occurs during FileInputStream creation or during copying
     */
    protected void createArchiveEntry(File file, String entryName, ArchiveOutputStream<E> archive) throws IOException {
        E entry = archive.createArchiveEntry(file, entryName);
        // TODO #23: read permission from file, write it to the ArchiveEntry
        archive.putArchiveEntry(entry);

        if (!entry.isDirectory()) {
            try (FileInputStream input = new FileInputStream(file)) {
                input.transferTo(archive);
            }
        }

        archive.closeArchiveEntry();
    }
}
