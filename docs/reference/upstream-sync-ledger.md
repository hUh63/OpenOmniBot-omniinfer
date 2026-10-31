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

### 2026-10-27 复核：`26ac7f4d` / `a5334740` 逐文件三方比对

这次不是「大概看一眼」，而是把两个提交的 **26 个 lib 文件**全部拉齐三份内容
（ours = 本分支 `omniinfer-apk`，base = 上游 `9ba1263e`，theirs = 上游对应提交），
逐文件跑 `git merge-file -p --diff3` 并统计冲突：

| 三方状态 | 文件数 | 文件 |
| --- | --- | --- |
| 与上游父提交完全一致（可直接套用） | 3 | `conversation_slidable.dart`、`pages/environment_progress_page.dart`、`widgets/onboarding_footer.dart` |
| 本分支不存在（上游新增/我们已删） | 8 | `home_search_header.dart`(新)、`provider_selection_page.dart`(新)、`home_drawer_home.dart`、`chat_input_compact_actions.dart`、`chat_input_compact_composer.dart`、`agent_run_details_sheet.dart`、`omni_circle_button.dart`、`settings_ui.dart` |
| 已分叉 | 15 | `chat_page.dart`、`chat_page_ui.dart`、`chat_app_bar.dart`、`chat_spotlight_tour.dart`、`chat_input_agent_controls.dart`、`chat_input_area.dart`、`home_drawer.dart`、`home_drawer_conversation_list.dart`、`onboarding_choice_page.dart`、`onboarding_definitions.dart`、`pages/provider_page.dart`、`common_app_bar.dart`、`glass_popup.dart`、`omni_segmented_slider.dart` |

分叉文件的三方合并共产出 **32 处冲突**，其中 `home_drawer.dart` 的合并结果还会
引入 5 个我们根本不存在的依赖（`home_search_header` / `omni_circle_button` /
`settings_ui` / `omni_menu` / `conversation_context_menu`）—— 说明整块套用这条路
在本分支走不通：我们的抽屉、顶栏、引导、输入区都是自己重写的一套。

**本次实际拿到的（已提交）**

- `conversation_slidable.dart`：整文件换成上游新版（全滑 dismiss 后按
  `_dismissalGeneration` 重建 Slidable key，解决「归档/删除失败回滚或搜索结果
  仍在时，这一行永久滑不动」）。新增 `followHabitualHand` / `resetAfterFullSwipe`
  两个开关。
- 调用点补 `resetAfterFullSwipe: true`：`chat_history_page.dart`、
  `chat_history_conversation_item.dart` —— 归档页/搜索结果里全滑后行会留下，
  必须重建控制器。

**本次明确不取的（不是漏了，是取了会坏）**

- `chat_app_bar` 的菜单锚点对齐、`omni_circle_button` 的尺寸常量：我们顶栏是重写的，
  锚点计算路径不同，套 hunk 只会得到半新半旧。
- `glass_popup` 动画时长/`RepaintBoundary`/`shadowProgress`：视觉调优，且我们版本已
  分叉（9 处冲突），收益低于回归风险。
- `home_search_header.dart`（429 行新文件）+ `home_drawer_home.dart` 重构：我们抽屉
  搜索用 `home_drawer_search_field.dart` + `home_drawer_search.dart`，两套实现不在
  一个坐标系里。
- `a5334740` 的 onboarding 改版（`provider_selection_page` 新增 + `provider_page`
  41/-110 + `onboarding_choice_page` 133/-70 + `environment_progress_page` 删回调 +
  `onboarding_footer`）：联动改动，枚举/构造/调用方必须同时改。若要做，应按「重写
  onboarding provider 选择流程」单独立项，而不是当作同步。
- `chat_spotlight_tour` 的步骤重构（`ChatSpotlightTour.modelStep` 常量、去掉 pet/island
  锚点）：我们 tour 的锚点集合与上游不同（有 pet/island），取常量会与本地锚点错位。

### 2026-10-28 复核结论：能合的都合完了，剩下的是「不该合」

