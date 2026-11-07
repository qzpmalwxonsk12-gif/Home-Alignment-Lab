package dev.animatedhome.diagnostics

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import java.io.File

object MediaImportRegression {
    @Suppress("DEPRECATION")
    fun run(i:Instrumentation):String {
        val dir=File(i.targetContext.cacheDir,"media-regression").apply {mkdirs()}
        fun fixture(name:String)=File(dir,name).also { f->i.context.assets.open(name).use {input->f.outputStream().use {input.copyTo(it)}} }
        val gif=fixture("Moving-transparent.gif");val info=MediaStore.inspect(gif)
        check(info.getString("kind")=="gif" && info.getInt("frames")==20)
        val m=Movie.decodeFile(gif.path)!!;val bitmap=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888)
        m.setTime(0);m.draw(Canvas(bitmap),0f,0f)
        check(Color.alpha(bitmap.getPixel(155,0))==0){"GIF transparency lost"}
        val pixels=IntArray(16000);bitmap.getPixels(pixels,0,160,0,0,160,100)
        bitmap.eraseColor(Color.TRANSPARENT);m.setTime(900);m.draw(Canvas(bitmap),0f,0f)
        val later=IntArray(16000);bitmap.getPixels(later,0,160,0,0,160,100)
        check(!pixels.contentEquals(later)){"GIF frames did not animate"};bitmap.recycle()
        for(name in listOf("Broken.gif","Too-wide.gif"))check(runCatching {MediaStore.inspect(fixture(name))}.isFailure){"Accepted $name"}
        val video=MediaStore.inspect(fixture("Color-motion.mp4"));check(video.getString("kind")=="video" && video.getInt("width")==320)
        val rotated=MediaStore.inspect(fixture("Rotated-motion.mp4"));check(rotated.getInt("rotation") in listOf(90,270))
        MediaStore.requireKind("gif",info.getString("kind"));MediaStore.requireKind("video",video.getString("kind"))
        check(runCatching {MediaStore.requireKind("gif",video.getString("kind"))}.isFailure){"GIF picker accepted video"}
        check(runCatching {MediaStore.requireKind("video",info.getString("kind"))}.isFailure){"Video picker accepted GIF"}
        return "PASS: GIF frames and alpha; corrupt/oversized GIF rejected; MP4 metadata and decoder; rotated MP4 metadata; GIF/video selector type mismatch rejected both ways. Synthetic fixtures only."
    }
}
