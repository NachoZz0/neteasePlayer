# Release 构建与签名说明

本文档记录本项目的 release 打包流程、签名信息与混淆规则。构建产物位于
`app/build/outputs/apk/release/app-release.apk`。

---

## 1. 签名信息

密钥库由 `keytool` 生成，位于项目根目录 `release.keystore`（已被 `.gitignore` 忽略）。

| 项目 | 值 |
|---|---|
| 密钥库文件 | `release.keystore` |
| 密钥库类型 | PKCS12 |
| 别名（alias） | `meting` |
| 密钥算法 | RSA 2048 位 |
| 签名算法 | SHA256withRSA |
| 有效期 | 10000 天（至 2054-03-02） |
| 主题（DN） | `CN=Meting Music, OU=Personal, O=Meting, L=Shenzhen, ST=Guangdong, C=CN` |
| SHA-256 证书指纹 | `AB:12:F5:D4:E5:90:63:02:74:38:A4:B1:85:15:34:80:9E:32:CA:35:E2:31:D5:52:AA:7D:18:AB:CA:A5:E9:C0` |

口令存放在 `local.properties`（同样不入库），字段名如下：

```properties
release.storeFile=release.keystore
release.storePassword=******
release.keyAlias=meting
release.keyPassword=******
```

`app/build.gradle.kts` 会在配置阶段读取这些字段并注入 `signingConfigs.release`，
因此口令不会出现在版本库里。**注意：`release.keystore` 与 `local.properties`
一旦丢失就无法再对同一个包名发布升级包，请务必另行备份。**

---

## 2. 常用命令

```bash
# 打 release 包
./gradlew assembleRelease

# 同时跑单元测试
./gradlew assembleRelease testDebugUnitTest

# 校验签名
D:/Android/Sdk/build-tools/35.0.0/apksigner.bat verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk

# 安装到已连接的设备
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 3. 体积

| 构建类型 | 大小 |
|---|---|
| debug | 21.44 MB |
| release | 4.76 MB |

release 开启了 `isMinifyEnabled = true`（R8 代码压缩/混淆）与
`isShrinkResources = true`（资源压缩），整体减少约 78%。

---

## 4. 混淆规则要点

规则文件：`app/proguard-rules.pro`。以下几条是踩过坑之后必须保留的：

### 4.1 Compose 接口默认方法冲突（重要）

报错现象：

```
java.lang.IncompatibleClassChangeError: Conflicting default method implementations
    boolean androidx.compose.ui.modifier.ModifierLocalProvider.all(A2.c)
```

原因：`build.gradle.kts` 为该模块开启了 `-Xjvm-default=all`，Compose UI 的接口会因此生成
大量默认方法。R8 在混淆时把不同接口里签名相同的默认方法重命名成了同一个短名
（例如都叫 `A2.c`），运行时类验证阶段检测到冲突便直接崩溃。

处理方式：保留 `androidx.compose.ui` 下接口成员的原始签名，不参与重命名。

```proguard
-keep interface androidx.compose.ui.** { *; }
-keep class androidx.compose.ui.node.** { *; }
-keep,allowshrinking class androidx.compose.ui.**
-keepclassmembers class androidx.compose.ui.** { <init>(...); <methods>; <fields>; }
-keepclassmembers interface * { public <methods>; }
```

> 注意：不要用 `-dontoptimize` 一把梭。那样虽然也能修好，但会基本废掉 R8 的优化，
> 体积会明显反弹。

### 4.2 Gson 反射模型

歌曲列表等数据由 Gson 通过反射反序列化，字段名不能被改写，否则解析结果全为 `null`：

```proguard
-keep class com.example.metingmusic.data.model.** { *; }
-keep class com.example.metingmusic.data.remote.** { *; }
-keep class com.example.metingmusic.data.local.** { *; }
```

### 4.3 其它

- OkHttp / Okio / Conscrypt / BouncyCastle：`-dontwarn` 掉可选依赖的警告。
- Media3：保留 `exoplayer` / `session` 包，播放器内部有反射创建解码器的逻辑。
- Compose 的 `@Immutable` / `@Stable` 注解需要保留（`-keepattributes *Annotation*`），
  否则 Compose 编译器插入的稳定性判断会失效，导致重组次数增加、滑动掉帧。

---

## 5. 图标资源说明

`AndroidManifest.xml` 引用 `@mipmap/ic_launcher`。

- **API 26+**：`res/mipmap-anydpi-v26/ic_launcher.xml`（自适应图标），
  背景是纯色 `@color/ic_launcher_background`，前景是 `@drawable/ic_launcher_art`。
- **API 24/25**：`res/mipmap-{mdpi..xxxhdpi}/ic_launcher.png`（带圆角烘焙好的位图）。

> 自适应图标的安全区只有中间 **72dp**（整个画布是 108dp）。
> 所以 `ic_launcher_art.png` 是**预先排版好**的 432×432 图片：插画只占中间 66%，
> 四周用背景色填满。这样不用在 XML 里写 inset，也不会因为各厂商遮罩形状不同而跑偏。
> 直接用整张插画当背景（full-bleed）会把头顶裁掉 —— 因为背景层会被放大 1.5 倍再裁。

> 踩坑记录（第一版）：别名文件必须放在 `mipmap` 目录下。若放进 `res/values/` 并用
> `<item name="ic_launcher" type="mipmap">` 指向 `@drawable/…`，lint 会在
> release 打包时以 `ReferenceType` 报 **fatal error** 并中断构建
> （`lintVitalRelease` 任务失败）。现在改用真实的各密度位图，这个问题不复存在。

图标由 `Pillow` 脚本从用户提供的插画生成（底色取插画四角平均色 `#F5F4F1`）。

