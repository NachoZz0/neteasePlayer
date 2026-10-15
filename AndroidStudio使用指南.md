# Android Studio 使用指南（针对本项目）

你的机器上 **Android Studio 2026.2.1 已经装好了**，位于 `D:\Android\Android Studio`，模拟器和 SDK 也齐了。
所以不用再装任何东西，只需要把项目跑起来。

---

## 一、你的环境现状（已确认）

| 项目 | 状态 |
| --- | --- |
| Android Studio | 2026.2.1，装在 `D:\Android\Android Studio` |
| Android SDK | `D:\Android\Sdk`（platform-tools 37.0.1、android-35、build-tools 35.0.0） |
| 模拟器镜像 | `android-37.0 / google_apis_playstore / x86_64` 已装 |
| 虚拟设备 | `Medium_Phone`（API 37）已创建 |
| Gradle | 8.9 已装入 wrapper 缓存 |
| JDK | 17（`C:\Program Files\Java\jdk-17`） |

---

## 二、启动 Android Studio

方式一：开始菜单搜索 **Android Studio**

方式二：直接双击
```
D:\Android\Android Studio\bin\studio64.exe
```

首次启动会问是否导入设置，选 **Do not import settings** 即可。

---

## 三、打开项目

1. 启动后如果停在欢迎页，点 **Open**（不是 New Project）
2. 在路径框里选到项目**根目录**（含 `settings.gradle.kts` 的那一层）：
   ```
   C:\Users\zhb08\WorkBuddy AI\2026-10-14-18-36-09\MetingMusicPlayer
   ```
3. 点 OK

> 注意：一定要选 `MetingMusicPlayer` 这一层，不要选它的上级目录，也不要选 `app` 子目录。

打开后右下角会出现 **Gradle Sync** 进度条，它会自动下载 AGP、Compose、Media3 等依赖。
**第一次同步需要下载几百 MB，耐心等**（取决于网速，可能 5-20 分钟）。

---

## 四、如果同步（Sync）报错

### 报错 1：SDK location not found
项目里的 `local.properties` 已正确指向 `D:\Android\Sdk`，理论上不会出现。
若出现，手动创建/修改 `local.properties`：
```properties
sdk.dir=D\:\\Android\\Sdk
```

### 报错 2：Gradle 下载慢 / 卡住
国内访问 `services.gradle.org` 和 `dl.google.com` 有时很慢。
Gradle 8.9 我已经帮你装进本地缓存了，所以**不该再下载 Gradle**。
如果仍然卡住，说明是依赖下载慢，可以考虑加国内镜像。

### 报错 3：Unsupported class file major version
说明用错 JDK 了。**File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**，
选择 **jdk-17**（`C:\Program Files\Java\jdk-17`）。
不要选 JDK 25，AGP 8.7.3 不支持。

### 报错 4：提示需要接受 license
**Tools → SDK Manager → SDK Platforms**，勾选需要的组件点 Apply，它会自动接受许可。

---

## 五、运行到模拟器

### 1. 启动模拟器
工具栏找到设备下拉框 → **Device Manager**（或点右上角手机图标）
→ 找到 `Medium_Phone` → 点 ▶ 启动

第一次冷启动模拟器会比较慢（1-3 分钟），之后会快很多。

### 2. 运行 App
确认顶部工具栏：
- 运行配置选 **app**
- 目标设备选 `Medium_Phone`

点绿色 ▶ 按钮（或按 `Shift + F10`）。

Android Studio 会编译 → 安装 → 自动打开 App。

### 3. 首次运行需要联网权限
App 启动后会请求**通知权限**（Android 13+ 要求），点「允许」，
这样锁屏和通知栏才能显示播放控制。

---

## 六、跑了之后怎么用

1. 顶部搜索框输入歌手名（比如「周杰伦」），会自动搜索
2. 点任意一首歌 → 底部出现迷你播放条，同时展开全屏播放页
3. 播放页可以拖动进度条、切歌、切音质（右上角 128K/192K/320K/FLAC）
4. 「歌词」标签页会跟随播放自动滚动高亮
5. 按返回键收起播放页，音乐继续播

**实测可行的搜索示例**：周杰伦、林俊杰、五月天

---

## 七、常用操作速查

| 操作 | 快捷键 / 位置 |
| --- | --- |
| 运行 App | `Shift + F10` |
| 调试运行 | `Shift + F9` |
| 停止 | 工具栏红色 ■ |
| 同步 Gradle | `Ctrl + Shift + O`，或工具栏大象图标 |
| 查看日志 | 底部 **Logcat** 面板 |
| 清除缓存重建 | **Build → Clean Project** 然后 **Rebuild Project** |
| 打包 APK | **Build → Build Bundle(s) / APK(s) → Build APK(s)** |

**构建产物位置**：
```
MetingMusicPlayer\app\build\outputs\apk\debug\app-debug.apk
```

**装到真机**：把 APK 拷到手机点击安装；
或手机开 USB 调试后用命令行：
```bash
D:\Android\Sdk\platform-tools\adb.exe install -r app-debug.apk
```

---

## 八、命令行构建（可选）

项目已配好 `gradlew`，在项目根目录执行：

```bash
# Git Bash
cd "C:/Users/zhb08/WorkBuddy AI/2026-10-14-18-36-09/MetingMusicPlayer"
./gradlew assembleDebug

# CMD / PowerShell
cd "C:\Users\zhb08\WorkBuddy AI\2026-10-14-18-36-09\MetingMusicPlayer"
gradlew.bat assembleDebug
```

运行单元测试（LRC 歌词解析器，12 个用例）：
```bash
./gradlew testDebugUnitTest
```

---

## 九、界面速览

Android Studio 几个关键区域：

- **左侧 Project 面板**：项目文件树。默认是 Android 视图，
  点左上角下拉可切成 **Project** 视图看真实目录结构
- **中间编辑区**：写代码的地方
- **底部 Logcat**：看运行日志和报错，App 崩溃时先看这里
- **右上角设备下拉**：选运行目标
- **工具栏 ▶ 按钮**：运行 App

Compose 项目还有一个好用的功能：
编辑区右上角有 **Split / Design** 切换，可以实时预览界面效果，
不用每次都跑到模拟器上看。

---

## 十、本项目代码结构（方便你改）

```
MetingMusicPlayer/app/src/main/java/com/example/metingmusic/
├── MainActivity.kt              入口 + 迷你播放条
├── data/
│   ├── model/
│   │   ├── Song.kt              歌曲数据模型
│   │   └── LrcParser.kt         歌词解析（有单元测试）
│   ├── remote/MetingApi.kt      API 请求封装
│   └── repository/              多步请求编排
├── player/
│   ├── PlaybackService.kt       后台播放服务
│   └── PlayerController.kt      播放状态中枢
└── ui/
    ├── search/                  搜索页
    ├── player/PlayerScreen.kt   播放页
    └── theme/Theme.kt           主题配色
```

想改配色 → `ui/theme/Theme.kt`
想换 API 地址 → `data/remote/MetingApi.kt` 里的 `DEFAULT_BASE_URL`
想改搜索每页数量 → `ui/search/SearchViewModel.kt`

---

## 十一、注意事项

- **首次 Sync 和首次构建都比较慢**（要下载依赖），属正常现象，别中途关掉
- 模拟器吃内存，建议至少留 4GB 空闲内存
- App 依赖第三方 API（`api.qijieya.cn`），若该服务不可用会导致搜不到歌
- VIP 歌曲解析不稳定，是上游接口的限制，不是 App 的问题
- 本项目仅供学习研究，音频版权归各音乐平台所有
