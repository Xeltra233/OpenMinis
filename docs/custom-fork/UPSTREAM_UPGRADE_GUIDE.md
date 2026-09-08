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
   - 确保 `stripChannelAffixes`、`isVisionModel`、`isAudioInputModel`、`isVideoInputModel`、`isPdfInputModel` 及其全模态输出生成规则保留，避免带渠道名的模型丢失模态。
3. **Agent 工具与提示词** (`AgentTools.kt` / `ChatViewModel.kt` / `SystemPromptSheet.kt` / `SettingsScreen.kt`)：
   - 确保 `todo`、`task`、`goal` 工具在 `makeAgentTools` 中注册；
   - 确保 `buildSystemPrompt` 中加载 `SYSTEM.md` 与 `APPEND.SYSTEM.md` 的逻辑保留；
   - 确保设置主页的“系统提示词”常驻入口保留。
4. **UI 卡片与滑动条交互** (`ChatScreen.kt` / `CollapsibleTodoCard.kt` / `ModelGroupDetailScreen.kt`)：
   - 保留输入框上方的 `CollapsibleTodoCard` 挂载；
   - 保留模型分组中的 `ThinkingIntensitySlider` 阶梯滑动条，避免回退到挤压的 6 键分段按钮。
5. **SSH 服务器与数据持久化** (`SSHServerRepository.kt` / `SSHServersScreen.kt` / `ChatViewModel.kt`)：
   - 保留 `SSHServerModel`、`SSHServerRepository` 与设置界面的路由；
   - 保留 `session_{id}_todos.json` 与 `session_{id}_goals.json` 的持久化逻辑。
6. **Rootfs 镜像与 rclone 依赖** (`rclone.aar` / `alpine-minirootfs.tar.gz` / `RootfsManager.kt`)：
   - 保持 `rclone.aar` 与 `alpine-minirootfs.tar.gz` 在 Git 中追踪；
   - 保持 `RootfsManager.kt` 中的清华源/阿里源在线下载自愈兜底机制。

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
- `SSHServerModelTest`
- `OpenAIProviderTest`

### 步骤 B：本地编译 APK
```bash
./gradlew :app:assembleDebug
```
确认在 `src/android/app/build/outputs/apk/debug/app-debug.apk` 生成可用 APK。

### 步骤 C：版本号规范与推送触发构建
1. **版本号命名规范 (`<upstream>-<fork>`)**：
   - **基础原则**：版本号前段完全跟随上游版本号，后段以短横线 `-` 衔接二开专属后缀版本（格式为 `<上游版本>-<二开版本>`，如 `1.13-1.0`）。
   - **新版本重算机制**：每当合并上游发布的新基础版本（如上游从 `1.13` 升级到 `1.14`），二开后缀**必须重新从 `-1.0` 起算**（即版本号重置为 `1.14-1.0`）。
   - **同版迭代机制**：若上游版本未变，二开自身的功能增补或修复仅递增后缀（例如：`1.13-1.0` -> `1.13-1.1` -> `1.13-1.2`）。
   - **文件修改**：更新 `src/android/app/build.gradle.kts` 中的 `versionName`（如 `"1.13-1.0"`）和递增 `versionCode`。

2. **推送分支与发布 Tag**：
```bash
git checkout main
git merge sync-upstream-$(date +%Y%m%d)
git push origin main

# 打上对应的二开版本 Tag 触发 GitHub Actions 自动 Release：
git tag v1.13-1.0
git push origin v1.13-1.0
```
GitHub Actions 会自动提取该版本号，构建 `OpenMinis-1.13-1.0-release.apk` 与 `debug.apk`，并在 GitHub Releases 发布带版本号的正式 Release。
