import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

val ktorVersion: String = findProperty("omniinfer.ktor.version")?.toString() ?: "3.1.3"
val liteRtLmVersion: String = findProperty("omniinfer.litertlm.version")?.toString() ?: "0.11.0"

/**
 * Native packaging mode (mirrors upstream OmniInfer):
 *
 *  - `true`  (default) self-contained: the inference `.so` files are bundled in the
 *            AAR/APK, published as the `omniinfer` artifact.
 *  - `false` lite: Kotlin/dex only, no native inference libraries. The engine is
 *            delivered at runtime as a verified zip (see the `bundleEnginePackage`
 *            task below, `OmniInferEngineDownloader` and `OmniInferEngineLoader`).
 *
 * One source tree, two artifacts — never add both to the same app.
 */
val bundleNativeLibs: Boolean =
    findProperty("omniinfer.packaging.native_bundled")?.toString()?.toBooleanStrictOrNull() ?: true
val omniInferMavenGroup: String =
    findProperty("omniinfer.maven.group")?.toString() ?: "io.github.omnimind-ai"
val omniInferMavenArtifact: String =
    findProperty("omniinfer.maven.artifact")?.toString()
        ?: if (bundleNativeLibs) "omniinfer" else "omniinfer-lite"
val omniInferMavenVersion: String =
    findProperty("omniinfer.maven.version")?.toString() ?: "0.1.0-SNAPSHOT"
val omniInferMavenRepo: String =
    findProperty("omniinfer.maven.repo")?.toString() ?: layout.buildDirectory.dir("repo").get().asFile.absolutePath
val omniInferRepoDir: File = projectDir.parentFile.parentFile
val omniInferMavenScmUrl: String =
    findProperty("omniinfer.maven.scm.url")?.toString() ?: "https://github.com/omnimind-ai/OmniInfer"
val omniInferMavenScmConnection: String =
    findProperty("omniinfer.maven.scm.connection")?.toString()
        ?: "scm:git:https://github.com/omnimind-ai/OmniInfer.git"
val omniInferMavenScmDeveloperConnection: String =
    findProperty("omniinfer.maven.scm.developerConnection")?.toString()
        ?: "scm:git:ssh://git@github.com/omnimind-ai/OmniInfer.git"
val omniInferMavenDeveloperId: String =
    findProperty("omniinfer.maven.developer.id")?.toString() ?: "omnimind-ai"
val omniInferMavenDeveloperName: String =
    findProperty("omniinfer.maven.developer.name")?.toString() ?: "OmniMind AI"
val omniInferMavenDeveloperUrl: String =
    findProperty("omniinfer.maven.developer.url")?.toString() ?: "https://github.com/omnimind-ai"
val hasInMemorySigningKey: Boolean =
    !findProperty("signingInMemoryKey")?.toString().isNullOrBlank()

group = omniInferMavenGroup
version = omniInferMavenVersion

fun boolProperty(name: String, default: Boolean = true): Boolean =
    findProperty(name)?.toString()?.toBooleanStrictOrNull() ?: default

fun isDynamicDependencyVersion(version: String): Boolean =
    version.contains("+") ||
        version.startsWith("latest.", ignoreCase = true) ||
        version.contains("[") ||
        version.contains("]") ||
        version.contains("(") ||
        version.contains(")")

val enableLlamaCpp: Boolean = boolProperty("omniinfer.backend.llama_cpp")
val enableMnn: Boolean = boolProperty("omniinfer.backend.mnn")
val enableExecutorchQnn: Boolean = boolProperty("omniinfer.backend.executorch_qnn")
val enableLiteRtLm: Boolean = boolProperty("omniinfer.backend.litert_lm")
val requireLiteRtLmInPublication: Boolean = boolProperty("omniinfer.publication.require_litert_lm")
if (enableLiteRtLm && isDynamicDependencyVersion(liteRtLmVersion)) {
    throw GradleException(
        "omniinfer.litertlm.version must be a pinned release version, got '$liteRtLmVersion'. " +
            "Publish a new OmniInfer version when upgrading LiteRT-LM."
    )
}
val enableMnnThreadPool: Boolean = boolProperty("omniinfer.mnn.thread_pool")
val llamaCppHtpPrebuiltDir: File =
    findProperty("omniinfer.llama_cpp.htp_prebuilt_dir")?.toString()?.let(::File)
        ?: File(omniInferRepoDir, "tmp/llama-cpp-submodule-snapdragon-minpkg-8a091c47/lib")
