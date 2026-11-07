package dev.animatedhome.diagnostics

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.SharedPreferences
import android.graphics.*
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.WindowInsets
import java.io.File
import java.util.UUID
import kotlin.math.max

class LabWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = LabEngine()

    inner class LabEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val state by lazy { State(this@LabWallpaper) }
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val id = UUID.randomUUID().toString()
        private var alive = false
        private var shown = false
        private var width = 0
        private var height = 0
        private var offset: Offset? = null
        private var stableSince = SystemClock.elapsedRealtime()
        private var zoom = 0f
        private var background: Bitmap? = null
        private var signature = ""
        private var bgSignature = ""
        private var compositor:WallpaperCompositor?=null
        // Recheck at the settling deadline, rather than adding a 0–100 ms poll delay.
        // drawFrame always re-evaluates current offset, visibility, zoom and calibration.
        private val settledFrame=Runnable { drawFrame() }
        private fun homeEngine() = !isPreview && (Build.VERSION.SDK_INT < 34 || wallpaperFlags and WallpaperManager.FLAG_SYSTEM != 0)
        private val tick = object : Runnable {
            override fun run() {
                if (!shown || !alive) return
                if (homeEngine()) BatteryTelemetry.periodicTick(this@LabWallpaper)
                drawFrame()
                // Static diagnostics only: redraw on change. Poll while visible for gate expiry/keyguard.
                handler.postDelayed(this, 100)
            }
        }
        private fun event(name: String, detail: Any) {
            DiagnosticLog.event(this@LabWallpaper, "$name engine=$id preview=$isPreview", detail)
            if (homeEngine()) Live.lastEvent = "$name $detail"
        }
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(true)
            setTouchEventsEnabled(true)
            GuidedSurvey.migrate(this@LabWallpaper)
            state.prefs.registerOnSharedPreferenceChangeListener(this)
            if (homeEngine()) {
                if(state.prefs.getString("mode","OFF")=="ALIGN") AlignmentFlow.blocked(this@LabWallpaper,"The wallpaper restarted. Tap the widget again and retry.")
                Live.engine = id
                Live.offset = null
                Live.offsetsChangedAcrossPages=false
                Live.swipeTracker.reset()
                SwipeFallback.configure(this@LabWallpaper)
                Live.stableSince = SystemClock.elapsedRealtime()
                state.prefs.edit().putString("mode", "OFF").apply()
            }
            loadBackground()
            event("ENGINE_CREATE", "Confirmed setup may be restored on the same page and display profile.")
        }
        private fun loadBackground() {
            val path = File(filesDir, "background.jpg")
            val fresh = if (path.exists() && !state.prefs.getBoolean("useGrid",false)) BitmapFactory.decodeFile(path.path) else null
            background?.recycle(); background = fresh; signature = ""; bgSignature = ""
        }
        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if(key?.startsWith("videoTelemetry")==true || key=="batterySession" || key=="batterySamples") return
            if (key == "backgroundVersion" || key == "useGrid") loadBackground()
            if (key == "syncGeneration") compositor?.resetAllToStart()
            if(key=="mode" && state.prefs.getString("mode","")!="VIDEO") compositor?.suspendVideo()
            signature = ""
        }
        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder); alive = true; event("SURFACE_CREATED", "")
            compositor?.close()
            bgSignature = ""; signature = ""
            compositor=WallpaperCompositor(this@LabWallpaper,holder.surface) { reason ->
                event("COMPOSITOR_ERROR",reason)
                if(state.prefs.getString("mode","")=="ALIGN") AlignmentFlow.blocked(this@LabWallpaper,"Drawing failed. Save a report from Help.")
                if(state.prefs.getString("mode","")=="VIDEO") state.put("videoAttempt",state.obj("videoAttempt").put("error",reason))
                val edit=state.prefs.edit()
                state.ids().forEach {widget->edit.putBoolean(WidgetAnimations.key("enabled",widget),false).putString(WidgetAnimations.key("mediaError",widget),"Playback stopped: $reason")}
                edit.apply()
                state.prefs.edit().putString("mode","OFF").apply()
            }.also { it.setVisible(shown) }
            if (shown) { handler.removeCallbacks(tick); handler.post(tick) }
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w; height = h; signature = ""; bgSignature = ""
            if (homeEngine()) { Live.width = w; Live.height = h }
            event("SURFACE_CHANGED", "format=$format width=$w height=$h desired=${desiredMinimumWidth}x$desiredMinimumHeight")
            drawFrame()
        }
        override fun onDesiredSizeChanged(w: Int, h: Int) { super.onDesiredSizeChanged(w,h); signature = ""; bgSignature = ""; event("DESIRED_SIZE", "${w}x$h") }
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) { signature = ""; bgSignature = ""; drawFrame() }
        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)
            @Suppress("DEPRECATION")
            val value = "system=${insets.systemWindowInsetLeft},${insets.systemWindowInsetTop},${insets.systemWindowInsetRight},${insets.systemWindowInsetBottom}; $insets"
            if (homeEngine()) {
                Live.insets = value
                Live.insetsKey = if (Build.VERSION.SDK_INT >= 30)
                    insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).toString()
                else value.substringBefore(';')
            }
            signature = ""
            event("WINDOW_INSETS", value)
            drawFrame()
        }
        override fun onOffsetsChanged(x: Float, y: Float, xs: Float, ys: Float, px: Int, py: Int) {
            val next=Offset(x,y,xs,ys,px,py).takeIf {it.valid()}
            if(homeEngine() && next!=null && offset?.distinct(next)==true)Live.offsetsChangedAcrossPages=true
            if(next!=offset) {
                stableSince=SystemClock.elapsedRealtime()
                if(state.prefs.getString("mode","OFF")=="VIDEO")compositor?.suspendVideo()
                handler.removeCallbacks(settledFrame)
                if(shown && alive)handler.postDelayed(settledFrame,VideoPolicy.SETTLE_MILLIS)
            }
            offset=next
            if(homeEngine()){Live.offset=offset;Live.stableSince=stableSince}
            event("OFFSETS","xOffset=$x yOffset=$y xOffsetStep=$xs yOffsetStep=$ys xPixelOffset=$px yPixelOffset=$py")
            signature="";drawFrame()
        }
        override fun onTouchEvent(event:android.view.MotionEvent) {
            super.onTouchEvent(event)
            if(!homeEngine() || !shown || !alive || !SwipeFallback.active(this@LabWallpaper))return
            val tracker=Live.swipeTracker
            val locked=getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if(locked || zoom>.01f || event.pointerCount>1){tracker.cancel();return}
            val sw=resources.displayMetrics.widthPixels.toFloat()
            when(event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> tracker.begin(event.x,event.y,event.eventTime,sw,android.view.ViewConfiguration.get(this@LabWallpaper).scaledTouchSlop.toFloat())
                android.view.MotionEvent.ACTION_MOVE -> tracker.move(event.x,event.y)
                android.view.MotionEvent.ACTION_UP -> {
                    val changed=tracker.finish(event.x,event.y,event.eventTime)
                    event("SWIPE_ESTIMATE","page=${tracker.page} changed=$changed gestures=${tracker.gestures}; not launcher evidence")
                }
                android.view.MotionEvent.ACTION_CANCEL,android.view.MotionEvent.ACTION_POINTER_DOWN -> tracker.cancel()
            }
            signature="";drawFrame()
        }
        override fun onZoomChanged(value: Float) {
            if(value!=zoom && state.prefs.getString("mode","OFF")=="VIDEO") compositor?.suspendVideo()
            zoom = value
            if(homeEngine() && value>.01f)Live.swipeTracker.cancel()
            if (homeEngine()) Live.zoom = value
            event("ZOOM", value); signature = ""; drawFrame()
        }
        override fun onVisibilityChanged(visible: Boolean) {
            shown = visible
            if(homeEngine() && !visible)Live.swipeTracker.cancel()
            compositor?.setVisible(visible)
            if (homeEngine()) {
                Live.visible = visible
                if (!visible) Live.hiddenAt = SystemClock.elapsedRealtime()
                BatteryTelemetry.onVisibilityChanged(this@LabWallpaper, visible)
                state.put("lastTelemetry", Live.snapshot())
            }
            event("VISIBILITY", visible)
            handler.removeCallbacks(tick)
            if(!visible) handler.removeCallbacks(settledFrame)
            if (visible) { signature = ""; handler.post(tick) }
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            alive = false; handler.removeCallbacks(tick); handler.removeCallbacks(settledFrame); event("SURFACE_DESTROYED", "")
            compositor?.close(); compositor=null
            super.onSurfaceDestroyed(holder)
        }
        override fun onWallpaperFlagsChanged(which: Int) {
            event("WALLPAPER_FLAGS", which)
            // Home/lock engine reattachment must not reuse a different engine's spatial evidence.
            if (homeEngine() && Live.engine != id) {
                Live.engine = id; Live.offset = offset; Live.stableSince = stableSince
                Live.width = width; Live.height = height; Live.zoom = zoom; Live.visible = shown
                state.prefs.edit().putString("mode","OFF").apply()
            }
            signature = ""; bgSignature = ""
        }
        override fun onDestroy() {
            handler.removeCallbacksAndMessages(null)
            compositor?.close(); compositor=null
            state.prefs.unregisterOnSharedPreferenceChangeListener(this)
            background?.recycle(); background = null
            if (!isPreview && Live.engine == id) { Live.engine = ""; Live.visible = false; Live.offset = null }
            event("ENGINE_DESTROY", "")
            super.onDestroy()
        }
        private fun drawFrame() {
            if (!alive || !shown || width <= 0 || height <= 0 || !surfaceHolder.surface.isValid) return
            val now = SystemClock.elapsedRealtime()
            val isHome = homeEngine()
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if(isHome && !locked)
                VideoFlow.restoreSaved(this@LabWallpaper,offset,now-stableSince>=500,zoom)
            val capture = if(state.prefs.getString("mode","")=="PAGE" && state.obj("surveyAnchor").length()>0) state.obj("surveyAnchor") else state.record(state.prefs.getInt("selected", -1))
            val screen = device(this@LabWallpaper)
            val sw = screen.getInt("width"); val sh = screen.getInt("height")
            val env = environment(this@LabWallpaper)
            val box = capture.box()
            val valid = capture.optBoolean("valid") && capture.optInt("measurementVersion") == WidgetMeasurement.VERSION && box?.valid(sw,sh) == true &&
                capture.optString("environment") == env
            val mode = state.prefs.getString("mode", "OFF")
            val gate = when (mode) {
                "ALIGN" -> isHome && !locked && valid && now < state.prefs.getLong("alignUntil", 0)
                "PAGE", "VIDEO" -> isHome && valid && (mode!="PAGE" || !state.prefs.getBoolean("isolationPending",false) || now<state.prefs.getLong("surveyBoxUntil",0)) && (mode!="VIDEO" || (VideoFlow.evidenceMatches(this@LabWallpaper) && now<state.prefs.getLong("videoUntil",0))) && RenderGate.pageAllowed(PageEvidence.assess(state.visits()).code,
                    capture.optString("environment") == env, now-stableSince, shown,
                    isPreview, locked, zoom, capture.optDouble("zoom", -1.0).toFloat(), offset, capture.optJSONObject("offset")?.offset(),
                    if(mode=="VIDEO") VideoPolicy.SETTLE_MILLIS else 500L)
                else -> false
            }
            if(mode=="VIDEO" && !gate) compositor?.suspendVideo()
            val reason = if (gate) "$mode rectangle #${capture.optInt("widgetId")}" else "hidden ($mode; valid=$valid; locked=$locked; preview=$isPreview)"
            if(mode=="ALIGN" && isHome && !gate) {
                val detail=when {
                    !valid -> "The widget position no longer matches the wallpaper/display. Return home and tap the widget again."
                    locked -> "Unlock the phone, then tap the widget and retry."
                    else -> "The viewing time expired. Tap the widget to repeat the check."
                }
                if(!state.obj("alignmentAttempt").optBoolean("submitted") || !valid) AlignmentFlow.blocked(this@LabWallpaper,detail)
            }
            if (isHome) Live.draw = reason
            val swipeFallback=isHome && SwipeFallback.active(this@LabWallpaper,env)
            val specs=ArrayList<WallpaperCompositor.LayerSpec>()
            val pinkId=state.prefs.getInt("pinkWidget",-1)
            val pinkActive=now<state.prefs.getLong("pinkUntil",0)
            if(!pinkActive && state.prefs.getLong("pinkUntil",0)!=0L) {
                state.prefs.edit().putLong("pinkUntil",0).apply()
                if(state.ids().contains(pinkId))CalibrationWidget.update(this@LabWallpaper,pinkId)
            }
            var pinkBox:Box?=null
            val mappingModel=state.prefs.getString("mapping","CENTER")!!
            val mapDx=state.prefs.getFloat("dx",0f)
            val mapDy=state.prefs.getFloat("dy",0f)
            val mapScale=state.prefs.getFloat("scale",1f)
            for(widget in state.ids()) {
                val record=state.record(widget)
                val b=record.box()
                val recEnv=record.optString("environment")
                val sameDisplay=recEnv.split('|').take(7)==env.split('|').take(7)
                val recOff=record.optJSONObject("offset")?.offset()
                val isSyntheticFallbackOff=recOff!=null && recOff.x==0.5f && recOff.y==0.5f && recOff.xStep==0f && recOff.yStep==0f && recOff.px==0 && recOff.py==0
                if(isHome && !locked && record.optBoolean("valid") && b?.valid(sw,sh)==true && sameDisplay &&
                    (recEnv.contains("surface=0x0") || recEnv.contains("insets=unknown") || record.optString("placement")=="estimated" || (isSyntheticFallbackOff && offset!=null))) {
                    record.put("environment",env).put("zoom",zoom)
                    if(offset!=null) record.put("offset",offset!!.json())
                    if(record.optString("placement")=="estimated") record.put("placement","auto-home")
                    state.put("widget_$widget",record)
                }
                val sameProf=VideoPolicy.sameProfile(record.optString("environment"),env) || (sameDisplay && record.optBoolean("valid"))
                val fallbackOff=Offset(0.5f,0.5f,0f,0f,0,0)
                val targetOff=(if(isSyntheticFallbackOff && offset!=null) offset else record.optJSONObject("offset")?.offset()) ?: offset ?: fallbackOff
                val currentOff=offset ?: targetOff
                val rawCapZoom=record.optDouble("zoom",-1.0).toFloat()
                val capZoom=if(rawCapZoom<0f || !rawCapZoom.isFinite()) zoom else rawCapZoom
                val pink=(pinkActive && widget==pinkId)
                val surveyTarget=mode=="PAGE" && state.prefs.getBoolean("isolationPending",false) && widget==capture.optInt("widgetId")
                val ignoreOffset=WidgetAnimations.ignorePageOffset(state,widget)
                val swipeMode=if(pink) "SETTLED" else if(ignoreOffset) "ALWAYS" else WidgetAnimations.swipeMode(state,widget)
                val assignedPage=SwipeFallback.assigned(state,widget)
                val estimated=swipeFallback && assignedPage!=null && !ignoreOffset
                val displayCurrent=if(estimated)Live.swipeTracker.current() else currentOff
                val displayTarget=if(estimated)Live.swipeTracker.at(assignedPage!!) else targetOff
                val liveB=if(record.optBoolean("valid")) WidgetPlacement.liveBox(b,sw,sh,sameProf,now-stableSince,shown,isHome,locked,zoom,capZoom,displayCurrent,displayTarget,swipeMode) else null
                val mapped=if(liveB!=null) Mapping.toSurface(liveB,sw,sh,width,height,if(ignoreOffset) null else offset,mappingModel,mapDx,mapDy,mapScale) else null
                if(pink) pinkBox=mapped
                if(WidgetAnimations.enabled(state,widget)) {
                    val source=state.obj(WidgetAnimations.key("playbackMedia",widget))
                    if(MediaStore.isPlayableSource(this@LabWallpaper,source))specs.add(WallpaperCompositor.LayerSpec(widget,if(pink || surveyTarget)null else mapped,source,WidgetAnimations.scaleMode(state,widget),swipeMode!="SETTLED"))
                }
            }
            val nextBgSignature = "$width:$height:$reason:${capture.optString("tap")}:${state.prefs.getLong("backgroundVersion",0)}:${state.prefs.getBoolean("useGrid",false)}:$pinkBox"
            val nextSignature = "$nextBgSignature:$zoom:$offset:${specs.map {"${it.id}:${it.box}:${it.source.optString("file")}:${it.scaleMode}:${it.keepPlaying}"}}"
            if (signature == nextSignature) return
            val needBgBitmap = bgSignature != nextBgSignature || gate
            var frame:Bitmap?=null
            var drewBox = false
            var videoBox:Box?=null
            try {
                if (needBgBitmap) {
                    frame=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    val canvas=Canvas(frame)
                    canvas.drawColor(Color.rgb(18,29,41))
                    val bmp = background
                    if (bmp != null) {
                        val scale = max(width.toFloat()/bmp.width, height.toFloat()/bmp.height)
                        val bw = bmp.width * scale; val bh = bmp.height * scale
                        paint.color = Color.WHITE; paint.style = Paint.Style.FILL
                        canvas.drawBitmap(bmp,null,RectF((width-bw)/2,(height-bh)/2,(width+bw)/2,(height+bh)/2),paint)
                    } else {
                        paint.color = Color.rgb(38,56,72); paint.strokeWidth = 1f
                        for (x in 0..width step 100) canvas.drawLine(x.toFloat(),0f,x.toFloat(),height.toFloat(),paint)
                        for (y in 0..height step 100) canvas.drawLine(0f,y.toFloat(),width.toFloat(),y.toFloat(),paint)
                    }
                    if (gate && box != null) {
                        val mapped = Mapping.toSurface(box,sw,sh,width,height,offset,mappingModel,mapDx,mapDy,mapScale)
                        val rect = RectF(mapped.left,mapped.top,mapped.right,mapped.bottom)
                        val personal=mode=="VIDEO" && state.prefs.getBoolean("personalPlayback",false)
                        val transparentGif=personal && MediaStore.selected(this@LabWallpaper).optString("kind")=="gif"
                        paint.color = if(mode=="VIDEO") Color.BLACK else Color.rgb(255,39,158); paint.style = Paint.Style.FILL
                        if(!transparentGif)canvas.drawRect(rect,paint)
                        paint.color = Color.CYAN; paint.strokeWidth = 4f; paint.style = Paint.Style.STROKE
                        if(!personal)canvas.drawRect(rect,paint)
                        if(mode!="VIDEO") {
                            canvas.drawLine(rect.left,rect.top,rect.right,rect.bottom,paint)
                            canvas.drawLine(rect.right,rect.top,rect.left,rect.bottom,paint)
                        }
                        paint.style = Paint.Style.FILL; paint.color = Color.BLACK; paint.textSize = 28f
                        canvas.save(); canvas.clipRect(rect)
                        if(mode!="VIDEO") canvas.drawText("Pink box test",rect.left+12,rect.bottom-12,paint)
                        canvas.restore()
                        event("DRAW_RECT", "$mapped zoom=$zoom mapping=$mappingModel stableMillis=${now-stableSince}")
                        drewBox = true
                        if(mode=="VIDEO") videoBox=if(personal)mapped else Box(mapped.left+2,mapped.top+2,mapped.right-2,mapped.bottom-2)
                    }
                    pinkBox?.let {b->
                        paint.color=Color.rgb(255,39,158);paint.style=Paint.Style.FILL
                        canvas.drawRect(b.left,b.top,b.right,b.bottom,paint)
                        paint.color=Color.CYAN;paint.strokeWidth=4f;paint.style=Paint.Style.STROKE
                        canvas.drawRect(b.left,b.top,b.right,b.bottom,paint);paint.style=Paint.Style.FILL
                    }
                    bgSignature = nextBgSignature
                }
                if(mode=="VIDEO")specs.add(WallpaperCompositor.LayerSpec(-1,videoBox,MediaStore.active(this@LabWallpaper),"FIT_BLACK"))
                signature = nextSignature
                val attemptId=state.obj("alignmentAttempt").optString("id")
                val renderer=compositor
                if(renderer!=null) {
                    renderer.scene(frame,specs) {
                        if(drewBox && mode=="ALIGN" && state.obj("alignmentAttempt").optString("id")==attemptId) AlignmentFlow.recordFrame(this@LabWallpaper)
                    }
                    frame=null // The renderer owns and recycles this bitmap.
                }
            } catch (e: Exception) {
                event("DRAW_FAILURE", e.toString())
                if(mode=="ALIGN") AlignmentFlow.blocked(this@LabWallpaper,"Drawing failed: ${e.javaClass.simpleName}. Save a report from Help.")
            } finally {
                frame?.recycle()
            }
        }
    }
}
