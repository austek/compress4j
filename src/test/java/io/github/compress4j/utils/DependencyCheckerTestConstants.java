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

public class DependencyCheckerTestConstants {

    private DependencyCheckerTestConstants() {}

    public static final String EXPECTED_MESSAGE_BROTLI =
            "Brotli compression is not available. In addition to Apache Commons Compress"
                    + " you need the Google Brotli Dec library - see https://github.com/google/brotli/";

    public static final String EXPECTED_MESSAGE_LZMA =
            "LZMA compression is not available. In addition to Apache Commons Compress"
                    + " you need the XZ for Java library - see https://tukaani.org/xz/java.html";

    public static final String EXPECTED_MESSAGE_XZ =
            "XZ compression is not available. In addition to Apache Commons Compress"
                    + " you need the XZ for Java library - see https://tukaani.org/xz/java.html";

    public static final String EXPECTED_MESSAGE_ZSTD =
            "Zstandard compression is not available. In addition to Apache Commons Compress"
                    + " you need the Zstd JNI library - see https://github.com/luben/zstd-jni";
}
