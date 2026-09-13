# Snapdragon HTP runtime libraries (llama.cpp b10919)

These prebuilt Hexagon/NPU runtime libraries were compiled from upstream
**llama.cpp `b10919`** (2026-09-12) with the official Snapdragon cross-compilation
toolchain, so they stay ABI-compatible with the `llama.cpp` source that the app
builds its own `libllama.so` / `libggml-base.so` from.

Build recipe (reproducible via `.github/workflows/build-htp.yml`):

```
image:  ghcr.io/snapdragon-toolchain/arm64-android:v0.7  (--platform linux/amd64)
source: https://codeload.github.com/ggml-org/llama.cpp/tar.gz/b10919
steps:  cp docs/backend/snapdragon/CMakeUserPresets.json .
        cmake --preset arm64-android-snapdragon-release -B build-snapdragon
        cmake --build build-snapdragon -j $(nproc)
```

Packaged files (expected by `omniinfer-server/build.gradle.kts`,
`llamaCppHtpRuntimeFiles`):

| file | notes |
|---|---|
| `libggml-hexagon.so` | Hexagon backend host side |
| `libggml-opencl.so`  | OpenCL (Adreno GPU) backend |
| `libggml-htp-v73.so` | HTP skel/stub for v73 |
| `libggml-htp-v75.so` | HTP skel/stub for v75 |
| `libggml-htp-v79.so` | HTP skel/stub for v79 |
| `libggml-htp-v81.so` | HTP skel/stub for v81 |
| `libggml-htp-v68.so` | placeholder copy of v73 (v68 no longer built upstream) |
| `libggml-htp-v69.so` | placeholder copy of v73 (v69 no longer built upstream) |

The directory name still carries the historical `8a091c47` tag for compatibility
with `omniinfer-server/build.gradle.kts`; the actual content is from `b10919`.
