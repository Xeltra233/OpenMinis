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

---

## 14. 恢复 Alpine Linux PRoot 核心资产与沙箱稳定性 (Alpine Sandbox PRoot Asset Fix)
- **背景与目标**：
  上游 1.13 版本依赖 `libproot.so` 与 `proot-aarch64` 进行 Linux PRoot 沙箱隔离运行。原 `.gitignore` 排除了 `*.so`，导致应用内启动沙箱终端时因找不到 `libproot.so` 发生致命崩溃 `java.io.FileNotFoundException: /data/user/0/com.openminis.app/files/usr/lib/libproot.so`。
- **修改与优化**：
  1. `.gitignore` 放行 `!src/android/app/src/main/jniLibs/arm64-v8a/libproot.so` 与 `!src/android/app/src/main/assets/proot-aarch64`。
  2. 提取并归档上游 1.13 官方二进制资产入库，保证克隆后开箱即用。
  3. 单元测试（1240 个）全面验证通过。

---

## 15. 深度思考 6 档位系统全链路对齐与 UI 规范重塑 (Complete 6-Tier Thinking Depth System)
- **背景与目标**：
  1. 上游 OpenMinis 默认仅支持 4 档（低、中、高、極高），未能开放更高级的 `MAX` 与 `ULTRA`（至高/極致）档位；且在不同中转和模型 ID 下存在思考天花板被强行截断的问题。
  2. 模型分组详情页根据用户视觉反馈与官方原始设计对齐，保留 6 档单行分段按钮（低、中、高、極高、最高、極致），去除排版阶段的 rank 截断。
  3. 聊天界面 Header Badge、`ThinkingLevelSheet`、快捷选择器完整支持 6 档档位无截断展示与选择。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/provider/ThinkingLevelCatalog.kt`
     - 修复剥离渠道标签（`stripChannelAffixes`）后的规则匹配；
  2. `src/android/app/src/main/java/com/openminis/app/ui/settings/ModelGroupDetailScreen.kt`
     - 恢复分段按钮（`SingleChoiceSegmentedButtonRow`）规范，全部开放 6 档（低、中、高、極高、最高、極致）；
  3. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatViewModel.kt`
     - 移除 chat header sheet 和 picker 中的天花板 rank 截断，思考模型直接暴露完整 6 档；
  4. 模拟器端到端真实测试验证（`[Antigravity渠道] gemini-3.8-flash-high` 在 `MAX` / `極高` 强度下正常发起深度推理与工具调用）。

---

## 16. 对标 oh-my-pi 重构 `/goal` 自主 Agent 闭环与任务状态机 (Goal Mode Autonomous Execution & State Machine)
- **背景与目标**：
  原版 `/goal <目标>` 仅仅在本地保存了状态，在发送逻辑中直接拦截并吞掉了用户输入，不会唤起 LLM 进行任何工作，导致用户输入完 `/goal` 后必须再次“正常说话”才能触发响应；且无论目标是否完成，任何指令都能随意触发，缺乏任务状态机管理。
- **参考 `can1357/oh-my-pi` 的重构方案**：
  1. **输入即启动（Kickoff）**：
     - 用户发送 `/goal <任务>` 或 `/goal set <任务>` 时，在更新本地 `_sessionGoal` 状态的同时，**立即触发 `sendMessage("Goal: $objective")` 发送对话轮次**，无需任何额外输入。
  2. **系统提示词动态注入 `<goal_context>`**：
     - 在 `ChatViewModel.kt` 的 `buildSystemPrompt()` 中注入当前活动的 Goal 目标、执行准则与已注册的 Todo/Task 任务列表（`<session_todos>`），引导模型进入自主规划与工具执行模式（自动使用 `todo`/`task` 分解任务、自主调用 `browser_use` / `shell_execute` 并在完成时调用 `goal(action="complete", summary="...")`）。
  3. **基于已注册任务的状态机约束**：
     - 综合检查当前目标（`_sessionGoal`）与已注册任务（`_sessionTodos`）：
       - `/goal resume`：检查是否存在已注册且未完成的任务。若目标已 `completed` 且所有任务均已完成，**明确禁止 resume** 并提示用户使用 `/goal <新任务>` 开启新目标；若存在进行中/待处理任务或处于 paused 状态，则恢复目标并立即发起带有任务列表的继续推进轮次。
       - `/goal pause`：已完成的目标禁止 pause；仅对未完成/进行中的目标生效。
       - `/goal drop` / `/goal clear` / `/goal cancel`：清空当前目标及磁盘持久化。
      - `/goal show` / `/goal status` / `/goal`：展示当前目标的详细状态、完成摘要、已注册任务统计（共几项、完成几项、待处理几项）与上下文可用指令提示。

