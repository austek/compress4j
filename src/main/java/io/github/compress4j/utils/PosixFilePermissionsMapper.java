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
package io.github.compress4j.utils;

import jakarta.annotation.Nonnull;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("OctalInteger")
public class PosixFilePermissionsMapper {

    public static final Map<Integer, PosixFilePermission> INT_TO_POSIX_FILE_PERMISSIONS = Map.of(
            0400, PosixFilePermission.OWNER_READ,
            0200, PosixFilePermission.OWNER_WRITE,
            0100, PosixFilePermission.OWNER_EXECUTE,
            0040, PosixFilePermission.GROUP_READ,
            0020, PosixFilePermission.GROUP_WRITE,
            0010, PosixFilePermission.GROUP_EXECUTE,
            0004, PosixFilePermission.OTHERS_READ,
            0002, PosixFilePermission.OTHERS_WRITE,
            0001, PosixFilePermission.OTHERS_EXECUTE);

    private static final Map<PosixFilePermission, Integer> POSIX_FILE_PERMISSIONS_TO_INT =
            INT_TO_POSIX_FILE_PERMISSIONS.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private PosixFilePermissionsMapper() {}

    public static Set<PosixFilePermission> fromUnixMode(int mode) {
        return INT_TO_POSIX_FILE_PERMISSIONS.entrySet().stream()
                .filter(entry -> (mode & entry.getKey()) > 0)
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
    }

    public static int toUnixMode(@Nonnull Set<PosixFilePermission> permissions) {
        return POSIX_FILE_PERMISSIONS_TO_INT.entrySet().stream()
                .filter(entry -> permissions.contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .reduce(0, (a, b) -> a | b);
    }
}
