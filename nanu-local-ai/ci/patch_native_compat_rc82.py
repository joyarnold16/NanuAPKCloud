#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one patch anchor, found {count}")
    path.write_text(text.replace(old, new, 1))


lib_gradle = Path("llama-upstream/examples/llama.android/lib/build.gradle.kts")
replace_once(
    lib_gradle,
    'arguments += "-DGGML_BACKEND_DL=ON"',
    'arguments += "-DGGML_BACKEND_DL=OFF"',
    "disable runtime backend loading",
)
replace_once(
    lib_gradle,
    'arguments += "-DGGML_CPU_ALL_VARIANTS=ON"',
    'arguments += "-DGGML_CPU_ALL_VARIANTS=OFF"',
    "disable CPU variant probing",
)
replace_once(
    lib_gradle,
    'arguments += "-DGGML_LLAMAFILE=OFF"',
    'arguments += "-DGGML_LLAMAFILE=OFF"\n'
    '                arguments += "-DGGML_CPU_KLEIDIAI=OFF"\n'
    '                arguments += "-DGGML_OPENMP=OFF"',
    "add conservative CPU flags",
)

cmake = Path("llama-upstream/examples/llama.android/lib/src/main/cpp/CMakeLists.txt")
replace_once(
    cmake,
    "        set(GGML_CPU_KLEIDIAI ON)\n        set(GGML_OPENMP ON)",
    "        # RC8.2 compatibility profile: baseline ARM64 without optional\n"
    "        # runtime dispatchers that failed during startup on a real device.\n"
    "        set(GGML_CPU_KLEIDIAI OFF)\n"
    "        set(GGML_OPENMP OFF)",
    "select baseline Android ARM64 backend",
)

engine = Path(
    "llama-upstream/examples/llama.android/lib/src/main/java/"
    "com/arm/aichat/internal/InferenceEngineImpl.kt"
)
replace_once(
    engine,
    '''                init(nativeLibDir)
                _state.value = InferenceEngine.State.Initialized
                Log.i(TAG, "Native library loaded! System info: \\n${systemInfo()}")
''',
    '''                init(nativeLibDir)
                // Do not report the engine as ready until a JNI round trip succeeds.
                val nativeSystemInfo = systemInfo()
                _state.value = InferenceEngine.State.Initialized
                Log.i(TAG, "Native library loaded! System info: \\n$nativeSystemInfo")
''',
    "run native startup self-test before ready state",
)
replace_once(
    engine,
    '''            } catch (e: Exception) {
                Log.e(TAG, "Failed to load native library", e)
                throw e
            }
''',
    '''            } catch (error: Throwable) {
                val causeChain = generateSequence(error) { it.cause }.take(8).toList()
                val root = causeChain.lastOrNull() ?: error
                val rootType = root.javaClass.simpleName.ifBlank { root.javaClass.name }
                val rootMessage = root.message?.trim()?.take(240)
                val detail = if (rootMessage.isNullOrBlank()) rootType else "$rootType: $rootMessage"
                val wrapped = if (error is Exception) {
                    error
                } else {
                    IllegalStateException("Native engine startup failed: $detail", error)
                }
                Log.e(TAG, "Failed to initialize native AI engine: $detail", error)
                _state.value = InferenceEngine.State.Error(wrapped)
            }
''',
    "capture native startup Throwable",
)

gradle_text = lib_gradle.read_text()
cmake_text = cmake.read_text()
engine_text = engine.read_text()
required = [
    "-DGGML_BACKEND_DL=OFF",
    "-DGGML_CPU_ALL_VARIANTS=OFF",
    "-DGGML_CPU_KLEIDIAI=OFF",
    "-DGGML_OPENMP=OFF",
]
for marker in required:
    if marker not in gradle_text:
        raise SystemExit(f"native compatibility Gradle marker missing: {marker}")
for forbidden in ["GGML_CPU_KLEIDIAI ON", "GGML_OPENMP ON"]:
    if forbidden in cmake_text:
        raise SystemExit(f"unsafe ARM64 backend marker remains: {forbidden}")
for marker in [
    "val nativeSystemInfo = systemInfo()",
    "catch (error: Throwable)",
    "InferenceEngine.State.Error(wrapped)",
]:
    if marker not in engine_text:
        raise SystemExit(f"engine startup guard missing: {marker}")

print("RC8.2 conservative ARM64/native startup patch applied.")
