# OpenMinis 上游升级与代码合并指南 (Upstream Upgrade Guide)

本文档面向后续维护者，指导如何在保持本项目二开特性的前提下，安全、平滑地拉取官方原版（Upstream）仓库的新版本更新。

---

## 1. 准备工作：配置 Upstream 远端
在本地克隆仓库中，确保已添加官方上游仓库：
```bash
# 查看当前 remotes
git remote -v

# 如果没有 upstream，添加官方上游仓库（如原作者仓库）
git remote add upstream https://github.com/OpenMinis/Minis.git

# 获取上游最新分支与标签
git fetch upstream --tags
```

---

## 2. 推荐的合并流程：基于分支的 Merge

### 步骤 A：创建合并工作分支
```bash
git checkout main
git pull origin main
git checkout -b sync-upstream-$(date +%Y%m%d)
```

### 步骤 B：执行合并
```bash
git merge upstream/main
```

### 步骤 C：解决冲突策略说明
若遇到冲突，参考 `docs/custom-fork/CHANGELOG.md` 中列出的二开修改点进行保留：
1. **思考深度相关文件** (`ThinkingLevelCatalog.kt` / `ProviderConfig.kt` / `LLMTypes.swift`)：
   - 保留 `ThinkingLevel.ULTRA` 作为上限和安全兜底（将原版可能的 `XHIGH` 改回 `ULTRA`）。
2. **多模态与模型 ID 识别** (`ModelIdNormalizer` / `VoiceModality.kt` / `ModelsDevApi.kt`)：
   - 确保 `stripChannelAffixes` 和 `isVisionModel` 逻辑不被官方原版覆盖。
3. **Agent 工具与提示词** (`AgentTools.kt` / `ChatViewModel.kt` / `SystemPromptSheet.kt`)：
   - 确保 `todo`、`task`、`goal` 工具在 `makeAgentTools` 中注册；
   - 确保 `buildSystemPrompt` 中加载 `SYSTEM.md` 与 `APPEND_SYSTEM.md` 的逻辑保留。
4. **可折叠卡片** (`ChatScreen.kt` / `CollapsibleTodoCard.kt`)：
   - 保留输入框上方的 `CollapsibleTodoCard` 挂载。

---

## 3. 合并后验证与构建

### 步骤 A：运行单元测试
```bash
cd src/android
./gradlew :app:testDebugUnitTest
```
确保全套单元测试通过，尤其是：
- `ThinkingLevelTest`
- `ModelIdNormalizerTest`
- `NativeVisionModalityTest`
- `AgentTodoTaskGoalTest`
- `OpenAIProviderTest`

### 步骤 B：本地编译 APK
```bash
./gradlew :app:assembleDebug
```
确认在 `src/android/app/build/outputs/apk/debug/app-debug.apk` 生成可用 APK。

### 步骤 C：推送并触发 Actions 构建
```bash
git checkout main
git merge sync-upstream-$(date +%Y%m%d)
git push origin main
```
如果是发布新版本，打上对应版本 Tag（例如 `v1.14`）并推送，GitHub Actions 会自动打包并发布带版本号的 Release。
