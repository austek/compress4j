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
import java.nio.file.CopyOption;
import java.util.Date;

/** Implementation of an {@link ArchiveEntry} that wraps the commons compress version of the same type. */
class CommonsArchiveEntry implements ArchiveEntry {

    /** The wrapped {@code ArchiveEntry} entry. */
    private final org.apache.commons.compress.archivers.ArchiveEntry entry;

    /** The {@link ArchiveStream} this entry belongs to. */
    private final ArchiveStream stream;

    CommonsArchiveEntry(ArchiveStream stream, org.apache.commons.compress.archivers.ArchiveEntry entry) {
        this.stream = stream;
        this.entry = entry;
    }

    @Override
    public String getName() {
        assertState();
        return entry.getName();
    }

    @Override
    public long getSize() {
        assertState();
        return entry.getSize();
    }

    @Override
    public Date getLastModifiedDate() {
        assertState();
        return entry.getLastModifiedDate();
    }

    @Override
    public boolean isDirectory() {
        assertState();
        return entry.isDirectory();
    }

    @Override
    public File extract(File destination, CopyOption... options)
            throws IOException, IllegalStateException, IllegalArgumentException {
        assertState();
        return IOUtils.copy(stream, destination, entry, options);
    }

    private void assertState() {
        if (stream.isClosed()) {
            throw new IllegalStateException("Stream has already been closed");
        }
        if (this != stream.getCurrentEntry()) {
            throw new IllegalStateException("Illegal stream pointer");
        }
    }
}
