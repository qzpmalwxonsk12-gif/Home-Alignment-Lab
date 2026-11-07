package dev.animatedhome.diagnostics

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.io.File

open class LabActivity : Activity() {
    companion object {
        val INK=Color.rgb(12,17,24)
        val PANEL=Color.rgb(23,31,42)
        val LINE=Color.rgb(42,55,70)
        val ACCENT=Color.rgb(153,235,199)
        val WHITE=Color.rgb(240,245,249)
        val MUTED=Color.rgb(157,175,193)
        val AMBER=Color.rgb(246,197,125)
    }
    lateinit var body: LinearLayout
    private lateinit var shell:LinearLayout
    private var bottom:View?=null
    fun rounded(color:Int,radius:Int=20,stroke:Boolean=false)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(radius).toFloat();if(stroke)setStroke(dp(1),LINE)
    }
    private fun ripple(color:Int,radius:Int=16)=android.graphics.drawable.RippleDrawable(
        ColorStateList.valueOf(Color.argb(32,255,255,255)),rounded(color,radius),rounded(Color.WHITE,radius))
    fun page(title:String,subtitle:String,back:(()->Unit)?=null) {
        shell=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(INK)}
        val scroll=ScrollView(this).apply {isFillViewport=true;isVerticalScrollBarEnabled=false}
        body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(20),dp(22),dp(28))}
        scroll.addView(body);shell.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));bottom=null
        setContentView(shell)
        window.statusBarColor=INK;window.navigationBarColor=INK
        if(Build.VERSION.SDK_INT>=30)window.insetsController?.setSystemBarsAppearance(0,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        shell.setOnApplyWindowInsetsListener {view,insets->
            if(Build.VERSION.SDK_INT>=30){val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom)}
            else {@Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)}
            insets
        }
        if(back!=null)link("‹  Back",back)
        text(title,30f,WHITE).apply {typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);setPadding(0,dp(6),0,dp(5))}
        if(subtitle.isNotBlank())text(subtitle,14f,MUTED).setPadding(0,0,0,dp(18))
    }
    fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    fun text(value:String,size:Float=15f,color:Int=WHITE):TextView=TextView(this).also {
        it.text=value;it.textSize=size;it.setTextColor(color);it.setTextIsSelectable(false)
        it.setLineSpacing(dp(2).toFloat(),1f);it.setPadding(0,dp(5),0,dp(9));body.addView(it)
    }
    fun section(value:String){text(value,19f,WHITE).apply {typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);setPadding(0,dp(20),0,dp(10))}}
    fun eyebrow(value:String){text(value.uppercase(),11f,ACCENT).apply {letterSpacing=.13f;typeface=android.graphics.Typeface.DEFAULT_BOLD}}
    fun space(height:Int=10){body.addView(View(this),LinearLayout.LayoutParams(1,dp(height)))}
    fun card(content:()->Unit):LinearLayout {
        val parent=body
        val c=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(16));background=rounded(PANEL,22,true)}
        parent.addView(c,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(6);bottomMargin=dp(8)})
        body=c;try {content()} finally {body=parent};return c
    }
    fun pill(label:String,warning:Boolean=false) {
        text("●  $label",12f,if(warning)AMBER else ACCENT).apply {
            setPadding(dp(11),dp(6),dp(11),dp(6));background=rounded(if(warning)Color.rgb(53,42,30) else Color.rgb(29,53,45),20)
            layoutParams=LinearLayout.LayoutParams(-2,-2).apply {bottomMargin=dp(8)}
        }
    }
    fun button(label:String,action:()->Unit):Button=Button(this).also {
        it.text=label;it.isAllCaps=false;it.minHeight=dp(52);it.minimumHeight=dp(52);it.textSize=15f
        it.typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);it.setTextColor(WHITE)
        it.background=ripple(Color.rgb(35,47,61));it.setPadding(dp(16),dp(12),dp(16),dp(12))
        body.addView(it,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(5);bottomMargin=dp(5)})
        it.setOnClickListener {action()}
    }
    fun primary(label:String,action:()->Unit)=button(label,action).apply {background=ripple(ACCENT);setTextColor(INK)}
    fun link(label:String,action:()->Unit)=button(label,action).apply {background=ripple(Color.TRANSPARENT);setTextColor(ACCENT);gravity=android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL}
    fun notice(value:String)=text(value,14f,WHITE).apply {
        setPadding(dp(16),dp(14),dp(16),dp(14));background=rounded(Color.rgb(28,44,51),16)
        (layoutParams as LinearLayout.LayoutParams).apply {topMargin=dp(6);bottomMargin=dp(12)}
    }
    fun actionRow(title:String,description:String,icon:String="widgets",action:()->Unit) {
        val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL;minimumHeight=dp(82);setPadding(dp(14),dp(12),dp(12),dp(12));background=ripple(PANEL,18);isClickable=true;isFocusable=true;setOnClickListener {action()}}
        row.addView(AppGlyph(this,icon,ACCENT),LinearLayout.LayoutParams(dp(28),dp(28)).apply {marginEnd=dp(14)})
        val words=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        words.addView(TextView(this).apply {text=title;textSize=16f;setTextColor(WHITE);typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)})
        words.addView(TextView(this).apply {text=description;textSize=13f;setTextColor(MUTED);setPadding(0,dp(5),0,0)})
        row.addView(words,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(TextView(this).apply {text="›";textSize=24f;setTextColor(MUTED);setPadding(dp(12),0,dp(3),0)})
        body.addView(row,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(5);bottomMargin=dp(5)})
    }
    fun metrics(values:List<Pair<String,String>>) {
        val row=LinearLayout(this)
        values.forEach { (value,label)->
            val col=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(16),dp(8),dp(16));background=rounded(PANEL,16)}
            col.addView(TextView(this).apply {text=value;textSize=26f;setTextColor(WHITE);typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)})
            col.addView(TextView(this).apply {text=label;textSize=12f;setTextColor(MUTED);setPadding(0,dp(4),0,0)})
            row.addView(col,LinearLayout.LayoutParams(0,-2,1f).apply {marginEnd=dp(5)})
        }
        body.addView(row,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8);bottomMargin=dp(8)})
    }
    fun navigation(selected:String,onSelect:(String)->Unit) {
        bottom?.let {shell.removeView(it)}
        val nav=LinearLayout(this).apply {setPadding(dp(12),dp(9),dp(12),dp(8));background=rounded(INK,0)}
        listOf("Home" to "home","Appearance" to "appearance","Diagnostics" to "diagnostics").forEach { (label,glyph)->
            val chosen=label==selected;val color=if(chosen)ACCENT else MUTED
            val item=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=android.view.Gravity.CENTER;minimumHeight=dp(56);background=ripple(if(chosen)Color.rgb(25,43,40) else INK,16);isClickable=true;isFocusable=true;isSelected=chosen;contentDescription=label;setOnClickListener {onSelect(label)}}
            item.addView(AppGlyph(this,glyph,color),LinearLayout.LayoutParams(dp(23),dp(23)))
            item.addView(TextView(this).apply {text=label;textSize=11f;gravity=android.view.Gravity.CENTER;setTextColor(color);setPadding(0,dp(4),0,0)})
            nav.addView(item,LinearLayout.LayoutParams(0,-2,1f).apply {marginStart=dp(3);marginEnd=dp(3)})
        }
        shell.addView(nav);bottom=nav
    }
    fun boxExample() {
        val v = object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                super.onDraw(c)
                val r=RectF(width*.2f,dp(12).toFloat(),width*.8f,height-dp(12).toFloat())
                p.style=Paint.Style.FILL; p.color=Color.rgb(255,39,158); c.drawRoundRect(r,dp(10).toFloat(),dp(10).toFloat(),p)
                p.style=Paint.Style.STROKE; p.strokeWidth=dp(3).toFloat(); p.color=Color.CYAN; c.drawRoundRect(r,dp(10).toFloat(),dp(10).toFloat(),p)
                r.inset(dp(4).toFloat(),dp(4).toFloat()); p.color=Color.YELLOW; p.strokeWidth=dp(1).toFloat(); c.drawRoundRect(r,dp(8).toFloat(),dp(8).toFloat(),p)
            }
        }.apply { contentDescription="Example: pink box with cyan edges behind the yellow widget outline" }
        body.addView(v,LinearLayout.LayoutParams(-1,dp(96)))
        text("Example of what to look for on your home screen",12f)
    }
    fun details(title: String, value: String) {
        val t=TextView(this).apply { text=value; setTextIsSelectable(true); setPadding(dp(20),dp(16),dp(20),dp(16)); textSize=13f }
        AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(t) }).setPositiveButton("Close",null).show()
    }
    fun openSetup(help: Boolean = false) { launch(Intent(this,SetupActivity::class.java).putExtra("help",help)); finish() }
    fun saveReport() { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Home-Alignment-Lab-report.txt"),71) }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode != 71 || resultCode != RESULT_OK || data?.data == null) return
        val snapshot=State(this).report(); val uri=data.data!!
        Thread {
            val result=runCatching {
                contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { out ->
                    out.write(snapshot)
                    for(name in listOf("diagnostics.previous.log","diagnostics.log")) {
                        val f=File(filesDir,name); if(f.exists()) { out.write("\n\nFULL LOG: $name\n"); f.reader().use { it.copyTo(out) } }
                    }
                }
            }
            runOnUiThread { if(!isDestroyed) message(if(result.isSuccess) "Report saved. Share it with support when you need help. Nothing was sent automatically." else "Could not save the report: ${result.exceptionOrNull()?.message}. Try Downloads as the save location.") }
        }.start()
    }
    fun message(value: String) { AlertDialog.Builder(this).setMessage(value).setPositiveButton("OK",null).show() }
    fun launch(intent: Intent) { runCatching { startActivity(intent) }.onFailure { message("Unable to open: ${it.message}") } }
    fun home() { launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish() }
    fun number(label: String, initial: String): EditText {
        text(label, 13f)
        return EditText(this).also { it.setSingleLine(); it.setText(initial); it.inputType = 8194 or 4096; body.addView(it) }
    }
}


