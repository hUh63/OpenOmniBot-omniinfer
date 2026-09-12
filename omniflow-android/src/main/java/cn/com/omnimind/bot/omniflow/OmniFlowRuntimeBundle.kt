package cn.com.omnimind.bot.omniflow

import java.io.File
import java.io.InputStream
import java.util.Properties

data class OmniFlowRuntimeManifest(
    val version: String,
    val protocol: String,
    val capabilities: Set<String>,
    val bridgeContractSha256: String,
    val pythonVersion: String,
    val omniFlowCommit: String,
    val omniFlowSourceSha256: String,
    val omniTransferCommit: String,
    val omniTransferSourceSha256: String,
    val omniTransferCheckpoint: String,
    val numpyVersion: String,
    val jsonRepairVersion: String,
)

fun parseOmniFlowRuntimeManifest(input: InputStream): OmniFlowRuntimeManifest {
    val properties = Properties().apply { input.use(::load) }
    fun required(name: String): String = properties.getProperty(name)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error("omniflow_runtime_manifest_missing:$name")
    val version = required("runtime.version")
    require(version.matches(Regex("[A-Za-z0-9._-]+"))) { "omniflow_runtime_version_invalid" }
    fun sourceSha256(name: String): String = required(name).lowercase().also { value ->
        require(value.matches(Regex("[a-f0-9]{64}"))) {
            "omniflow_runtime_source_sha256_invalid:$name"
        }
    }
    val capabilities = required("runtime.capabilities")
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
    require(capabilities.isNotEmpty()) { "omniflow_runtime_capabilities_invalid" }
    val omniTransferCheckpoint = required("omnitransfer.checkpoint")
    require(
        !omniTransferCheckpoint.startsWith('/') &&
            ".." !in omniTransferCheckpoint.split('/'),
    ) { "omniflow_runtime_checkpoint_path_invalid" }
    return OmniFlowRuntimeManifest(
        version = version,
        protocol = required("runtime.protocol"),
        capabilities = capabilities,
        bridgeContractSha256 = sourceSha256("bridge.contract.sha256"),
        pythonVersion = required("runtime.python"),
        omniFlowCommit = required("omniflow.commit"),
        omniFlowSourceSha256 = sourceSha256("omniflow.source.sha256"),
        omniTransferCommit = required("omnitransfer.commit"),
        omniTransferSourceSha256 = sourceSha256("omnitransfer.source.sha256"),
        omniTransferCheckpoint = omniTransferCheckpoint,
        numpyVersion = required("numpy.version"),
        jsonRepairVersion = required("json_repair.version"),
    )
}