val enableLlamaCppHtp: Boolean =
    boolProperty("omniinfer.backend.llama_cpp_htp", enableLlamaCpp && llamaCppHtpPrebuiltDir.isDirectory)
val llamaCppRuntimeJniDir = layout.buildDirectory.dir("generated/llamaCppRuntimeJniLibs")
val llamaCppHtpRuntimeFiles = listOf(
    "libggml-opencl.so",
    "libggml-hexagon.so",
    "libggml-htp-v68.so",
    "libggml-htp-v69.so",
    "libggml-htp-v73.so",
    "libggml-htp-v75.so",
    "libggml-htp-v79.so",
    "libggml-htp-v81.so",
)

val syncLlamaCppHtpJniLibs by tasks.registering {
    description = "Collect llama.cpp Snapdragon HTP runtime libraries for AAR packaging"
    onlyIf { enableLlamaCppHtp }
    outputs.dir(llamaCppRuntimeJniDir)
    doLast {
        val outputDir = llamaCppRuntimeJniDir.get().dir("arm64-v8a").asFile
        outputDir.mkdirs()
        llamaCppHtpRuntimeFiles.forEach { name ->
            val prebuiltLib = File(llamaCppHtpPrebuiltDir, name).takeIf { it.isFile }
            val source = prebuiltLib ?: throw GradleException(
                "Missing llama.cpp HTP runtime library $name. " +
                    "Pass -Pomniinfer.llama_cpp.htp_prebuilt_dir=/path/to/llama.cpp/lib " +
                    "or disable -Pomniinfer.backend.llama_cpp_htp=false."
            )
            source.copyTo(File(outputDir, name), overwrite = true)
        }
    }
}

