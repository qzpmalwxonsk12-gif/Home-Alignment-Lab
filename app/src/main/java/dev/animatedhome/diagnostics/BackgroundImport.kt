package dev.animatedhome.diagnostics

import android.Manifest
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min

object BackgroundImport {
    const val PICK = 40
    const val READ_PERMISSION = 41
    const val READ_IMAGES_PERMISSION = 43
    private val worker = Executors.newSingleThreadExecutor()
    fun picker(a: LabActivity) {
        val intent = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
            else Intent.createChooser(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), "Choose background photo")
        try { a.startActivityForResult(intent, PICK) }
        catch (_: Exception) { a.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PICK) }
    }
    fun resumeCurrent(a:LabActivity) {
        val p=State(a).prefs
        if(!p.getBoolean("currentWallpaperPermissionPending",false))return
        p.edit().putBoolean("currentWallpaperPermissionPending",false).apply()
        if(Build.VERSION.SDK_INT>=30 && Environment.isExternalStorageManager())current(a)
        else a.message("Current-wallpaper access was not enabled. You can try again or choose a photo instead.")
    }
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = drawable.intrinsicWidth.coerceAtLeast(1080)
        val height = drawable.intrinsicHeight.coerceAtLeast(2400)
        val factor = min(1f, 2048f / maxOf(width, height))
        return Bitmap.createBitmap((width * factor).toInt().coerceAtLeast(1), (height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, it.width, it.height)
            drawable.draw(Canvas(it))
        }
    }
    @Suppress("DEPRECATION", "MissingPermission")
    private fun tryLoadSystemWallpaperBitmap(a: LabActivity, wm: WallpaperManager): Bitmap? {
        val d = device(a)
        val sw = d.optInt("width", 1080).coerceAtLeast(1080)
        val sh = d.optInt("height", 2400).coerceAtLeast(2400)
        val ourWallpaperActive = wm.wallpaperInfo?.component == ComponentName(a, LabWallpaper::class.java)

        // 1. Try reading user-set static system wallpaper file descriptor (highest quality)
        if (!ourWallpaperActive) {
            for (flag in listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK)) {
                val pfd = runCatching { wm.getWallpaperFile(flag) }.getOrNull()
                if (pfd != null) {
                    pfd.use { fd ->
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            opts.inJustDecodeBounds = false
                            opts.inSampleSize = 1
                            while (maxOf(opts.outWidth, opts.outHeight) / opts.inSampleSize > 2048) opts.inSampleSize *= 2
                            val bmp = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, opts)
                            if (bmp != null) return bmp
                        }
                    }
                }
            }
        }

        // 2. Try WallpaperManager.getDrawable(FLAG_SYSTEM / FLAG_LOCK) or legacy drawable
        if (!ourWallpaperActive) {
            val sysDrawable = runCatching {
                if (Build.VERSION.SDK_INT >= 34) wm.getDrawable(WallpaperManager.FLAG_SYSTEM) ?: wm.getDrawable(WallpaperManager.FLAG_LOCK)
                else wm.drawable
            }.getOrNull()
            if (sysDrawable != null) return drawableToBitmap(sysDrawable)
        } else {
            val lockDrawable = runCatching {
                if (Build.VERSION.SDK_INT >= 34) wm.getDrawable(WallpaperManager.FLAG_LOCK) else null
            }.getOrNull()
            if (lockDrawable != null) return drawableToBitmap(lockDrawable)
        }

        // 3. Try Android's built-in stock system wallpaper (requires NO storage permissions!)
        val builtIn = runCatching {
            wm.getBuiltInDrawable(sw, sh, true, 0.5f, 0.5f, WallpaperManager.FLAG_SYSTEM)
                ?: wm.getBuiltInDrawable(sw, sh, true, 0.5f, 0.5f)
                ?: wm.builtInDrawable
        }.getOrNull()
        if (builtIn != null) return drawableToBitmap(builtIn)

        // 4. If an OEM live/dynamic system wallpaper is active, fall back to its high-res thumbnail drawable
        val info = wm.wallpaperInfo
        if (info != null && info.component != ComponentName(a, LabWallpaper::class.java)) {
            val thumb = runCatching { info.loadThumbnail(a.packageManager) }.getOrNull()
            if (thumb != null) return drawableToBitmap(thumb)
        }
        return null
    }
    fun current(a: LabActivity) {
        val wm = WallpaperManager.getInstance(a)
        val ourWallpaperActive = wm.wallpaperInfo?.component == ComponentName(a, LabWallpaper::class.java)
        val existingSaved = File(a.filesDir, "background.jpg")
        if (ourWallpaperActive && existingSaved.exists()) {
            State(a).prefs.edit().putBoolean("backgroundChosen",true).putBoolean("useGrid",false).putLong("backgroundVersion",System.currentTimeMillis()).apply()
            android.widget.Toast.makeText(a,"Restored your saved wallpaper background.",android.widget.Toast.LENGTH_LONG).show();return
        }
        // First check if we can read the built-in or current wallpaper without asking for special permissions
        val hasStoragePerm = if (Build.VERSION.SDK_INT >= 33) Environment.isExternalStorageManager()
            else a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (!hasStoragePerm && !ourWallpaperActive) {
            if (Build.VERSION.SDK_INT >= 33 && !Environment.isExternalStorageManager()) {
                AlertDialog.Builder(a).setTitle("Allow reading current wallpaper")
                    .setMessage("Android restricts reading the current system wallpaper behind All files access.\n\n• Granting access lets the app copy your current static home/lock wallpaper.\n• Or tap Use built-in / stock fallback below to import the phone's built-in wallpaper without any permissions.")
                    .setNeutralButton("Use built-in fallback") { _,_ ->
                        work(a, "built-in system wallpaper") {
                            tryLoadSystemWallpaperBitmap(a, wm) ?: error("No built-in static wallpaper available")
                        }
                    }
                    .setNegativeButton("Choose a photo") { _,_ -> picker(a) }
                    .setPositiveButton("Open access settings") { _,_ ->
                        State(a).prefs.edit().putBoolean("currentWallpaperPermissionPending",true).apply()
                        try { a.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:${a.packageName}"))) }
                        catch (_: Exception) { a.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    }.show()
                return
            }
            if (Build.VERSION.SDK_INT <= 32) {
                a.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),READ_PERMISSION); return
            }
        }
        work(a,"current system wallpaper") {
            tryLoadSystemWallpaperBitmap(a, wm)
                ?: error("Android blocked access to this system wallpaper. If it is a protected OEM theme, save a screenshot or pick the image via Choose a photo.")
        }
    }
    fun selected(a: LabActivity, uri: Uri) = work(a,"selected photo") {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(a.contentResolver,uri)) { decoder,info,_ ->
                val scale = min(1f,2048f/maxOf(info.size.width,info.size.height))
                decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            a.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,options) }
            require(options.outWidth > 0 && options.outHeight > 0) { "Unsupported image" }
            options.inJustDecodeBounds = false; options.inSampleSize = 1
            while (maxOf(options.outWidth,options.outHeight)/options.inSampleSize > 2048) options.inSampleSize *= 2
            a.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,options) } ?: error("Unable to decode image")
        }
    }
    private fun work(a: LabActivity, source: String, load: () -> Bitmap) {
        android.widget.Toast.makeText(a,"Importing $source…",android.widget.Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val bitmap = load()
                try {
                    val temp = File(a.filesDir,"background-new.jpg")
                    temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,94,it)) }
                    val saved = File(a.filesDir,"background.jpg")
                    if (saved.exists()) saved.delete()
                    check(temp.renameTo(saved)) { "Unable to save background" }
                    State(a).prefs.edit().putBoolean("backgroundChosen",true).putBoolean("useGrid",false)
                        .putLong("backgroundVersion",System.currentTimeMillis()).apply()
                    DiagnosticLog.event(a,"IMPORT_SUCCESS","$source ${bitmap.width}x${bitmap.height} saved privately before activation")
                } finally { bitmap.recycle() }
                a.runOnUiThread { if (!a.isFinishing && !a.isDestroyed) {
                    if(a is SetupActivity) android.widget.Toast.makeText(a,if(AlignmentFlow.wallpaperActive(a)) "Background updated." else "Background saved. Next: apply the wallpaper.",android.widget.Toast.LENGTH_LONG).show()
                    else a.message("Photo saved. Continue wallpaper setup to apply it to your home screen.")
                } }
            } catch (e: Exception) {
                DiagnosticLog.event(a,"IMPORT_FAILURE",e.toString())
                a.runOnUiThread { if (!a.isFinishing && !a.isDestroyed) a.message("Import failed: ${e.javaClass.simpleName}: ${e.message}\n\nUse Choose a photo as the permission-free fallback. Your previously saved image is retained.") }
            } catch (_: OutOfMemoryError) {
                DiagnosticLog.event(a,"IMPORT_FAILURE","Insufficient memory")
                a.runOnUiThread { if (!a.isFinishing && !a.isDestroyed) a.message("Insufficient memory to read that wallpaper. Choose a smaller image.") }
            }
        }
    }
}
