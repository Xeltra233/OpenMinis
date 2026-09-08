# OpenMinis 二次开发更改记录 (Secondary Development Changelog)

本文档记录本项目相较于上游 OpenMinis 官方原版仓库的所有二次开发修改与新增功能，供团队查阅以及未来合并上游新版本代码时作为依据与比对索引。

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
