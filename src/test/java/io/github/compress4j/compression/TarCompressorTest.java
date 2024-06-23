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
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.getPosixFilePermissions;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.compress4j.archive.compression.TarCompressor;
import io.github.compress4j.archive.decompression.TarDecompressor;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;
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
        write(data, "789");
        try (var compressor = new TarCompressor(tar)) {
            compressor.addFile("empty.txt", new byte[0]);
            compressor.addFile("file1.txt", "123".getBytes());
            compressor.addFile("file2.txt", "456".getBytes());
            compressor.addFile("file3.txt", data);
        }

        assertTar(tar, Map.of("empty.txt", "", "file1.txt", "123", "file2.txt", "456", "file3.txt", "789"));
    }

    @Test
    void recursiveTarWithPrefix(@TempDir Path tempDir) throws IOException {
        var dir = tempDir.resolve("dir");
        write(tempDir.resolve("dir/f1"), "1");
        write(tempDir.resolve("dir/f2"), "2");
        write(tempDir.resolve("dir/d1/f11"), "11");
        write(tempDir.resolve("dir/d1/f12"), "12");
        write(tempDir.resolve("dir/d1/d11/f111"), "111");
        write(tempDir.resolve("dir/d1/d11/f112"), "112");
        write(tempDir.resolve("dir/d2/f21"), "21");
        write(tempDir.resolve("dir/d2/f22"), "22");

        var tar = tempDir.resolve("test.tar");
        try (var compressor = new TarCompressor(tar)) {
            compressor.addDirectory("tar/", dir);
        }
        assertTar(
                tar,
                Map.ofEntries(
                        entry("tar/", ""),
                        entry("tar/d1/", ""),
                        entry("tar/d1/d11/", ""),
                        entry("tar/d2/", ""),
                        entry("tar/f1", "1"),
                        entry("tar/f2", "2"),
                        entry("tar/d1/f11", "11"),
                        entry("tar/d1/f12", "12"),
                        entry("tar/d1/d11/f111", "111"),
                        entry("tar/d1/d11/f112", "112"),
                        entry("tar/d2/f21", "21"),
                        entry("tar/d2/f22", "22")));
    }

    @Test
    void tarWithEmptyPrefix(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("dir/file");
        createDirectories(file.getParent());
        createFile(file);
        var tar = tempDir.resolve("test.tar");
        try (var compressor = new TarCompressor(tar)) {
            compressor.addDirectory("", file.getParent());
        }
        assertTar(tar, Map.of(file.getFileName().toString(), ""));
    }

    @Test
    void tarWithExecutableFiles(@TempDir Path tempDir) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        var dir = tempDir.resolve("dir");
        createDirectories(dir);
        var regular = dir.resolve("regular");
        createFile(regular);
        var executable = dir.resolve("executable");
        createFile(executable, PosixFilePermissions.asFileAttribute(Set.of(PosixFilePermission.values())));

        var tar = tempDir.resolve("test.tgz");
        try (var compressor = new TarCompressor(tar)) {
            compressor.addDirectory(dir);
        }
        var out = tempDir.resolve("out");
        new TarDecompressor(tar).extract(out);
        assertThat(getPosixFilePermissions(out.resolve(regular.getFileName())))
                .doesNotContain(PosixFilePermission.OWNER_EXECUTE);
        assertThat(getPosixFilePermissions(out.resolve(executable.getFileName())))
                .contains(PosixFilePermission.OWNER_EXECUTE);
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
