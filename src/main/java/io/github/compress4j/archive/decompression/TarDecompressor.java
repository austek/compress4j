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

import io.github.compress4j.utils.SystemUtils;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

/**
 * The Tar decompressor automatically detects the compression of an input file/stream.
 *
 * <p><b>NOTE</b>: requires {@code commons-compress} and {@code commons-io} libraries to be on the classpath.
 */
public final class TarDecompressor extends Decompressor {
    private final Object mySource;
    private TarArchiveInputStream myStream;

    public TarDecompressor(Path file) {
        mySource = file;
    }

    public TarDecompressor(InputStream stream) {
        mySource = stream;
    }

    private static Entry.Type type(TarArchiveEntry te) {
        return te.isSymbolicLink() ? Entry.Type.SYMLINK : te.isDirectory() ? Entry.Type.DIR : Entry.Type.FILE;
    }

    @Override
    protected void openStream() throws IOException {
        InputStream input = new BufferedInputStream(
                mySource instanceof Path path ? Files.newInputStream(path) : (InputStream) mySource);
        try {
            input = new CompressorStreamFactory().createCompressorInputStream(input);
        } catch (CompressorException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) throw ioException;
        }
        myStream = new TarArchiveInputStream(input);
    }

    @Override
    protected Entry nextEntry() throws IOException {
        TarArchiveEntry te;
        while ((te = myStream.getNextEntry()) != null
                && !((te.isFile() && !te.isLink()) // ignore hardlink
                        || te.isDirectory()
                        || te.isSymbolicLink())) /* skipping unsupported */
            ;
        if (te == null) return null;
        if (!SystemUtils.IS_OS_WINDOWS)
            return new Entry(te.getName(), type(te), te.getMode(), te.getLinkName(), te.getSize());
        // UNIX permissions are ignored on Windows
        if (te.isSymbolicLink()) return new Entry(te.getName(), Entry.Type.SYMLINK, 0, te.getLinkName(), te.getSize());
        return new Entry(te.getName(), te.isDirectory(), te.getSize());
    }

    @Override
    protected InputStream openEntryStream(Entry entry) {
        return myStream;
    }

    @Override
    protected void closeEntryStream(InputStream stream) {
        // no-op
    }

    @Override
    protected void closeStream() throws IOException {
        if (mySource instanceof Path) {
            myStream.close();
        }
    }
}