---

## 17. 系统提示词独立存储架构重构与记忆文件列表净化 (System Prompt Isolation & Memory File Cleanup)
- **问题与根因分析**：
  1. **重复提示词文件生成**：之前在保存追加系统提示词时，为了兼容性同时写入了 `APPEND_SYSTEM.md` 与 `APPEND.SYSTEM.md` 两个文件，导致磁盘上冗余生成两个追加提示词文件；
  2. **系统提示词污染记忆文件列表**：之前系统提示词直接借用 `MemoryRepository` 存储在 `/var/minis/memory/`（`minis-global/memory/`）目录下；而 `MemoryRepository.listAllFiles()` 仅过滤排除了 `GLOBAL.md`，导致记忆界面的“檔案”列表将 `SYSTEM.md`、`APPEND_SYSTEM.md`、`APPEND.SYSTEM.md` 误作为每日对话记忆日志列出；
  3. **架构职责混淆**：系统提示词属于 Agent 运行时的全局指令配置，不属于基于日期的对话上下文持久化记忆（Conversation Memory）。
- **重构与修复方案**：
  1. **新建独立的 `SystemPromptRepository` 架构**：
     - 存储路径物理隔离至 `/var/minis/prompts/`（Android 本地对应 `minis-global/prompts/`），与 `minis-global/memory/` 彻底解耦；
     - 确立统一且规范的文件常量名 `APPEND_SYSTEM.md`，彻底废弃 `APPEND.SYSTEM.md` 双写逻辑；
     - 内置无缝平滑迁移（Migration & Self-Cleaning）：应用启动或首次加载时，自动从旧 `memory` 目录检测并迁移 `SYSTEM.md` 与 `APPEND_SYSTEM.md` / `APPEND.SYSTEM.md`，迁移后自动删除旧目录中的残留文件，实现用户无感知升级且保证不丢数据。
  2. **`MemoryRepository` 引入严格的每日记忆正则白名单**：
     - 定义严格白名单规则：`DAILY_LOG_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}\.md$""")`；
     - 在 `listAllFiles()`、`getMemory()` 与 `searchMemory()` 中，仅允许展示和搜索 `GLOBAL.md` 以及严格匹配 `YYYY-MM-DD.md` 格式的真实对话记忆日志；
     - 彻底封死任何非记忆文件（系统提示词、临时文件等）泄漏到记忆文件列表的可能。
  3. **全局调用层解耦替换**：
     - 在 `MinisApp` 中集中提供 `SystemPromptRepository` 单例；
     - `ChatViewModel`、`ChatScreen` 与 `SettingsScreen` 全面改由 `SystemPromptRepository` 负责系统提示词与追加提示词的加载、保存与注入（`buildSystemPrompt()`）；
     - `BackupExporter` 中系统提示词读取对齐新路径。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/data/repository/SystemPromptRepository.kt`（新增独立仓储）
  2. `src/android/app/src/main/java/com/openminis/app/data/repository/MemoryRepository.kt`（白名单正则过滤）
  3. `src/android/app/src/main/java/com/openminis/app/MinisApp.kt`（注册系统提示词单例）
  4. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatViewModel.kt`（提示词构建与保存解耦）
  5. `src/android/app/src/main/java/com/openminis/app/ui/chat/ChatScreen.kt`（对话菜单系统提示词注入对齐）
  6. `src/android/app/src/main/java/com/openminis/app/ui/settings/SettingsScreen.kt`（设置界面提示词保存对齐）
  7. `src/android/app/src/test/java/com/openminis/app/data/repository/SystemPromptRepositoryTest.kt`（自动化迁移与读写单元测试）
  8. `src/android/app/src/test/java/com/openminis/app/data/repository/MemoryRepositoryFilterTest.kt`（记忆文件白名单单元测试）
- **真机与模拟器验证**：
  - 在 BlueStacks 5 模拟器上实机验证升级迁移：成功将旧环境中的追加提示词自动迁移并清理旧目录；
  - 进入“設定 -> 記憶 -> 檔案”复测：原本显示的重复提示词（`APPEND_SYSTEM.md` 和 `APPEND.SYSTEM.md`）彻底消失，仅展示 `GLOBAL.md`，界面完全净化；
  - 重新编辑并保存系统提示词与追加提示词，再次检查记忆列表，确认绝不再被污染，且聊天注入正常生效。

