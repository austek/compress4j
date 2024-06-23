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

import io.github.compress4j.utils.PosixFilePermissionsMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads *nix file mode flags of commons-compress ArchiveEntry (where possible) and maps them onto Files on the file
 * system.
 */
abstract class FileModeMapper<E extends ArchiveEntry> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileModeMapper.class.getCanonicalName());

    private final E archiveEntry;

    protected FileModeMapper(E archiveEntry) {
        this.archiveEntry = archiveEntry;
    }

    public abstract void map(File file) throws IOException;

    public E getArchiveEntry() {
        return archiveEntry;
    }

    /**
     * Utility method to create a FileModeMapper for the given entry, and use it to map the file mode onto the given
     * file.
     *
     * @param entry the archive entry that holds the mode
     * @param file the file to apply the mode onto
     */
    public static <T extends ArchiveEntry> void map(T entry, File file) throws IOException {
        create(FileSystems.getDefault(), entry).map(file);
    }

    /**
     * Factory method for creating a FileModeMapper for the given ArchiveEntry. Unknown types will yield a
     * FallbackFileModeMapper that discretely does nothing.
     *
     * @param entry the archive entry for which to create a FileModeMapper for
     * @return a new FileModeMapper instance
     */
    public static <T extends ArchiveEntry> FileModeMapper<T> create(FileSystem fileSystem, T entry) {
        if (fileSystem.supportedFileAttributeViews().contains("posix")) {
            return new PosixPermissionMapper<>(entry);
        }

        // TODO: implement basic windows permission mapping (e.g. with File.setX or attrib)
        return new FallbackFileModeMapper<>(entry);
    }

    /** Does nothing! */
    public static class FallbackFileModeMapper<F extends ArchiveEntry> extends FileModeMapper<F> {

        public FallbackFileModeMapper(F archiveEntry) {
            super(archiveEntry);
        }

        @Override
        public void map(File file) {
            // do nothing
        }
    }

    /**
     * Uses an AttributeAccessor to extract the posix file permissions from the ArchiveEntry and sets them on the given
     * file.
     */
    @SuppressWarnings("OctalInteger")
    public static class PosixPermissionMapper<P extends ArchiveEntry> extends FileModeMapper<P> {
        public static final int UNIX_PERMISSION_MASK = 0777;

        public PosixPermissionMapper(P archiveEntry) {
            super(archiveEntry);
        }

        @Override
        public void map(File file) throws IOException {
            int mode = getMode() & UNIX_PERMISSION_MASK;

            if (mode > 0) {
                setPermissions(mode, file);
            }
        }

        public int getMode() throws IOException {
            return AttributeAccessor.create(getArchiveEntry()).getMode();
        }

        private void setPermissions(int mode, File file) {
            try {
                Set<PosixFilePermission> posixFilePermissions = PosixFilePermissionsMapper.fromUnixMode(mode);
                Files.setPosixFilePermissions(file.toPath(), posixFilePermissions);
            } catch (Exception e) {
                LOGGER.warn("Could not set file permissions of {}", file.getName(), e);
            }
        }
    }
}
