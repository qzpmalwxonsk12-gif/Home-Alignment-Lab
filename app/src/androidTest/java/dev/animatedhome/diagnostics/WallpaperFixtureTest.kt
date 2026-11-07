package dev.animatedhome.diagnostics

import android.app.Instrumentation
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle

/** Disposable emulator fixture only. Never run this helper on the user's phone. */
class WallpaperFixtureInstrumentation : Instrumentation() {
    private var pageRegression=false
    private var scaledRegression=false
    private var videoFixture=false
    private var mediaRegression=false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); pageRegression=arguments?.getString("pageRegression")=="true"; scaledRegression=arguments?.getString("scaledRegression")=="true"; videoFixture=arguments?.getString("videoFixture")=="true"; mediaRegression=arguments?.getString("mediaRegression")=="true"; start() }
    override fun onStart() {
        val result=Bundle()
        if(pageRegression) {
            try {result.putString("pages",PageFallbackRegression.run(this));finish(-1,result)}
            catch(e:Throwable){result.putString("pages","FAIL: ${android.util.Log.getStackTraceString(e)}");finish(0,result)}
            return
        }
        if(mediaRegression) {
            try {result.putString("media",MediaImportRegression.run(this));finish(-1,result)}
            catch(e:Throwable){result.putString("media","FAIL: ${android.util.Log.getStackTraceString(e)}");finish(0,result)}
            return
        }
        if(videoFixture) {
            try { result.putString("fixture",VideoTestFixture.prepare(targetContext)); finish(-1,result) }
            catch(e:Throwable) { result.putString("fixture","FAIL: $e"); finish(0,result) }
            return
        }
        if(scaledRegression) {
            try { result.putString("regression",ScaledWidgetRegression(this).run()); finish(-1,result) }
            catch(e:Throwable) { result.putString("regression","FAIL: ${android.util.Log.getStackTraceString(e)}"); finish(0,result) }
            return
        }
        uiAutomation.adoptShellPermissionIdentity("android.permission.SET_WALLPAPER")
        try {
            val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(35,50,68))
            WallpaperManager.getInstance(targetContext).setBitmap(bitmap,null,false,WallpaperManager.FLAG_SYSTEM)
            bitmap.recycle()
            check(WallpaperManager.getInstance(targetContext).wallpaperInfo == null)
            result.putString("fixture","PASS: static home wallpaper set")
            finish(-1,result)
        } catch(e:Exception) {
            result.putString("fixture","FAIL: $e"); finish(0,result)
        } finally { uiAutomation.dropShellPermissionIdentity() }
    }
}
