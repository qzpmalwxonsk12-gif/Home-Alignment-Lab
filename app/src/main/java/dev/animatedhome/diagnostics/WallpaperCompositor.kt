package dev.animatedhome.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.opengl.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/** A single EGL surface composites independent media layers for the active home page. */
class WallpaperCompositor(private val context:Context,surface:Surface,private val onFailure:(String)->Unit) {
    data class LayerSpec(val id:Int,val box:Box?,val source:JSONObject,val scaleMode:String="FIT",val keepPlaying:Boolean=true)
    private val thread=HandlerThread("WallpaperGL").apply {start()}
    private val handler=Handler(thread.looper)
    private val main=Handler(context.mainLooper)
    @Volatile private var visible=false
    @Volatile private var allowed=false
    @Volatile private var closed=false
    @Volatile private var failed=false
    @Volatile private var pendingSyncReset=false
    private var sharedSyncNanos=0L
    private val gateEpoch=AtomicLong()
    private var display=EGL14.EGL_NO_DISPLAY
    private var eglContext=EGL14.EGL_NO_CONTEXT
    private var window=EGL14.EGL_NO_SURFACE
    private var bitmapProgram=0
    private var videoProgram=0
    private var staticTexture=0
    private var blackTexture=0
    private var width=0
    private var height=0
    private val identity=FloatArray(16).apply {Matrix.setIdentityM(this,0)}
    private val fullUv=floatArrayOf(0f,0f,1f,1f)
    private val layers=linkedMapOf<Int,MediaLayer>()
    private data class Scene(val bitmap:Bitmap?,val specs:List<LayerSpec>,val epoch:Long,val posted:()->Unit)
    private val sceneLock=Any()
    private var pendingScene:Scene?=null
    init {handler.post {protect {createEgl(surface)}}}
    private fun protect(action:()->Unit) {
        if(closed || failed)return
        try {action()} catch(e:Exception) {
            allowed=false;failed=true;gateEpoch.incrementAndGet()
            layers.values.forEach {runCatching {it.pause()}}
            DiagnosticLog.event(context,"COMPOSITOR_FAILURE",e.toString())
            main.post {onFailure(e.javaClass.simpleName+": "+(e.message ?: "Drawing failed"))}
        }
    }
    fun setVisible(value:Boolean) {
        visible=value
        if(!value){allowed=false;gateEpoch.incrementAndGet()}
        handler.post {protect {
            if(!value) {
                layers.values.forEach {it.pause()}
            } else {
                val syncNow=if(pendingSyncReset) System.nanoTime().also {sharedSyncNanos=it;pendingSyncReset=false} else 0L
                if(syncNow!=0L) layers.values.forEach {it.rewindToStart(syncNow)}
                layers.values.forEach {it.syncGifFrame()}
                draw()
                layers.values.forEach {it.schedulePump(syncNow)}
            }
        }}
    }
    fun resetAllToStart() {
        pendingSyncReset=true
        handler.post {protect {
            val now=System.nanoTime()
            sharedSyncNanos=now
            layers.values.forEach {it.rewindToStart(now)}
            if(visible) {
                pendingSyncReset=false
                draw()
                layers.values.forEach {it.schedulePump(now)}
            }
        }}
    }
    fun suspendVideo() {
        val wasAllowed=allowed;allowed=false;gateEpoch.incrementAndGet()
        if(wasAllowed)handler.post {protect {layers.values.forEach {it.pause()};draw()}}
    }
    fun scene(bitmap:Bitmap?,specs:List<LayerSpec>,posted:()->Unit={}) {
        if(closed || failed){bitmap?.recycle();return}
        synchronized(sceneLock){
            val previousBitmap=pendingScene?.bitmap
            val mergedBitmap=if(bitmap!=null) { previousBitmap?.recycle(); bitmap } else previousBitmap
            pendingScene=Scene(mergedBitmap,specs,gateEpoch.get(),posted)
        }
        handler.post {protect {
            val next=synchronized(sceneLock){pendingScene.also {pendingScene=null}} ?: return@protect
            try {
                if(next.bitmap!=null) {
                    width=next.bitmap.width;height=next.bitmap.height
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,staticTexture)
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,next.bitmap,0)
                }
                val wanted=next.specs.map {it.id}.toSet()
                layers.keys.filter {it !in wanted}.forEach {layers.remove(it)?.dispose()}
                allowed=visible && next.epoch==gateEpoch.get()
                for(spec in next.specs) {
                    var layer=layers[spec.id]
                    if(layer!=null && layer.requestedSource.optString("file","bundled")!=spec.source.optString("file","bundled")) {
                        layer.dispose();layers.remove(spec.id);layer=null
                    }
                    if(layer==null){layer=MediaLayer(spec.id,spec.source);layers[spec.id]=layer}
                    layer.rect=spec.box
                    layer.scaleMode=spec.scaleMode
                    layer.keepPlaying=spec.keepPlaying
                    if(!allowed || (spec.box==null && layer.isReady()))layer.pause()
                    else if(spec.box!=null)layer.syncGifFrame()
                }
                if(draw())main.post(next.posted)
                layers.values.forEach {it.schedulePump()}
            } finally {next.bitmap?.recycle()}
        }}
    }
    private fun createEgl(surface:Surface) {
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display!=EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0))
        val configs=arrayOfNulls<EGLConfig>(1); val count=IntArray(1)
        val attrs=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
            EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_NONE)
        check(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0) && count[0]>0)
        eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        window=EGL14.eglCreateWindowSurface(display,configs[0],surface,intArrayOf(EGL14.EGL_NONE),0)
        check(eglContext!=EGL14.EGL_NO_CONTEXT && window!=EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display,window,window,eglContext))
        bitmapProgram=program(false); videoProgram=program(true)
        staticTexture=texture(GLES20.GL_TEXTURE_2D)
        blackTexture=texture(GLES20.GL_TEXTURE_2D)
        val blackBmp=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,blackTexture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,blackBmp,0)
        blackBmp.recycle()
        DiagnosticLog.event(context,"COMPOSITOR_READY","EGL OpenGL ES 2; static texture + clipped video texture")
    }
    private fun texture(target:Int):Int {
        val ids=IntArray(1); GLES20.glGenTextures(1,ids,0); GLES20.glBindTexture(target,ids[0])
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }
    private fun program(external:Boolean):Int {
        val vertex="attribute vec2 aPosition; attribute vec2 aUv; uniform mat4 uMatrix; varying vec2 vUv; void main(){gl_Position=vec4(aPosition,0.0,1.0);vUv=(uMatrix*vec4(aUv,0.0,1.0)).xy;}"
        val fragment=(if(external) "#extension GL_OES_EGL_image_external : require\n" else "")+
            "precision mediump float; varying vec2 vUv; uniform ${if(external) "samplerExternalOES" else "sampler2D"} uTexture; void main(){gl_FragColor=texture2D(uTexture,vUv);}"
        fun shader(type:Int,source:String):Int {
            val s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,source); GLES20.glCompileShader(s)
            val ok=IntArray(1); GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0)
            check(ok[0]!=0) { GLES20.glGetShaderInfoLog(s) }; return s
        }
        val vs=shader(GLES20.GL_VERTEX_SHADER,vertex); val fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment)
        val p=GLES20.glCreateProgram(); GLES20.glAttachShader(p,vs); GLES20.glAttachShader(p,fs); GLES20.glLinkProgram(p)
        val ok=IntArray(1); GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0)
        check(ok[0]!=0) { GLES20.glGetProgramInfoLog(p) }; GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs); return p
    }
    private fun quad(p:Int,target:Int,tex:Int,b:Box,external:Boolean,matrix:FloatArray=identity,uvRect:FloatArray=fullUv) {
        val l=2*b.left/width-1; val r=2*b.right/width-1; val t=1-2*b.top/height; val bottom=1-2*b.bottom/height
        val u0=uvRect[0]; val v0=uvRect[1]; val u1=uvRect[2]; val v1=uvRect[3]
        val uvBottom=if(external) v0 else v1; val uvTop=if(external) v1 else v0
        val data=floatArrayOf(l,bottom,u0,uvBottom,r,bottom,u1,uvBottom,l,t,u0,uvTop,r,t,u1,uvTop)
        val buffer=ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
        GLES20.glUseProgram(p); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(target,tex)
        val pos=GLES20.glGetAttribLocation(p,"aPosition"); val uv=GLES20.glGetAttribLocation(p,"aUv")
        GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,buffer)
        buffer.position(2); GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,buffer)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(p,"uMatrix"),1,false,matrix,0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uTexture"),0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(uv)
    }
    private fun draw():Boolean {
        if(!visible || width<=0 || height<=0 || closed || failed)return false
        GLES20.glViewport(0,0,width,height)
        quad(bitmapProgram,GLES20.GL_TEXTURE_2D,staticTexture,Box(0f,0f,width.toFloat(),height.toFloat()),false)
        if(allowed)layers.values.forEach {it.paint()}
        check(EGL14.eglSwapBuffers(display,window)){"eglSwapBuffers failed: ${EGL14.eglGetError()}"}
        return true
    }
    private inner class MediaLayer(val id:Int,val requestedSource:JSONObject) {
        var rect:Box?=null
        var scaleMode:String="FIT"
        var keepPlaying:Boolean=true
        private var disposed=false
        fun isReady()=ready
        private val shouldRun get()=this@WallpaperCompositor.allowed && !disposed && (rect!=null || (!ready && keepPlaying))
        private val shouldPaint get()=this@WallpaperCompositor.allowed && !disposed && rect!=null && ready
        private val textureMatrix=FloatArray(16)
        private var oesTexture=texture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
    private var videoTexture:SurfaceTexture?=null
    private var decoderSurface:Surface?=null
    private var codec:MediaCodec?=null
    private var extractor:MediaExtractor?=null
    private val info=MediaCodec.BufferInfo()
    private var pendingIndex=-1
    private var inputEos=false
    private var running=false
    private var ready=false
    private var needsFirstFrame=true
    private var clockStart=0L
    private var activeStart=0L
    private var activeNanos=0L
    private var gifElapsedMs=0L
    private var lastRenderedPtsUs=-1_000_000L
    private var frames=0L
    private var dropped=0L
    private var decoded=0L
    private var resyncs=0
    private var loops=0
    private var pauses=0
    private var videoWidth=640
    private var videoHeight=360
    private var decoderName="not started"
    private var hardware="unknown"
    private var lastFrame=0L
    private var lastReport=0L
    private var maxGapMs=0L
    private val frameIntervals=ArrayList<Long>()
    private var durationUs=0L
    private var sourceFps=0
    private var lastResumeToFrameMillis=0L
    private var sourceKey=""
    private var movie:Movie?=null
    private var gifBitmap:Bitmap?=null
    private var gifTexture=0
    private var gifLoop=0L
    private var preparingSince=0L
    private var streamPlayer:StreamPlayer?=null
        init {
            gifTexture=texture(GLES20.GL_TEXTURE_2D)
            videoTexture=SurfaceTexture(oesTexture).also {st->
                st.setOnFrameAvailableListener({mediaProtect {
                    st.updateTexImage();st.getTransformMatrix(textureMatrix)
                    if(this@WallpaperCompositor.allowed && !disposed && visible && running && movie==null){
                        ready=true
                        if(rect!=null) {
                            if(draw())recordFrame()
                        } else {
                            pause()
                        }
                    }
                }},handler)
            }
            decoderSurface=Surface(videoTexture)
        }
        private fun mediaProtect(action:()->Unit) {
            if(disposed || closed || failed)return
            try {action()} catch(e:Exception) {
                runCatching {pause(true)};runCatching {dispose()}
                val reason="Playback stopped: "+(e.message ?: e.javaClass.simpleName)
                DiagnosticLog.event(context,"WIDGET_PLAYBACK_ERROR","widget=$id; $reason")
                main.post {
                    if(id<0)onFailure(reason) else {
                        State(context).prefs.edit().putBoolean(WidgetAnimations.key("enabled",id),false)
                            .putString(WidgetAnimations.key("mediaError",id),reason).apply()
                        CalibrationWidget.update(context,id)
                    }
                }
            }
        }
        fun syncGifFrame() {
            val gif=movie ?: return
            val bitmap=gifBitmap ?: return
            if(!running)return
            val elapsed=gifElapsedMs+(System.nanoTime()-activeStart)/1_000_000
            val duration=(durationUs/1000).coerceAtLeast(100)
            val movieDur=gif.duration().coerceAtLeast(100)
            val targetTime=((elapsed%duration)*movieDur/duration).toInt()
            if(gif.setTime(targetTime) || !ready) {
                bitmap.eraseColor(Color.TRANSPARENT)
                val c=Canvas(bitmap)
                if(bitmap.width!=videoWidth || bitmap.height!=videoHeight) {
                    c.scale(bitmap.width.toFloat()/videoWidth.coerceAtLeast(1),bitmap.height.toFloat()/videoHeight.coerceAtLeast(1))
                }
                gif.draw(c,0f,0f)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,gifTexture);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
                ready=true
            }
        }
        fun paint() {
            val b=rect ?: return
            if(!shouldPaint)return
            val drawBox=if(scaleMode=="FILL") b else VideoPolicy.fit(b,videoWidth,videoHeight)
            val uv=if(scaleMode=="FILL") VideoPolicy.cropUv(b,videoWidth,videoHeight) else fullUv
            if(movie!=null) {
                GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA)
                quad(bitmapProgram,GLES20.GL_TEXTURE_2D,gifTexture,drawBox,false,identity,uv)
                GLES20.glDisable(GLES20.GL_BLEND)
            } else {
                val backing=if(scaleMode=="FIT_BLACK") b else drawBox
                quad(bitmapProgram,GLES20.GL_TEXTURE_2D,blackTexture,backing,false)
                quad(videoProgram,GLES11Ext.GL_TEXTURE_EXTERNAL_OES,oesTexture,drawBox,true,textureMatrix,uv)
            }
        }
    private fun recordFrame() {
        frames++
        val now=System.nanoTime()
        if(lastFrame!=0L) {val gap=(now-lastFrame)/1_000_000;maxGapMs=max(maxGapMs,gap);if(frameIntervals.size<10000)frameIntervals.add(gap)}
        lastFrame=now
        if(needsFirstFrame) {
            needsFirstFrame=false;lastResumeToFrameMillis=(now-activeStart)/1_000_000
            DiagnosticLog.event(context,"VIDEO_RESUME_FRAME","decodeToFrameMillis=$lastResumeToFrameMillis")
            if(id<0)main.post {VideoFlow.framePosted(context)}
        }
        report("PLAYING")
    }
    private fun prepareDecoder() {
        val selected=requestedSource
        val key=selected.optString("file","bundled")
        if(key!=sourceKey) {
            pause(true);releaseMedia();sourceKey=key
            frames=0;decoded=0;dropped=0;loops=0;pauses=0;resyncs=0;activeNanos=0;gifElapsedMs=0;frameIntervals.clear();maxGapMs=0
            preparingSince=SystemClock.elapsedRealtime()
        }
        if(movie!=null)return
        if(codec!=null) return
        if(streamPlayer!=null) return
        if(MediaStore.isStream(selected)) {
            videoWidth=selected.optInt("width",1280).coerceAtLeast(16)
            videoHeight=selected.optInt("height",720).coerceAtLeast(16)
            durationUs=0L; sourceFps=30
            decoderName=if(selected.optString("kind")=="youtube") "YouTube Stream Engine" else "Chrome Web Stream Engine"
            hardware="true"
            streamPlayer=StreamPlayer(
                context=context,
                widgetId=id,
                source=selected,
                decoderSurface=decoderSurface!!,
                videoTexture=videoTexture!!,
                onVideoSizeChanged={w,h->handler.post {mediaProtect {if(!disposed){videoWidth=w;videoHeight=h;if(ready && rect!=null)draw()}}}},
                onFrameHeartbeat={handler.post {mediaProtect {
                    if(this@WallpaperCompositor.allowed && !disposed && visible && running && streamPlayer!=null) {
                        ready=true
                        if(rect!=null) {
                            if(draw())recordFrame()
                        }
                    }
                }}}
            ).also { it.start() }
            return
        }
        val file=if(key!="bundled") MediaStore.file(context,selected) ?: error("The saved animation is missing. Choose it again.") else null
        if(selected.optString("kind")=="gif") {
            @Suppress("DEPRECATION")
            val m=Movie.decodeFile(file!!.path) ?: error("This GIF could not be decoded. Choose another GIF.")
            movie=m;videoWidth=m.width();videoHeight=m.height()
            durationUs=selected.optLong("durationUs",0L).coerceAtLeast(m.duration().coerceAtLeast(100)*1000L)
            val gifScale=minOf(1f,512f/maxOf(videoWidth,videoHeight).coerceAtLeast(1))
            gifBitmap=Bitmap.createBitmap((videoWidth*gifScale).toInt().coerceAtLeast(1),(videoHeight*gifScale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            decoderName="Android GIF decoder";hardware="false";sourceFps=0;return
        }
        if(layers.values.count { it.codec != null } >= 6) {
            layers.values.filter { it !== this && !it.running && it.rect == null && it.codec != null }
                .forEach { it.releaseIdleCodecKeepFrame() }
        }
        val ex=MediaExtractor(); extractor=ex
        if(file!=null)ex.setDataSource(file.path)
        else context.resources.openRawResourceFd(R.raw.motion_test).use { ex.setDataSource(it.fileDescriptor,it.startOffset,it.length) }
        val track=(0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true }
        ex.selectTrack(track); val format=ex.getTrackFormat(track); val mime=format.getString(MediaFormat.KEY_MIME)!!
        videoWidth=format.getInteger(MediaFormat.KEY_WIDTH); videoHeight=format.getInteger(MediaFormat.KEY_HEIGHT)
        val scaleDown=minOf(1f,1920f/maxOf(videoWidth,videoHeight).coerceAtLeast(1))
        videoTexture?.setDefaultBufferSize((videoWidth*scaleDown).toInt().coerceAtLeast(1),(videoHeight*scaleDown).toInt().coerceAtLeast(1))
        val rotation=if(format.containsKey(MediaFormat.KEY_ROTATION))runCatching {format.getInteger(MediaFormat.KEY_ROTATION)}.getOrDefault(selected.optInt("rotation",0)) else selected.optInt("rotation",0)
        if(rotation==90 || rotation==270){val old=videoWidth;videoWidth=videoHeight;videoHeight=old}
        durationUs=if(format.containsKey(MediaFormat.KEY_DURATION))runCatching {format.getLong(MediaFormat.KEY_DURATION)}.getOrDefault(selected.optLong("durationUs",10_000_000L)) else selected.optLong("durationUs",10_000_000L)
        sourceFps=if(format.containsKey(MediaFormat.KEY_FRAME_RATE))runCatching {format.getInteger(MediaFormat.KEY_FRAME_RATE)}.getOrElse {runCatching {format.getFloat(MediaFormat.KEY_FRAME_RATE).toInt()}.getOrDefault(0)} else 0
        val candidates=MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder && it.supportedTypes.any { type -> type.equals(mime,true) } }
            .sortedByDescending { if(Build.VERSION.SDK_INT>=29 && it.isHardwareAccelerated) 1 else 0 }
        var last:Exception?=null
        for(candidate in candidates) {
            var next:MediaCodec?=null
            try {
                next=MediaCodec.createByCodecName(candidate.name); next.configure(format,decoderSurface,null,0); next.start(); codec=next
                decoderName=candidate.name; hardware=if(Build.VERSION.SDK_INT>=29) candidate.isHardwareAccelerated.toString() else "unknown (API <29)"
                DiagnosticLog.event(context,"VIDEO_DECODER","name=$decoderName hardware=$hardware format=$format"); return
            } catch(e:Exception) { last=e; runCatching { next?.release() }; DiagnosticLog.event(context,"VIDEO_DECODER_REJECTED",candidate.name+": "+e.javaClass.simpleName) }
        }
        throw IllegalStateException("This phone could not play the selected video. Try a standard H.264 MP4 or another file.",last)
    }
    private val pump=object:Runnable {
        override fun run() { mediaProtect {
            if(!shouldRun || !visible) { pause(); return@mediaProtect }
            prepareDecoder()
            if(!running) { running=true; activeStart=System.nanoTime(); clockStart=0; lastFrame=0; preparingSince=SystemClock.elapsedRealtime(); report("PLAYING",true) }
            val gif=movie
            if(gif!=null) {
                val elapsed=gifElapsedMs+(System.nanoTime()-activeStart)/1_000_000
                val duration=(durationUs/1000).coerceAtLeast(100)
                val cycle=elapsed/duration
                if(cycle>gifLoop){loops+=(cycle-gifLoop).toInt();gifLoop=cycle}
                val movieDur=gif.duration().coerceAtLeast(100)
                val targetTime=((elapsed%duration)*movieDur/duration).toInt()
                val changed=gif.setTime(targetTime)
                if((changed || !ready) && (rect!=null || !ready)) {
                    val bitmap=gifBitmap!!;bitmap.eraseColor(Color.TRANSPARENT)
                    val c=Canvas(bitmap)
                    if(bitmap.width!=videoWidth || bitmap.height!=videoHeight) {
                        c.scale(bitmap.width.toFloat()/videoWidth.coerceAtLeast(1),bitmap.height.toFloat()/videoHeight.coerceAtLeast(1))
                    }
                    gif.draw(c,0f,0f)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,gifTexture);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
                    ready=true;decoded++
                    if(rect!=null) {
                        if(draw())recordFrame()
                    } else {
                        pause();return@mediaProtect
                    }
                }
                report("PLAYING")
                if(shouldRun && visible)handler.postDelayed(this,if(State(context).prefs.getBoolean("highFpsMode",false)) 33 else 40)
                return@mediaProtect
            }
            val sp=streamPlayer
            if(sp!=null) {
                sp.start()
                report("PLAYING")
                if(shouldRun && visible)handler.postDelayed(this,500)
                return@mediaProtect
            }
            if(needsFirstFrame && !ready && SystemClock.elapsedRealtime()-preparingSince>10000)
                error("The video did not produce a frame. Choose another file or try an H.264 MP4.")
            val c=codec!!; val ex=extractor!!
            repeat(4) {
                if(!inputEos) {
                    val index=c.dequeueInputBuffer(0)
                    if(index>=0) {
                        val data=c.getInputBuffer(index)!!; val size=ex.readSampleData(data,0)
                        if(size<0) { c.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEos=true }
                        else { c.queueInputBuffer(index,0,size,ex.sampleTime,0); ex.advance() }
                    }
                }
            }
            if(pendingIndex<0) pendingIndex=c.dequeueOutputBuffer(info,0)
            var delay=4L
            if(pendingIndex>=0) {
                if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    c.releaseOutputBuffer(pendingIndex,false); pendingIndex=-1; loops++
                    c.flush(); ex.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC); inputEos=false; clockStart=0; lastRenderedPtsUs=-1_000_000L
                    DiagnosticLog.event(context,"VIDEO_LOOP","loop=$loops frames=$frames"); report("PLAYING",true)
                } else {
                    val now=System.nanoTime()
                    if(clockStart==0L) clockStart=now-info.presentationTimeUs*1000
                    if(VideoPolicy.needsResync(clockStart,info.presentationTimeUs,now)) {
                        // Recover after a long GPU/decoder stall instead of discarding an
                        // entire loop when decode cannot catch the old wall-clock deadline.
                        clockStart=now-info.presentationTimeUs*1000; lastRenderedPtsUs=-1_000_000L; resyncs++
                    }
                    delay=VideoPolicy.frameDelayMillis(clockStart,info.presentationTimeUs,now)
                    if(delay<=1) {
                        val late=now-(clockStart+info.presentationTimeUs*1000)>100_000_000
                        val minStepUs=if(State(context).prefs.getBoolean("highFpsMode",false)) 0L else 28_000L
                        val skipForEco=!late && ready && minStepUs>0L && lastRenderedPtsUs>=0L && info.presentationTimeUs>=lastRenderedPtsUs && (info.presentationTimeUs-lastRenderedPtsUs)<minStepUs
                        val renderFrame=!late && !skipForEco && shouldRun && visible
                        c.releaseOutputBuffer(pendingIndex,renderFrame)
                        if(renderFrame) lastRenderedPtsUs=info.presentationTimeUs
                        decoded++
                        if(late) dropped++
                        pendingIndex=-1; delay=1
                    }
                }
            }
            report("PLAYING")
            if(shouldRun && visible) handler.postDelayed(this,delay.coerceIn(1,33))
        } }
    }
    fun schedulePump(syncNanos:Long=0L) {
        handler.removeCallbacks(pump)
        if(shouldRun && visible) {
            if(!running && syncNanos!=0L) {
                running=true; activeStart=syncNanos; clockStart=syncNanos; lastFrame=0; preparingSince=SystemClock.elapsedRealtime()
            }
            handler.post(pump)
        }
    }
    fun rewindToStart(syncNanos:Long) {
        handler.removeCallbacks(pump)
        running=false
        gifElapsedMs=0L
        gifLoop=0L
        activeStart=syncNanos
        clockStart=syncNanos
        lastRenderedPtsUs=-1_000_000L
        if(pendingIndex>=0) {
            runCatching {codec?.releaseOutputBuffer(pendingIndex,false)}
            pendingIndex=-1
        }
        if(codec!=null) {
            runCatching {codec?.flush()}
            runCatching {extractor?.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC)}
            inputEos=false
        } else if(streamPlayer!=null) {
            runCatching {streamPlayer?.rewind()}
        } else if(movie==null) {
            ready=false
        }
        val gif=movie;val bitmap=gifBitmap
        if(gif!=null && bitmap!=null) {
            gif.setTime(0)
            bitmap.eraseColor(Color.TRANSPARENT)
            val c=Canvas(bitmap)
            if(bitmap.width!=videoWidth || bitmap.height!=videoHeight) {
                c.scale(bitmap.width.toFloat()/videoWidth.coerceAtLeast(1),bitmap.height.toFloat()/videoHeight.coerceAtLeast(1))
            }
            gif.draw(c,0f,0f)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,gifTexture);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
            ready=true
        }
    }
    fun releaseIdleCodecKeepFrame() {
        if(running || codec==null)return
        if(pendingIndex>=0) {
            runCatching {codec?.releaseOutputBuffer(pendingIndex,false)}
            pendingIndex=-1
        }
        runCatching {codec?.stop()};runCatching {codec?.release()};codec=null
        runCatching {extractor?.release()};extractor=null
        inputEos=false;clockStart=0;lastRenderedPtsUs=-1_000_000L
    }
    private fun releaseMedia() {
        runCatching {streamPlayer?.dispose()};streamPlayer=null
        runCatching {codec?.stop()};runCatching {codec?.release()};codec=null
        runCatching {extractor?.release()};extractor=null;movie=null
        gifBitmap?.recycle();gifBitmap=null;ready=false;needsFirstFrame=true;inputEos=false;pendingIndex=-1;sourceKey=""
    }
    fun pause(resetDecoder:Boolean=false) {
        handler.removeCallbacks(pump)
        runCatching {streamPlayer?.pause()}
        if(running) {
            val deltaNanos=System.nanoTime()-activeStart
            activeNanos+=deltaNanos
            gifElapsedMs+=deltaNanos/1_000_000
            running=false; pauses++
            if(resetDecoder) {
                codec?.flush(); extractor?.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                inputEos=false; pendingIndex=-1; ready=false
            }
            clockStart=0; needsFirstFrame=true; lastFrame=0
            report(if(visible) "PAUSED_OFF_PAGE" else "PAUSED_HIDDEN",true)
        }
    }
    private fun report(status:String,force:Boolean=false) {
        val now=SystemClock.elapsedRealtime()
        if(!force && now-lastReport<2000) return
        lastReport=now
        val seconds=(activeNanos+if(running) System.nanoTime()-activeStart else 0)/1e9
        val ordered=frameIntervals.sorted()
        val result=JSONObject().put("status",status).put("decoder",decoderName).put("hardwareAccelerated",hardware)
            .put("durationUs",durationUs).put("sourceFps",sourceFps).put("sourceWidth",videoWidth).put("sourceHeight",videoHeight)
            .put("renderedFrames",frames).put("droppedLateFrames",dropped).put("loops",loops).put("pauses",pauses)
            .put("decodedFrames",decoded).put("clockResyncs",resyncs)
            .put("lastResumeToFrameMillis",lastResumeToFrameMillis).put("settleMillis",VideoPolicy.SETTLE_MILLIS)
            .put("source",sourceKey).put("kind",when {streamPlayer!=null -> requestedSource.optString("kind","stream"); movie!=null -> "gif"; else -> "video"})
            .put("activeSeconds",seconds).put("averageRenderedFps",if(seconds>0) frames/seconds else 0)
            .put("frameGapP95Millis",if(ordered.isNotEmpty()) ordered[((ordered.size-1)*.95).toInt()] else 0).put("maxFrameGapMillis",maxGapMs)
        State(context).put(if(id<0) "videoTelemetry" else WidgetAnimations.key("videoTelemetry",id),result.put("widgetId",id))
        if(force) DiagnosticLog.event(context,"VIDEO_STATUS",result)
    }
        fun dispose() {
            if(disposed)return
            pause(true);releaseMedia();disposed=true
            videoTexture?.setOnFrameAvailableListener(null)
            decoderSurface?.release();videoTexture?.release()
            GLES20.glDeleteTextures(2,intArrayOf(oesTexture,gifTexture),0)
        }
    }
    fun close() {
        if(closed)return
        visible=false;allowed=false
        val done=CountDownLatch(1)
        handler.post {
            try {
                closed=true
                synchronized(sceneLock){pendingScene?.bitmap?.recycle();pendingScene=null}
                layers.values.forEach {runCatching {it.dispose()}};layers.clear()
                if(display!=EGL14.EGL_NO_DISPLAY) {
                    GLES20.glDeleteTextures(2,intArrayOf(staticTexture,blackTexture),0)
                    EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
                    EGL14.eglDestroySurface(display,window);EGL14.eglDestroyContext(display,eglContext);EGL14.eglTerminate(display);EGL14.eglReleaseThread()
                }
            } finally {closed=true;done.countDown();thread.quitSafely()}
        }
        done.await(3,TimeUnit.SECONDS)
    }
}
