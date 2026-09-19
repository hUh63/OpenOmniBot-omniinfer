# OmniInfer Android Integration

How this repo embeds the OmniInfer local-inference runtime, and which upstream
behaviour we deliberately mirror. Written after syncing with upstream
`omnimind-ai/OmniInfer` (`docs/android/*`) so the next sync does not have to
rediscover any of it.

## 1. Two packaging modes, one source tree

The SDK module is `third_party/omniinfer/android/omniinfer-server`.

| Mode | Gradle | Native `.so` in the artifact | Maven artifact |
|---|---|---|---|
| Bundled (what we ship) | default (`omniinfer.packaging.native_bundled=true`) | yes | `omniinfer` |
| Lite + downloaded engine | `-Pomniinfer.packaging.native_bundled=false` | no | `omniinfer-lite` |

Never add both coordinates to one app. The lite artifact is Kotlin/dex only; the
engine arrives at runtime (section 3).

## 2. Dependency and ABI notes

* **Prefer the Maven coordinate over a flat AAR.** If you vendor a flat AAR you
  must add the transitive runtime dependencies by hand:

  ```kotlin
  implementation("io.ktor:ktor-server-core:3.1.3")
  implementation("io.ktor:ktor-server-cio:3.1.3")
  implementation("io.ktor:ktor-server-content-negotiation:3.1.3")
  implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
  ```

  That list is exactly what `app/build.gradle.kts` carries for the `omniinfer`
  edition (`omniinferImplementation(...)`) because this repo consumes the module
  as a project, not from Maven. Keep it in sync with the module.

* **`abiFilters` — ship arm64 only for releases.** When LiteRT-LM is on the
  classpath its transitive native dependencies also pull an `x86_64` slice
  (`liblitertlm_jni.so` ~18 MB, `libLiteRt.so`, `libLiteRtClGlAccelerator.so`).
  That is dead weight in a device APK. Release builds pass
  `-Pomniinfer.abis=arm64-v8a` (see `.github/workflows/build-apk.yml`). Local
  builds keep `x86_64` so emulators still work.

* Do not declare `litertlm-android` yourself; let it come transitively so the
  version stays matched to the SDK.

## 3. Engine packages (downloaded runtime)

Contract, matching upstream `docs/android/engine-download.md`:

```text
<engine>.zip
  manifest.json
  lib/arm64-v8a/*.so
<engine>.zip.sha256        # first whitespace-delimited token = lowercase sha256
```

Manifest fields: `formatVersion`, `engineVersion`, `interfaceVersion`, `abi`,
`minSdk`, `backends`, `coreLibs`, `libs[{name,sha256,sizeBytes}]`.

Install flow (`OmniInferEngineDownloader` + `OmniInferEngineLoader`):

1. fetch `<url>.sha256`;
2. stream the zip to a temp file while hashing, compare;
3. extract into a staging dir under app-private storage, rejecting entries whose
   canonical path escapes it;
4. rename the verified staging dir into place;
5. verify the manifest, then every library size + SHA-256, and reject any `.so`
   that the manifest does not list;
6. `System.load` the `coreLibs` in order (the DT_NEEDED chain, ending in
   `libomniinfer-jni.so`).

**One engine per process.** Changing the engine version requires an app restart:
once a ggml backend is registered it cannot be torn down safely. This is
upstream's constraint, not a limitation of our port.

Build a package from this repo:

```bash
./gradlew :omniinfer-server:bundleEnginePackage \
  -Pomniinfer.backend.llama_cpp=true \
  -Pomniinfer.backend.mnn=false \
  -Pomniinfer.backend.executorch_qnn=false \
  -Pomniinfer.backend.litert_lm=false \
  -Pomniinfer.maven.version=<version>
```

Output lands in `omniinfer-server/build/distributions/engine/` as
`omniinfer-engine-<version>-arm64-v8a.zip` plus `.zip.sha256`. The task derives
`coreLibs` by reading `DT_NEEDED` from the packaged ELF files, writes the zip, then
**reopens it and verifies every entry against the manifest** before reporting
success. `-Pomniinfer.engine.jni_dir=` overrides where the libraries are read
from.

