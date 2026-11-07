package dev.animatedhome.diagnostics

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Hardware-accelerated Surface-to-Surface video optimizer.
 * Converts heavy 2K/4K or AV1/VP9/60fps videos into battery-friendly 720p 30fps H.264 (AVC) MP4s
 * so the phone's dedicated fixed-function H.264 hardware decoder can play them at minimal power.
 */
object VideoOptimizer {
    private const val MAX_OPTIMIZED_DIMENSION = 1280
    private const val TARGET_FPS = 30
    private const val TARGET_BITRATE = 3_200_000 // 3.2 Mbps H.264
    private const val MIN_FRAME_STEP_US = 30_000L // ~30 fps cap

    fun needsOptimization(info: JSONObject): Boolean {
        if (info.optString("kind") != "video") return false
        if (info.optBoolean("optimized", false)) return false
        val mime = info.optString("mime", "").lowercase()
        val w = info.optInt("width", 0)
        val h = info.optInt("height", 0)
        val fps = info.optDouble("fps", 0.0)
        val isHeavyCodec = !mime.contains("avc") // AV1 (av01), VP9, HEVC
        val isHighRes = maxOf(w, h) > MAX_OPTIMIZED_DIMENSION
        val isHighFps = fps > 35.0
        return isHeavyCodec || isHighRes || isHighFps
    }

