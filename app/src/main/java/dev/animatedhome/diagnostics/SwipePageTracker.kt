package dev.animatedhome.diagnostics

import kotlin.math.abs

/** A user-anchored gesture estimate, never launcher evidence. No page is assumed on startup. */
class SwipePageTracker {
    var page:Int?=null; private set
    var pageCount=3; private set
    var gestures=0; private set
    var swipes=0; private set
    var dragging=false; private set
    private var down=false
    private var x=0f
    private var y=0f
    private var start=0L
    private var width=1f
    private var slop=1f
    private var vertical=false

    fun configure(count:Int) { val next=count.coerceIn(2,10);if(next!=pageCount){pageCount=next;reset()} }
    fun reset(){page=null;cancel()}
    fun anchor(index:Int):Boolean {
        if(index !in 0 until pageCount)return false
        page=index;cancel();return true
    }
    fun begin(px:Float,py:Float,time:Long,screenWidth:Float,touchSlop:Float) {
        cancel()
        if(page==null || !px.isFinite() || !py.isFinite() || !screenWidth.isFinite() || screenWidth<=0)return
        // Avoid interpreting system back gestures at either screen edge as page changes.
        if(px<screenWidth*.04f || px>screenWidth*.96f)return
        x=px;y=py;start=time;width=screenWidth;slop=touchSlop.coerceAtLeast(1f);down=true;gestures++
    }
    fun move(px:Float,py:Float) {
        if(!down || !px.isFinite() || !py.isFinite())return
        val dx=abs(px-x);val dy=abs(py-y)
        if(!dragging && dy>slop && dy>dx*1.15f)vertical=true
        if(!vertical && dx>slop && dx>dy*1.3f)dragging=true
    }
    fun finish(px:Float,py:Float,time:Long):Boolean {
        if(!down)return false
        move(px,py)
        val dx=px-x;val dt=time-start
        val distance=abs(dx);val velocity=if(dt>0)distance*1000f/dt else 0f
        val crossed=dragging && !vertical && dt in 1..1200 &&
            (distance>=width*.22f || (distance>=width*.08f && velocity>=700f))
        val previous=page
        if(crossed && previous!=null){page=(previous+if(dx<0)1 else -1).coerceIn(0,pageCount-1);if(page!=previous)swipes++}
        cancel();return page!=previous
    }
    fun cancel(){down=false;dragging=false;vertical=false}
    fun current():Offset?=if(dragging)null else page?.let {at(it)}
    fun at(index:Int):Offset?=if(index !in 0 until pageCount)null else Offset(index.toFloat()/(pageCount-1),.5f,1f/(pageCount-1),0f,0,0)
}
