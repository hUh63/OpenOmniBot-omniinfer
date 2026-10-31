# Project-specific R8 rules.
#
# Android framework components, JNI methods, WebView JavaScript interfaces,
# Parcelable implementations, and common Android libraries are covered by the
# optimized Android defaults or by dependency consumer rules. Keep this file
# limited to reflection that is owned by this application.

# 上游 v0.6.3.4 同步：保留可读的类名/成员名。R8 的代码优化、无用代码剔除和资源压缩
# 仍全部开启，只是不再改名——开源 App 的崩溃堆栈、日志与线上排查要靠这些名字。
-dontobfuscate

# Runtime metadata used by Gson, Kotlin, and reflective generic type lookup.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
-keepattributes Signature,InnerClasses,EnclosingMethod

# FlutterEngineConnectionRegistry keys plugins by their concrete runtime class.
# Keep each FlutterPlugin implementation as a distinct class so R8 cannot merge
# unrelated plugins and make later registrations look like duplicates.
-keep,allowobfuscation,allowshrinking class * implements io.flutter.embedding.engine.plugins.FlutterPlugin {
    <init>(...);
}

# OkHttpManager resolves this value by its binary class and field names.
-keep class cn.com.omnimind.bot.BuildConfig {
    public static final java.lang.String BASE_URL;
}

# Gson constructs these models reflectively. Keep their class identity,
# constructors and fields: keeping fields alone lets R8 erase generic targets,
# merge model classes or remove Kotlin default constructors. Unlisted methods
# remain eligible for shrinking and optimization.
-keep class cn.com.omnimind.bot.agent.runtime.AcpAgentProfile {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.runtime.AcpAgentHealth {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.BuiltinSkillManifest {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.BuiltinSkillAsset {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.SkillRegistryEntry {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.AgentAlarmToolService$AlarmSoundSettings {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.AgentAlarmToolService$ExactAlarmRecordRaw {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.AgentAlarmToolService$ExactAlarmRecord {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.WorkspaceScheduledTaskScheduler$StoredTask {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.MemoryIndexEntry {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.PlatformEmbeddingGateway$EmbeddingResponse {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.agent.PlatformEmbeddingGateway$EmbeddingItem {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.mcp.RemoteMcpServerConfig {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.quicklog.QuickLogRecord {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.bot.quicklog.QuickLogWidgetSettings {
    <init>(...);
    <fields>;
}
-keepclassmembers,allowoptimization class cn.com.omnimind.baselib.config.ModelSceneConfigCache$CacheData {
    <fields>;
}
-keepclassmembers,allowoptimization class cn.com.omnimind.baselib.config.ModelSceneConfigCache$Metadata {
    <fields>;
}
-keep class cn.com.omnimind.baselib.llm.OfficialVlmOperationConfig {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.baselib.llm.AiRequestLogEntry {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.baselib.llm.ModelProviderConfigStore$StoredModelProviderProfile {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.baselib.llm.SceneModelBindingEntry {
    <init>(...);
    <fields>;
}
-keep class cn.com.omnimind.baselib.util.RuntimeLogEntry {
    <init>(...);
    <fields>;
}

# Some persisted enums do not yet declare @SerializedName values.
-keepclassmembers,allowoptimization enum cn.com.omnimind.** {
    <fields>;
}

# Logback is an optional JVM logging backend and is not packaged on Android.
-dontwarn ch.qos.logback.**

# Ktor's IntelliJ debugger detector probes these JVM-only management APIs.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# PDFBox-Android 2.0.27.0 deliberately makes JPEG 2000 decoding optional.
# JPXFilter checks Class.forName before using this class and reports a missing
# image reader. Our attachment reader extracts the text layer, not JPX pixels.
# https://github.com/TomRoush/PdfBox-Android#reading-jpx-images
-dontwarn com.gemalto.jp2.JP2Decoder
