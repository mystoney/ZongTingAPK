# 纵听 App 开发文档

> 最后更新：2025-05-24 | 当前版本：v1.0.468

---

## 1. 项目概述

**纵听**是一款基于酷我音乐 API 的 Android 音乐播放器，使用 Jetpack Compose 构建 UI，ExoPlayer（Media3）作为播放核心，支持歌词显示、收藏管理、歌单浏览、排行榜、iTunes 封面增强、定时关闭等功能。

---

## 2. 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 1.9.x |
| UI 框架 | Jetpack Compose（Material 3） |
| 最低 SDK | Android 8.0（API 26） |
| 目标 SDK | Android 14（API 34） |
| 架构 | MVVM + Hilt 依赖注入 |
| 音乐播放 | Media3 ExoPlayer + MediaSessionService |
| 网络 | Retrofit2 + OkHttp + Gson |
| 图片加载 | Coil Compose |
| 本地存储 | DataStore Preferences |
| 协程 | Kotlin Coroutines + Flow |
| 编译工具 | Gradle 8.x + Kotlin DSL |

---

## 3. 项目结构

```
app/src/main/java/com/zongting/zongting/
├── ZongTingApp.kt                  # Application 类（Hilt 入口）
├── MainActivity.kt                 # 主 Activity
├── InstallResultActivity.kt        # APK 安装结果页
├── data/
│   ├── api/
│   │   ├── KuwoApi.kt              # 酷我音乐 Retrofit API 接口
│   │   ├── KuwoService.kt          # 酷我音乐数据获取逻辑
│   │   └── NetworkModule.kt        # Hilt 网络模块（OkHttp + Retrofit DI）
│   ├── model/
│   │   ├── Models.kt               # 所有数据类（Songs, Playlist, Ranking 等）
│   │   └── VersionInfo.kt          # 版本更新数据类
│   └── repository/
│       ├── MusicRepository.kt      # 音乐数据（酷我 + iTunes 封面增强）
│       ├── PlaylistRepository.kt   # 歌单管理（收藏、最近播放）
│       ├── FavoriteRepository.kt   # 收藏歌曲 DataStore 持久化
│       ├── PlaybackStateRepository.kt  # 播放状态恢复
│       ├── UpdateRepository.kt     # 版本更新检测
│       └── PendingInstallManager.kt    # APK 下载安装管理
├── player/
│   ├── PlaybackService.kt          # MediaSessionService（后台播放服务）
│   └── PlayerManager.kt            # 播放器核心（播放列表、切歌、URL 缓存）
├── ui/
│   ├── MainNavigation.kt           # 导航入口（BottomNavigation）
│   ├── MainViewModel.kt            # 全局 ViewModel（播放状态分发）
│   ├── SplashScreen.kt             # 启动页
│   ├── MiniPlayer.kt               # 迷你播放器组件
│   ├── LyricModels.kt              # 歌词解析模型
│   ├── theme/
│   │   ├── Theme.kt                # Material 3 主题定义
│   │   └── Type.kt                 # 字体样式
│   └── screens/
│       ├── HomeScreen.kt / HomeViewModel.kt       # 首页（推荐歌单）
│       ├── SearchScreen.kt / SearchViewModel.kt   # 搜索
│       ├── PlaylistScreen.kt / PlaylistViewModel.kt # 歌单详情
│       ├── RankingsScreen.kt / RankingsViewModel.kt # 排行榜
│       ├── PlayerScreen.kt                        # 全屏播放器（含歌词）
│       └── UpdateCheckScreen.kt                   # 更新检测页
└── player/
    ├── PlaybackService.kt           # 后台播放服务
    ├── PlayerManager.kt            # 播放控制核心
    └── SleepTimerManager.kt        # 定时关闭管理器
```

---

## 4. 产品版本体系

### 4.1 版本配置

| | 测试版（beta） | 正式版（prod） |
|---|---|---|
| 包名 | `com.zongting.zongting.beta` | `com.zongting.zongting` |
| 显示名 | 纵听测试版 | 纵听 |
| 更新检测地址 | `http://172.16.1.93:8080/ZongTing/test/version.json` | `http://172.16.1.93:8080/ZongTing/release/version.json` |
| 版本名后缀 | `-beta` | 无后缀 |

> **关键约束**：同一包名在同一部手机上只能安装一个。测试版和正式版通过 `applicationIdSuffix = ".beta"` 实现包名隔离，可在同一设备上同时安装。

### 4.2 版本号策略

- **versionCode**：整数，每次编译递增（Android 系统用于判断版本新旧）
- **versionName**：格式 `1.0.{buildNumber}`
- **自动递增**：Gradle `assemble*` 任务完成后自动执行 `incrBuildNum`，通过 `finalizedBy` 确保在编译**后**递增（避免 APK 内容与 version.properties 不同步）
- **GitHub 同步**：递增版本号后自动 commit + push

### 4.3 version.json 格式

