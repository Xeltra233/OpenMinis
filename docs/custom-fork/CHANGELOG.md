# OpenMinis 二次开发更改记录 (Secondary Development Changelog)

本文档记录本项目相较于上游 OpenMinis 官方原版仓库的所有二次开发修改与新增功能，供团队查阅以及未来合并上游新版本代码时作为依据与比对索引。

### 版本命名规范 (`<upstream>-<fork>`)
- **前缀跟随上游**：基础版本号完全跟随上游（如当前上游版本为 `1.13`）；
- **后缀独立起算**：二开版本以后缀表示（如当前首发版本为 `1.13-1.0`）；
- **上游更新重新起算**：每当合并上游新版本（如上游升级为 `1.14`），二开后缀统一**重新从 `-1.0` 起算**（即 `1.14-1.0`）；在同个上游版本下的持续修复与优化则递增后缀（如 `1.13-1.1`）。
---

## 1. 最大思考深度支持 ULTRA (Elevate Max Thinking Level to ULTRA)
- **背景与目标**：
  上游原版将思考深度 ceiling 限制在 `xhigh`，无法选取和下发顶格思考强度 `ultra`。本次二开在 Android 和 iOS 双端全面解除了该限制，将默认天花板及未知兜底统一升级至 `ULTRA`。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/data/model/ProviderConfig.kt`
     - `ThinkingLevel.decoded` 安全解析兜底从 `XHIGH` 升级为 `ULTRA`。
  2. `src/android/app/src/main/java/com/openminis/app/provider/ThinkingLevelCatalog.kt`
     - `catalogMaxThinkingLevel` 默认兜底从 `XHIGH` 提升至 `ULTRA`；
     - `selectableThinkingLevels` 补充 `"ultra" to ThinkingLevel.ULTRA`。
  3. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatViewModel.kt`
     - `currentModelMaxThinkingLevel` 在没有模型元数据时的 fallback 提升至 `ThinkingLevel.ULTRA`。
  4. `src/android/app/src/main/java/com/openminis/app/config/ConfigBuiltins.kt` & `GroupsCollection.kt`
     - 枚举定义扩充 `"max"`, `"ultra"`。
  5. `src/android/app/src/main/java/com/openminis/app/ui/settings/ModelGroupDetailScreen.kt`
     - 分组最大思考深度计算兜底提升至 `ULTRA`。
  6. `src/ios/Providers/LLMTypes.swift` & `src/ios/Agent/Chat/AIChatViewModel.swift` & `src/ios/Views/Providers/ModelGroupDetailView.swift`
     - iOS 端对应枚举解码、天花板计算、选择器过滤同步提升至 `.ultra`。

---

## 2. 渠道前缀清洗与核心模型 ID 智能识别 (Channel Affix Stripping & Capability Matching)
- **背景与目标**：
  中转渠道（如 NewAPI、OneAPI、Antigravity 代理）常常带有通道前缀，例如 `[Antigravity渠道] gemini-3.8-flash-high`、`[Antigravity渠道] gemini-pro-agent`、`【某中转】gpt-4o`。原版代码使用纯前缀匹配或精准匹配，导致带渠道名的模型无法激活多模态（无法发图）以及无法开启深度思考。
- **新增模块**：
  - `src/android/app/src/main/java/com/openminis/app/data/model/ModelIdNormalizer.kt`
  - `src/ios/Providers/ModelIdNormalizer.swift`
  - 核心功能：
    - `stripChannelAffixes`: 递归清洗中英文括号 `[...]`, `【...】`, `(...)`, `（...）`, `<...>`, `{...}` 及冒号前缀；
    - `isVisionModel`: 检测模型是否属于 Gemini、GPT-4o、Claude-3/4、o1/o3/o4、VL 系列等视觉多模态模型；
    - `isReasoningModel`: 检测模型是否属于 Gemini 3/2.5 Pro、o1/o3/o4、DeepSeek-R1/V4、Claude-3.7 等思考模型。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/data/model/VoiceModality.kt`
     - `LLMModel.hasImageInput` 增加 `ModelIdNormalizer.isVisionModel` 智能启发式兜底，无论是否有渠道前缀均开启多模态。
  2. `src/android/app/src/main/java/com/openminis/app/provider/openai/OpenAIModelsApi.kt`
     - 解析 `/v1/models` 时，如果为视觉模型自动补齐 `["text", "image"]` 输入模态；如果为思考模型预设 `supportsReasoning = true`。
  3. `src/android/app/src/main/java/com/openminis/app/provider/ModelsDevApi.kt` & `src/ios/Providers/ModelsDevAPI.swift`
     - 增强 `findMatchingDevEntry`，支持先剥离渠道标签再查 catalog，若未直接命中则执行包含匹配与子串最长匹配，并对离线未收录模型执行能力增强。
  4. `src/android/app/src/main/java/com/openminis/app/provider/thinking/ThinkingRule.kt` & `ThinkingRuleResolver.kt`
     - 规则作用域匹配与 Gemini 思考参数组装兼容剥离前后缀后的模型标识。
  5. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatViewModel.kt`
     - `currentModelSupportsReasoning` 结合 `isReasoningModel` 解除思考功能门禁。

