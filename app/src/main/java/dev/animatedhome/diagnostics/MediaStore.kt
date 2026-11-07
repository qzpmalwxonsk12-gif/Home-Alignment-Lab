package dev.animatedhome.diagnostics

import android.content.Context
import android.graphics.Movie
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One private, validated file. Publication is atomic; failed imports retain the previous selection. */
object MediaStore {
    val busy=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor()
    fun selected(context:Context,id:Int=WidgetAnimations.current(context))=State(context).obj(WidgetAnimations.key("selectedMedia",id))
    fun isStream(info:JSONObject)=info.optBoolean("stream",false) || info.optString("file").startsWith("stream://")
    fun file(context:Context,info:JSONObject=selected(context)):File? {
        val name=info.optString("file")
        return if(name.matches(Regex("media-[a-f0-9-]+\\.bin"))) File(context.filesDir,name).takeIf { it.isFile } else null
    }
    fun isPlayableSource(context:Context,info:JSONObject=selected(context)):Boolean=isStream(info) || file(context,info)!=null
    fun pending(context:Context,id:Int=WidgetAnimations.current(context))=State(context).obj(WidgetAnimations.key("pendingMedia",id))
    fun active(context:Context)=if(State(context).prefs.getBoolean("personalPlayback",false)) State(context).obj("playbackMedia") else JSONObject()
    fun canPlay(context:Context,id:Int=WidgetAnimations.current(context)):Boolean {
        val s=State(context)
        val sel=selected(context,id)
        return SavedSetupPolicy.canPlay(isPlayableSource(context,sel),busy.get(),pending(context,id).length()>0,s.prefs.getBoolean(WidgetAnimations.key("mediaImportFailed",id),false))
    }
    fun accept(context:Context,id:Int=WidgetAnimations.current(context)):Boolean {
        val s=State(context);val candidate=pending(context,id)
        if(!s.ids().contains(id) || busy.get() || file(context,candidate)==null)return false
        val ok=s.prefs.edit().putString(WidgetAnimations.key("selectedMedia",id),candidate.toString()).remove(WidgetAnimations.key("pendingMedia",id))
            .putBoolean(WidgetAnimations.key("mediaImportFailed",id),false).putString(WidgetAnimations.key("mediaImportMessage",id),"").putString(WidgetAnimations.key("mediaError",id),"")
            .putBoolean(WidgetAnimations.key("enabled",id),false).commit()
        if(ok)DiagnosticLog.event(context,"MEDIA_CONFIRMED",candidate)
        return ok
    }
    fun keepPrevious(context:Context,id:Int=WidgetAnimations.current(context)) {
        State(context).prefs.edit().remove(WidgetAnimations.key("pendingMedia",id)).putBoolean(WidgetAnimations.key("mediaImportFailed",id),false)
            .putString(WidgetAnimations.key("mediaImportMessage",id),"").putBoolean(WidgetAnimations.key("enabled",id),false).apply()
    }
    fun requireKind(expected:String,actual:String) {
        require(expected==actual){
            if(expected=="gif" && actual=="video") "You asked for a gif, but the file provider returned a video. Nothing was selected. If your file is a looping MP4 (many apps save GIFs as MP4s), tap 'Choose a video' instead, or pick the original .gif from the Files app (☰ menu)."
            else "You asked for a $expected, but the file provider returned a $actual. Nothing was selected. Try the Files app or download the original file first."
        }
    }
    fun import(context:Context,uri:Uri,expectedKind:String,id:Int=WidgetAnimations.current(context)) {
        if(!busy.compareAndSet(false,true))return
        val app=context.applicationContext;val state=State(app)
        state.prefs.edit().remove(WidgetAnimations.key("pendingMedia",id)).putString(WidgetAnimations.key("mediaImportMessage",id),"").putBoolean(WidgetAnimations.key("enabled",id),false).apply()
        worker.execute {
            var temp=File(app.filesDir,"import-${UUID.randomUUID()}.tmp")
            var published:File?=null
            var name="the selected file"
            try {
                name=runCatching { app.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } }.getOrNull()?.take(160) ?: "My animation"
                app.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { MediaPolicy.copy(input,it,MediaPolicy.VIDEO_BYTES) } }
                    ?: error("The file could not be opened. Download it to your phone, then choose it again.")
                check(state.ids().contains(id)){"This widget was removed during import."}
                val info=inspect(temp).put("widgetId",id).put("name",name)
                val actualKind=info.getString("kind")
                if(expectedKind!=actualKind) {
                    info.put("kindMismatch",true)
                }
                temp=VideoOptimizer.optimizeIfNeeded(app,temp,info)
                info.put("bytes",temp.length())
                val digest=MessageDigest.getInstance("SHA-256")
                temp.inputStream().use { input->val buffer=ByteArray(32768);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
                info.put("sha256",digest.digest().joinToString(""){"%02x".format(it)})
                    .put("provider",uri.authority ?: "unknown").put("requestedKind",expectedKind)
                val dest=File(app.filesDir,"media-${UUID.randomUUID()}.bin")
                check(temp.renameTo(dest)){"Could not save the file. Check free space and try again."};published=dest
                info.put("file",dest.name)
                check(state.prefs.edit().putString(WidgetAnimations.key("pendingMedia",id),info.toString()).putString(WidgetAnimations.key("mediaImportMessage",id),"")
                    .putBoolean(WidgetAnimations.key("mediaImportFailed",id),false).putString(WidgetAnimations.key("mediaError",id),"").commit()){"Could not save your selection."}
                // Old files are retained until this process next starts, so an existing renderer
                // never races deletion while switching. Failed imports never touch the selection.
                DiagnosticLog.event(app,"MEDIA_PREVIEW_READY",info)
            } catch(e:Exception) {
                published?.delete()
                state.prefs.edit().remove(WidgetAnimations.key("pendingMedia",id)).putBoolean(WidgetAnimations.key("mediaImportFailed",id),true).putString(WidgetAnimations.key("mediaImportMessage",id),"Could not use $name: ${(e.message ?: "Unreadable file").trimEnd('.')}.").apply()
                DiagnosticLog.event(app,"MEDIA_IMPORT_FAILED",e.javaClass.simpleName+": "+e.message)
            } finally {temp.delete();busy.set(false)}
        }
    }
    @Suppress("DEPRECATION")
    fun inspect(file:File):JSONObject {
        val head=file.inputStream().use { val h=ByteArray(6);it.read(h);h }
        if(MediaPolicy.isGif(head)) {
            require(file.length()<=MediaPolicy.GIF_BYTES){"Choose a GIF under 10 MB."}
            val data=file.readBytes();val info=MediaPolicy.gif(data,maxDimension=1920,maxFrames=600,maxCanvasPixels=120_000_000L)
            val movie=Movie.decodeByteArray(data,0,data.size) ?: error("Android cannot decode this GIF. Try exporting it again.")
            require(movie.width()==info.width && movie.height()==info.height){"The GIF dimensions could not be verified."}
            val durationMs=maxOf(movie.duration().toLong(),info.durationMs).coerceAtLeast(100L)
            require(durationMs<=120000L){"Choose a GIF no longer than two minutes."}
            return JSONObject().put("kind","gif").put("width",info.width).put("height",info.height).put("frames",info.frames)
                .put("durationUs",durationMs*1000L).put("fps",0).put("rotation",0)
        }
        val ex=MediaExtractor()
        try {
            ex.setDataSource(file.path)
            val track=(0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true }
                ?: error("Choose a video or GIF, not a still image or audio file.")
            val f=ex.getTrackFormat(track);val mime=f.getString(MediaFormat.KEY_MIME)!!
            val w=f.getInteger(MediaFormat.KEY_WIDTH);val h=f.getInteger(MediaFormat.KEY_HEIGHT)
            var duration=if(f.containsKey(MediaFormat.KEY_DURATION))runCatching { f.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L) else 0L
            var rotation=if(f.containsKey(MediaFormat.KEY_ROTATION))runCatching { f.getInteger(MediaFormat.KEY_ROTATION) }.getOrDefault(0) else 0
            if(duration<=0L || !f.containsKey(MediaFormat.KEY_ROTATION)) {
                val mmr=MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.path)
                    if(duration<=0L) {
                        val ms=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        if(ms>0L)duration=ms*1000L
                    }
                    if(!f.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotation=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: rotation
                    }
                } catch(_:Exception) {} finally { runCatching { mmr.release() } }
            }
            ex.selectTrack(track);require(ex.sampleTime>=0){"The video contains no readable frames."}
            if(duration<=0L)duration=10_000_000L
            val rawFps=if(f.containsKey(MediaFormat.KEY_FRAME_RATE))runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrElse { runCatching { f.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(0f) } else 0f
            val fps=if(rawFps.isFinite() && rawFps>=0f) rawFps.coerceAtMost(120f) else 0f
            val normRotation=((rotation%360)+360)%360
            MediaPolicy.video(
                width=w,
                height=h,
                durationUs=duration,
                fps=fps,
                rotation=normRotation,
                maxDimension=4096,
                maxPixels=3840L*2160L,
                maxDurationUs=600_000_000L,
                maxFps=125f
            )
            val list=MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val minimalFormat=MediaFormat.createVideoFormat(mime,w,h)
            val hasDecoder=list.findDecoderForFormat(f)!=null ||
                list.findDecoderForFormat(minimalFormat)!=null ||
                list.codecInfos.any { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime,true) } }
            require(hasDecoder){"This phone cannot decode this video ($mime). Try a standard H.264 MP4."}
            return JSONObject().put("kind","video").put("mime",mime).put("width",w).put("height",h).put("durationUs",duration).put("fps",fps).put("rotation",normRotation)
        } catch(e:IllegalArgumentException){throw e}
        catch(e:Exception){throw IllegalArgumentException(e.message ?: "This video could not be read. Try an H.264 MP4.",e)}
        finally {ex.release()}
    }
}
