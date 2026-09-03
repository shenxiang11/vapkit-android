# VAPKit Android

独立实现的 VAP 礼物动画播放器（Kotlin），不是腾讯官方 SDK 的封装。

协议与旁边的 [vap-ios](../vap-ios) 相同：读 MP4 顶层 `vapc`，`MediaCodec` 解码，OpenGL ES 按 `rgbFrame` / `aFrame` 合成透明通道。运行时不依赖 FFmpeg、也不依赖第三方播放器。

## 运行 Demo

用 Android Studio 打开本目录，选一个 API 26+ 的模拟器或真机，Run `demo`。

或：

```bash
./gradlew :demo:installDebug
```

Demo 是直播间送礼页：背景循环视频，底部礼物栏，点发送后播 VAP 并自动收起面板。

可送：星际兔、月下玉兔、热血一拳、星光应援、告白花语。

加礼物：把带 `vapc` 的 MP4 放到 `demo/src/main/assets/gifts/`，再在 `demo/src/main/java/com/vapkit/demo/GiftCatalog.kt` 加一行。`assetName` 留 `null` 的格子会显示「待上架」。

## 接入

`minSdk` 26。`settings.gradle.kts` 加上 JitPack：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

模块依赖：

```kotlin
implementation("com.github.shenxiang11:vapkit-android:0.1.0")
```

## 用法

```kotlin
val player = VapPlayer()
val view = VapTextureView(context)
view.player = player

lifecycleScope.launch {
    player.load(file)
    player.loop = false
    player.play()
}
```

只解析：

```kotlin
val manifest = VapParser.parseMp4(file.readBytes())
```

## 目录

```text
vap-android/
├── vapkit/     解析、MediaCodec、OpenGL ES、VapPlayer、VapTextureView
└── demo/       直播送礼 Demo
```