---

## 3. AI Agent 任务体系与可折叠 UI (AI Todo / Task / Goal Tools & Collapsible UI)
- **背景与目标**：
  让 AI 在多步骤执行过程中能主动调用工具记录和汇报任务进度；在手机屏幕上完整展示任务容易遮挡视线，因此设计了可折叠卡片。
- **新增模块**：
  - `src/android/app/src/main/java/com/openminis/app/ui/chat/CollapsibleTodoCard.kt`
    - 吸附在输入框上方；
    - 折叠状态：紧凑展示 `Goal: ... 📋 进度 (2/5) [展开]`；
    - 展开状态：显示全部 Todo 项的状态勾选图标、序号、标题与详情，并提供底部折叠按钮。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/tools/AgentTools.kt`
     - 注册 `todo`、`task`、`goal` 三个 Agent 工具。
  2. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatModels.kt`
     - 增加 `SessionTodo` 与 `SessionGoal` 数据模型。
  3. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatViewModel.kt`
     - 增加 `sessionTodos`、`sessionGoal`、`todosCollapsed` 状态流；
     - 增加 `executeTodoTool`、`executeTaskTool`、`executeGoalTool` 调度执行器；
     - 注册 `/goal` 快捷指令及快捷执行。
  4. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatScreen.kt`
     - 在聊天界面挂载 `CollapsibleTodoCard`。

---

## 4. Agent 运行时系统提示词区 (SYSTEM.md / APPEND_SYSTEM.md)
- **背景与目标**：
  用户需要在会话运行时便捷查看与编辑系统提示词，并能够追加个人提示词（不污染主系统提示词）。
- **新增模块**：
  - `src/android/app/src/main/java/com/openminis/app/ui/chat/SystemPromptSheet.kt`
    - 双文件 Tab 编辑界面：
      - `SYSTEM.md`：核心系统提示词编辑器，支持重置为默认；
      - `APPEND_SYSTEM.md`：追加提示词编辑器，自动拼接至系统提示词尾部生效；
    - 保存直接持久化写入存储。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatViewModel.kt`
     - 提供 `loadSystemPromptFiles()` 与 `saveSystemPromptFiles()`；
     - `buildSystemPrompt()` 优先加载 `SYSTEM.md`，并在末尾附加 `APPEND_SYSTEM.md`。
  2. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatScreen.kt`
     - 在右上角菜单提供“系统提示词 (SYSTEM.md)”入口，点击弹出 `SystemPromptSheet`。

---

## 5. 持续集成与发布工作流 (GitHub Actions APK Workflow)
- **新增文件**：
  - `.github/workflows/build-apk.yml`
    - 自动提取 Git Tag 或 `build.gradle.kts` 中的 `versionName`；
    - 构建发布 Android Release/Debug APK，并在 GitHub Releases 自动打上版本号。

---

## 6. 会话任务与目标本地文件持久化 (Session Goals & Todos Persistence)
- **背景与目标**：
  在之前的实现中，会话内的 Goal 与 Todo 任务仅保存在内存 `StateFlow` 中。用户退出聊天界面、重启应用或在不同会话间切换时，任务列表会丢失或出现交叉污染。本次二开实现了每个会话独立的本地 JSON 文件持久化与生命周期自动加载/清理。
