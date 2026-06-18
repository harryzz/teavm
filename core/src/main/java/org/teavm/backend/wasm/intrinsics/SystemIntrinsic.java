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
package org.teavm.backend.wasm.intrinsics;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.runtime.WasmGCSupport;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.model.MethodReference;

public class SystemIntrinsic implements WasmGCInlineIntrinsic {
    private static final MethodReference WASI_CURRENT_TIME_MILLIS = new MethodReference(
            WasmGCSupport.class, "currentTimeMillis", double.class);

    private WasmFunctionTypes functionTypes;
    private WasmModule module;
    private final BaseWasmFunctionRepository functions;
    private WasmFunction workerFunction;
    private final boolean wasi;

    public SystemIntrinsic(WasmFunctionTypes functionTypes, WasmModule module,
            BaseWasmFunctionRepository functions, boolean wasi) {
        this.functionTypes = functionTypes;
        this.module = module;
        this.functions = functions;
        this.wasi = wasi;
    }

    @Override
    public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context,
            WasmInstructionBuilder builder) {
        if (wasi) {
            // task 113 WASI floor: real wall clock via WasmGCSupport.currentTimeMillis (clock_time_get).
            builder.call(functions.forStaticMethod(WASI_CURRENT_TIME_MILLIS))
                    .convert(WasmNumType.FLOAT64, WasmNumType.INT64, true);
            return;
        }
        if (workerFunction == null) {
            workerFunction = new WasmFunction(functionTypes.of(WasmType.FLOAT64));
            workerFunction.setName("teavm@currentTimeMillis");
            workerFunction.setImportName("currentTimeMillis");
            workerFunction.setImportModule("teavmDate");
            module.functions.add(workerFunction);
        }
        builder.call(workerFunction).convert(WasmNumType.FLOAT64, WasmNumType.INT64, true);
    }
}