把两个提交里 **全部 26 个 lib 文件**的 hunk 逐个过了一遍（不是只看三方状态），结论分三类：

**A. 已落地（1 个文件 + 2 个调用点）**
- `conversation_slidable.dart` 整文件（全滑 dismiss 后重建 Slidable key）。
- `chat_history_page.dart` / `chat_history_conversation_item.dart` 补
  `resetAfterFullSwipe: true`。

**B. 与我们同名但 hunk 无法独立落地（15 个，逐个给过理由）**
- `chat_app_bar` / `omni_circle_button` / `settings_ui` / `common_app_bar`：
  上游在给 `OmniCircleButton` 加 `iconSize`/`_navigationButtonSize`，并把菜单锚点
  对齐到「可见圆」；我们顶栏是重写的（不用 OmniCircleButton），套 hunk 只能得到
  半新半旧。
- `glass_popup`：动画时长/`RepaintBoundary`/`shadowProgress` 三处视觉调优，
  我们版本已分叉 9 处冲突，收益 < 回归风险。
- `omni_segmented_slider`：只加 `splashFactory/highlightColor` 两行去水波纹；我们的
  该组件结构不同，对应位置不存在这两个参数。
- `home_drawer` / `home_drawer_conversation_list` / `home_drawer_home` /
  `home_search_header`（新 429 行）：上游把抽屉搜索头抽成独立组件、并给「首页
  会话列表」加滑动归档；我们抽屉是 `home_drawer_search_field` +
  `home_drawer_search` + 自己的行组件，且抽屉列表本来就支持滑动归档 —— 取上游
  这组等于换一套实现。
- `chat_spotlight_tour` / `chat_page` / `chat_page_ui`：上游把 tour 从 5 步压成 4 步
  （去掉 pet/island 锚点，新增 actions 锚点）并抽 `ChatSpotlightTour.modelStep`；
  我们是 **6 步**且保留了 pet/island，常量与本地锚点会错位。
- `chat_input_agent_controls` / `chat_input_area` / `chat_input_compact_*`：
  配合上游 compact composer（我们未采用这套版式）。
- `onboarding_choice_page` / `pages/provider_page` / `pages/provider_selection_page`
  （新 123 行）/ `pages/environment_progress_page` / `widgets/onboarding_footer` /
  `onboarding_definitions`：一整组联动改动（新增独立「provider 选择页」、把
  onBack/onContinue/onRetry 从进度页挪进 footer、加 `TutorialPage.providerSelection`
  枚举值）。我们的 onboarding 与之分叉 170+ 行，只取任意一两个文件都编译不过或
  半新半旧。
- `agent_run_details_sheet` / `chat_input_compact_composer` 等：本分支不存在。
- `settings_ui` 的 `OmniMenuStyle.panelRadius`：我们既没有 `settings_ui.dart` 也没有
  `OmniMenuStyle`。

**C. 要真正跟上这几块 UI，只能单独立项（不是「同步」）**
1. onboarding 的 provider 选择流程重写（含新页、枚举、footer 分工）。
2. spotlight tour 步数模型改造（4 步 + actions 锚点），要连带动画/测试。
3. 抽屉搜索头抽组件（`home_search_header`）+ 首页会话列表滑动归档。

这三块各自都是 8~10 个文件的联动改动、必须真机验证，放进「同步」里做只会得到
编译门能过但体验半新半旧的版本。需要时单独排期。

### 上游 `v0.6.3.6` 之后的 2 个提交（2026-10-26，初判）

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

---

## 上游 `v0.7.0.2`（`bfe8ba1e99`，40 个提交 / 601 个文件，2026-10-30 评估）

这是 `v0.6.3.6` 之后的一次**大版本跃迁**（0.6.3.7 → 0.7.0.2），含 ACP 运行时重构、
FlClash 导航组件族、可选 Root 后端、会话历史读取架构重构等多条主线。逐文件三方比对后，
本轮只吸收与主线解耦的部分。

### 已吸收