    fun targetDimensions(width: Int, height: Int, maxDim: Int = MAX_OPTIMIZED_DIMENSION): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 640 to 360
        val longest = maxOf(width, height)
        val scale = if (longest > maxDim) maxDim.toFloat() / longest.toFloat() else 1f
        val outW = (((width * scale).toInt() + 8) / 16 * 16).coerceAtLeast(160)
        val outH = (((height * scale).toInt() + 8) / 16 * 16).coerceAtLeast(160)
        return outW to outH
    }

    fun optimizeIfNeeded(context: Context, sourceFile: File, info: JSONObject): File {
        if (!needsOptimization(info)) return sourceFile
        val outTemp = File(context.filesDir, "opt-${UUID.randomUUID()}.mp4")
        val ok = runCatching { transcodeToAvc(context, sourceFile, outTemp, info) }.getOrDefault(false)
        if (ok && outTemp.isFile && outTemp.length() > 1024L) {
            sourceFile.delete()
            return outTemp
        } else {
            outTemp.delete()
            return sourceFile
        }
    }

    fun optimizeExistingWidgets(context: Context, onProgress: (String) -> Unit = {}): Int {
        val s = State(context)
        var optimizedCount = 0
        for (id in s.ids()) {
            for (base in listOf("selectedMedia", "playbackMedia")) {
                val key = WidgetAnimations.key(base, id)
                val info = s.obj(key)
                val file = MediaStore.file(context, info) ?: continue
                if (!needsOptimization(info)) continue
                onProgress("Optimizing widget #$id (${info.optString("name", "video")})…")
                val outTemp = File(context.filesDir, "opt-${UUID.randomUUID()}.mp4")
                val ok = runCatching { transcodeToAvc(context, file, outTemp, info) }.getOrDefault(false)
                if (ok && outTemp.isFile && outTemp.length() > 1024L) {
                    val dest = File(context.filesDir, "media-${UUID.randomUUID()}.bin")
                    if (outTemp.renameTo(dest)) {
                        val digest = MessageDigest.getInstance("SHA-256")
                        dest.inputStream().use { input ->
                            val buf = ByteArray(32768)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                digest.update(buf, 0, n)
                            }
                        }
                        info.put("file", dest.name)
                            .put("bytes", dest.length())
                            .put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
                            .put("optimized", true)
                        s.put(key, info)
                        if (base == "selectedMedia" && s.obj(WidgetAnimations.key("playbackMedia", id)).length() > 0) {
                            s.put(WidgetAnimations.key("playbackMedia", id), info)
                        }
                        optimizedCount++
                    } else {
                        outTemp.delete()
                    }
                } else {
                    outTemp.delete()
                }
            }
        }
        return optimizedCount
    }

    private fun transcodeToAvc(context: Context, inputFile: File, outputFile: File, info: JSONObject): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var glBridge: GlTranscodeBridge? = null
        try {
            extractor.setDataSource(inputFile.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return false
            extractor.selectTrack(track)
            val inFormat = extractor.getTrackFormat(track)
            val inMime = inFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val inW = inFormat.getInteger(MediaFormat.KEY_WIDTH)
            val inH = inFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = info.optInt("rotation", if (inFormat.containsKey(MediaFormat.KEY_ROTATION)) inFormat.getInteger(MediaFormat.KEY_ROTATION) else 0)
            val dispW = if (rotation == 90 || rotation == 270) inH else inW
            val dispH = if (rotation == 90 || rotation == 270) inW else inH
            val (outW, outH) = targetDimensions(dispW, dispH)

            val outMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val outFormat = MediaFormat.createVideoFormat(outMime, outW, outH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(outFormat)
                ?: MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull {
                    it.isEncoder && it.supportedTypes.any { t -> t.equals(outMime, true) }
                }?.name ?: return false
            encoder = MediaCodec.createByCodecName(encName)
            encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderInputSurface = encoder.createInputSurface()
            encoder.start()

            glBridge = GlTranscodeBridge(encoderInputSurface, outW, outH)

            val decCandidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter {
                !it.isEncoder && it.supportedTypes.any { t -> t.equals(inMime, true) }
            }.sortedByDescending { if (Build.VERSION.SDK_INT >= 29 && it.isHardwareAccelerated) 1 else 0 }

            for (cand in decCandidates) {
                var next: MediaCodec? = null
                try {
                    next = MediaCodec.createByCodecName(cand.name)
                    next.configure(inFormat, glBridge.decoderSurface, null, 0)
                    next.start()
                    decoder = next
                    break
                } catch (_: Exception) {
                    runCatching { next?.release() }
                }
            }
            if (decoder == null) return false

            muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            var muxerTrack = -1
            var muxerStarted = false
            var lastEncodedPtsUs = -1_000_000L
            var outputFrames = 0
            val deadlineMs = System.currentTimeMillis() + 90_000L // Max 90s safety timeout

            while (!encoderDone && System.currentTimeMillis() < deadlineMs) {
                if (!inputDone) {
                    val inBufIdx = decoder.dequeueInputBuffer(2_000)
                    if (inBufIdx >= 0) {
                        val buf = decoder.getInputBuffer(inBufIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inBufIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inBufIdx, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                var drainedDecoder = true
                while (drainedDecoder && !decoderDone) {
                    val outIdx = decoder.dequeueOutputBuffer(decInfo, 2_000)
                    when {
                        outIdx >= 0 -> {
                            val isEos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            val ptsUs = decInfo.presentationTimeUs
                            val shouldRender = decInfo.size > 0 && (ptsUs - lastEncodedPtsUs >= MIN_FRAME_STEP_US || outputFrames == 0)
                            decoder.releaseOutputBuffer(outIdx, shouldRender)
                            if (shouldRender) {
                                if (glBridge.awaitAndDraw(ptsUs * 1000L)) {
                                    lastEncodedPtsUs = ptsUs
                                    outputFrames++
                                }
                            }
                            if (isEos) {
                                decoderDone = true
                                encoder.signalEndOfInputStream()
                            }
                        }
                        else -> drainedDecoder = false
                    }
                }

                var drainedEncoder = true
                while (drainedEncoder && !encoderDone) {
                    val encIdx = encoder.dequeueOutputBuffer(encInfo, 2_000)
                    when {
                        encIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                muxerTrack = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        encIdx >= 0 -> {
                            val encodedData = encoder.getOutputBuffer(encIdx)
                            if ((encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                encInfo.size = 0
                            }
                            if (encInfo.size > 0 && muxerStarted && encodedData != null) {
                                encodedData.position(encInfo.offset)
                                encodedData.limit(encInfo.offset + encInfo.size)
                                muxer.writeSampleData(muxerTrack, encodedData, encInfo)
                            }
                            encoder.releaseOutputBuffer(encIdx, false)
                            if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoderDone = true
                            }
                        }
                        else -> drainedEncoder = false
                    }
                }
            }

            if (muxerStarted && outputFrames > 0) {
                muxer.stop()
                info.put("mime", outMime)
                    .put("width", outW)
                    .put("height", outH)
                    .put("rotation", 0)
                    .put("fps", TARGET_FPS)
                    .put("optimized", true)
                DiagnosticLog.event(context, "VIDEO_OPTIMIZED", "${inW}x${inH} $inMime -> ${outW}x${outH} $outMime frames=$outputFrames")
                return true
            }
            return false
        } finally {
            runCatching { decoder?.stop() }; runCatching { decoder?.release() }
            runCatching { glBridge?.release() }
            runCatching { encoder?.stop() }; runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private class GlTranscodeBridge(encoderSurface: Surface, private val width: Int, private val height: Int) {
        private val cbThread = HandlerThread("VideoOptGL").apply { start() }
        private val cbHandler = Handler(cbThread.looper)
        private var display = EGL14.EGL_NO_DISPLAY
        private var context = EGL14.EGL_NO_CONTEXT
        private var surface = EGL14.EGL_NO_SURFACE
        private var oesTex = 0
        private var program = 0
        private val texMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
        private val frameSync = Object()
        private var frameAvailable = false
        val surfaceTexture: SurfaceTexture
        val decoderSurface: Surface

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2)
            EGL14.eglInitialize(display, ver, 0, ver, 1)
            val attrs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGLExt.EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, numConfigs, 0)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surface = EGL14.eglCreateWindowSurface(display, configs[0], encoderSurface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(display, surface, surface, context)

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            oesTex = texIds[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            val vs = compileShader(
                GLES20.GL_VERTEX_SHADER,
                "attribute vec2 aPosition; attribute vec2 aUv; uniform mat4 uMatrix; varying vec2 vUv; void main(){gl_Position=vec4(aPosition,0.0,1.0);vUv=(uMatrix*vec4(aUv,0.0,1.0)).xy;}"
            )
            val fs = compileShader(
                GLES20.GL_FRAGMENT_SHADER,
                "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vUv; uniform samplerExternalOES uTexture; void main(){gl_FragColor=texture2D(uTexture,vUv);}"
            )
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vs)
            GLES20.glAttachShader(program, fs)
            GLES20.glLinkProgram(program)

            surfaceTexture = SurfaceTexture(oesTex)
            surfaceTexture.setOnFrameAvailableListener({
                synchronized(frameSync) {
                    frameAvailable = true
                    frameSync.notifyAll()
                }
            }, cbHandler)
            decoderSurface = Surface(surfaceTexture)
        }

        fun awaitAndDraw(presentationTimeNs: Long): Boolean {
            synchronized(frameSync) {
                val deadline = System.currentTimeMillis() + 1_500L
                while (!frameAvailable) {
                    val waitMs = deadline - System.currentTimeMillis()
                    if (waitMs <= 0L) return false
                    frameSync.wait(waitMs)
                }
                frameAvailable = false
            }
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(texMatrix)
            GLES20.glViewport(0, 0, width, height)
            val data = floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)
            val buf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
            val pos = GLES20.glGetAttribLocation(program, "aPosition")
            val uv = GLES20.glGetAttribLocation(program, "aUv")
            GLES20.glEnableVertexAttribArray(pos)
            GLES20.glEnableVertexAttribArray(uv)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, buf)
            buf.position(2)
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, buf)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMatrix"), 1, false, texMatrix, 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(pos)
            GLES20.glDisableVertexAttribArray(uv)
            EGLExt.eglPresentationTimeANDROID(display, surface, presentationTimeNs)
            EGL14.eglSwapBuffers(display, surface)
            return true
        }

        private fun compileShader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }

        fun release() {
            runCatching { surfaceTexture.setOnFrameAvailableListener(null) }
            runCatching { decoderSurface.release() }
            runCatching { surfaceTexture.release() }
            if (display != EGL14.EGL_NO_DISPLAY) {
                runCatching { GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0) }
                runCatching { GLES20.glDeleteProgram(program) }
                runCatching { EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
                runCatching { EGL14.eglDestroySurface(display, surface) }
                runCatching { EGL14.eglDestroyContext(display, context) }
                runCatching { EGL14.eglTerminate(display) }
            }
            runCatching { cbThread.quitSafely() }
        }
    }
}