---

## 18. 记忆文件可见性回归修复与数据安全加固 (Memory File Visibility Regression & Data-Loss Hardening)
- **问题与用户反馈**：
  1. 升级到 `1.13-1.1` 后，用户反馈「`soul.md` 没了」：进入「設定 -> 記憶 -> 檔案」看不到 `SOUL.md`，但磁盘上 `minis-global/memory/SOUL.md` 的内容完好（358 B），属于**纯 UI 可见性回归，并非文件被删除**；
  2. 用户同时反馈「之前设置的 `global.md` 内容没了」，需要定位真正会造成内容丢失的代码路径并封堵，而不是归因于用户操作。
- **根因分析**：
  1. **SOUL.md 被正则白名单误伤**：第 17 节把 `MemoryRepository.listAllFiles()` / `getMemory()` / `searchMemory()` 的过滤条件收紧为 `DAILY_LOG_PATTERN = ^\d{4}-\d{2}-\d{2}\.md$`。该白名单只放行 `GLOBAL.md` 与严格日期命名的每日日志，`SOUL.md` 既不在正则内、也不等于 `GLOBAL.md`，于是被整体过滤掉。磁盘文件与系统提示词注入链路（`SoulStore` -> `buildSystemPrompt`）均未受影响；
  2. **唯一可造成 GLOBAL.md 内容丢失的真实路径**：`MemoryRepository.readFile` 把任何读取失败吞成空字符串 `""`，编辑器据此显示为空白；用户按下保存时就会把**唯一副本**覆盖为空文件。修复前的 `readFile` 无法区分「文件不存在/为空」与「读取失败」；
  3. **删除即不可恢复**：`SoulStore` 只会在文件缺失时重新写入 `DEFAULT_CONTENT`，一旦 `SOUL.md` / `GLOBAL.md` 被删除，用户的个性化内容无法找回；
  4. 记忆目录（`minis-global/memory`）自基线以来未变更，升级流程本身不会删除该目录——已通过模拟器原地覆盖安装（`adb install -r`，`firstInstallTime` 保持不变）实测确认。