// --- ExecuTorch QNN: auto-download pre-built binaries ---
if (enableExecutorchQnn) {
    val etQnnVersion = 3  // bump when uploading new binaries to OSS
    val baseUrl = "https://omnimind-model.oss-cn-beijing.aliyuncs.com/omniinfer-android/arm64-v8a"
    val jniDir = file("src/main/jniLibs/arm64-v8a")
    val versionFile = File(jniDir, ".etqnn_version")

    val etQnnFiles = listOf(
        // Universal
        "libetqnn_runner.so",
        "libqnn_executorch_backend.so",
        "libQnnHtp.so",
        "libQnnHtpPrepare.so",
        "libQnnSystem.so",
        "libQnnHtpNetRunExtensions.so",
        // Chip-specific skel/stub
        "libQnnHtpV75Skel.so", "libQnnHtpV75Stub.so",  // SM8650 (8 Gen 3)
        "libQnnHtpV79Skel.so", "libQnnHtpV79Stub.so",  // SM8750 (8 Elite)
        "libQnnHtpV81Skel.so", "libQnnHtpV81Stub.so",  // SM8850 (8 Elite Gen 5)
    )

    val downloadEtQnnLibs by tasks.registering {
        description = "Download ExecuTorch QNN pre-built binaries (v$etQnnVersion)"
        outputs.upToDateWhen {
            versionFile.exists()
                && versionFile.readText().trim() == etQnnVersion.toString()
                && etQnnFiles.all { File(jniDir, it).exists() }
        }
        doLast {
            jniDir.mkdirs()
            val outdated = !versionFile.exists()
                || versionFile.readText().trim() != etQnnVersion.toString()
            if (outdated) logger.lifecycle("ET QNN prebuilt v$etQnnVersion — updating all binaries")
            etQnnFiles.forEach { name ->
                val target = File(jniDir, name)
                if (!target.exists() || outdated) {
                    logger.lifecycle("  Downloading $name ...")
                    uri("$baseUrl/$name").toURL().openStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            versionFile.writeText(etQnnVersion.toString())
        }
    }

    tasks.matching { it.name.startsWith("buildCMake") || it.name.startsWith("merge") }
        .configureEach { dependsOn(downloadEtQnnLibs) }
}

android {
    namespace = "com.omniinfer.server"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                if (enableLlamaCpp) {
                    arguments += "-DGGML_NATIVE=OFF"
                    arguments += "-DGGML_LLAMAFILE=OFF"
                    arguments += "-DLLAMA_BUILD_COMMON=ON"
                    arguments += "-DGGML_BACKEND_DL=ON"
                    arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                }
                arguments += "-DOMNIINFER_BACKEND_LLAMA_CPP=${if (enableLlamaCpp) "ON" else "OFF"}"
                arguments += "-DOMNIINFER_BACKEND_MNN=${if (enableMnn) "ON" else "OFF"}"
                arguments += "-DOMNIINFER_BACKEND_EXECUTORCH_QNN=${if (enableExecutorchQnn) "ON" else "OFF"}"
                if (enableMnn) {
                    arguments += "-DMNN_USE_THREAD_POOL=${if (enableMnnThreadPool) "ON" else "OFF"}"
                }
            }
        }
    }

    sourceSets {
        getByName("main") {
            if (enableLiteRtLm) {
                java.srcDir("src/litertLm/java")
            }
            val jniLibDirs = mutableListOf<File>()
            if (enableLlamaCppHtp) {
                jniLibDirs += llamaCppRuntimeJniDir.get().asFile
            }
            if (enableExecutorchQnn) {
                jniLibDirs += file("src/main/jniLibs")
            }
            jniLibs.setSrcDirs(jniLibDirs)
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/omniinfer-jni/CMakeLists.txt")
        }
    }

    // Shorten .cxx build path on Windows to avoid MAX_PATH (260 char) limit.
    // Set -Pomniinfer.cxx.dir=D:/.cxx/omniinfer to override (e.g. to save C: drive space).
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        val cxxDir = findProperty("omniinfer.cxx.dir")?.toString()
            ?: "${System.getProperty("user.home")}/.cxx/omniinfer"
        externalNativeBuild.cmake.buildStagingDirectory = file(cxxDir)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    if (!bundleNativeLibs) {
        // Lite AAR: keep every native library out of the artifact. Both the
        // CMake-produced llama.cpp/ggml libraries and any prebuilt jniLibs are
        // filtered here; the runtime engine payload supplies them instead.
        packaging {
            jniLibs {
                excludes += listOf("**/*.so")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.core:core-ktx:1.12.0")
    if (enableLiteRtLm) {
        implementation("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmVersion")
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.contains("JniLibFolders") }
    .configureEach { dependsOn(syncLlamaCppHtpJniLibs) }

val mavenCentralJavadocDir = layout.buildDirectory.dir("generated/mavenCentralJavadoc")

val generateMavenCentralJavadoc by tasks.registering {
    description = "Generate placeholder API documentation for Maven Central publication"
    outputs.dir(mavenCentralJavadocDir)
    doLast {
        val outputFile = mavenCentralJavadocDir.get().file("README.md").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            # OmniInfer Android

            Android local inference server for llama.cpp CPU/HTP and LiteRT-LM GPU.
            API entry point: com.omniinfer.server.OmniInferServer.
            """.trimIndent() + "\n",
        )
    }
}

val mavenCentralJavadocJar by tasks.registering(Jar::class) {
    description = "Package API documentation for Maven Central publication"
    archiveClassifier.set("javadoc")
    dependsOn(generateMavenCentralJavadoc)
    from(mavenCentralJavadocDir)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = omniInferMavenGroup
                artifactId = omniInferMavenArtifact
                version = omniInferMavenVersion
                artifact(mavenCentralJavadocJar)
                pom {
                    name.set("OmniInfer Android")
                    description.set("Android local inference server for llama.cpp CPU/HTP and LiteRT-LM GPU.")
                    inceptionYear.set("2026")
                    url.set(omniInferMavenScmUrl)
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set(omniInferMavenDeveloperId)
                            name.set(omniInferMavenDeveloperName)
                            url.set(omniInferMavenDeveloperUrl)
                        }
                    }
                    scm {
                        url.set(omniInferMavenScmUrl)
                        connection.set(omniInferMavenScmConnection)
                        developerConnection.set(omniInferMavenScmDeveloperConnection)
                    }
                }
            }
        }
        repositories {
            maven {
                name = "omniInferLocal"
                url = uri(omniInferMavenRepo)
            }
        }
    }

    if (hasInMemorySigningKey) {
        signing {
            useInMemoryPgpKeys(
                findProperty("signingInMemoryKey")?.toString(),
                findProperty("signingInMemoryKeyPassword")?.toString(),
            )
            sign(publishing.publications["release"])
        }
    }
}

tasks.register<Zip>("bundleMavenCentralPublication") {
    description = "Create a Maven Central upload bundle from the local Maven repository"
    group = "publishing"
    dependsOn("publishReleasePublicationToOmniInferLocalRepository")
    archiveFileName.set("${omniInferMavenArtifact}-${omniInferMavenVersion}-maven-central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    val publicationPath = "${omniInferMavenGroup.replace('.', '/')}/$omniInferMavenArtifact/$omniInferMavenVersion"
    from(File(omniInferMavenRepo, publicationPath)) {
        into(publicationPath)
    }
}

val verifyAarDependencyMetadata by tasks.registering {
    description = "Verify Maven metadata keeps runtime dependencies transitive and pinned"
    group = "verification"
    dependsOn("publishReleasePublicationToOmniInferLocalRepository")
    doLast {
        val publicationPath = "${omniInferMavenGroup.replace('.', '/')}/$omniInferMavenArtifact/$omniInferMavenVersion"
        val publicationDir = File(omniInferMavenRepo, publicationPath)
        val pomFile = File(publicationDir, "$omniInferMavenArtifact-$omniInferMavenVersion.pom")
        val aarFile = File(publicationDir, "$omniInferMavenArtifact-$omniInferMavenVersion.aar")

        if (!pomFile.isFile) {
            throw GradleException("Missing generated POM: ${pomFile.absolutePath}")
        }
        if (!aarFile.isFile) {
            throw GradleException("Missing generated AAR: ${aarFile.absolutePath}")
        }

        fun dependencyVersion(groupId: String, artifactId: String): String? {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
            val dependencies = document.getElementsByTagName("dependency")
            for (index in 0 until dependencies.length) {
                val dependency = dependencies.item(index) as? Element ?: continue
                fun childText(name: String): String? {
                    val nodes = dependency.getElementsByTagName(name)
                    return if (nodes.length > 0) nodes.item(0).textContent.trim() else null
                }
                if (childText("groupId") == groupId && childText("artifactId") == artifactId) {
                    return childText("version")
                }
            }
            return null
        }

        if (requireLiteRtLmInPublication && !enableLiteRtLm) {
            throw GradleException(
                "This publication is expected to include LiteRT-LM. " +
                    "Set -Pomniinfer.backend.litert_lm=true, or explicitly pass " +
                    "-Pomniinfer.publication.require_litert_lm=false for a custom trimmed artifact."
            )
        }

        if (enableLiteRtLm || requireLiteRtLmInPublication) {
            val publishedLiteRtVersion = dependencyVersion(
                "com.google.ai.edge.litertlm",
                "litertlm-android",
            )
            if (publishedLiteRtVersion == null) {
                throw GradleException(
                    "Generated POM does not declare com.google.ai.edge.litertlm:litertlm-android. " +
                        "Third-party apps would have to add LiteRT-LM manually."
                )
            }
            if (publishedLiteRtVersion != liteRtLmVersion) {
                throw GradleException(
                    "Generated POM declares LiteRT-LM $publishedLiteRtVersion, expected $liteRtLmVersion."
                )
            }
            if (isDynamicDependencyVersion(publishedLiteRtVersion)) {
                throw GradleException("Generated POM uses dynamic LiteRT-LM version: $publishedLiteRtVersion")
            }
        }

        ZipFile(aarFile).use { zip ->
            val x86Entries = zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("jni/x86_64/") }
                .toList()
            if (x86Entries.isNotEmpty()) {
                throw GradleException(
                    "OmniInfer AAR must not package x86_64 native libraries: ${x86Entries.joinToString()}"
                )
            }
        }
    }
}

tasks.named("bundleMavenCentralPublication") {
    dependsOn(verifyAarDependencyMetadata)
}


// ---------------------------------------------------------------------------
// Engine package (downloaded-runtime mode)
//
// Mirrors upstream OmniInfer: `gradle :omniinfer-server:bundleEnginePackage`
// produces
//   build/distributions/engine/omniinfer-engine-<version>-arm64-v8a.zip
//   build/distributions/engine/omniinfer-engine-<version>-arm64-v8a.zip.sha256
// and then reopens the finished zip and verifies every entry against the manifest.
//
// Layout inside the zip (consumed by OmniInferEngineDownloader/Loader):
//   manifest.json
//   lib/arm64-v8a/*.so
// ---------------------------------------------------------------------------

/** System libraries that are supplied by the platform and never packaged. */
val systemLibNames = setOf(
    "libc.so", "libm.so", "libdl.so", "libz.so", "liblog.so", "libandroid.so",
    "libstdc++.so", "libc++_shared.so", "libunwind.so", "libOpenSLES.so",
    "libEGL.so", "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so", "libvulkan.so",
    "libnativewindow.so", "libsync.so", "libjnigraphics.so",
)

fun isSystemLib(name: String): Boolean =
    name in systemLibNames || name.startsWith("libcdsprpc") || name.startsWith("libadsprpc")

/**
 * Minimal ELF64 little-endian reader returning the DT_NEEDED soname list.
 * Section headers are used when present (the normal case for AGP-stripped
 * libraries); the program-header view is the fallback for fully stripped files.
 */
fun readElfNeeded(file: File): List<String> {
    val bytes = file.readBytes()
    if (bytes.size < 64 ||
        bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
        bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
    ) {
        return emptyList()
    }
    if (bytes[4] != 2.toByte() || bytes[5] != 1.toByte()) {
        throw GradleException("Unsupported ELF class/endianness in ${file.name} (expected ELF64 LE)")
    }
    val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    fun u16(offset: Long): Int = buffer.getShort(offset.toInt()).toInt() and 0xFFFF
    fun u32(offset: Long): Long = buffer.getInt(offset.toInt()).toLong() and 0xFFFFFFFFL
    fun u64(offset: Long): Long = buffer.getLong(offset.toInt())
    fun str(offset: Long): String {
        var end = offset.toInt()
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        return String(bytes, offset.toInt(), end - offset.toInt(), Charsets.UTF_8)
    }

    val shoff = u64(0x28L)
    val shentsize = u16(0x3AL).toLong()
    val shnum = u16(0x3CL)

    var dynOffset = -1L
    var dynSize = -1L
    var strtabOffset = -1L

    if (shoff > 0L && shnum > 0) {
        var dynLink = -1L
        for (index in 0 until shnum) {
            val base = shoff + index.toLong() * shentsize
            if (u32(base + 4L) == 6L) { // SHT_DYNAMIC
                dynOffset = u64(base + 0x18L)
                dynSize = u64(base + 0x20L)
                dynLink = u32(base + 0x28L)
            }
        }
        if (dynLink >= 0L) {
            strtabOffset = u64(shoff + dynLink * shentsize + 0x18L) // .dynstr
        }
    }

    // Fallback: no section headers (or no SHT_DYNAMIC) -> use program headers.
    if (dynOffset < 0L) {
        val phoff = u64(0x20L)
        val phentsize = u16(0x36L).toLong()
        val phnum = u16(0x38L)
        val loads = mutableListOf<LongArray>() // vaddr, offset, filesz
        if (phoff > 0L && phnum > 0) {
            for (index in 0 until phnum) {
                val base = phoff + index.toLong() * phentsize
                when (u32(base)) {
                    1L -> loads += longArrayOf(u64(base + 0x10L), u64(base + 0x08L), u64(base + 0x20L))
                    2L -> {
                        dynOffset = u64(base + 0x08L)
                        dynSize = u64(base + 0x20L)
                    }
                }
            }
        }
        if (dynOffset >= 0L) {
            var pos = dynOffset
            val stop = dynOffset + dynSize
            while (pos + 16L <= stop) {
                if (u64(pos) == 5L) { // DT_STRTAB
                    val strtabVaddr = u64(pos + 8L)
                    loads.forEach { load ->
                        val vaddr = load[0]
                        if (strtabVaddr >= vaddr && strtabVaddr < vaddr + load[2]) {
                            strtabOffset = load[1] + (strtabVaddr - vaddr)
                        }
                    }
                    break
                }
                pos += 16L
            }
        }
    }

    if (dynOffset < 0L || strtabOffset < 0L) return emptyList()

    val needed = mutableListOf<String>()
    var pos = dynOffset
    val stop = dynOffset + dynSize
    while (pos + 16L <= stop) {
        val tag = u64(pos)
        val value = u64(pos + 8L)
        if (tag == 0L) break // DT_NULL
        if (tag == 1L) needed += str(strtabOffset + value) // DT_NEEDED
        pos += 16L
    }
    return needed
}

val engineDistributionDir = layout.buildDirectory.dir("distributions/engine")

/** Explicit engine lib source; override with -Pomniinfer.engine.jni_dir=... */
val engineJniDirOverride: File? =
    findProperty("omniinfer.engine.jni_dir")?.toString()?.let(::File)

/**
 * Locate the merged arm64-v8a native libs of the release variant. The AGP
 * intermediate path has moved between versions, so scan for it and fall back to
 * the current layout instead of hard-coding one.
 */
fun resolveEngineJniDir(): File {
    engineJniDirOverride?.let { return it }
    val intermediates =
        layout.buildDirectory.dir("intermediates/merged_native_libs").get().asFile
    intermediates.walkTopDown()
        .filter { it.isDirectory && it.name == "arm64-v8a" && it.parentFile?.name == "lib" }
        .maxByOrNull { it.lastModified() }
        ?.let { return it }
    return File(intermediates, "release/mergeReleaseNativeLibs/out/lib/arm64-v8a")
}

/** Extra library file names (or prefixes) to include, comma separated. */
val engineExtraLibs: List<String> =
    findProperty("omniinfer.engine.extra_libs")?.toString().orEmpty()
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }

val engineBackends: List<String> = buildList {
    if (enableLlamaCpp) add("llama.cpp-cpu")
    if (enableLlamaCppHtp) add("llama.cpp-htp")
    if (enableLiteRtLm) { add("litert-lm-cpu"); add("litert-lm-gpu") }
    if (enableMnn) { add("mnn-cpu"); add("mnn-opencl"); add("mnn-vulkan") }
}.distinct()

val bundleEnginePackage by tasks.registering {
    group = "distribution"
    description = "Build a downloadable OmniInfer engine zip plus its .sha256 checksum"
    dependsOn(tasks.matching { it.name == "mergeReleaseNativeLibs" })
    outputs.dir(engineDistributionDir)

    doLast {
        val sourceDir = resolveEngineJniDir()
        if (!sourceDir.isDirectory) {
            throw GradleException(
                "Engine native lib dir not found: ${sourceDir.absolutePath}. " +
                    "Run :omniinfer-server:assembleRelease first, or pass -Pomniinfer.engine.jni_dir=..."
            )
        }
        val allLibs = sourceDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".so") }
            .sortedBy { it.name }
        if (allLibs.isEmpty()) {
            throw GradleException("No .so files found in ${sourceDir.absolutePath}")
        }

        // --- coreLibs: DT_NEEDED closure of the JNI bridge, dependencies first ---
        val neededBy = allLibs.associate { it.name to readElfNeeded(it) }
        val bridge = "libomniinfer-jni.so"  // OmniInferEngineManifest.JNI_BRIDGE_LIB
        if (bridge !in neededBy) {
            throw GradleException(
                "Engine build is missing $bridge in ${sourceDir.absolutePath}; cannot derive coreLibs."
            )
        }
        val ordered = mutableListOf<String>()
        val visiting = mutableSetOf<String>()
        fun visit(name: String) {
            if (name in ordered || name in visiting) return
            visiting += name
            neededBy[name].orEmpty()
                .filter { it != name && it in neededBy }
                .sorted()
                .forEach { visit(it) }
            visiting -= name
            ordered += name
        }
        visit(bridge)
        val coreLibs = ordered
        if (coreLibs.last() != bridge) {
            throw GradleException("coreLibs must end with $bridge, got $coreLibs")
        }

        // --- packaged set: core chain + ggml variants/accelerators (+ extras) ---
        val packaged = allLibs.filter { file ->
            val name = file.name
            name in coreLibs ||
                name.startsWith("libggml-cpu-") ||
                name.startsWith("libggml-htp-") ||
                name == "libggml-hexagon.so" ||
                name == "libggml-opencl.so" ||
                engineExtraLibs.any { name == it || name.startsWith(it) }
        }
        if (packaged.isEmpty()) {
            throw GradleException("Engine package would be empty; check the backend flags.")
        }

        packaged.flatMap { neededBy[it.name].orEmpty() }
            .filter { !isSystemLib(it) && it !in packaged.map { file -> file.name } }
            .distinct()
            .forEach { missing ->
                logger.warn("Engine package: dependency $missing is not packaged; the target device must supply it")
            }

        val engineVersion = omniInferMavenVersion
        val outDir = engineDistributionDir.get().asFile
        outDir.mkdirs()
        val zipName = "omniinfer-engine-$engineVersion-arm64-v8a"
        val zipFile = File(outDir, "$zipName.zip")
        val checksumFile = File(outDir, "$zipName.zip.sha256")
        zipFile.delete()

        fun sha256Of(file: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val libEntries = packaged.associate { file ->
            file.name to mapOf(
                "name" to file.name,
                "sha256" to sha256Of(file),
                "sizeBytes" to file.length(),
            )
        }
        val manifest = groovy.json.JsonOutput.toJson(
            mapOf(
                "formatVersion" to 1,
                "engineVersion" to engineVersion,
                "interfaceVersion" to 1,
                "abi" to "arm64-v8a",
                "minSdk" to 26,
                "backends" to engineBackends,
                "coreLibs" to coreLibs,
                "libs" to packaged.map { libEntries.getValue(it.name) },
            )
        )

        java.util.zip.ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            packaged.forEach { file ->
                zip.putNextEntry(java.util.zip.ZipEntry("lib/arm64-v8a/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        val zipSha = sha256Of(zipFile)
        checksumFile.writeText("$zipSha  ${zipFile.name}\n")

        // --- verify the finished artifact against its own manifest ---
        val slurper = groovy.json.JsonSlurper()
        val parsed = slurper.parseText(manifest) as Map<*, *>
        val declared = (parsed["libs"] as List<*>).map { entry ->
            @Suppress("UNCHECKED_CAST")
            val map = entry as Map<String, Any?>
            map["name"] as String to map
        }.toMap()
        var seen = 0
        java.util.zip.ZipFile(zipFile).use { archive ->
            val manifestEntry = archive.getEntry("manifest.json")
                ?: throw GradleException("Engine zip is missing manifest.json")
            val zipManifest = archive.getInputStream(manifestEntry).use { it.readBytes().decodeToString() }
            val zipParsed = slurper.parseText(zipManifest) as Map<*, *>
            if (zipParsed["coreLibs"] != parsed["coreLibs"]) {
                throw GradleException("Engine zip manifest coreLibs mismatch")
            }
            val entries = archive.entries().toList()
            entries.forEach { entry ->
                if (entry.isDirectory) return@forEach
                val name = entry.name
                if (name == "manifest.json") return@forEach
                if (!name.startsWith("lib/arm64-v8a/") || !name.endsWith(".so")) {
                    throw GradleException("Engine zip has an unexpected entry: $name")
                }
                val libName = name.substringAfterLast('/')
                val declaredEntry = declared[libName]
                    ?: throw GradleException("Engine zip contains unlisted lib: $libName")
                val bytes = archive.getInputStream(entry).use { it.readBytes() }
                val declaredSize = (declaredEntry["sizeBytes"] as Number).toLong()
                if (bytes.size.toLong() != declaredSize) {
                    throw GradleException("Engine lib $libName size mismatch (${bytes.size} != $declaredSize)")
                }
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                if (digest != declaredEntry["sha256"]) {
                    throw GradleException("Engine lib $libName failed SHA-256 verification")
                }
                seen++
            }
        }
        if (seen != declared.size) {
            throw GradleException("Engine zip is missing ${declared.size - seen} declared lib(s)")
        }

        logger.lifecycle(
            "Engine package written: ${zipFile.absolutePath} " +
                "(${packaged.size} libs, ${coreLibs.size} core, ${packaged.sumOf { it.length() } / (1024 * 1024)} MiB)"
        )
        logger.lifecycle("Engine checksum:      ${checksumFile.absolutePath}")
        logger.lifecycle("Engine backends:      $engineBackends")
        logger.lifecycle("Engine coreLibs:      $coreLibs")
    }
}
