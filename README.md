# 音乐（Android 播放器）

一个基于 [Meting API](https://api.qijieya.cn/meting/) 的 Android 音乐播放器，使用 **Kotlin + Jetpack Compose + Media3 (ExoPlayer)** 构建。

支持搜索播放、导入网易云歌单、全屏歌词、后台播放等完整能力。

当前版本 1.1.0，变更记录见 [CHANGELOG.md](CHANGELOG.md)。

---

## 功能特性

| 功能 | 说明 |
| --- | --- |
| 歌曲搜索 | 输入关键词实时搜索（400ms 防抖），支持分页 |
| **歌单导入** | 输入网易云歌单号或粘贴分享链接，一键拉取整个歌单 |
| 歌单收藏 | 搜索结果可「添加到歌单」，自建歌单本地持久化 |
| 播放控制 | 播放 / 暂停、上一首 / 下一首、进度条拖拽跳转 |
| **全屏歌词** | 歌词为主视觉，当前行放大高亮、自动滚动、点击歌词行跳转 |
| **底部导航** | 搜索 / 歌单双页面，底部导航栏 + 迷你播放条 |
| 播放队列 | 底部弹出式队列面板，自动定位当前播放项 |
| 音质选择 | 128K / 192K / 320K / FLAC 四档音质 |
| 循环模式 | 顺序播放 → 列表循环 → 单曲循环 |
| LRC 歌词 | 自动解析时间轴，剔除「作词/作曲」等制作人员行 |
| 后台播放 | 前台服务 + MediaSession，切后台 / 锁屏继续播放 |
| 通知栏控制 | Media3 自动生成系统媒体通知与锁屏控制卡片 |

> **音源说明**：QQ 音乐（`server=tencent`）经实测绝大多数歌曲解析失败，已下线，目前仅保留网易云音乐。

---

## 页面结构

```
┌─────────────────────────┐
│   内容区                │
│  （搜索 / 歌单导入）      │
├─────────────────────────┤
│  迷你播放条（有歌时）      │  ← 点击左侧展开全屏歌词
├─────────────────────────┤
│  底部导航栏              │
│   [搜索]      [歌单]     │
└─────────────────────────┘
```

全屏歌词页与队列面板以覆盖层形式从底部滑入。

---

## 项目结构

```
MetingMusicPlayer/
├── app/src/main/java/com/example/metingmusic/
│   ├── MainActivity.kt                 # 入口：底部导航 + 迷你播放条 + 全屏歌词 + 队列
│   ├── data/
│   │   ├── local/
│   │   │   └── PlaylistStore.kt        # 自建歌单持久化（SharedPreferences + JSON）
│   │   ├── model/
│   │   │   ├── Song.kt                 # 歌曲模型，含 id 解析与音源标记
│   │   │   ├── Playlist.kt             # 歌单模型
│   │   │   ├── PlaylistIdParser.kt     # 从歌单号/分享链接提取 ID
│   │   │   └── LrcParser.kt            # LRC 歌词解析器 + 时长格式化
│   │   ├── remote/
│   │   │   └── MetingApi.kt            # Meting API 客户端（OkHttp）
│   │   └── repository/
│   │       └── MusicRepository.kt      # 编排多步请求，产出「可播放歌曲」
│   ├── player/
│   │   ├── PlaybackService.kt          # 前台播放服务 + MediaSession
│   │   └── PlayerController.kt         # 播放状态中枢（StateFlow 驱动 UI）
│   └── ui/
│       ├── search/                     # 搜索页 + ViewModel（含歌单导入状态）
│       ├── playlist/
│       │   ├── PlaylistImportScreen.kt # 网易云歌单导入页
│       │   ├── PlaylistScreen.kt       # 自建歌单列表 + 详情
│       │   └── AddToPlaylistDialog.kt  # 「添加到歌单」多选弹窗
│       ├── player/
│       │   ├── PlayerScreen.kt         # 全屏歌词页
│       │   └── QueueBottomSheet.kt     # 底部队列面板
│       └── theme/
│           ├── Theme.kt                # Material 3 主题
│           └── Modifiers.kt            # clickableNoRipple 等共享修饰符
├── app/src/test/                       # 22 个单元测试
│   ├── LrcParserTest.kt                # 歌词解析（8）
│   ├── PlaylistIdParserTest.kt         # 歌单号提取（10）
│   └── PlaylistTest.kt                 # 歌单逻辑（4）
└── gradle/libs.versions.toml           # 依赖版本集中管理
```

---

## API 使用说明

所有接口均为 `GET`，基础地址 `https://api.qijieya.cn/meting/`。

### 公共参数

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `server` | 否 | `netease`（默认）/ `tencent`（本项目已下线） |
| `type` | 是 | `search` / `song` / `playlist` / `url` / `pic` / `lrc` / `name` / `artist` |
| `id` | 是 | 类型 ID；`type=search` 时为搜索关键词 |

### 本项目用到的接口

```bash
# 搜索（返回 Song JSON 数组）
GET /meting/?type=search&id=周杰伦&limit=30&page=1

# 单曲详情
GET /meting/?type=song&id=210049

# 歌单（id 为网易云歌单号）
GET /meting/?type=playlist&id=24381616

# 歌词（返回纯文本 LRC）
GET /meting/?type=lrc&id=210049

# 播放链接（302 跳转到真实 mp3/flac 地址）
GET /meting/?type=url&id=210049&br=320

# 封面（302 跳转到图片地址）
GET /meting/?type=pic&id=109951172453712025&cover=500
```

### 三个关键实现细节

1. **`type=url` 和 `type=pic` 返回的是 302 重定向**，不是内容本身。
   代码中通过 OkHttp 跟随重定向后读取 `Location` 头拿到最终直链：

   ```kotlin
   suspend fun resolvePlayUrl(songUrl: String, br: Int = 320): String? {
       val final = followRedirect(withBr(songUrl, br))   // 拿到 mp3/flac 真实地址
       // 过滤掉音源返回的 404 占位地址
       return if (final.contains("404") && !final.endsWith(".mp3")) null else final
   }
   ```

2. **搜索接口返回的 `url` / `pic` / `lrc` 字段本身也是接口地址**（形如 `...&type=url&id=xxx`），
   需要二次请求才能真正拿到媒体资源。`MusicRepository.resolve()` 把这三步**并发**执行，
   避免串行等待导致点击歌曲后卡顿。

3. **⚠️ 歌单接口的「HTTP 200 伪成功」陷阱**：当歌单号无效时，接口**不返回 404**，
   而是返回 `200` + `[{"error":"unknown playlist id"}]`。若直接交给 Gson 反序列化成
   `List<Song>`，会得到**一条所有字段为空字符串的幽灵歌曲**，界面上表现为
   「导入成功但歌单里多了一首空歌」。`MetingApi.playlist()` 因此显式探测 `error` 字段并抛异常：

   ```kotlin
   val json = getText(url)
   detectApiError(json)?.let { throw IOException(it) }   // 拦下伪成功
   parseSongList(json)
   ```

---

## 构建与运行

### 环境要求

- Android Studio Ladybug (2024.2) 或更高版本
- **JDK 17**（务必！见下方注意事项）
- Android SDK：compileSdk 35，minSdk 24（Android 7.0）

> ⚠️ **必须使用 JDK 17**
> 项目已在 `gradle.properties` 中写死 `org.gradle.java.home` 指向 JDK 17。
> 原因：Kotlin 2.0.21 的编译器在解析 JDK 25 的版本号 `25.0.4.1` 时会抛
> `IllegalArgumentException`，Gradle 只会打印一句 `What went wrong: 25.0.4.1`，
> 非常难以排查。如果你的 JDK 17 装在别的位置，请同步修改该配置。

### 步骤

1. 用 Android Studio 打开 `MetingMusicPlayer` 目录（`File → Open`，选择含 `settings.gradle.kts` 的文件夹）。

2. 首次打开时 IDE 会提示同步 Gradle，等待依赖下载完成即可。

3. 连接真机（开启 USB 调试）或启动模拟器，点击运行按钮。

### 命令行构建

```bash
cd MetingMusicPlayer

# 开发调试包
./gradlew assembleDebug

# 运行单元测试（30 个）
./gradlew testDebugUnitTest

# 正式发布包（已配好签名，见 RELEASE.md）
./gradlew assembleRelease
```

Windows 环境下将 `./gradlew` 换成 `gradlew.bat`。

产物位置：
- debug：`app/build/outputs/apk/debug/app-debug.apk`（约 21 MB）
- release：`app/build/outputs/apk/release/app-release.apk`（约 4.8 MB，已签名）

> release 包开启了 R8 代码压缩混淆与资源压缩，体积比 debug 小约 78%。
> 签名密钥库、口令配置与混淆踩坑记录见 **[RELEASE.md](RELEASE.md)**。

---

## 架构说明

采用单向数据流（UDF）：

```
Compose UI  ──事件──▶  PlayerController / ViewModel
     ▲                        │
     │                    修改 StateFlow
     └──── collectAsState ────┘
```

- **`PlayerController`** 是播放能力的中枢，持有 `MediaController` 与 `StateFlow<PlayerUiState>`。
  UI 只读状态、只发意图，不直接接触 ExoPlayer，因此后续加音效、下载等功能不需要改动界面层。

- **`PlaybackService`** 继承 `MediaSessionService`，把 ExoPlayer 放在前台服务里。
  这是后台播放和系统媒体通知的前提，也天然支持车机 / 蓝牙设备的媒体按键控制。

- **切歌时的直链重解析**：`onMediaItemTransition` 回调里会重新解析播放地址。
  因为音源直链带有时效性签名，缓存过久会失效，每次切歌重新拿一次更稳定。

- **并发保护**：`resolvingId` 标记防止用户连点同一首歌时重复发起解析请求；
  解析完成后还会校验当前歌曲是否已变化，避免「慢请求覆盖新歌」的竞态。

- **歌单持久化策略**：只存歌名 / 歌手 / 接口地址（几 KB），播放直链每次播放实时解析
  —— 直链带时效签名，存下来隔天就失效。因此用 SharedPreferences + JSON，
  不为此引入 Room。

- **点击穿透的处理**：全屏歌词页最外层挂 `Modifier.consumeAllTouches()`，
  在根节点消费所有触摸事件；队列面板用 `ModalBottomSheet` 自带遮罩兜第二层。

---

## 已知限制

1. **依赖第三方 API**：`api.qijieya.cn` 是个人维护的公益服务，
   官方公告称将迁移至 `musicapi.qijieya.cn`。若接口变更，
   只需修改 `MetingApi.DEFAULT_BASE_URL` 一处。

2. **QQ 音乐不可用**：`server=tencent` 实测大面积解析失败，已从界面下线。

3. **VIP 解析不稳定**：文档提到「如果 VIP 没了，就是有人在恶意调用被全局禁止」，
   存在可用性波动。非 VIP 歌曲不受影响。

4. **音质参数会静默降级**：请求 FLAC 但音源无版权时，接口可能返回较低音质的地址，
   无法通过响应判断，只能靠听觉区分。

5. **导入的歌单不持久化**：导入的网易云歌单只存在内存中，App 重启后需重新导入
   （这是有意设计：保证歌单内容与线上一致）。

6. **未实现随机播放**：UI 上无入口，完整实现需要在 `PlayerController.next()` 里加随机索引逻辑。

7. **无本地音频缓存**：每次播放都走网络，重复听同一首歌会重复请求。

---

## 合规提示

本项目仅用于 **Android 开发学习与技术研究**。

Meting API 解析的是第三方音乐平台的资源，音频版权归各音乐平台及著作权人所有。
请勿将本项目用于商业用途或大规模分发获取的内容。使用者需自行承担合规责任。

---

## 技术栈

- Kotlin 2.0.21
- Jetpack Compose (BOM 2024.12.01) + Material 3
- AndroidX Media3 / ExoPlayer 1.5.1
- OkHttp 4.12.0 + Gson 2.11.0
- Coil 2.7.0（图片加载）
- Kotlin Coroutines & StateFlow
- AGP 8.7.3 / Gradle 8.9