Distribution note: Google Play apps must not download executable code from
outside Play. This mode is for private / enterprise / direct distribution.

## 4. Native lib directory injection

`OmniInferNativeLibs.resolve(context)` is the single place that decides which
directory the native backend scans (`ggml_backend_load_all_from_path`):

1. an installed engine payload wins (`OmniInferEngineLoader.installedNativeLibDir`);
2. otherwise a **filtered mirror** of `applicationInfo.nativeLibraryDir` is built
   under `filesDir/omniinfer-native/<fingerprint>`, containing symlinks to
   everything except the excluded CPU variants;
3. before the mirror is used, the native side `dlopen`s its ggml libraries the
   same way ggml would (`nativeProbeLibDir`) — if the platform linker refuses the
   directory, we fall back to the plain `nativeLibraryDir` so a model always loads.

### Why filtering instead of runtime downgrade

All CPU variants stay bundled in the APK. The problem is that ggml scores them at
runtime and picks the highest applicable tier, which on consumer armv9 SoCs can be
the SVE2 kernels (`libggml-cpu-android_armv9.0_1.so`) — the ones that SIGSEGV on
MediaTek MT6991 / Dimensity 9400.

`OmniInferNativeLibs.excludedVariantPrefixes` defaults to `["armv9"]`. Because the
*scan directory* is filtered, ggml never sees the excluded tiers: no `dlopen`,
`unload`, deny-list or warm-up probe is needed. Excluding by directory is the only
safe moment — an already-registered ggml backend cannot be unloaded, which is also
why upstream requires a restart to change engine payloads.

Set `OmniInferNativeLibs.filterEnabled = false` to hand every shipped variant over
(diagnostics only), and call `invalidate(context)` after changing the policy.

## 5. Backend selectors

`BackendSelectors.kt` is the single source of truth (canonical names follow
upstream):

```text
auto | llama.cpp-cpu | llama.cpp-htp | litert-lm-cpu | litert-lm-gpu
     | mnn-cpu | mnn-opencl | mnn-vulkan
```

Legacy spellings keep working: `llama.cpp`, `llama-cpp`, `llama.cpp/cpu`,
`litert`, `litert-lm`, `litertlm`, `litert/gpu`, `mnn`, … Anything unrecognised is
passed through unchanged (e.g. `executorch-qnn`).

`normalizeSelector` resolution order for `auto`: catalog defaults → file
extension (`.gguf` → llama.cpp-cpu, `.litertlm`/`.litert` → litert-lm-gpu) →
`__unsupported_auto_backend__`.

Per-backend defaults (threads / ctx / `extraConfig`, including the HTP map) live in
the same object. Do not restate them in the app layer — that duplication is how the
onboarding page once forced LiteRT onto GGUF imports and produced
`Please select a .litertlm model file`.

The **app-level** vocabulary (`llama.cpp`, `omniinfer-mnn`, `executorch-qnn`,
`litert`, `litert-npu`) is a separate layer; its single source is
`ui/lib/services/inference_backend.dart`, mirrored by
`OmniInferLocalRuntime.normalizeBackend`. Keep those two in sync.

## 6. llama.cpp / ggml build flags

Identical to upstream, and required for runtime backend discovery:

```text
-DGGML_NATIVE=OFF
-DGGML_LLAMAFILE=OFF
-DGGML_BACKEND_DL=ON
-DGGML_CPU_ALL_VARIANTS=ON
-DLLAMA_BUILD_COMMON=ON
-BUILD_SHARED_LIBS=ON
```

llama.cpp is linked statically into `libomniinfer-jni.so`; the CPU variants and
accelerators (`libggml-cpu-*`, `libggml-hexagon`, `libggml-htp-*`,
`libggml-opencl`) are discovered at runtime from the injected directory.
