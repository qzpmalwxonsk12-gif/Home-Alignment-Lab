package dev.animatedhome.diagnostics

import android.annotation.SuppressLint
import android.app.Presentation
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject
import java.util.UUID

object StreamUrlHelper {
    private val urlRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val ytRegexes = listOf(
        Regex("""(?:youtube\.com/watch\?.*?v=|youtu\.be/|youtube\.com/shorts/|youtube\.com/embed/|youtube-nocookie\.com/embed/|youtube\.com/live/)([A-Za-z0-9_-]{11})""", RegexOption.IGNORE_CASE)
    )

    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = urlRegex.find(text.trim())?.value?.trimEnd('.', ',', ')', ']', ';')
        if (match != null) return match
        val trimmed = text.trim()
        if (trimmed.matches(Regex("""^[A-Za-z0-9_-]{11}$"""))) {
            return "https://m.youtube.com/watch?v=$trimmed"
        }
        return null
    }

    fun extractYoutubeId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        for (r in ytRegexes) {
            val m = r.find(url)
            if (m != null) return m.groupValues[1]
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val v = uri.getQueryParameter("v")
        if (v != null && v.length == 11) return v
        return null
    }

    fun isYoutubeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (extractYoutubeId(url) != null) return true
        val host = runCatching { Uri.parse(url).host?.lowercase() ?: "" }.getOrDefault("")
        return host.contains("youtube.com") || host.contains("youtu.be") || host.contains("youtube-nocookie.com")
    }

    fun isDirectMediaUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        if (clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".m3u8") ||
            clean.endsWith(".mov") || clean.endsWith(".m4v") || clean.endsWith(".mkv")) {
            return true
        }
        return url.lowercase().contains("googlevideo.com/videoplayback")
    }

    fun buildStreamSpec(
        kind: String,
        url: String,
        title: String = "",
        isolateVideo: Boolean = true,
        muted: Boolean = true,
        directVideoUrl: String = ""
    ): JSONObject {
        val ytId = if (kind == "youtube") extractYoutubeId(url) ?: "" else ""
        val normalizedUrl = if (kind == "youtube" && ytId.isNotEmpty()) {
            "https://m.youtube.com/watch?v=$ytId"
        } else {
            url
        }
        val host = runCatching { Uri.parse(normalizedUrl).host?.removePrefix("www.")?.removePrefix("m.") ?: "stream" }.getOrDefault("stream")
        val displayTitle = when {
            title.isNotBlank() -> title
            kind == "youtube" && ytId.isNotEmpty() -> "YouTube · $ytId"
            kind == "youtube" -> "YouTube Stream"
            else -> "Chrome · $host"
        }
        val token = UUID.nameUUIDFromBytes("$kind|$normalizedUrl|$directVideoUrl|$isolateVideo|$muted".toByteArray()).toString()
        return JSONObject()
            .put("kind", kind)
            .put("stream", true)
            .put("url", normalizedUrl)
            .put("videoId", ytId)
            .put("directVideoUrl", directVideoUrl)
            .put("isolateVideo", isolateVideo)
            .put("muted", muted)
            .put("name", displayTitle)
            .put("width", 1280)
            .put("height", 720)
            .put("durationUs", 0L)
            .put("fps", 30)
            .put("rotation", 0)
            .put("file", "stream://$kind/$token")
    }
}

/**
 * Streams either a YouTube video or a Chrome/web video directly into a widget's OpenGL SurfaceTexture
 * (`decoderSurface`) using a hardware MediaPlayer (for direct stream URLs) or an off-screen VirtualDisplay +
 * Presentation + WebView (for YouTube, Shorts, MSE blob streams, and interactive web video players).
 */
