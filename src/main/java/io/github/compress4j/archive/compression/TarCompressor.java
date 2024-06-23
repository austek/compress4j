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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

public class TarCompressor extends Compressor<TarArchiveOutputStream> {
    private final TarArchiveOutputStream myStream;

    public TarCompressor(Path file) throws IOException {
        this(Files.newOutputStream(file));
    }

    public TarCompressor(Path file, Map<String, Object> options) throws IOException {
        this(Files.newOutputStream(file), options);
    }

    public TarCompressor(OutputStream stream) throws IOException {
        this(stream, Collections.emptyMap());
    }

    public TarCompressor(OutputStream stream, Map<String, Object> options) throws IOException {
        myStream = createArchiveOutputStream(stream, options);
    }

    @Override
    protected void writeDirectoryEntry(String name, long timestamp) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name + '/');
        e.setModTime(timestamp);
        myStream.putArchiveEntry(e);
        myStream.closeArchiveEntry();
    }

    @Override
    protected void writeFileEntry(String name, InputStream source, long length, long timestamp, int mode)
            throws IOException {
        writeFileEntry(name, source, length, timestamp, mode, Optional.empty());
    }

    @Override
    protected void writeFileEntry(
            String name, InputStream source, long length, long timestamp, int mode, String symlinkTarget)
            throws IOException {
        writeFileEntry(name, source, length, timestamp, mode, Optional.of(symlinkTarget));
    }

    private void writeFileEntry(
            String name,
            InputStream source,
            long length,
            long timestamp,
            int mode,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<String> symlinkTarget)
            throws IOException {
        TarArchiveEntry e = symlinkTarget
                .map(link -> {
                    var entry = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);
                    entry.setSize(0);
                    entry.setLinkName(link);
                    return entry;
                })
                .orElseGet(() -> new TarArchiveEntry(name));
        if (length < 0) {
            length = source.available();
        }
        e.setSize(length);
        e.setModTime(timestamp);
        if (mode != 0) {
            e.setMode(mode);
        }
        myStream.putArchiveEntry(e);
        if (length > 0) {
            source.transferTo(myStream);
        }
        myStream.closeArchiveEntry();
    }

    @Override
    protected TarArchiveOutputStream createArchiveOutputStream(OutputStream s) throws IOException {
        return createArchiveOutputStream(s, Collections.emptyMap());
    }

    @Override
    protected TarArchiveOutputStream createArchiveOutputStream(OutputStream stream, Map<String, Object> options)
            throws IOException {
        TarArchiveOutputStream out = new TarArchiveOutputStream(stream, UTF_8.name());
        out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        out.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        return applyFormatOptions(out, options);
    }

    @Override
    public void close() throws IOException {
        myStream.close();
    }
}
