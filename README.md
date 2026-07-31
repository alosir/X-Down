# X-Down

一款简洁的 Android 应用，用于解析并下载 Twitter / X 推文中的视频到本地。

## 功能特性

- **链接解析**：粘贴 Twitter / X 分享链接，自动解析推文视频与多清晰度资源。
- **多清晰度下载**：多视频帖子按视频拆分为独立卡片，逐卡片选择分辨率下载。
- **本地历史归档**：已下载视频保存到本地，支持离线播放、删除和再次下载。
- **下载队列持久化**：APP 关闭后下载任务不丢失，重新打开可继续下载。
- **作者排行**：按作者统计下载次数，快速查看常下载的账号。
- **剪贴板自动检测**：检测到有效 X 视频链接时弹出提示，一键进入首页下载。
- **应用内播放**：支持在线预览与本地离线播放。
- **关于页面**：查看项目地址、当前版本号与通知权限设置入口。
- **检查更新**：每天自动静默检查新版本，也可在「更新记录」页手动检查；发现新版本后自动下载安装包并在系统通知栏显示进度，点击通知即可安装。
- **下载通知**：每个下载任务在系统通知栏显示实时进度条，完成后自动消失；下载完成推送含文件大小与清晰度信息。
- **桌面角标**：应用图标实时显示下载队列中的任务数量。

## 下载安装

在 [Releases](https://github.com/alosir/X-Down/releases) 页面下载最新版本的 `app-release.apk`。

## 技术栈

- **UI**：Jetpack Compose + Material 3
- **网络**：Retrofit + OkHttp + Moshi（调用 FxTwitter API）
- **本地存储**：Room + SQLite
- **视频播放**：AndroidX Media3 ExoPlayer
- **图片加载**：Coil
- **构建**：Gradle + Kotlin DSL，最低支持 Android 7.0（API 24）

## 项目结构

```
app/src/main/java/com/example/
├── MainActivity.kt              # 入口 Activity
├── data/                        # 数据层：Retrofit API、Room 实体与 Repository
├── download/                    # 下载任务管理
├── sharing/                     # 分享相关
└── ui/                          # Jetpack Compose UI 与 ViewModel
```

## 构建说明

1. 克隆仓库：
   ```bash
   git clone git@github.com:alosir/X-Down.git
   cd X-Down
   ```

2. 使用 Android Studio 或命令行构建：
   ```bash
   ./gradlew assembleRelease
   ```

3. 发布签名需要配置环境变量：
   - `KEYSTORE_PATH`：签名密钥路径
   - `STORE_PASSWORD`：密钥库密码
   - `KEY_PASSWORD`：密钥密码

   或直接使用项目根目录下的 `my-upload-key.jks`。

## 隐私政策

详见 [`privacy_policy.html`](privacy_policy.html)。

## 开发协作

版本号规则、迭代同步清单与发布流程见 [`AGENTS.md`](AGENTS.md)。

## 开源协议

本项目仅用于学习与交流，请遵守相关平台的使用条款。

---

**作者**：alosir  
**联系邮箱**：admin@alosir.com
