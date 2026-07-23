# X-Down

一款简洁的 Android 应用，用于解析并下载 Twitter / X 推文中的视频到本地。

## 功能特性

- **链接解析**：粘贴 Twitter / X 分享链接，自动解析推文视频与多清晰度资源。
- **多清晰度下载**：支持按视频段落选择不同分辨率，批量加入下载队列。
- **本地历史归档**：已下载视频保存到本地，支持离线播放、删除和再次下载。
- **作者排行**：按作者统计下载次数，快速查看常下载的账号。
- **剪贴板自动检测**：检测到有效 X 视频链接时弹出提示，一键进入首页下载。
- **应用内播放**：支持在线预览与本地离线播放。

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

## 开源协议

本项目仅用于学习与交流，请遵守相关平台的使用条款。

---

**作者**：alosir  
**联系邮箱**：admin@alosir.com