/** Small original line icons; no external assets or font dependencies. */
class AppGlyph(context:android.content.Context,private val kind:String,private val tint:Int):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c:Canvas) {
        super.onDraw(c);c.save();c.scale(width/24f,height/24f)
        p.color=tint;p.style=Paint.Style.STROKE;p.strokeWidth=1.65f;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
        fun line(x:Float,y:Float,a:Float,b:Float)=c.drawLine(x,y,a,b,p)
        when(kind) {
            "home" -> {val path=android.graphics.Path();path.moveTo(3f,10f);path.lineTo(12f,3f);path.lineTo(21f,10f);c.drawPath(path,p);line(5f,9f,5f,21f);line(5f,21f,19f,21f);line(19f,21f,19f,9f);c.drawRect(10f,14f,14f,21f,p)}
            "appearance" -> {c.drawRoundRect(3f,3f,21f,21f,4f,4f,p);c.drawCircle(8f,8f,1.5f,p);val path=android.graphics.Path();path.moveTo(4f,18f);path.lineTo(10f,12f);path.lineTo(14f,16f);path.lineTo(18f,11f);path.lineTo(21f,14f);c.drawPath(path,p)}
            "diagnostics" -> {c.drawRoundRect(3f,3f,21f,21f,5f,5f,p);line(5f,12f,9f,12f);line(9f,12f,11f,7f);line(11f,7f,14f,17f);line(14f,17f,16f,12f);line(16f,12f,19f,12f)}
            "download" -> {line(12f,3f,12f,16f);line(7f,11f,12f,16f);line(12f,16f,17f,11f);line(4f,17f,4f,21f);line(4f,21f,20f,21f);line(20f,21f,20f,17f)}
            "pages" -> {c.drawRoundRect(2f,7f,8f,19f,1.5f,1.5f,p);c.drawRoundRect(9f,4f,15f,19f,1.5f,1.5f,p);c.drawRoundRect(16f,7f,22f,19f,1.5f,1.5f,p)}
            "play" -> {val path=android.graphics.Path();path.moveTo(8f,4f);path.lineTo(20f,12f);path.lineTo(8f,20f);path.close();c.drawPath(path,p)}
            else -> {for(x in listOf(3f,14f))for(y in listOf(3f,14f))c.drawRoundRect(x,y,x+7,y+7,2f,2f,p)}
        }
        c.restore()
    }
}
