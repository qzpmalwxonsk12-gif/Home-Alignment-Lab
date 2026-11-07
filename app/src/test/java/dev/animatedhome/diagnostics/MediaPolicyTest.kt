package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MediaPolicyTest {
    private fun gif()=byteArrayOf(71,73,70,56,57,97,1,0,1,0,128.toByte(),0,0,0,0,0,255.toByte(),255.toByte(),255.toByte(),44,0,0,0,0,1,0,1,0,0,2,2,68,1,0,59)
    @Test fun validGifHasCanvasAndFrame(){assertEquals(MediaPolicy.GifInfo(1,1,1),MediaPolicy.gif(gif()))}
    @Test fun recognizesBothGifVersions(){assertTrue(MediaPolicy.isGif("GIF87a".toByteArray()));assertTrue(MediaPolicy.isGif("GIF89a".toByteArray()));assertFalse(MediaPolicy.isGif("GIFxxx".toByteArray()))}
    @Test(expected=IllegalArgumentException::class) fun truncatedGifRejected(){MediaPolicy.gif(gif().copyOf(30))}
    @Test(expected=IllegalArgumentException::class) fun gifWithoutTrailerRejected(){MediaPolicy.gif(gif().dropLast(1).toByteArray())}
    @Test(expected=IllegalArgumentException::class) fun hugeGifRejectedBeforeDecode(){val g=gif();g[7]=3;MediaPolicy.gif(g)}
    @Test(expected=IllegalArgumentException::class) fun frameOutsideCanvasRejected(){val g=gif();g[24]=2;MediaPolicy.gif(g)}
    @Test(expected=IllegalArgumentException::class) fun invalidGifCompressionRejected(){val g=gif();g[29]=1;MediaPolicy.gif(g)}
    @Test(expected=IllegalArgumentException::class) fun excessiveFramesRejected(){val g=gif();val stream=ByteArrayOutputStream();stream.write(g,0,19);repeat(301){stream.write(g,19,15)};stream.write(59);MediaPolicy.gif(stream.toByteArray())}
    @Test fun highResSquareGifAcceptedWithExtendedBounds(){
        val g=gif()
        g[6]=0x38;g[7]=0x04;g[8]=0x38;g[9]=0x04 // 1080 x 1080
        val info=MediaPolicy.gif(g,1920,600,120_000_000L)
        assertEquals(MediaPolicy.GifInfo(1080,1080,1),info)
    }
    @Test(expected=IllegalArgumentException::class) fun degenerateStripGifRejectedEvenWithExtendedBounds(){
        val g=gif()
        g[6]=0x01;g[7]=0x02;g[8]=0x01;g[9]=0x00 // 513 x 1
        MediaPolicy.gif(g,1920,600,120_000_000L)
    }
    @Test fun graphicControlExtensionFrameDelayExtracted(){
        val g=gif()
        val stream=ByteArrayOutputStream()
        stream.write(g,0,19)
        stream.write(byteArrayOf(0x21,0xF9.toByte(),4,0,12,0,0,0)) // 120 ms delay
        stream.write(g,19,16)
        val info=MediaPolicy.gif(stream.toByteArray())
        assertEquals(120L,info.durationMs)
    }
    @Test fun boundedCopyPreservesBytes(){val input=byteArrayOf(1,2,3);val out=ByteArrayOutputStream();assertEquals(3L,MediaPolicy.copy(ByteArrayInputStream(input),out,3));assertArrayEquals(input,out.toByteArray())}
    @Test(expected=IllegalArgumentException::class) fun oversizedCopyRejected(){MediaPolicy.copy(ByteArrayInputStream(ByteArray(4)),ByteArrayOutputStream(),3)}
    @Test(expected=IllegalArgumentException::class) fun emptyCopyRejected(){MediaPolicy.copy(ByteArrayInputStream(byteArrayOf()),ByteArrayOutputStream(),3)}
    @Test fun landscapeAndPortraitVideoAccepted(){MediaPolicy.video(1920,1080,120000000,60f,0);MediaPolicy.video(1080,1920,1,30f,90)}
    @Test(expected=IllegalArgumentException::class) fun fourKRejected(){MediaPolicy.video(3840,2160,1000,30f,0)}
    @Test(expected=IllegalArgumentException::class) fun tooLongRejected(){MediaPolicy.video(640,360,120000001,30f,0)}
    @Test(expected=IllegalArgumentException::class) fun zeroDurationRejected(){MediaPolicy.video(640,360,0,30f,0)}
    @Test(expected=IllegalArgumentException::class) fun highFpsRejected(){MediaPolicy.video(640,360,1000,120f,0)}
    @Test(expected=IllegalArgumentException::class) fun nanFpsRejected(){MediaPolicy.video(640,360,1000,Float.NaN,0)}
    @Test(expected=IllegalArgumentException::class) fun invalidRotationRejected(){MediaPolicy.video(640,360,1000,30f,45)}
}
