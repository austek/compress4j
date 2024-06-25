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

import static io.github.compress4j.utils.SystemUtils.getOsNameAndVersion;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.LinkPermission;

public class Assumptions {
    public static final boolean isSymLinkCreationSupported = SystemUtils.IS_OS_UNIX || canCreateSymlinks();

    private Assumptions() {}

    @SuppressWarnings("removal")
    private static boolean canCreateSymlinks() {
        try {
            java.security.AccessController.checkPermission(new LinkPermission("symbolic"));
        } catch (java.security.AccessControlException ignore) {
            return false;
        }
        return true;
    }

    public static void assumeSymLinkCreationIsSupported() {
        assumeTrue(isSymLinkCreationSupported, "Can't create symlinks on " + getOsNameAndVersion());
    }
}
