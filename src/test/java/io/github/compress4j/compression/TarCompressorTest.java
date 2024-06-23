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
package io.github.compress4j.compression;

import static io.github.compress4j.utils.FileUtils.write;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.compress4j.archive.compression.TarCompressor;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarCompressorTest {

    @Test
    void simpleTar(@TempDir Path tempDir) throws IOException {
        var tar = tempDir.resolve("test.tar");
        var data = tempDir.resolve("file.txt");
        write(data.toFile(), "789");
        try (var compressor = new TarCompressor(tar)) {
            compressor.addFile("empty.txt", new byte[0]);
            compressor.addFile("file1.txt", "123".getBytes());
            compressor.addFile("file2.txt", "456".getBytes());
            compressor.addFile("file3.txt", data);
        }

        assertTar(tar, Map.of("empty.txt", "", "file1.txt", "123", "file2.txt", "456", "file3.txt", "789"));
    }

    private void assertTar(Path tar, Map<String, String> expected) throws IOException {
        try (InputStream fi = Files.newInputStream(tar);
                InputStream bi = new BufferedInputStream(fi);
                TarArchiveInputStream o = new TarArchiveInputStream(bi)) {
            ArchiveEntry e;
            while ((e = o.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String content = new String(IOUtils.toByteArray(o), StandardCharsets.UTF_8).trim();
                assertThat(content).isEqualTo(expected.get(e.getName()));
            }
        }
    }
}