- **存储设计**：
  - 存储目录：应用私有数据目录下的 `sessions/`。
  - 文件命名：
    - `session_{sessionId}_goals.json`：存储会话当前的 `SessionGoal`（目标说明、已完成/失败状态、更新时间）。
    - `session_{id}_todos.json`：存储会话的 `List<SessionTodo>`（各项任务的 ID、标题、详情、状态 `pending/in_progress/completed/cancelled`）。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatViewModel.kt`
     - 在 `loadSession(sessionId)` 时同步调用 `loadSessionTasks(sessionId)` 从对应 JSON 文件恢复 Goal 和 Todos；若文件不存在则重置为空，杜绝会话间串扰；
     - 在 `executeTodoTool`、`executeTaskTool`、`executeGoalTool` 每次状态流更新后，触发 `saveSessionTasks(sessionId)` 原子写入磁盘；
     - 增加清晰的 JSON 序列化/反序列化及异常防护机制。
  2. `src/android/app/src/test/java/com/openminis/app/ui/chat/AgentTodoTaskGoalTest.kt`
     - 增加持久化数据往返与解析单元测试验证。

---

## 7. SSH 服务器配置管理与 AI 查验工具 (SSH Server Management & AI Agent Tool)
- **背景与目标**：
  为便于用户在手机端集中管理远端 Linux 服务器、配合 AI Agent 自动获取运维环境信息，二开实现了完整的 SSH 服务器管理模块，并向 AI 暴露了专属只读工具。
- **新增模块**：
  - `src/android/app/src/main/java/com/openminis/app/data/model/SSHServerModel.kt`
    - 数据模型 `SSHServer`（包含 `id`, `name`, `host`, `port`, `username`, `authType` (PASSWORD / PRIVATE_KEY), `password`, `privateKey`, `passphrase`, `group`, `tags`, `notes`, `createdAt`, `updatedAt`）。
  - `src/android/app/src/main/java/com/openminis/app/data/repository/SSHServerRepository.kt`
    - 仓库层，支持 CRUD、分组检索、敏感密码/私钥的本地受保护存储、以及连接连通性测试（Socket / SSH Banner 检测）。
  - `src/android/app/src/main/java/com/openminis/app/ui/settings/SSHServersScreen.kt`
    - 设置页面中的专用管理界面，支持服务器列表展示、按组过滤、新增/编辑/删除弹窗、表单校验以及一键“测试连接”功能。
- **AI Agent 工具集成**：
  - `src/android/app/src/main/java/com/openminis/app/tools/AgentTools.kt`
    - 注册 `ssh_servers` 工具给 AI Agent；
    - AI 可自主调用该工具列出用户已配置的服务器列表（包含主机、端口、用户名、分组与备注，密码与私钥严格脱敏屏蔽），辅助 Agent 自动决定远程运维的目标机器。
  - `src/android/app/src/main/java/com/openminis/app/ui/navigation/AppNavigation.kt` & `SettingsScreen.kt`
    - 设置主菜单新增“SSH 服务器”入口及路由。

---

## 8. UI Sheets 弹窗规范与全量中文本地化 (UI Sheets Presentation & Full Localization)
- **背景与目标**：
  1. 系统提示词 Sheet 和 Todo 卡片折叠交互在部分高刷新率或分屏 Android 设备上存在手势冲突；
  2. 新增的设置项、SSH 管理和任务卡片需要完整的简繁体中文支持。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatScreen.kt`
     - 优化 `SystemPromptSheet` 的状态悬挂与遮罩层逻辑，修复退出重建时的闪烁。
  2. `src/android/app/src/main/java/com/openminis/app/ui/chat/CollapsibleTodoCard.kt`
     - 优化卡片布局层级，确保在小屏设备上不遮挡底部输入框及快捷工具栏。
  3. `src/android/app/src/main/res/values/strings.xml` & `values-zh/strings.xml` & `values-zh-rTW/strings.xml`
     - 补充 `ssh_servers_title`、`add_ssh_server`、`test_connection`、`system_prompt_title`、`todo_tasks_title` 等多项国际化资源字符串，达到全量中文界面。

---

## 9. 依赖分发与 Release 构建修复 (rclone.aar Packaging & Release Alignment)
- **背景与目标**：
  在 CI/CD 和常规开发者环境中，构建 Android APK 需依赖 `:app:mergeReleaseNativeLibs` 所需的 `rclone.aar`。原上游将 `src/android/app/libs/rclone.aar` 放入 `.gitignore`，导致 GitHub Actions 在全新拉取代码编译 Release APK 时报 `Could not find :rclone:` 致命错误。
- **修改与优化**：
  1. `.gitignore`
     - 取消对 `src/android/app/libs/rclone.aar` 的忽略，将其作为预编译二进制归档入库，使拉取仓库后即可直接构建，无需本地配置 Go/gomobile 交叉编译工具链。
  2. `.github/workflows/build-apk.yml`
     - 增加 `Verify Android native libraries` 步骤，在执行 Gradle 编译前校验 `rclone.aar` 完整性；
     - 更新 Release 发布说明，自动囊括所有二次开发核心特性。
  3. `README.md` & `UpdateChecker.kt`
     - 移除 App Store 标识，将下载与应用内升级检查全面重定向至 `https://github.com/Xeltra233/OpenMinis/releases`。

---

## 10. Rootfs 资产解封与在线下载双保险兜底 (Alpine Rootfs Packaging & Fallback)
- **背景与目标**：
  在沙箱管理安装 Alpine Linux 时，由于原 `.gitignore` 排除了 `*.tar.gz`，导致 `alpine-minirootfs.tar.gz` 缺失引发 `FileNotFoundException`。此外若打包时遗漏资产，用户无任何自愈手段。
