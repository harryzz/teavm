/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.backend.wasm.runtime;

import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.Intrinsified;
import org.teavm.runtime.heap.Heap;

public class WasmGCSupport {
    private static int lastObjectId = 1831433054;

    private WasmGCSupport() {
    }

    public static NullPointerException npe() {
        return new NullPointerException();
    }

    public static ArrayIndexOutOfBoundsException aiiobe() {
        return new ArrayIndexOutOfBoundsException();
    }

    public static ClassCastException cce() {
        return new ClassCastException();
    }

    public static void throwCloneNotSupportedException() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public static Object defaultClone(Object object) throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public static int nextObjectId() {
        var x = lastObjectId;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        lastObjectId = x;
        return x;
    }

    public static void putCharStdout(char c) {
        if (PlatformDetector.isWebAssemblyGCWasi()) {
            wasiWrite(1, c);
        } else {
            putCharStdoutJS(c);
        }
    }

    public static void putCharStderr(char c) {
        if (PlatformDetector.isWebAssemblyGCWasi()) {
            wasiWrite(2, c);
        } else {
            putCharStderrJS(c);
        }
    }

    @Import(name = "putcharStdout", module = "teavmConsole")
    private static native void putCharStdoutJS(char c);

    @Import(name = "putcharStderr", module = "teavmConsole")
    private static native void putCharStderrJS(char c);

    // task 113 WASI floor: write one byte to fd via wasi_snapshot_preview1.fd_write.
    @Import(name = "fd_write", module = "wasi_snapshot_preview1")
    private static native int wasiFdWrite(int fd, int iovs, int iovsLen, int nwrittenPtr);

    private static int wasiScratch;

    private static void wasiWrite(int fd, char c) {
        var scratch = wasiScratch;
        if (scratch == 0) {
            // 4-aligned: [0..4]=iovec.buf, [4..8]=iovec.len, [8..12]=nwritten, [12]=byte
            scratch = (Heap.alloc(20).toInt() + 3) & ~3;
            wasiScratch = scratch;
        }
        Address.fromInt(scratch + 12).putByte((byte) c);
        Address.fromInt(scratch).putInt(scratch + 12);
        Address.fromInt(scratch + 4).putInt(1);
        wasiFdWrite(fd, scratch, 1, scratch + 8);
    }

    // task 113 WASI floor: real wall clock via wasi_snapshot_preview1.clock_time_get.
    // Returns milliseconds since the epoch as f64 (the SystemIntrinsic converts to long).
    @Import(name = "clock_time_get", module = "wasi_snapshot_preview1")
    private static native int wasiClockTimeGet(int clockId, long precision, int resultPtr);

    private static int clockScratch;

    public static double currentTimeMillis() {
        var scratch = clockScratch;
        if (scratch == 0) {
            scratch = (Heap.alloc(16).toInt() + 7) & ~7; // 8-byte-aligned u64 timestamp slot
            clockScratch = scratch;
        }
        wasiClockTimeGet(0, 1000, scratch); // clock id 0 = realtime, 1us precision
        long ns = Address.fromInt(scratch).getLong();
        return ns / 1_000_000.0;
    }

    public static char[] nextCharArray() {
        var length = nextLEB();
        var result = new char[length];
        var pos = 0;
        while (pos < length) {
            var b = nextByte();
            if ((b & 0x80) == 0) {
                result[pos++] = (char) b;
            } else if ((b & 0xE0) == 0xC0) {
                var b2 = nextByte();
                result[pos++] = (char) (((b & 0x1F) << 6) | (b2 & 0x3F));
            } else if ((b & 0xF0) == 0xE0) {
                var b2 = nextByte();
                var b3 = nextByte();
                var c = (char) (((b & 0x0F) << 12) | ((b2 & 0x3f) << 6) | (b3 & 0x3F));
                result[pos++] = c;
            } else if ((b & 0xF8) == 0xF0) {
                var b2 = nextByte();
                var b3 = nextByte();
                var b4 = nextByte();
                var code = ((b & 0x07) << 18) | ((b2 & 0x3f) << 12) | ((b3 & 0x3F) << 6) | (b4 & 0x3F);
                result[pos++] = Character.highSurrogate(code);
                result[pos++] = Character.lowSurrogate(code);
            }
        }
        return result;
    }

    private static int nextLEB() {
        var shift = 0;
        var result = 0;
        while (true) {
            var b = nextByte();
            var digit = b & 0x7F;
            result |= digit << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        return result;
    }

    @Intrinsified
    private static native byte nextByte();

    private static native void error();

    public static StringBuilder createStringBuilder() {
        return new StringBuilder();
    }

    public static String[] createStringArray(int size) {
        return new String[size];
    }

    public static void setToStringArray(String[] array, int index, String value) {
        array[index] = value;
    }
}
