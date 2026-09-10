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
1. **思考深度全链路与分段按钮** (`ThinkingLevelCatalog.kt` / `ProviderConfig.kt` / `ModelGroupDetailScreen.kt` / `ChatViewModel.kt` / `LLMTypes.swift`)：
   - 保留 `ThinkingLevel.ULTRA` 作为上限和安全兜底（将原版可能的 `XHIGH` 改回 `ULTRA`）；
   - 保留模型分组中的 6 档单行分段按钮（`SingleChoiceSegmentedButtonRow`：低、中、高、極高、最高、極致），避免回退到被 rank 截断的旧布局；
   - 保留 `ChatViewModel.kt` 中思考模型直接提供完整 6 档可选能力。
2. **多模态与模型 ID 识别** (`ModelIdNormalizer` / `VoiceModality.kt` / `ModelsDevApi.kt`)：
   - 确保 `stripChannelAffixes`、`isVisionModel`、`isAudioInputModel`、`isVideoInputModel`、`isPdfInputModel` 及其全模态输出生成规则保留，避免带渠道名的模型丢失模态。
3. **Agent 任务体系与 /goal 状态机** (`AgentTools.kt` / `ChatViewModel.kt` / `SystemPromptSheet.kt` / `SettingsScreen.kt`)：
   - 确保 `todo`、`task`、`goal` 工具在 `makeAgentTools` 中注册；
   - 确保对标 `oh-my-pi` 的 `/goal` 执行闭环（输入即发送、动态注入 `<goal_context>` 与 `<session_todos>`、基于已注册任务的状态机判断）保留；
   - 确保 `buildSystemPrompt` 中加载 `SYSTEM.md` 与 `APPEND.SYSTEM.md` 的逻辑保留；
   - 确保设置主页的“系统提示词”常驻入口保留。
4. **UI 卡片与交互** (`ChatScreen.kt` / `CollapsibleTodoCard.kt`)：
   - 保留输入框上方的 `CollapsibleTodoCard` 挂载与状态同步；
   - 确保 Header Badge 与 `ThinkingLevelSheet` 支持 6 档平滑展开。
5. **SSH 服务器与数据持久化** (`SSHServerRepository.kt` / `SSHServersScreen.kt` / `ChatViewModel.kt`)：
   - 保留 `SSHServerModel`、`SSHServerRepository` 与设置界面的路由；
   - 保留 `session_{id}_todos.json` 与 `session_{id}_goals.json` 的持久化逻辑。
6. **Rootfs 镜像、PRoot 与 rclone 资产** (`libproot.so` / `proot-aarch64` / `rclone.aar` / `alpine-minirootfs.tar.gz` / `RootfsManager.kt`)：
   - 保持 `libproot.so`、`proot-aarch64`、`rclone.aar` 与 `alpine-minirootfs.tar.gz` 在 Git 中追踪；
   - 保持 `RootfsManager.kt` 中的清华源/阿里源在线下载自愈兜底机制。
7. **记忆文件可见性与数据安全** (`MemoryRepository.kt` / `MemoryManagementScreen.kt`)：
   - 保留 `listAllFiles()` 的白名单语义：`GLOBAL.md`（常驻）+ 存在时的 `SOUL.md` + 每日日志（`^\d{4}-\d{2}-\d{2}\.md$`）；
   - **不要**把 `SOUL.md` 排除出记忆列表（`1.13-1.1` 曾因正则白名单过严导致「`soul.md` 没了」的可见性回归，详见 `CHANGELOG.md` 第 18 节）；
   - 保留 `PROMPT_FILES` 在列表/搜索/保存/删除四层的排除逻辑，避免系统提示词污染记忆列表；
   - 保留 `canDelete`（`GLOBAL.md` / `SOUL.md` 不可删除）、`readFileOrNull` 的「缺失 vs 不可读」区分、编辑页读取失败禁用保存、以及 `*.tmp` + rename 的原子写入；
   - 这些不变量是防止用户唯一副本被覆盖为空文件的最后防线，合并冲突时不得回退为 `readFile` 返回 `""` 的旧实现。
8. **DebugServer 兼容性与稳健性** (`debug/DebugServer.kt` / `debug/DebugRPCHandler.kt`)：
   - 保留 `readAtMost` 分块读取（**不要**使用 `InputStream.readNBytes`：Android 13（API 33）才提供，API 28 设备会抛 `NoSuchMethodError`）；
   - 保留 `SupervisorJob` + `catch (t: Throwable)` + 单请求 `withTimeoutOrNull`，避免单个异常请求取消作用域后整个 Debug 服务静默失效。

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
- `MemoryRepositoryFilterTest`（记忆文件白名单与数据安全契约）
- `SystemPromptRepositoryTest`（系统提示词独立仓储与迁移）

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
   - **文件修改**：递增 `src/android/app/build.gradle.kts` 中的 `versionCode`，并同步 `versionName` 字面量（如 `"1.13-1.2"`）。该字面量只是**本地回退值**：CI 会用 Release tag 的版本号通过 `-Pminis.versionName=<tag 版本>` 注入构建，从而避免出现「tag 是 `1.13-1.1`、APK 内嵌 `1.13-1.0`」的漂移。

2. **推送分支与发布 Tag**：
```bash
git checkout main
git merge sync-upstream-$(date +%Y%m%d)
git push origin main

# 打上对应的二开版本 Tag 触发 GitHub Actions 自动 Release：
git tag v1.13-1.2
git push origin v1.13-1.2
```
GitHub Actions 会自动提取该版本号，构建 `OpenMinis-1.13-1.2-release.apk` 与 `debug.apk`，并在 GitHub Releases 发布带版本号的正式 Release。

**发布后必须校验产物一致性**（版本名与实际 APK 不能只靠 Release 标题判断）：
```bash
aapt2 dump badging OpenMinis-1.13-1.2-release.apk | head -1
# 期望输出：package: name='com.openminis.app' versionCode='27' versionName='1.13-1.2'
```
