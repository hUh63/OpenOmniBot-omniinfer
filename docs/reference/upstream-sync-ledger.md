# 上游同步台账（OmniBot / OmniInfer）

本分支（`omniinfer-apk`）是"聚合仓库 + 本地推理版"，与上游 `omnimind-ai/OmniBot` 早已分叉。
这份台账记录**每次同步到哪个上游点、拿了什么、为什么没拿什么**，避免下次同步从零开始比对。

## 当前同步基线

| 项目 | 基线 | 说明 |
| --- | --- | --- |
| OmniBot | `v0.6.3.6`（commit `9ba1263e`，2026-10-25） | 之后上游 `main` 只有 2 个纯 UI 提交，未合并（见下） |
| OmniInfer | `v0.3.31` 的 `android/` 子树 | 上游 `android/` 在 2026-09-18 之后没有任何提交 |

## 已合并的上游修复

### 2026-10-26：v0.6.3.4 ~ v0.6.3.6 中的可移植修复（commit `e0e526d0`）

- `3f227478` — `ProviderModelOption` 每个字段补 `@field:SerializedName`：该对象被原生侧缓存、
  又被 Flutter 按 key 读取，不写死 key 时字段名一变缓存就解不出来。
- `3f227478` — `proguard-rules.pro` 加 `-dontobfuscate`（代码优化/剔除/资源压缩照旧），
  开源 App 的崩溃堆栈与日志要靠真实类名定位。
- `1be076e0` — `packaging.dex.useLegacyPackaging = true`（dex 压缩进 APK，体积更小）。
- `8c255455` — WebChat 请求体改用 Gson 解析（异构 `Map<String, Any?>` 交给 MCP 的 kotlinx
  转换器会整请求失败），非 JSON 415、非法 JSON 400；`session/bootstrap` 响应改 `respondJson`。
- `907314d7` — Agent 展示顺序统一（`acp/agents.json` 里 Kimi Code 移到末位、`AgentWebPlugin`
  的能力列表与 `quickLaunchOrder` 同步、WebChat 新建菜单按 id 排掉重复的「小万」）。

### 更早（历史记录，仅供追溯）

- 终端 `libtalloc` 包源：上游 `31cef63f` / `2e90ab9b` 修的是同一件事，本分支更早用自己的
  方式修好（`ReTerminal/core/main/build.gradle.kts` 固定 `libtalloc_2.5.0_aarch64.deb` + sha256，
  并按正则挑实体 `.so`），无需再取。
- Android 仅 arm64 打包、`jniLibs.useLegacyPackaging`：本分支发布流程早已等价实现。

## 未合并（附原因）

### 上游 `v0.6.3.6` 之后的 2 个提交（2026-10-26）

| 提交 | 内容 | 为什么不合并 |
| --- | --- | --- |
| `26ac7f4d` | 会话生命周期与界面处理（drawer 搜索头 `home_search_header.dart` 新增 429 行、`conversation_slidable`、chat 输入区紧凑态等 21 个文件） | 涉及文件与本分支 **全部 DIVERGED**，其中 `chat_input_compact_actions.dart` / `chat_input_compact_composer.dart` / `settings_ui.dart` / `agent_run_details_sheet.dart` 本分支根本不存在；抽出单文件会变成孤儿组件 |
| `a5334740` | 首次使用引导改版（新增 `TutorialPage.providerSelection` 一步 + `provider_selection_page.dart`；`environment_progress_page` 删掉 onBack/onContinue/onRetry 改由 footer 承载；spotlight tour 步骤常量抽取） | 属于**联动改动**：枚举、构造签名、调用方（`onboarding_choice_page.dart` 与上游分叉 170+ 行）必须同时改。只取单个文件会编译不过或半新半旧 |

### 更早判定为"新功能、非修复"的（需单独排期，不能当作同步）

- Na 云端空间 / 托管工作区（`3e5dbf59`、`5c923421`、`e5bbfe92` 等）
- ChatGPT OAuth Provider（`0a5e283c`、`3a5e70ba`、`be59c865`）
- Pi Agent 接入（`d491b30f`）、OpenCode V2 + 官方 WebUI（`3309f632`、`b1496448`）
- 设置页/工具目录的现代化 UI 重构（`3c43c8c5`、`27003c3f`、`46b43ec4`、`817e7f22`、`ce6d2b4c`）
- HTML 预览改原生 Activity（`8dfae985`）、splash 静态矢量（`d2f0200a`）

## 下次同步怎么做

1. 拉上游最新 tag 与 `main` 的提交列表（sandbox 里 `api.github.com` 单次响应约 310KB 就会被截断，
   用 `per_page=25` 分页，别用 `/compare`）：

   ```bash
   GET /repos/omnimind-ai/OmniBot/releases?per_page=4
   GET /repos/omnimind-ai/OmniBot/commits?sha=main&per_page=25&page=N
   ```

2. 对**纯修复类**提交（`fix:` 前缀）取 `files` 列表，逐文件判断三方状态：
   我们的 blob sha 是否等于上游父提交的 blob sha。相同 = 可直接套用上游新版；
   不同 = 已分叉，必须人工逐 hunk 判断。

3. OmniInfer 只看 `android/` 子树是否有新提交：

   ```bash
   GET /repos/omnimind-ai/OmniInfer/commits?sha=v<latest>&path=android&per_page=15
   ```

   本分支内嵌的 SDK 还额外带了自己的加固（JNI 层 generate 串行化、session shared_ptr 生命周期、
   `mt6991` 线程默认值），**覆盖上游文件前先看这些加固是否还在**。

4. 合并后按惯例过编译门（workflow `363430385`，约 25 分钟），再考虑发版。
