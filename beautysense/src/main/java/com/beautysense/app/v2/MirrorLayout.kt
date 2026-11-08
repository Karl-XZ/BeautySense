package com.beautysense.app.v2
import kotlin.math.min
data class MirrorRect(val x:Float,val y:Float,val width:Float,val height:Float)
data class MirrorLayout(val camera:MirrorRect,val panel:MirrorRect,val oval:MirrorRect)
fun mirrorLayout(width:Float,height:Float,wide:Boolean=width>height,collapsed:Boolean=false):MirrorLayout {
 val camera=MirrorRect(0f,0f,if(wide) width*.60f else width,if(wide) height else height*.57f)
 val panel=if(wide) MirrorRect(camera.width,0f,width-camera.width,height) else MirrorRect(0f,camera.height,width,height-camera.height)
 val h=min(camera.height*.84f,camera.width*.70f*1.32f);val w=h/1.32f
 val oval=MirrorRect((camera.width-w)/2,(camera.height-h)/2,w,h)
 if(collapsed) return MirrorLayout(MirrorRect(0f,0f,width,height),MirrorRect(if(wide) width-140f else 0f,height-56f,if(wide) 140f else width,56f),oval)
 return MirrorLayout(camera,panel,oval)
}
fun faceInGuide(points:List<Pair<Float,Float>>,imageWidth:Int,imageHeight:Int,layout:MirrorLayout):Boolean {
 if(points.size!=478 || imageWidth<=0 || imageHeight<=0) return false
 val c=layout.camera;val o=layout.oval;val scale=min(c.width/imageWidth,c.height/imageHeight)
 val dx=c.x+(c.width-imageWidth*scale)/2;val dy=c.y+(c.height-imageHeight*scale)/2
 var top=Float.POSITIVE_INFINITY;var bottom=Float.NEGATIVE_INFINITY
 for((px,py) in points) {
  val x=dx+(1-px)*imageWidth*scale;val y=dy+py*imageHeight*scale
  val nx=(x-o.x-o.width/2)/(o.width/2);val ny=(y-o.y-o.height/2)/(o.height/2)
  if(!x.isFinite() || !y.isFinite() || nx*nx+ny*ny>1) return false
  top=min(top,y);bottom=kotlin.math.max(bottom,y)
 }
 return bottom-top>=o.height*.50f
}
