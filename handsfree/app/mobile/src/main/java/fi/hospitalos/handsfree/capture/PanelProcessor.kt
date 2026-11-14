package fi.hospitalos.handsfree.capture

import kotlin.math.*

/** One complete high-contrast rectangular panel, with bounded perspective and roll.
 * Geometry comes only from the connected outer border; labels/digits never influence the warp.
 * No extrapolation beyond the image, and no image retained after normalization.
 */
class PanelProcessor {
  private data class P(val x:Double,val y:Double) {
    operator fun minus(p:P)=P(x-p.x,y-p.y)
    fun length()=hypot(x,y)
  }
  private data class Line(val nx:Double,val ny:Double,val c:Double)
  fun normalize(input:PixelFrame):PixelFrame? {
    val scale=maxOf(1.0,maxOf(input.width,input.height)/1280.0)
    val w=(input.width/scale).toInt();val h=(input.height/scale).toInt()
    val dark=BooleanArray(w*h){i->input.gray[((i/w)*scale).toInt()*input.width+((i%w)*scale).toInt()]<100}
    val seen=BooleanArray(w*h);val queue=IntArray(w*h);val panels=mutableListOf<List<P>>()
    try {
      for(start in dark.indices){if(!dark[start] || seen[start])continue
        var head=0;var tail=1;queue[0]=start;seen[start]=true
        var left=w;var right=0;var top=h;var bottom=0
        while(head<tail){val p=queue[head++];val x=p%w;val y=p/w;left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y)
          fun visit(n:Int){if(dark[n] && !seen[n]){seen[n]=true;queue[tail++]=n}}
          if(x>0)visit(p-1);if(x<w-1)visit(p+1);if(y>0)visit(p-w);if(y<h-1)visit(p+w)
        }
        val pw=right-left+1;val ph=bottom-top+1
        if(pw<300 || ph<220 || tail.toDouble()/(pw*ph) !in .008..0.16)continue
        val boundary=ArrayList<P>()
        for(k in 0 until tail){val i=queue[k];val x=i%w;val y=i/w
          if(x==0 || y==0 || x==w-1 || y==h-1 || !dark[i-1] || !dark[i+1] || !dark[i-w] || !dark[i+w])boundary.add(P(x+.5,y+.5))
        }
        val corners=listOf(boundary.minBy{it.x+it.y},boundary.maxBy{it.x-it.y},boundary.maxBy{it.x+it.y},boundary.minBy{it.x-it.y})
        if(corners.toSet().size!=4)continue
        val center=P(corners.sumOf{it.x}/4,corners.sumOf{it.y}/4)
        val lines=ArrayList<Line>()
        for(i in 0..3){val a=corners[i];val b=corners[(i+1)%4];val d=b-a;val len=d.length()
          if(len<180)break
          val near=boundary.filter{p->val t=((p.x-a.x)*d.x+(p.y-a.y)*d.y)/(len*len);t in .08..0.92 && abs((p.x-a.x)*d.y-(p.y-a.y)*d.x)/len<1.6}
          if(near.size<len*.4)break
          val mx=near.sumOf{it.x}/near.size;val my=near.sumOf{it.y}/near.size
          var xx=0.0;var xy=0.0;var yy=0.0
          for(p in near){xx+=(p.x-mx).pow(2);xy+=(p.x-mx)*(p.y-my);yy+=(p.y-my).pow(2)}
          val angle=.5*atan2(2*xy,xx-yy);var nx=-sin(angle);var ny=cos(angle)
          if(nx*(center.x-mx)+ny*(center.y-my)<0){nx=-nx;ny=-ny}
          // Pixel centres are half a pixel inside the dark outer edge.
          val c=nx*mx+ny*my-.5
          if(sqrt(near.sumOf{(nx*it.x+ny*it.y-(c+.5)).pow(2)}/near.size)>.8)break
          lines.add(Line(nx,ny,c))
        }
        if(lines.size!=4)continue
        fun cross(a:Line,b:Line):P? {val det=a.nx*b.ny-b.nx*a.ny;if(abs(det)<.3)return null;return P((a.c*b.ny-b.c*a.ny)/det,(a.nx*b.c-b.nx*a.c)/det)}
        val quad=(0..3).map{cross(lines[(it+3)%4],lines[it])?:return@map null}
        if(quad.any{it==null})continue
        val q=quad.filterNotNull();val lengths=(0..3).map{(q[(it+1)%4]-q[it]).length()}
        if(minOf(lengths[0],lengths[2])<300 || minOf(lengths[1],lengths[3])<220)continue
        if(maxOf(lengths[0],lengths[2])/minOf(lengths[0],lengths[2])>1.6 || maxOf(lengths[1],lengths[3])/minOf(lengths[1],lengths[3])>1.6)continue
        val ratio=(lengths[0]+lengths[2])/(lengths[1]+lengths[3])
        if(ratio !in 1.0..1.85 || abs(atan2(q[1].y-q[0].y,q[1].x-q[0].x))>Math.toRadians(25.0))continue
        // Require continuous complete outer edges; a cut border cannot define a panel.
        if((0..3).any{i->val a=q[i];val b=q[(i+1)%4];val l=lines[i];var hits=0
          for(k in 4..95){val t=k/100.0;val x=(a.x+(b.x-a.x)*t+l.nx*1.5).toInt();val y=(a.y+(b.y-a.y)*t+l.ny*1.5).toInt();if(x in 0 until w && y in 0 until h && dark[y*w+x])hits++};hits<88})continue
        panels.add(q.map{P(it.x*scale,it.y*scale)})
      }
    } finally {queue.fill(0);seen.fill(false);dark.fill(false)}
    if(panels.size!=1)return null
    val q=panels.single()
    // Projective mapping from unit square to detected quadrilateral.
    val dx1=q[1].x-q[2].x;val dx2=q[3].x-q[2].x;val dx3=q[0].x-q[1].x+q[2].x-q[3].x
    val dy1=q[1].y-q[2].y;val dy2=q[3].y-q[2].y;val dy3=q[0].y-q[1].y+q[2].y-q[3].y
    val den=dx1*dy2-dx2*dy1;if(abs(den)<1)return null
    val g=(dx3*dy2-dx2*dy3)/den;val j=(dx1*dy3-dx3*dy1)/den
    val a=q[1].x-q[0].x+g*q[1].x;val b=q[3].x-q[0].x+j*q[3].x
    val d=q[1].y-q[0].y+g*q[1].y;val e=q[3].y-q[0].y+j*q[3].y
    val output=IntArray(480*360)
    for(i in output.indices){val u=(i%480+.5-12)/456;val v=(i/480+.5-12)/336;val divisor=g*u+j*v+1
      var x=(a*u+b*v+q[0].x)/divisor-.5;var y=(d*u+e*v+q[0].y)/divisor-.5
      if(!x.isFinite() || !y.isFinite() || x<-.00001 || y<-.00001 || x>input.width-1+.00001 || y>input.height-1+.00001){output.fill(0);return null}
      x=x.coerceIn(0.0,input.width-1.0);y=y.coerceIn(0.0,input.height-1.0)
      val x0=floor(x).toInt();val y0=floor(y).toInt();val x1=minOf(x0+1,input.width-1);val y1=minOf(y0+1,input.height-1);val fx=x-x0;val fy=y-y0
      output[i]=((input.gray[y0*input.width+x0]*(1-fx)+input.gray[y0*input.width+x1]*fx)*(1-fy)+(input.gray[y1*input.width+x0]*(1-fx)+input.gray[y1*input.width+x1]*fx)*fy).roundToInt()
    }
    // Global exposure correction only. Never threshold away uncertain strokes or repair a digit.
    val histogram=IntArray(256);output.forEach{histogram[it.coerceIn(0,255)]++}
    fun percentile(f:Double):Int {var n=0;for(i in histogram.indices){n+=histogram[i];if(n>=output.size*f)return i};return 255}
    val lo=percentile(.02);val hi=percentile(.95)
    if(hi-lo<110){output.fill(0);return null}
    for(i in output.indices)output[i]=(25+(output[i]-lo)*210.0/(hi-lo)).roundToInt().coerceIn(0,255)
    return PixelFrame(480,360,output)
  }
}