---

## 6. 已验证项（release 包实测）

在 Android 模拟器（1080×2400）上安装 release 包并逐项验证：

- [x] 冷启动无崩溃，免责声明弹窗完整显示
- [x] 同意后进入主界面；重启不再重复弹出
- [x] 歌单导入（网易云 24381616）成功解析 1000 首 —— Gson 反射在混淆后正常
- [x] 导入的歌单重启后仍在「我的导入」列表中
- [x] 播放《一縷》正常，进度与歌词同步滚动 —— Media3 反射路径正常
- [x] 播放模式按钮循环切换（顺序 → 列表循环 → 单曲循环 → 随机）
- [x] 二级菜单（横三点）位置正确，位于「播放队列」正下方同列
- [x] 定时播放对话框：预设标签 `1 小时 / 1.5 小时 / 2 小时` 正确，倒计时正常走动
- [x] 播放模式提示为自替换浮层，连点 3 次立即显示最新模式（不再排队）
- [x] 控制区整体上移，二级菜单三点按钮为紧凑行，不再挤压底部
- [x] 单元测试 30/30 通过

---

## 8. 第 7 轮：通知栏控制 + 歌单分享 + 下载管理

### 8.1 通知栏切歌（根因与修复）

**症状**：通知栏 / 锁屏 / 蓝牙耳机的上一首、下一首按钮点了没反应。

**根因**：Media3 的通知栏按钮直接映射到 `Player.seekToNext/PreviousMediaItem()`，
而当时 `PlayerController` 只把「当前这一首」塞进 ExoPlayer（队列调度由应用自己算，
播放器里永远只有 1 个 MediaItem）。播放列表长度为 1 时这两个命令根本不可用 ——
按钮会变灰，或者点了什么都不发生。

**修复**：把**整个队列**都作为 `MediaItem` 交给 ExoPlayer，并把四种播放模式映射到
Media3 原生的 `repeatMode` / `shuffleModeEnabled`：

| 应用模式 | repeatMode | shuffle |
|---|---|---|
| 顺序播放 | `OFF` | false |
| 列表循环 | `ALL` | false |
| 单曲循环 | `ONE` | false |
| 随机播放 | `ALL` | true |

**关键设计**：MediaItem 的 uri 直接给 Meting 的 `type=url` 接口地址，
**由 ExoPlayer 自己跟随 302** 拿真实音频。所以放进 1000 首也不会产生 1000 次解析请求。
歌词仍然「切到哪首拉哪首」。

配套改动：
- `PlaybackService` 自定义 `DefaultHttpDataSource.Factory`，把 UA 设成和接口请求一致
  （网易云 CDN 校验 UA，否则 403），并打开 `allowCrossProtocolRedirects`
  （直链 302 可能 http/https 互换）。
- 自动续播交给 ExoPlayer，删掉了原先监听 `STATE_ENDED` 自己算下一首的逻辑。
- `onPlayerError` 里自动跳到下一首，避免某首 VIP / 下架的歌卡死整条队列。
- 定时播放的「播完当前歌曲再暂停」改在 `onMediaItemTransition(reason == AUTO)` 里处理。

实测（媒体键等价于通知栏按钮）：`spiral → 断气 → 雀跃` 连续切歌正常，
暂停 / 播放正常，应用内 UI 与通知栏保持同步。

### 8.2 其余功能

