# Android WebRTC 入门教程

### WebRTC 简介

> WebRTC (Web Real-Time Communications)
> 是一项实时通讯技术，它允许网络应用或者站点，在不借助中间媒介的情况下，建立浏览器之间点对点(Peer-to-Peer)
> 的连接，实现视频流和视频流或者任意数据的传输。
>
> 目前，WebRTC 的应用已经不局限在浏览器和浏览器之间，通过官网提供的 SDK ，我们可以实现本地应用的音视频传输。
> 用法也非常简单，可以用非常简介的代码实现强大可靠的音视频功能。

一些基本概念：

* **RTC(Real Time Communication)**:  实时通信
* **WebRTC**:  基于web的实时通信
* **Signaling**:  信令, 一些描述媒体或网络的字符串
* **SDP(Session Description Protocol)**:  会话描述协议, 主要描述媒体信息
* **ICE(Interactive Connectivity Establishment)**:  交互式连接建立
* **STUN(Session Traversal Utilities for NAT)**:  NAT会话穿透工具
* **TURN(Traversal Using Relays around NAT)**:  中继穿透NAT

### 一、添加 WebRTC 库

使用 JCenter 上提供的[官方](https://webrtc.github.io/webrtc-org/native-code/android/)预构建库：

```groovy
// WebRTC 官方集成库
implementation("org.webrtc:google-webrtc:1.0.32006")
```

### 二、使用相机获取视频数据

1. **创建 Surface**

```kotlin
import org.webrtc.SurfaceViewRenderer

// 视频控件用来承载画面，它是 SurfaceView 的子类
SurfaceViewRenderer(context)
```

> 它实现了 `VideoSink` 接口，它内部是通过 openGL 去渲染的。

2. **创建 PeerConnectionFactory**

```kotlin
// 步骤1：创建 PeerConnectionFactory 
val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
    .setEnableInternalTracer(true)
    .createInitializationOptions()
PeerConnectionFactory.initialize(initializationOptions)
val peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
```

3. **创建音频源**

```kotlin
// 步骤2：创建音频源
val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
// 添加音频源
val audioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
```

4. **创建视频源**

```kotlin
// 步骤3：创建视频源
val videoCapturer = createCameraCapturer()
val videoSource = peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast ?: false)

val eglBaseContext = EglBase.create().eglBaseContext
// 拿到 surface 帮助类，用来创建 camera 初始化的线程
val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
// 用来初始化当前 camera 的线程，和 application context，当调用 startCapture 才会回调
videoCapturer?.initialize(surfaceTextureHelper, context.applicationContext, videoSource.capturerObserver)
// 开始采集
videoCapturer?.startCapture(surfaceViewRenderer.width, surfaceViewRenderer.height, 30)
```

> 对于音频来说，在创建 `AudioSource` 的时候，就开始捕获设备音频数据了。对于视频流说来， WebRTC 定义了 `VideoCaturer`
> 抽象接口，并提供了三种实现： `ScreenCapturerAndroid`、`CameraCapturer` 和 `FileVideoCapturer`，分别为从录屏、摄像头及文件中获取视频流，调用
> `startCapture()` 后将开始获取数据。
> 因为我们用到相机，所以是 `CamreaCapturer` ，它有两个子类，`Camera1Enumerator` 和 `Camera2Enumerator` ，这里使用
> `Camera1Enumerator`。

```kotlin
private fun createCameraCapturer(): VideoCapturer? {
    val enumerator = Camera1Enumerator(false)
    val deviceNames = enumerator.deviceNames

    // First, try to find back facing camera
    for (deviceName in deviceNames) {
        if (enumerator.isBackFacing(deviceName)) {
            val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
            if (videoCapturer != null) {
                return videoCapturer
            }
        }
    }

    // Back facing camera not found, try something else
    for (deviceName in deviceNames) {
        if (!enumerator.isFrontFacing(deviceName)) {
            val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
            if (videoCapturer != null) {
                return videoCapturer
            }
        }
    }
    return null
}
```

5. **初始化视频控件 SurfaceViewRender**

```kotlin
// 步骤4：初始化视频控件 SurfaceViewRender
// 是否镜像
surfaceViewRenderer.setMirror(false)
// 这个方法非常重要，不初始化黑屏
surfaceViewRenderer.init(eglBaseContext, null)
```

6. **将 VideoTrack 展示到 SurfaceViewRenderer 中**

```kotlin
// 步骤5：将 VideoTrack 展示到 SurfaceViewRenderer 中
// 创建视频轨道
val videoTrack = peerConnectionFactory.createVideoTrack(AUDIO_TRACK_ID, videoSource)

// 添加渲染接收器到轨道中，画面开始呈现
videoTrack.addSink(surfaceViewRenderer)
```

<br>

[WebRTC 中文社区](https://webrtc.org.cn/)
<br>
[webrtc.github.io](https://webrtc.github.io/webrtc-org/native-code/android/)
<br>
[与WebRTC实时通信](https://codelabs.developers.google.com/codelabs/webrtc-web/#0)
