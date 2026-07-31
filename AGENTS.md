# X-Down 项目协作约定

本文件记录本项目的开发迭代约定，所有协作者（含 AI 编程助手）在修改本项目前必须阅读并遵守。

## 项目简介

X-Down 是一款 Android 应用，用于解析并下载 Twitter / X 推文视频到本地。技术栈：Kotlin + Jetpack Compose + Room + Retrofit（FxTwitter API）。

- 包名（applicationId）：`com.alosir.xdown`
- 代码 namespace：`com.example`（历史遗留，不影响应用包名）
- 仓库：https://github.com/alosir/X-Down

## 一、版本号规则（三段式：主版本.次版本.修订号）

自 v1.4 起，版本号从两段扩展为三段（当前 v1.4 视为 v1.4.0）：

| 变更级别 | 更新段 | 适用场景 | 示例 |
|---------|-------|---------|------|
| 小型改动 | **第三段**（修订号，从 0 到任意整数） | 优化 bug、调整文案、修复样式、微调原有功能 | 1.4.0 → 1.4.1 |
| 新增模块 | **第二段**（次版本，第三段归零） | 新增独立非静态页面、新增功能模块 | 1.4.1 → 1.5.0 |
| 大型改动 | **第一段**（主版本，后两段归零） | 视觉或代码彻底重构、突破原有产品定位的全面改造 | 1.5.0 → 2.0.0 |

**强制要求：更新第一段（主版本号）前必须先获得人工确认，任何人（含 AI）不得擅自升级主版本号。**

`versionCode` 规则：每次发布构建递增 1，与版本号段位无关（当前为 5）。

## 二、每次迭代必须同步更新的位置（防信息过期清单）

任何一次修改迭代，提交前必须检查并同步以下所有位置的**版本号**与**版本更新记录**，缺一不可：

### 项目中

- [ ] `app/build.gradle.kts` — `versionCode`（+1）与 `versionName`
- [ ] `app/src/main/java/com/example/data/FxTwitterService.kt` — User-Agent 中的版本号（共 2 处：`@Header` 默认值与 OkHttp 拦截器）

### APP 中

- [ ] `app/src/main/java/com/example/ui/AboutScreen.kt` — `fallbackLogs` 内置更新记录：在列表**顶部**插入新版本条目（版本号、日期、简洁更新清单）

### GitHub 中

- [ ] `README.md` — 功能特性、下载安装等与本次改动相关的描述
- [ ] Git tag：`v<新版本号>`（附注标签）
- [ ] GitHub Release：标题 `X-Down v<新版本号>`，正文含「更新内容」清单；附件上传新构建的 `app-release.apk`

### 衍生页面

- [ ] 基于 APP 内容生成的其他页面（官网宣传页、隐私政策页 `privacy_policy.html` 等）：如内容涉及本次改动或展示版本号，同步更新

## 三、发布流程（每次迭代按序执行）

1. 按上述清单更新所有版本号与更新记录
2. 构建：`./gradlew assembleRelease`（签名自动读取项目根目录 `keystore.properties`）
3. 验证 APK：`aapt2 dump badging app/build/outputs/apk/release/app-release.apk` 确认包名与版本号
4. Git 提交并推送 `main`
5. 打标签并推送：`git tag -a v<新版本号> -m "Release v<新版本号>" && git push origin v<新版本号>`
6. 创建/更新 GitHub Release，上传 `app-release.apk`

## 四、重要资产与注意事项

- **签名密钥**：`my-upload-key.jks` 与 `keystore.properties` 位于项目根目录，已在 `.gitignore` 中忽略，**严禁提交**。丢失密钥将导致新版本无法覆盖升级，请妥善备份。
- **包名兼容性**：v1.4 起包名为 `com.alosir.xdown`（v1.3 及之前为 `com.aistudio.xdown.oifald`），两者不能覆盖升级。
- **构建环境**：Gradle 使用 Android Studio 自带 JBR 21（全局 `~/.gradle/gradle.properties` 中 `org.gradle.java.home` 指定）；项目缓存建议放本地磁盘（`--project-cache-dir`），避免在云同步盘上进行构建。