```json
{
  "versionCode": 468,
  "versionName": "1.0.468-beta",
  "apkUrl": "http://172.16.1.93:8080/ZongTing/test/zongting-test.apk",
  "releaseNote": "修复定时关闭倒计时不更新问题"
}
```

---

## 5. 核心模块说明

### 5.1 播放器（PlayerManager + PlaybackService）

- 使用 **Media3 ExoPlayer** + **MediaSessionService** 实现后台播放
- `PlaybackService` 创建 `MediaSession`，由 `PlayerManager` 持有 player 引用
- 播放 URL 从酷我 API 获取，带缓存（`urlCache: Map<rid, url>`）
- 切歌时异步获取 URL，避免 ExoPlayer 拿空 URI 崩溃
- 播放列表循环播放（最后一首 → 第一首）
- 通知栏媒体控制（MediaSession 联动）

### 5.2 定时关闭（SleepTimerManager）

- 使用 `CountDownTimer`，每秒 `onTick` 更新 `remainingSeconds` StateFlow
- 启动时显示常驻通知栏，tick 时更新通知内容
- 倒计时结束自动调用 `PlayerManager.pause()`
- 支持通知栏"取消"按钮（通过 `PendingIntent` + `MainActivity` 处理）

### 5.3 iTunes 封面增强（MusicRepository）

- 酷我封面 URL 格式为 `100x100bb`，iTunes API 可提供更高清封面
- 搜索歌曲时同时调用 iTunes Search API，将 `100x100bb` 替换为 `600x600bb` 获取高清图
- 降级策略：iTunes 无结果时保留酷我原始封面

### 5.4 版本更新（UpdateRepository）

- 从 `BuildConfig.VERSION_JSON_URL` 获取远程 version.json
- 对比 `versionCode` 判断是否有新版本
- 下载 APK 后调用 `PendingInstallManager` 完成安装

---

## 6. 部署流程

### 6.1 编译命令

```bash
# 编译测试版
./gradlew assembleBetaDebug

# 编译正式版
./gradlew assembleProdDebug

# 两者同时编译
./gradlew assembleBetaDebug assembleProdDebug
```

> 编译完成后 APK 路径：
> - 测试版：`app/build/outputs/apk/beta/debug/app-beta-debug.apk`
> - 正式版：`app/build/outputs/apk/prod/debug/app-prod-debug.apk`

### 6.2 部署到服务器

APK 和 version.json 通过 HTTP 服务器（`172.16.1.93:8080`）分发。

**手动部署步骤：**
1. 将 APK 复制到服务器对应目录
2. 更新 `version.json`（确保 versionCode 和 versionName 与 APK 对齐）
3. 用户端 App 自动检测到新版本并提示更新

**目录结构：**
```
/usr/ZongTing/
├── test/              # 测试版
│   ├── version.json
│   └── zongting-test.apk
└── release/           # 正式版
    ├── version.json
    └── zongting-release.apk
```

### 6.3 版本发布工作流程

```
新功能开发完成
    ↓
编译测试版 → 上传到 test/ → 通知测试用户
    ↓
测试稳定后
    ↓
编译正式版（assembleProdDebug）
    ↓
上传 release/ 目录 → 更新 release/version.json
    ↓
正式版用户收到更新推送
```

---

## 7. 服务器信息

| 项目 | 信息 |
|------|------|
| HTTP 服务器 | `172.16.1.93:8080` |
| 测试版地址 | `http://172.16.1.93:8080/ZongTing/test/` |
| 正式版地址 | `http://172.16.1.93:8080/ZongTing/release/` |
| GitHub 仓库 | `https://github.com/mystoney/ZongTingAPK` |

---

## 8. 常见问题

### Q: 为什么手机上装了新版本但没有提示更新？
检查 `UpdateRepository` 是否有局域网限制（已移除），以及 `MainNavigation` 是否正确渲染更新对话框。

### Q: versionCode 为什么只能递增不能重置？
Android 系统按整数比较版本新旧，已安装的 versionCode 大于服务端时会拒绝安装。

### Q: 测试版和正式版能同时装吗？
能，因为 `applicationIdSuffix = ".beta"` 使两者包名不同（`com.zongting.zongting` vs `com.zongting.zongting.beta`）。

### Q: 定时关闭没有倒计时显示？
检查 `SleepTimerManager` 的 `CountDownTimer` tick 间隔，当前为每秒更新（1_000L）。

---

## 9. 开发注意事项

1. **版本号递增时机**：`incrBuildNum` 使用 `finalizedBy` 确保在编译**后**递增，避免 APK 和 version.properties 不同步
2. **切歌 URL 获取**：必须在 `onMediaItemTransition` 回调中异步获取，避免 ExoPlayer 崩溃
3. **通知栏权限**：Android 13+ 需要 `POST_NOTIFICATIONS` 权限才能显示通知
4. **iTunes API 降级**：iTunes 无封面时保留酷我原始封面，避免搜索失败导致无图