| 提交 | 内容 | 落地方式 |
| --- | --- | --- |
| `aadb83dcc4` | Preserve Gson models from R8 shrinking | 三方合并 `app/proguard-rules.pro`（`-keepclassmembers,allowoptimization` → `-keep` + `<init>(...)`，保住类身份/构造器/泛型目标），0 冲突 |
| `ace39919fb` | Optimize cold startup and remove the launcher transition | 三方合并 `LauncherActivity.kt`（启动即 finish，不再等 MainActivity 覆盖）与 `MainActivity.kt`（splash `setOnExitAnimationListener` 立即移除、抽 `applyResponsiveOrientation` + `onConfigurationChanged`、分屏允许旋转），0 冲突 |
| `48e6350a0d` | Measure startup costs and defer cache and shader work | 只取 `ui/lib/app_bootstrap.dart`、`ui/lib/services/models_dev_catalog_service.dart`（本分支未分叉，0 冲突）；`App.kt` 因引入 `measureStartupStep` / `AppSecretStore.prepare` / workspace seed 线程化，与本分支启动序列（含 `LocalModelFeatureInstaller`）交叉，保留本地版 |
| `08daea9d1c` | Prefer compressed DEX packaging | 本分支 `dex.useLegacyPackaging` 本来就是 `true`；上游只是把它改成 gradle property 可关闭，无实质收益，不引入 |

### 未吸收（按依赖关系与改动性质）

| 提交 | 内容 | 为什么不合并 |
| --- | --- | --- |
| `084b44fb61` | 可选 Root 后端与系统设备控制（新增 `baselib/privileged` 包，约 30 文件） | **新功能**，且改动横跨 ACP（`XiaowanAcpConnection` / `OmniAgentExecutor` / `AgentToolRegistry` / `AgentToolDefinitions`）与本分支定制区 |
| `cf436b7e42` | 修正系统命令能力检测误判 | **依赖 `084b44fb61`**：新版 `ShizukuCapabilityManager` 直接 import `PrivilegedRequest/Result/Provider/Policy`，单取必然编译不过；要这条修复就得整体引入 Root 后端 |
| `765e1f8cae` | 限制历史展示与运行日志的内存占用（52 文件） | 本质是**会话历史读取架构重构**（新增 `HistoryMessageJsonReader` / `AgentHistoryDisplayPager` / `AgentHistoryDisplayProjection`），与本分支的分页 `scanned` 口径、按需快照、`refreshConversationMetadata` 定向更新深度交叉 |
| `4746538b1f`、`13fea761dc`、`e463001ce0`、`9a1d76878e`、`11b26b790d` | ACP runtime 生命周期与会话归属重构（5 轮） | 结构性重构，本分支保留自己的 ACP 集成与 OmniInfer 运行时 |
| `52562f9615`、`25b32e03ad` | 移除全局助手选择 / 移除内置 DeepSeek Harness（BREAKING） | 破坏性删除，需先确认本分支的功能取舍 |
| `d2b8b804c1`、`ad7f2c607b`、`c2486eba05`、`c472873696` | FlClash 导航与 `liquid_glass` 组件族、移除 Miuix、移除自定义预测性返回 | 引入整套新 UI 框架（新增约 15 个文件、上万行），必须单独一期连同集成点一起做 |
| `bfe8ba1e99` | 上下文贴纸 + 有界 API 重试 | 贴纸是新功能；重试策略需与 `AgentLlmClient` 既有退避/幂等守卫逐条比对 |
| `0edcb929f3`、`ddc51ffc34` | 侧边栏行距、筛选标签文案 | 改的是上游 `home_drawer_home.dart`，本分支已拆成 `home_drawer_*.dart`，需手工移植（收益低） |

### 教训（下次先查这个）

**上游某条修复是否可以单取，取决于它是否依赖「更早的新增包/新 API」。**
`cf436b7e42` 与 `084b44fb61` 就是典型：修的是老文件的 bug，但新版实现被搬进了新包，
单取老文件会引用不存在的类。判断顺序应是：先看 `files` 里有没有 `status=added` 的包，
再看被改文件的新 import 指向哪里。