class StreamPlayer(
    private val context: Context,
    private val widgetId: Int,
    private val source: JSONObject,
    private val decoderSurface: Surface,
    private val videoTexture: SurfaceTexture,
    private val onVideoSizeChanged: (Int, Int) -> Unit,
    private val onFrameHeartbeat: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var disposed = false
    @Volatile private var active = false
    private var mediaPlayer: MediaPlayer? = null
    private var usingMediaPlayer = false
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var webView: StreamWebView? = null
    private var displayWidth = 854
    private var displayHeight = 480

    private val kind = source.optString("kind", "chrome")
    private val pageUrl = source.optString("url", "")
    private val directUrl = source.optString("directVideoUrl", "")
    private val isolateVideo = source.optBoolean("isolateVideo", true)
    private val muted = source.optBoolean("muted", true)

    private val webWatchdog = object : Runnable {
        override fun run() {
            if (disposed || !active) return
            val wv = webView
            if (wv != null) {
                injectStreamScript(wv)
                wv.postInvalidateOnAnimation()
                if (!usingCanvasFallback && presentation != null) {
                    onFrameHeartbeat()
                } else if (usingCanvasFallback) {
                    drawWebViewToSurface(wv)
                }
            }
            main.postDelayed(this, if (usingCanvasFallback) 40L else 650L)
        }
    }

    private var usingCanvasFallback = false

    fun start() {
        if (disposed) return
        active = true
        main.post {
            if (disposed) return@post
            val directCandidate = directUrl.ifBlank { if (StreamUrlHelper.isDirectMediaUrl(pageUrl)) pageUrl else "" }
            if (directCandidate.isNotBlank() && mediaPlayer == null && webView == null) {
                startDirectMediaPlayer(directCandidate)
            } else if (mediaPlayer != null) {
                runCatching {
                    val vol = if (muted) 0f else 1f
                    mediaPlayer?.setVolume(vol, vol)
                    if (mediaPlayer?.isPlaying == false) mediaPlayer?.start()
                }
            } else {
                ensureWebViewStarted()
            }
        }
    }

    fun pause() {
        active = false
        main.post {
            main.removeCallbacks(webWatchdog)
            runCatching {
                if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
            }
            val wv = webView
            if (wv != null) {
                wv.keepAwake = false
                runCatching {
                    wv.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){try{v.pause();}catch(e){}});", null)
                    wv.onPause()
                }
            }
        }
    }

    fun rewind() {
        main.post {
            if (disposed) return@post
            runCatching {
                mediaPlayer?.seekTo(0)
                if (active && mediaPlayer?.isPlaying == false) mediaPlayer?.start()
            }
            webView?.let { wv ->
                runCatching {
                    wv.evaluateJavascript(
                        "document.querySelectorAll('video').forEach(function(v){try{v.currentTime=0;if(${active})v.play();}catch(e){}});",
                        null
                    )
                }
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        active = false
        main.post {
            main.removeCallbacksAndMessages(null)
            releaseMediaPlayer()
            releaseWebPresentation()
        }
    }

    private fun startDirectMediaPlayer(streamUrl: String) {
        try {
            usingMediaPlayer = true
            videoTexture.setDefaultBufferSize(1280, 720)
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            mp.setSurface(decoderSurface)
            mp.isLooping = true
            val vol = if (muted) 0f else 1f
            mp.setVolume(vol, vol)
            mp.setDataSource(context, Uri.parse(streamUrl), mapOf("User-Agent" to MOBILE_UA))
            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) {
                    videoTexture.setDefaultBufferSize(w.coerceAtMost(1920), h.coerceAtMost(1920))
                    onVideoSizeChanged(w, h)
                }
            }
            mp.setOnPreparedListener { player ->
                if (disposed) {
                    runCatching { player.release() }
                    return@setOnPreparedListener
                }
                val v = if (muted) 0f else 1f
                player.setVolume(v, v)
                if (active) player.start()
            }
            mp.setOnErrorListener { _, _, _ ->
                // Fall back to VirtualDisplay + WebView if MediaPlayer cannot play this stream directly
                releaseMediaPlayer()
                if (!disposed && pageUrl.isNotBlank()) {
                    ensureWebViewStarted()
                }
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            releaseMediaPlayer()
            if (!disposed && pageUrl.isNotBlank()) {
                ensureWebViewStarted()
            }
        }
    }

    private fun releaseMediaPlayer() {
        usingMediaPlayer = false
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        runCatching { mp.setSurface(null) }
        runCatching { mp.stop() }
        runCatching { mp.release() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebViewStarted() {
        if (disposed) return
        val existing = webView
        if (existing != null) {
            existing.keepAwake = true
            runCatching {
                existing.onResume()
                existing.resumeTimers()
                injectStreamScript(existing)
            }
            main.removeCallbacks(webWatchdog)
            main.post(webWatchdog)
            return
        }
        videoTexture.setDefaultBufferSize(displayWidth, displayHeight)
        onVideoSizeChanged(displayWidth, displayHeight)

        val dm = context.getSystemService(DisplayManager::class.java)
        var createdWv: StreamWebView? = null
        try {
            val vd = dm.createVirtualDisplay(
                "WidgetStream_$widgetId",
                displayWidth,
                displayHeight,
                200,
                decoderSurface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )
            virtualDisplay = vd
            val pres = object : Presentation(context, vd.display) {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    window?.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                    )
                    val root = FrameLayout(getContext()).apply {
                        setBackgroundColor(Color.BLACK)
                    }
                    val wv = buildStreamWebView(getContext())
                    createdWv = wv
                    root.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    setContentView(root)
                }
            }
            pres.show()
            presentation = pres
            webView = createdWv
            usingCanvasFallback = false
        } catch (e: Exception) {
            DiagnosticLog.event(context, "STREAM_PRESENTATION_FALLBACK", e.toString())
            runCatching { presentation?.dismiss() }
            presentation = null
            runCatching { virtualDisplay?.release() }
            virtualDisplay = null
            val wv = buildStreamWebView(context)
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(displayWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(displayHeight, View.MeasureSpec.EXACTLY)
            )
            wv.layout(0, 0, displayWidth, displayHeight)
            webView = wv
            usingCanvasFallback = true
        }

        val wv = webView ?: return
        wv.keepAwake = true
        wv.onResume()
        wv.resumeTimers()
        val targetUrl = if (kind == "youtube") {
            val ytId = StreamUrlHelper.extractYoutubeId(pageUrl)
            if (ytId != null) "https://m.youtube.com/watch?v=$ytId" else pageUrl
        } else {
            pageUrl
        }
        wv.loadUrl(targetUrl)
        main.removeCallbacks(webWatchdog)
        main.postDelayed(webWatchdog, 500L)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildStreamWebView(ctx: Context): StreamWebView {
        val wv = StreamWebView(ctx)
        wv.setBackgroundColor(Color.BLACK)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = MOBILE_UA
        }
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onVideoSize(w: Int, h: Int) {
                if (w > 16 && h > 16) {
                    onVideoSizeChanged(w, h)
                }
            }

            @JavascriptInterface
            fun onDirectSrc(src: String) {
                // Keep playing cleanly inside the isolated WebView, or record telemetry
                DiagnosticLog.event(context, "STREAM_VIDEO_ACTIVE", "widget=$widgetId kind=$kind src=${src.take(80)}")
            }
        }, "AndroidStreamBridge")
        wv.webChromeClient = object : WebChromeClient() {}
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                if (u.startsWith("http://") || u.startsWith("https://")) return false
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                injectStreamScript(view)
            }
        }
        return wv
    }

    private fun injectStreamScript(view: WebView) {
        val muteFlag = if (muted) "true" else "false"
        val isolateFlag = if (isolateVideo) "true" else "false"
        val js = """
            (function() {
                try {
                    var isolate = $isolateFlag;
                    var wantMuted = $muteFlag;
                    if (isolate && !document.getElementById('__agy_stream_css')) {
                        var st = document.createElement('style');
                        st.id = '__agy_stream_css';
                        st.textContent =
                            'html, body { margin:0 !important; padding:0 !important; background:#000 !important; overflow:hidden !important; width:100vw !important; height:100vh !important; }' +
                            'ytm-mobile-topbar-renderer, ytm-watch-metadata, ytm-item-section-renderer, ytm-single-column-watch-next-results-renderer, ' +
                            'ytm-reel-player-header-renderer, ytm-reel-player-overlay-renderer, .player-controls-content, .ytp-chrome-top, .ytp-chrome-bottom, ' +
                            '.ytp-watermark, .ytp-gradient-top, .ytp-gradient-bottom, #player-control-overlay, .ytm-autonav-bar, ytm-engagement-panel, ' +
                            'ytm-consent-bump-v2-lightbox, tp-yt-iron-overlay-backdrop, .ytp-pause-overlay, .ytp-UnmuteButton, .ytm-unmute-icon { display:none !important; opacity:0 !important; pointer-events:none !important; }' +
                            'video { position:fixed !important; top:0 !important; left:0 !important; width:100vw !important; height:100vh !important; object-fit:fill !important; z-index:2147483647 !important; background:#000 !important; transform:none !important; }';
                        (document.head || document.documentElement).appendChild(st);
                    }
                    // Auto-skip ads if any
                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                    if (skipBtn) { try { skipBtn.click(); } catch(e){} }

                    var vids = Array.prototype.slice.call(document.querySelectorAll('video'));
                    if (vids.length > 0) {
                        var best = vids[0];
                        for (var i = 0; i < vids.length; i++) {
                            var v = vids[i];
                            if (!v.paused || (v.videoWidth * v.videoHeight > best.videoWidth * best.videoHeight)) {
                                best = v;
                            }
                        }
                        best.playsInline = true;
                        best.setAttribute('playsinline', '');
                        best.setAttribute('webkit-playsinline', '');
                        best.loop = true;
                        if (!best.__agyEndedBound) {
                            best.__agyEndedBound = true;
                            best.addEventListener('ended', function() {
                                try { best.currentTime = 0; best.play(); } catch(e){}
                            });
                        }
                        if (best.paused && ${if (active) "true" else "false"}) {
                            best.muted = true;
                            var p = best.play();
                            if (p && p.then) {
                                p.then(function() {
                                    best.muted = wantMuted;
                                    if (!wantMuted) best.volume = 1.0;
                                }).catch(function(){});
                            }
                        } else {
                            best.muted = wantMuted;
                            if (!wantMuted) best.volume = 1.0;
                        }
                        if (best.videoWidth > 0 && best.videoHeight > 0 && window.AndroidStreamBridge) {
                            window.AndroidStreamBridge.onVideoSize(best.videoWidth, best.videoHeight);
                        }
                    } else if (isolate) {
                        var iframes = document.querySelectorAll('iframe');
                        for (var j = 0; j < iframes.length; j++) {
                            var f = iframes[j];
                            var r = f.getBoundingClientRect();
                            if (r.width >= 180 && r.height >= 100) {
                                f.style.setProperty('position', 'fixed', 'important');
                                f.style.setProperty('top', '0', 'important');
                                f.style.setProperty('left', '0', 'important');
                                f.style.setProperty('width', '100vw', 'important');
                                f.style.setProperty('height', '100vh', 'important');
                                f.style.setProperty('z-index', '2147483647', 'important');
                                f.style.setProperty('border', '0', 'important');
                                break;
                            }
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        runCatching { view.evaluateJavascript(js, null) }
    }

    private fun drawWebViewToSurface(wv: StreamWebView) {
        if (!decoderSurface.isValid) return
        runCatching {
            val canvas = decoderSurface.lockHardwareCanvas()
            try {
                canvas.drawColor(Color.BLACK)
                wv.draw(canvas)
            } finally {
                decoderSurface.unlockCanvasAndPost(canvas)
            }
            onFrameHeartbeat()
        }
    }

    private fun releaseWebPresentation() {
        val wv = webView
        webView = null
        if (wv != null) {
            wv.keepAwake = false
            runCatching {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
        }
        runCatching { presentation?.dismiss() }
        presentation = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
    }

    private class StreamWebView(context: Context) : WebView(context) {
        @Volatile var keepAwake = true
        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(if (keepAwake) View.VISIBLE else visibility)
        }
    }

    companion object {
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * Handles Android Share intents (`ACTION_SEND` from YouTube or Chrome) and routes them automatically
 * to the user's designated YouTube Widget or designated Chrome Widget.
 */
class StreamShareActivity : LabActivity() {
    private val state by lazy { State(this) }
    private var sharedUrl: String = ""
    private var isYoutube: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WidgetAnimations.migrate(this)
        val rawText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> intent?.getStringExtra("url") ?: intent?.dataString
        }
        val extracted = StreamUrlHelper.extractFirstUrl(rawText)
        if (extracted == null) {
            Toast.makeText(this, "No playable web or YouTube link found in share.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        sharedUrl = extracted
        isYoutube = StreamUrlHelper.isYoutubeUrl(extracted)
        routeSharedUrl()
    }

    private fun routeSharedUrl() {
        val ids = state.ids()
        if (ids.isEmpty()) {
            page("Add a widget first", "Stream to Home Widget", { finish() })
            notice("You shared a ${if (isYoutube) "YouTube" else "Chrome"} link, but there are no Home Alignment widgets on your home screen yet.")
            primary("Open Home Alignment setup") { openSetup() }
            return
        }
        val designatedId = if (isYoutube) WidgetAnimations.youtubeWidget(state) else WidgetAnimations.chromeWidget(state)
        if (designatedId != -1 && ids.contains(designatedId)) {
            startStreamOnWidget(designatedId)
            return
        }
        renderDesignatePicker()
    }

    private fun renderDesignatePicker() {
        val roleTitle = if (isYoutube) "YouTube Widget" else "Chrome Widget"
        val roleCode = if (isYoutube) "YOUTUBE" else "CHROME"
        page("Choose your $roleTitle", "Designate once · Future shares stream automatically", { finish() })
        card {
            pill(if (isYoutube) "YouTube Share Received" else "Chrome / Web Share Received")
            text("Which widget should be your designated $roleTitle?", 20f)
            text(
                "Link: $sharedUrl\n\nPick a widget below. It will be saved as your dedicated $roleTitle so every future ${if (isYoutube) "YouTube" else "Chrome"} share plays directly in it with zero prompts.",
                13f,
                MUTED
            )
        }
        section("Select widget on home screen")
        for (id in state.ids()) {
            val currentRole = WidgetAnimations.role(state, id)
            val subtitle = "${WidgetAnimations.roleLabel(currentRole)} · #${id}"
            actionRow(WidgetAnimations.name(state, id), subtitle, "widgets") {
                WidgetAnimations.setRole(this, id, roleCode)
                startStreamOnWidget(id)
            }
        }
        if (!isYoutube) {
            section("Need to pick a specific video on the page?")
            button("Open in interactive Chrome Video Catcher…") {
                val firstId = state.ids().first()
                WidgetAnimations.setRole(this, firstId, "CHROME")
                launch(
                    Intent(this, WebStreamActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, firstId)
                        .putExtra("url", sharedUrl)
                        .putExtra("kind", "chrome")
                )
                finish()
            }
        }
        link("Cancel") { finish() }
    }

    private fun startStreamOnWidget(targetId: Int) {
        val kind = if (isYoutube) "youtube" else "chrome"
        val muted = WidgetAnimations.streamMuted(state, targetId)
        val spec = StreamUrlHelper.buildStreamSpec(
            kind = kind,
            url = sharedUrl,
            isolateVideo = true,
            muted = muted
        )
        if (WidgetAnimations.startStream(this, targetId, spec, goHome = true)) {
            Toast.makeText(
                this,
                "Streaming ${if (isYoutube) "YouTube" else "Chrome"} video to ${WidgetAnimations.name(state, targetId)}…",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}

/**
 * Interactive Chrome Background Video Catcher & YouTube Stream Browser.
 * Lets the user browse any website or paste any YouTube/Chrome link, detects playing HTML5 <video>
 * elements and direct stream URLs, and streams them directly into the designated widget.
 */
class WebStreamActivity : LabActivity() {
    private val state by lazy { State(this) }
    private var widgetId: Int = -1
    private var streamKind: String = "chrome"
    private var currentUrl: String = ""
    private var detectedVideoSrc: String = ""
    private var detectedPageTitle: String = ""
    private var webView: WebView? = null
    private var statusBadge: android.widget.TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = savedInstanceState?.getInt("widgetId")
            ?: intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WidgetAnimations.current(this))
        streamKind = savedInstanceState?.getString("streamKind")
            ?: intent.getStringExtra("kind")
            ?: if (WidgetAnimations.role(state, widgetId) == "YOUTUBE") "youtube" else "chrome"
        val existingMedia = MediaStore.selected(this, widgetId)
        currentUrl = savedInstanceState?.getString("currentUrl")
            ?: intent.getStringExtra("url")
            ?: existingMedia.optString("url").takeIf { it.isNotBlank() }
            ?: if (streamKind == "youtube") "https://m.youtube.com" else "https://www.google.com"
        renderBrowser()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("widgetId", widgetId)
        outState.putString("streamKind", streamKind)
        outState.putString("currentUrl", webView?.url ?: currentUrl)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        runCatching {
            webView?.stopLoading()
            webView?.destroy()
        }
        webView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun renderBrowser() {
        val roleName = if (streamKind == "youtube") "YouTube Streamer" else "Chrome Video Streamer"
        page("$roleName · ${WidgetAnimations.name(state, widgetId)}", "Play any video below, then tap Stream to Widget", { leave() })

        val urlInput = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(currentUrl)
            hint = if (streamKind == "youtube") "Paste YouTube link or search…" else "Enter website URL or search Google…"
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            background = rounded(INK, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(urlInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        val goBtn = android.widget.Button(this).apply {
            text = "Go"
            isAllCaps = false
            setTextColor(INK)
            background = rounded(ACCENT, 14)
            setOnClickListener {
                val raw = urlInput.text.toString().trim()
                val target = normalizeInputToUrl(raw)
                currentUrl = target
                webView?.loadUrl(target)
            }
        }
        bar.addView(goBtn, LinearLayout.LayoutParams(dp(68), dp(46)))
        body.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        statusBadge = text(
            if (streamKind == "youtube") "Open any YouTube video or Short, then tap Stream video below."
            else "Browse to any page and play a video — we'll lock onto it automatically.",
            13f,
            ACCENT
        )

        val wv = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = StreamPlayer.MOBILE_UA
            }
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onVideoDetected(src: String, title: String) {
                    runOnUiThread {
                        if (src.isNotBlank() && !src.startsWith("blob:")) {
                            detectedVideoSrc = src
                        }
                        if (title.isNotBlank()) detectedPageTitle = title
                        statusBadge?.text = "✓ Video active on page — ready to stream to ${WidgetAnimations.name(state, widgetId)}!"
                    }
                }
            }, "AndroidSniffer")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    if (!url.isNullOrBlank()) {
                        currentUrl = url
                        urlInput.setText(url)
                    }
                    detectedPageTitle = view.title ?: ""
                    injectSniffer(view)
                }
            }
        }
        webView = wv
        val wvContainer = FrameLayout(this).apply {
            background = rounded(INK, 16)
            clipToOutline = true
        }
        wvContainer.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)))
        body.addView(wvContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)).apply {
            topMargin = dp(8)
            bottomMargin = dp(12)
        })
        wv.loadUrl(currentUrl)

        val muted = WidgetAnimations.streamMuted(state, widgetId)
        primary("Stream isolated video to ${WidgetAnimations.name(state, widgetId)}") {
            startSelectedStream(isolateVideo = true)
        }
        if (streamKind != "youtube") {
            button("Stream full live webpage view to ${WidgetAnimations.name(state, widgetId)}") {
                startSelectedStream(isolateVideo = false)
            }
        }
        button(if (muted) "Stream audio: Muted (Tap to enable sound)" else "Stream audio: Sound ON (Tap to mute)") {
            WidgetAnimations.setStreamMuted(this, widgetId, !muted)
            currentUrl = webView?.url ?: currentUrl
            renderBrowser()
        }
        link("Back to ${WidgetAnimations.name(state, widgetId)}") { leave() }
    }

    private fun normalizeInputToUrl(input: String): String {
        if (input.isBlank()) return if (streamKind == "youtube") "https://m.youtube.com" else "https://www.google.com"
        val extracted = StreamUrlHelper.extractFirstUrl(input)
        if (extracted != null) return extracted
        if (input.contains('.') && !input.contains(' ')) return "https://$input"
        return if (streamKind == "youtube") {
            "https://m.youtube.com/results?search_query=${Uri.encode(input)}"
        } else {
            "https://www.google.com/search?q=${Uri.encode(input)}"
        }
    }

    private fun injectSniffer(view: WebView) {
        val js = """
            (function() {
                if (window.__agySnifferInstalled) return;
                window.__agySnifferInstalled = true;
                setInterval(function() {
                    try {
                        var vids = document.querySelectorAll('video');
                        for (var i = 0; i < vids.length; i++) {
                            var v = vids[i];
                            var src = v.currentSrc || v.src || '';
                            if (!v.paused || src) {
                                if (window.AndroidSniffer) {
                                    window.AndroidSniffer.onVideoDetected(src, document.title || '');
                                }
                                break;
                            }
                        }
                    } catch(e) {}
                }, 700);
            })();
        """.trimIndent()
        runCatching { view.evaluateJavascript(js, null) }
    }

    private fun startSelectedStream(isolateVideo: Boolean) {
        val activeUrl = (webView?.url ?: currentUrl).trim()
        val isYt = streamKind == "youtube" || StreamUrlHelper.isYoutubeUrl(activeUrl)
        val finalKind = if (isYt) "youtube" else "chrome"
        WidgetAnimations.setRole(this, widgetId, if (isYt) "YOUTUBE" else "CHROME")
        val direct = if (!isYt && isolateVideo && StreamUrlHelper.isDirectMediaUrl(detectedVideoSrc)) detectedVideoSrc else ""
        val spec = StreamUrlHelper.buildStreamSpec(
            kind = finalKind,
            url = activeUrl,
            title = detectedPageTitle,
            isolateVideo = isolateVideo,
            muted = WidgetAnimations.streamMuted(state, widgetId),
            directVideoUrl = direct
        )
        WidgetAnimations.startStream(this, widgetId, spec, goHome = true)
    }

    private fun leave() {
        WidgetAnimations.open(this, widgetId)
    }

    @Deprecated("Legacy back support")
    override fun onBackPressed() {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            leave()
        }
    }
}