- **修改文件列表**：
  1. `.gitignore`：白名单放行 `!src/android/app/src/main/assets/alpine-minirootfs.tar.gz`。
  2. `.github/workflows/build-apk.yml`：构建前自动核验并在缺失时自动下载 Alpine Minirootfs 3.21.3 aarch64 官方压缩包。
  3. `src/android/app/src/main/java/com/openminis/app/sandbox/RootfsManager.kt`：增加清华源、阿里源、官方 CDN 在线自动下载兜底，并在下载过程中上报安装进度。
  4. `src/android/app/src/main/java/com/openminis/app/ui/sandbox/RootfsManagementViewModel.kt`：支持 `RootfsInstallState.Downloading` 状态流及百分比展示。

---

## 11. 模型分组思考深度 UI 阶梯滑动条重构 (Thinking Intensity Slider)
- **背景与目标**：
  原版在模型分组详情中启用思考深度时，将 6 个档位（Min, Low, Medium, High, XHigh, Ultra）强行塞在单行 `SingleChoiceSegmentedButtonRow` 中，且套在第二层突兀的圆角卡片内，在绝大多数手机视口下出现严重换行、文字截断和 UI 撕裂。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/ui/settings/ModelGroupDetailScreen.kt`：
     - 移除嵌套的突兀子卡片；
     - 将 6 键分段按钮重构为与“上下文上限”设计规范一致的 `ThinkingIntensitySlider` 阶梯滑动条；
     - 支持当前选中档位紫调高亮显示与平滑拖动吸附。

---

## 12. 系统提示词规范化与全局设置常驻入口 (System Prompt Refactor & Global Settings)
- **背景与目标**：
  1. 聊天菜单原图标误用了终端图标 `Icons.Default.Terminal`，与“打开终端”产生视觉混淆，标题带有冗余的 `(SYSTEM.md)` 尾缀；
  2. 原弹窗内存在编造的虚拟提示词，违背软件真实动态构造；
  3. 设置主界面缺失全局的“系统提示词”常驻管理入口。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatScreen.kt`：
     - 菜单图标换为专属齿轮图标 `Icons.Default.Settings`；
     - 标题简化为纯净规范的“系统提示词”；
  2. `src/android/app/src/main/java/com/openminis/app/ui/chat/SystemPromptSheet.kt`：
     - 清除所有预设的人工编造假数据，默认留空自动走系统内置底层系统词；提供一键“填入内置提示词”与“清空”操作；
  3. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatViewModel.kt`：
     - 读写对齐 `/var/minis/memory/` 目录规范，同时支持 `APPEND.SYSTEM.md` 与 `APPEND_SYSTEM.md` 向后兼容；
  4. `src/android/app/src/main/java/com/openminis/app/ui/settings/SettingsScreen.kt`：
     - 在“Agent 运行时”分组中新增“系统提示词”常驻入口与持久化读写。

---

## 13. 全模态能力智能匹配增强 (Comprehensive Multimodal Modality Matching)
- **背景与目标**：
  中转接口（NewAPI / OneAPI 等）通常只下发基础模型 ID，不声明 `architecture.input_modalities`。原版仅对视觉模型增加 `"image"`，导致 Gemini、Qwen-Omni、Claude 等模型在运行时缺失音频、视频、PDF 支持，甚至在系统提示词中向模型误报“无法处理音频/视频/PDF”。
- **权威证据检索与核查**：
  通过官方开发者文档与技术报告验证了各模型输入/输出模态特性（Google Gemini 2.5/3.x 原生支持文本、图像、音频、视频、PDF；Anthropic Claude 原生支持文本、图像、PDF；Qwen3-Omni 原生支持文本、图像、音频、视频并支持流式语音输出；Wan2.2 视频生成；Kolors/Z-Image 图像生成；CosyVoice2/SenseVoice 专属语音）。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/data/model/ModelIdNormalizer.kt` & `src/ios/Providers/ModelIdNormalizer.swift`：
     - 新增 `isAudioInputModel`、`isVideoInputModel`、`isPdfInputModel`、`isImageOutputModel`、`isAudioOutputModel`、`isVideoOutputModel` 智能匹配；
  2. `src/android/app/src/main/java/com/openminis/app/data/model/VoiceModality.kt`：
     - 扩充 ASR/TTS 专用语音模型识别规则（包含 SenseVoice、CosyVoice 等），智能兜底 `hasAudioInput` 与 `hasAudioOutput`；
  3. `src/android/app/src/main/java/com/openminis/app/provider/ModelsDevApi.kt`：
     - 在 `applyDevData` 中，根据全模态规则全面补齐 `image`、`audio`、`video`、`pdf` 输入模态及对应生成输出模态。
  4. `src/android/app/src/test/java/com/openminis/app/data/ModelIdNormalizerTest.kt`：
     - 补充针对 Gemini、Claude、Qwen-Omni、Wan2.2、CosyVoice 的全模态断言测试并全部通过。