- **修复方案**：
  1. **明确白名单语义（列表层）**：`listAllFiles()` 固定为 `GLOBAL.md`（常驻，缺失也显示）+ 存在时的 `SOUL.md` + 每日日志（按日期倒序）；系统提示词文件 `PROMPT_FILES = {SYSTEM.md, APPEND_SYSTEM.md, APPEND.SYSTEM.md}` 在**列表、搜索、保存、删除**四个层面全部排除，保持第 17 节的净化目标不回退；
  2. **不可删除保护**：`MemoryFileInfo` 新增 `canDelete`；UI 仅在 `canDelete = true` 时渲染删除按钮；`deleteFile()` 在数据层直接拒绝 `GLOBAL.md` 与 `SOUL.md`（UI 与仓储双重保护），每日日志仍可删除；
  3. **读写语义分离**：新增 `readFileOrNull(name)`——`null` = 不可读、`""` = 不存在/空；编辑页在读取失败时禁用保存按钮并提示 `memory_editor_load_failed`，杜绝「读取失败 -> 空白编辑器 -> 保存覆盖唯一副本」的路径；
  4. **原子写入**：`saveFile()` 改为先写 `*.tmp` 再 `renameTo` 覆盖（失败回退原地写），避免写入中断产生半截文件；`saveGlobalMd` / `loadGlobalMd` 统一走同一套读写路径；
  5. **版本号单一来源**：CI 在构建时把 Release tag 的版本号通过 `-Pminis.versionName` 注入 APK，避免再出现「tag 是 1.13-1.1、APK 内嵌 1.13-1.0」的漂移（见第 20 节）。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/data/repository/MemoryRepository.kt`（白名单语义、`SOUL_FILE`/`PROMPT_FILES`/`canDelete`、`readFileOrNull`、原子写入、删除保护）
  2. `src/android/app/src/main/java/com/openminis/app/ui/settings/MemoryManagementScreen.kt`（删除按钮按 `canDelete` 渲染、编辑页读取失败禁用保存并提示）
  3. `src/android/app/src/main/res/values/strings.xml`、`values-zh/strings.xml`、`values-zh-rTW/strings.xml`（新增 `memory_editor_load_failed`）
  4. `src/android/app/src/test/java/com/openminis/app/data/repository/MemoryRepositoryFilterTest.kt`（重写为 7 条契约测试）
- **回归测试与验证证据**：
  1. **单元测试（当前工作区实跑）**：`cd src/android && ./gradlew :app:testDebugUnitTest` -> `BUILD SUCCESSFUL`，**1250 个测试全部通过、0 失败**；`MemoryRepositoryFilterTest` 覆盖：`GLOBAL.md` + `SOUL.md` + 每日日志三类可见、`SOUL.md` 在 `GLOBAL.md` 缺失时仍可见、`searchMemory` 只搜 `GLOBAL.md` 与每日日志、原子写入不留 `.tmp`、拒绝写入系统提示词名、`readFileOrNull` 区分缺失与不可读、`deleteFile` 拒绝 `GLOBAL.md`/`SOUL.md` 但可删每日日志；
  2. **模拟器红态复现（修复前 1.13-1.0）**：「設定 -> 記憶」只显示 `GLOBAL.md 86 B`，磁盘上存在的 `SOUL.md 358 B` 不显示；
  3. **模拟器绿态验收（修复后 1.13-1.2，原地覆盖安装）**：
     - `adb install -r` 覆盖安装，`versionCode 26 -> 27`、`versionName 1.13-1.0 -> 1.13-1.2`，`firstInstallTime` 保持 `2026-09-08 23:00:52`（**未卸载、未清数据**）；
     - `minis-global/memory/GLOBAL.md`（86 B）与 `SOUL.md`（358 B）的大小与修改时间均未变化；
     - 打开 `GLOBAL.md` 编辑器：升级前写入的内容完整显示；追加一行并保存后，磁盘内容 = 旧内容 + 新行（**未被截断**），界面提示「已儲存」；
     - 打开 `SOUL.md` 编辑器：内容完整可读；
     - 强制停止并重启 App 后复查：`記憶` 列表显示 `GLOBAL.md`（103 B，含新增行）与 `SOUL.md`（358 B），内容持久化；
     - `GLOBAL.md` / `SOUL.md` 行右侧只有箭头、**没有删除按钮**（每日日志行仍可删除）。
- **升级与迁移说明**：本次修复不改变任何存储路径与文件格式，`minis-global/memory/` 无需迁移；旧版本升级只需原地覆盖安装（`adb install -r` 或应用市场更新），**不要卸载或清除数据**。用户此前「看不到 SOUL.md」的文件一直在磁盘上，升级后自动重新出现在列表中，无需任何手工操作。
- **版本与回滚**：修复随 `v1.13-1.2` 发布。回滚方式为安装 `v1.13-1.1` 或 `v1.13-1.0` 的 APK（同一 `applicationId`，可原地覆盖）；由于数据结构未变，回滚不会造成数据损坏，但回滚后 `SOUL.md` 会重新从记忆列表中消失（文件仍在磁盘上）。

---

## 19. Debug 服务器稳健性与 Android 9 / API 28 读取崩溃修复 (Debug Server Resilience & API-28 readNBytes Crash)
- **问题现象**：通过 `adb forward` 使用 Debug RPC（`debug.readFile` 等）时，调用一次 `debug.readFile` 会得到空响应；此后**所有** RPC（包括 `GET /` 健康检查）永久超时，端口仍处于监听状态，必须强制停止并重启 App 才能恢复。
- **根因分析（两处叠加）**：
  1. **API 兼容性**：`DebugRPCHandler.handleReadFile` 使用 `InputStream.readNBytes(int)`，该方法是 Java 9 / Android 13（API 33）新增 API。目标模拟器为 Android 9（API 28），运行时抛出 `java.lang.NoSuchMethodError: No virtual method readNBytes(I)[B in class Ljava/io/FileInputStream`；
  2. **异常逃逸 + 作用域取消**：`NoSuchMethodError` 属于 `Error` 而非 `Exception`，逃出了连接处理的 `catch (e: Exception)`；同时 `DebugServer` 的协程作用域为 `CoroutineScope(Dispatchers.IO)`（**没有 `SupervisorJob`**），子协程失败会取消整个作用域的父 Job，导致 accept 循环终止——端口仍被占用，服务从此静默，且不会打印任何错误。
- **修复方案**：
  1. `DebugRPCHandler.kt` 新增私有 `readAtMost(stream, limit)`：以 64 KB 分块读取到 `ByteArrayOutputStream`，彻底移除对 `readNBytes` 的依赖（主源码中唯一一处 API 33+ 调用，已全量审计）；
  2. `DebugServer.kt`：作用域改为 `CoroutineScope(Dispatchers.IO + SupervisorJob())`；accept 循环与连接处理均改为 `catch (t: Throwable)` 并打印完整堆栈；
  3. 单个请求加 `withTimeoutOrNull(REQUEST_TIMEOUT_MS = 30_000L)`，避免请求永久占用连接；
  4. 每个请求记录 `Log.i(TAG, "$method $path")`，处理失败时返回 HTTP 500 JSON（而不是直接断连），移除连接处理里的 `runBlocking`。
- **验证证据**：
  1. 修复前：受控探针脚本依次调用 `readFile` -> `ls` -> `viewTree` -> `appInfo` -> `GET /`，首调用返回「Remote end closed connection without response」，其余全部超时，服务进程仍在但 RPC 永久不可用；
  2. 修复作用域/异常处理（`readAtMost` 尚未替换）时：`debug.readFile` 返回 HTTP 500 + JSON 错误，**端口与服务保持存活**，证明异常已被 containment；
  3. 修复 `readAtMost` 后：同一探针序列 `readFile(GLOBAL.md)`、`readFile(SOUL.md)`、`readFile(probe.txt)`、`ls`、`writeFile`、`viewTree`、`appInfo` 全部成功返回，最终健康检查为 alive；设备日志中可见 `readNBytes` 的 `NoSuchMethodError` 堆栈（修复前）。
- **修改文件列表**：
  1. `src/android/app/src/main/java/com/openminis/app/debug/DebugRPCHandler.kt`
  2. `src/android/app/src/main/java/com/openminis/app/debug/DebugServer.kt`
- **影响范围与回滚**：DebugServer 只在 debug 构建中启动，不影响 release 产物；本次为纯加固与兼容性修复，无数据结构变更，回滚只需安装旧版本 APK。

---

## 20. 版本号单一来源与发布产物一致性 (Single-Source Version Stamping & Release Consistency)
- **问题现象**：`v1.13-1.1` 的 GitHub Release 页面与 asset 文件名都是 `1.13-1.1`，但下载的 APK 内嵌 `versionName` 仍是 `1.13-1.0`（tag/产物漂移，用户无法凭系统设置判断实际版本）。
- **根因分析**：`src/android/app/build.gradle.kts` 中的 `versionName` 是手写字面量，发版时只打了 tag 而没有同步递增；CI 仅执行打包，不把 tag 版本号注入构建。
- **修复方案**：
  1. `build.gradle.kts` 改为优先读取 `-Pminis.versionName`（CI 注入），缺省回退到仓库内字面量（保持**单行**便于 CI 解析）；`versionCode` 手工递增；
  2. `.github/workflows/build-apk.yml` 的 `Determine Version` 步骤在无法解析版本号时直接 `::error::` 失败，不再静默使用错误版本；
  3. Build 步骤把解析出的版本号以 `-Pminis.versionName=<version>` 传给 Gradle；
  4. Release body 改为按实际版本号动态生成，并同步更新「本版修复」清单。
- **验证证据**：
  1. 本地构建：`./gradlew :app:assembleDebug -Pminis.versionName=1.13-1.2` -> `aapt2 dump badging` 输出 `versionCode='27' versionName='1.13-1.2'`；
  2. CI 解析逻辑本地模拟：`Determine Version` 的 grep 表达式对当前分支输出 `1.13-1.2`；
  3. 远端 Release：`v1.13-1.2` 的 asset `OpenMinis-1.13-1.2-release.apk` / `debug.apk` 下载后 `aapt2 dump badging` 校验内嵌版本号与 tag 一致。
- **回滚**：若 CI 版本注入失效，回退方案是手工修改 `build.gradle.kts` 中的字面量并递增 `versionCode` 后重新打 tag（tag 不可强制覆盖，需 use 新后缀版本号）。

---

## 21. 全屏图片查看器手势修复：放大后拖拽跟手 + 适配视图恢复翻页 (Image Viewer Zoom/Pan Gesture Fix)
- **问题现象**（用户报告）：点开 AI 返回的图片进入全屏查看后，把图片放大（双击 2.5x 或捏合）再拖拽，图片移动明显慢于手指；模拟器真手势实测：300 px 手指位移只带来 110 px 图片位移（比例 0.37 ≈ 1/2.5）。
- **附带发现**（同一手势块）：在未放大的适配（Fit）视图下左右滑动**完全无法翻页**——同目录双图 gallery 一次都切不过去，因为内层手势把整个滑动序列吃掉了。
- **根因分析**：
  1. **拖拽速度**：`detectTransformGestures` 回调中的 `pan` 是**局部（层内）坐标**——`Modifier.graphicsLayer(scaleX = scale)` 位于 `pointerInput` **外层**，指针管线会先做该图层变换的逆变换，所以 `scale = 2.5` 时 300 px 手指位移只上报 120 px；而同一个 `graphicsLayer` 的 `translationX/Y` 是**父坐标（屏幕）像素**，不受 scale 影响。原实现 `offsetX += pan.x` 缺少 `× scale` 补偿，图片因此以 1/scale 的速度移动。
  2. **翻页被吞**：`detectTransformGestures` 一旦超过 touch slop 就**无条件 consume** 每个样本（与回调如何使用无关）；它嵌套在 `HorizontalPager` 的页面里，父级 pager 永远收不到未被消费的事件 → 适配视图下无法翻页。「放大时阻止翻页」其实是这个副作用的副产品。
- **修复方案**：
  1. 新增共享状态与手势数学 `src/android/app/src/main/java/com/openminis/app/ui/components/ZoomPanTransform.kt`：
     - `ZoomPanTransform(scale, offsetX, offsetY)` 与 `gestureBy(panX, panY, zoom)`：平移量按**当前** scale 补偿后累加（当前 scale 才是指针管线做逆变换时使用的那一档），缩放夹在 `1f..8f`，缩回 1x 时偏移自动归零居中；
     - `toggledByDoubleTap()`：双击在 1x 与 2.5x 之间切换并复位偏移；
     - `detectViewerGestures(isZoomed, onGesture)`：替代框架探测器——只有**放大状态下的平移**或**任意多指手势**（捏合放大）才 consume；适配视图下的单指横滑**不消费**并直接交还给父级 pager。
  2. `ImageGalleryViewer.kt`（聊天图片 / 用户气泡附件 / 文件预览多图 gallery）与 `FullscreenImageViewer.kt`（输入框粘贴、拍照图片查看器）改为共用上述状态与探测器，避免两份拷贝再次分叉。
  3. 新增单测 `src/android/app/src/test/java/com/openminis/app/ui/components/ZoomPanTransformTest.kt`（7 例）：把设备上量到的「300 px → 120 px 局部位移」场景固化为 `= 300 px` 的回归断言，并覆盖 1:1 补偿、8x 夹取、缩回 1x 归零、双击切换。
- **验证证据**（同一模拟器、同一测试图，RED → GREEN 均为 `adb shell input swipe` 真手势 + 截图模板匹配量化）：
  1. 修复前（`v1.13-1.2` 装机版）：双击 2.5x 后拖拽 300 px → 位移 110 px（比例 0.37，模板匹配 sse = 0.0）；
  2. 修复后（随 `1.13-1.3` 发布，`adb install -r` 就地升级且 `firstInstallTime` 未变）：同样双击 2.5x 拖拽 300 px → 位移 275 px（比例 0.92，差额来自触摸 slop）；
  3. 修复前：适配视图横滑后截图与滑动前**字节完全一致**（未翻页）；修复后：B 图 → A 图翻页成功，反向滑动也能回到 B；
  4. 放大状态下朝「下一页」方向横滑仍**不翻页**（截图仍是当前图，同时 61.2% 像素发生变化 = 确实在平移）；双击复位后的视图与放大前的适配视图字节一致；
  5. 单测：`ZoomPanTransformTest` 7/7 通过；全量 `:app:testDebugUnitTest` 1257 tests / 0 failures / 0 errors（140 suites）。
- **影响范围与回滚**：仅影响两个全屏图片查看器的状态、手势与绘制参数；无数据结构、无存储、无网络、无版本号变更。回滚可整体还原 `ImageGalleryViewer.kt` / `FullscreenImageViewer.kt` 并删除新增的 `ZoomPanTransform.kt` 与其单测；旧版缺陷行为为「放大后拖拽速度 = 手指速度 ÷ 缩放倍数」与「适配视图无法翻页」。
