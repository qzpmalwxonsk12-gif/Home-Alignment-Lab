package dev.animatedhome.diagnostics

import java.io.InputStream
import java.io.OutputStream

object MediaPolicy {
    const val VIDEO_BYTES=100L*1024*1024
    const val GIF_BYTES=10L*1024*1024
    fun isGif(head:ByteArray)=head.size>=6 && String(head,0,6,Charsets.US_ASCII) in listOf("GIF87a","GIF89a")
    fun copy(input:InputStream,output:OutputStream,limit:Long):Long {
        val buffer=ByteArray(32768);var total=0L
        while(true) {
            val n=input.read(buffer);if(n<0)break
            total+=n;require(total<=limit){"This file is too large. Choose a video under 100 MB or a GIF under 10 MB."}
            output.write(buffer,0,n)
        }
        require(total>0){"The selected file is empty."};return total
    }
    fun video(
        width:Int,
        height:Int,
        durationUs:Long,
        fps:Float,
        rotation:Int,
        maxDimension:Int=1920,
        maxPixels:Long=1920L*1080L,
        maxDurationUs:Long=120_000_000L,
        maxFps:Float=60f
    ) {
        require(width>0 && height>0 && width<=maxDimension && height<=maxDimension && width.toLong()*height<=maxPixels){"Choose a video up to 4K / 2160p (portrait or landscape)."}
        require(durationUs in 1..maxDurationUs){"Choose a video no longer than ${maxDurationUs/60_000_000L} minutes."}
        require(fps.isFinite() && fps in 0f..maxFps){"Choose a video at ${maxFps.toInt()} FPS or less."}
        require(rotation in listOf(0,90,180,270)){"This video's rotation is unsupported. Export it as a standard MP4."}
    }
    data class GifInfo(val width:Int,val height:Int,val frames:Int) {
        var durationMs:Long=0L
    }
    /** Check dimensions and block boundaries before handing untrusted data to the native decoder. */
    fun gif(data:ByteArray,maxDimension:Int=512,maxFrames:Int=300,maxCanvasPixels:Long=16_000_000L):GifInfo {
        require(data.size<=GIF_BYTES && isGif(data) && data.size>=13){"This is not a readable GIF."}
        fun word(i:Int)=(data[i].toInt() and 255) or ((data[i+1].toInt() and 255) shl 8)
        val w=word(6);val h=word(8)
        require(w in 1..maxDimension && h in 1..maxDimension){"Choose a GIF no larger than $maxDimension × $maxDimension pixels."}
        require(maxOf(w,h)/minOf(w,h).coerceAtLeast(1)<=64){"The GIF aspect ratio is unsupported."}
        var p=13;var count=0;var durationMs=0L
        fun byte():Int { require(p<data.size){"The GIF is incomplete."};return data[p++].toInt() and 255 }
        fun skip(n:Int){require(n>=0 && p+n<=data.size){"The GIF is incomplete."};p+=n}
        fun blocks(){while(true){val n=byte();if(n==0)return;skip(n)}}
        val packed=data[10].toInt() and 255
        if(packed and 128!=0)skip(3*(1 shl ((packed and 7)+1)))
        while(true)when(byte()) {
            0x21 -> {
                val label=byte()
                if(label==0xf9 && p+6<=data.size && (data[p].toInt() and 255)==4) {
                    val delayCs=word(p+2)
                    durationMs+=(if(delayCs<=1) 10 else delayCs)*10L
                }
                blocks()
            }
            0x2c -> {
                require(p+9<=data.size){"The GIF is incomplete."}
                val left=word(p);val top=word(p+2);val fw=word(p+4);val fh=word(p+6)
                require(fw>0 && fh>0 && left+fw<=w && top+fh<=h){"The GIF has invalid frame dimensions."}
                skip(8);val flags=byte();if(flags and 128!=0)skip(3*(1 shl ((flags and 7)+1)))
                require(byte() in 2..8){"The GIF encoding is unsupported."};blocks();count++
                require(count<=maxFrames){"Choose a GIF with $maxFrames frames or fewer."}
                require(w.toLong()*h*count<=maxCanvasPixels){"This GIF is too complex to play safely. Choose a smaller resolution or fewer frames."}
            }
            0x3b -> {
                require(count>0){"The GIF has no frames."}
                return GifInfo(w,h,count).also { it.durationMs = if(durationMs>0L) durationMs else count*100L }
            }
            else -> error("The GIF contains an unreadable block.")
        }
    }
}
