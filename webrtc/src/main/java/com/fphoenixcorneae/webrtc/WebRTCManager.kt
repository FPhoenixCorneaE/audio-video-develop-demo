package com.fphoenixcorneae.webrtc

import android.content.Context
import org.webrtc.Camera1Enumerator
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer


object WebRTCManager {
    // 可以为任意字符串
    private const val AUDIO_TRACK_ID = "101"

    /**
     * 展示视频轨道
     */
    fun showVideoTrack(context: Context, surfaceViewRenderer: SurfaceViewRenderer) {
        // 步骤1：创建 PeerConnectionFactory
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        val peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        // 步骤2：创建音频源
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        // 添加音频源
        val audioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)

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

        // 步骤4：初始化视频控件 SurfaceViewRender
        // 是否镜像
        surfaceViewRenderer.setMirror(false)
        // 这个方法非常重要，不初始化黑屏
        surfaceViewRenderer.init(eglBaseContext, null)

        // 步骤5：将 VideoTrack 展示到 SurfaceViewRenderer 中
        // 创建视频轨道
        val videoTrack = peerConnectionFactory.createVideoTrack(AUDIO_TRACK_ID, videoSource)

        // 添加渲染接收器到轨道中，画面开始呈现
        videoTrack.addSink(surfaceViewRenderer)
    }

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
}