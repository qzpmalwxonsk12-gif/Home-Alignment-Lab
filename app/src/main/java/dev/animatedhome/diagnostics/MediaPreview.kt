package dev.animatedhome.diagnostics

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.view.View
import java.io.File

/** Preview the private copy that will be played, never a provider thumbnail or another URI. */
@Suppress("DEPRECATION")
class MediaPreview(context:Context,file:File,kind:String,private val durationUs:Long=0L,private val animate:Boolean=true):View(context) {
    private var movie:Movie?=null
    private var poster:Bitmap?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var detached=false
    private var showing=true
    private val started=SystemClock.uptimeMillis()
    private var caption="Loading saved-file preview…"
    init {
        contentDescription="Preview of the saved $kind file"
        Thread {
            var loadedMovie:Movie?=null;var loadedBitmap:Bitmap?=null
            runCatching {
                if(kind=="gif" && animate)loadedMovie=Movie.decodeFile(file.path)
                else if(kind=="gif") {
                    val options=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,options)
                    options.inJustDecodeBounds=false;options.inSampleSize=(maxOf(options.outWidth,options.outHeight)/220).coerceAtLeast(1)
                    loadedBitmap=BitmapFactory.decodeFile(file.path,options)
                }
                else MediaMetadataRetriever().let { reader ->
                    try {
                        reader.setDataSource(file.path)
                        val sampleUs=if(durationUs>1_000_000L) durationUs/5 else 0L
                        loadedBitmap=reader.getFrameAtTime(sampleUs,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: reader.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {reader.release()}
                }
            }
            post {
                if(detached){loadedBitmap?.recycle();return@post}
                movie=loadedMovie;poster=loadedBitmap;caption="Preview unavailable for this file. Check its name before continuing.";invalidate()
            }
        }.start()
    }
    override fun onVisibilityAggregated(isVisible:Boolean){super.onVisibilityAggregated(isVisible);showing=isVisible;if(isVisible)invalidate()}
    override fun onDetachedFromWindow(){detached=true;movie=null;poster?.recycle();poster=null;super.onDetachedFromWindow()}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val tile=(20*resources.displayMetrics.density).toInt().coerceAtLeast(1)
        for(y in 0..height/tile)for(x in 0..width/tile){paint.color=if((x+y)%2==0)Color.rgb(45,57,68) else Color.rgb(66,78,89);canvas.drawRect((x*tile).toFloat(),(y*tile).toFloat(),((x+1)*tile).toFloat(),((y+1)*tile).toFloat(),paint)}
        val m=movie;val b=poster
        val w=m?.width() ?: b?.width ?: 0;val h=m?.height() ?: b?.height ?: 0
        if(w>0 && h>0) {
            val scale=minOf(width.toFloat()/w,height.toFloat()/h)
            canvas.save();canvas.translate((width-w*scale)/2,(height-h*scale)/2);canvas.scale(scale,scale)
            if(m!=null){
                val durMs=(durationUs/1000L).coerceAtLeast(m.duration().coerceAtLeast(100).toLong())
                val movieDur=m.duration().coerceAtLeast(100)
                val targetTime=(((SystemClock.uptimeMillis()-started)%durMs)*movieDur/durMs).toInt()
                m.setTime(targetTime);m.draw(canvas,0f,0f)
            } else canvas.drawBitmap(b!!,0f,0f,paint)
            canvas.restore()
            if(m!=null && showing)postInvalidateDelayed(33)
        } else {paint.color=Color.WHITE;paint.textSize=12*resources.displayMetrics.density;canvas.drawText(caption,12f,height/2f,paint)}
    }
}