| 功能 | 实现要点 |
|---|---|
| 歌单封面 | `SavedPlaylist.cover`，默认取第一首歌的 `pic` |
| 自动补全歌单名 | `ShareLinkParser` 从分享文本的「」或 `分享歌单:` 后提取，回填名称输入框 |
| 分享歌单 | 生成网易云格式文案 + 复制到剪贴板，能被本应用的导入框解析回去（往返一致） |
| 刷新歌单 | 重新拉取曲目并更新本地记录（网易云歌单会增删歌） |
| 下载歌单全部歌曲 | `downloadAll()` 串行解析 + 下载，带进度与取消 |
| 从外部导入歌曲 | 粘贴单曲 / 歌单分享文本，解析后加入下载队列 |
| 自定义定时时长 | 预设 chip 之外可手填 1~600 分钟 |
| 取消下载 | 批量任务与单首都可取消，见 8.3 |
| 长按多选删除 | 下载列表 + 我的导入列表都支持 |

### 8.3 取消下载的实现要点

**必须靠取消协程，光改状态停不掉网络传输。** OkHttp 的同步 `execute()` 在协程被取消时，
会在 `input.read()` 处抛 `CancellationException` 并把连接一起关掉。
所以 `DownloadManager` 给每个任务存了一份 `Job`，`cancel()` 就是 `job.cancel()`。

**取消后状态置为 `Cancelled` 而不是直接删掉任务**：批量下载的 `awaitFinished()`
正等着这个任务结束，删掉会让它永远等下去。`awaitFinished` 判定条件因此是
`Done || Failed || Cancelled` 三者之一。

**批量状态要单独记一份**：批量下载是「解析 → 下载 → 再解析下一首」的循环，
解析那一小段时间里下载库里是没有 `Downloading` 任务的。如果 UI 靠「有没有正在下载的任务」
来决定要不要显示「取消下载」按钮，按钮就会一首一闪地跳，用户根本点不中。
所以新增了 `BatchDownloadState`（running / done / total / label）单独承载这个状态。

实测：批量下载 1000 首歌单，点「取消下载」后文件数停在 76 不再增长，
无 `.part` 残留，界面转为「未完成 1 首」并提供「清理未完成」入口。

### 8.4 一个必须记住的 Gson 陷阱

给持久化模型新增字段时，**Kotlin 的非空类型 + 默认值保护不了你**：

Gson 反序列化不走 Kotlin 构造器，而是用 `Unsafe` 分配对象后反射写字段。
所以「JSON 里没有这个字段」时字段会保持 JVM 默认值（对象类型即 **null**），
默认值只在走构造器时才生效。给 `SavedPlaylist` 新增 `cover: String = ""` 后，
老记录读出来 `cover` 是 null，`cover.ifBlank {}` 直接抛 NPE —— 一打开歌单页就崩。

**结论：给持久化模型新增字段一律声明成可空类型，并在 getter 里做兜底。**
已固化成回归测试 `SavedPlaylistMigrationTest`（4 个用例）。

---

## 7. 交互细节的两处修正

### 7.1 播放模式提示不再排队

原先用 `SnackbarHostState.showSnackbar()`。它是**挂起函数**，会一直挂起直到上一条
Snackbar 播放完毕，所以连点模式按钮时提示会一条条排队慢慢冒出来，反馈严重滞后。

改为 `ui/common/ToastOverlay.kt` 里的自替换浮层：每条消息带一个自增 `id`，
`LaunchedEffect(message?.id)` 在 id 变化时重新起算显示时长，因此新的提示会**立即覆盖**
上一条，点多少下都是即时的。同时给浮层加了 `liveRegion = Polite`，读屏软件也会自动播报。

### 7.2 控制区上移与紧凑化

- 整块控制区 `padding(bottom = 12.dp)`，抬离底部导航条，缓解「挤在底部」的观感。
- 「上一首 / 播放暂停 / 下一首」的间距由 `SpaceEvenly` 配合行内边距收紧。
- 二级菜单那一行**不能用 `IconButton`**：它自带 48dp 最小触摸区，会让这一行白白占掉
  一整条高度。改用 44×32dp 的 `Box` + 无涟漪 `clickable`，既保持可点击又不撑高。
- 两行的 `horizontal padding` 必须一致，并用与上一行等宽的 `Spacer` 占位，
  这样三点按钮才会和上方的「播放队列」落在同一列（实测 x 都是 881–944）。

### 7.3 播放模式图标去重

`PlayMode → 图标` 的映射原先在 `PlayerScreen` 和 `PlayerMenuSheet` 里各写了一份 `when`，
新增提示时又要写第三份，很容易漏改导致「按钮显示随机、提示显示循环」。
统一抽到 `ui/common/PlayModeIcon.kt` 的 `PlayMode.icon` 扩展属性。
