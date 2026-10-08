package cn.lightrun.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Offline route overview. No map tiles, network or SDK keys. */
public final class RouteView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path(),emptyPath=new Path(),routePath=new Path();
    private final RectF bounds=new RectF();
    private List<RunSession.Point> points=Collections.emptyList();
    public RouteView(Context context) { super(context); setContentDescription("离线运动轨迹，北向上"); }
    public void setRun(RunSession run) { points=run==null?Collections.emptyList():run.points; invalidate(); }
    private float dp(float n) { return n*getResources().getDisplayMetrics().density; }
    private void text(Canvas c,String text,float x,float y,int color,float size) { paint.setColor(color); paint.setStyle(Paint.Style.FILL); paint.setTextSize(dp(size)); paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); c.drawText(text,x,y,paint); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); float w=getWidth(),h=getHeight();
        paint.setColor(Color.parseColor("#EAEDE4")); paint.setStyle(Paint.Style.FILL); c.drawRoundRect(0,0,w,h,dp(24),dp(24),paint);
        c.save(); clip.reset();bounds.set(0,0,w,h); clip.addRoundRect(bounds,dp(24),dp(24),Path.Direction.CW); c.clipPath(clip);
        paint.setColor(Color.parseColor("#DCE1D6")); paint.setStrokeWidth(dp(1));
        for(float x=dp(24);x<w;x+=dp(36)) c.drawLine(x,0,x,h,paint);
        for(float y=dp(24);y<h;y+=dp(36)) c.drawLine(0,y,w,y,paint);
        text(c,"离线轨迹",dp(18),dp(29),Color.parseColor("#5C6B5F"),12);
        text(c,"↑ 北",w-dp(48),dp(29),Color.parseColor("#5C6B5F"),12);
        if(points.isEmpty()) {
            float cx=w/2,cy=h/2; paint.setStyle(Paint.Style.STROKE); paint.setColor(Color.parseColor("#A9B7A1")); paint.setStrokeWidth(dp(3));
            emptyPath.reset();emptyPath.moveTo(cx-dp(38),cy+dp(5)); emptyPath.cubicTo(cx-dp(15),cy-dp(48),cx+dp(10),cy+dp(40),cx+dp(36),cy-dp(15)); c.drawPath(emptyPath,paint);
            paint.setStyle(Paint.Style.FILL); c.drawCircle(cx-dp(38),cy+dp(5),dp(5),paint); c.drawCircle(cx+dp(36),cy-dp(15),dp(5),paint);
            String line="每一步，都有迹可循"; paint.setTextSize(dp(14)); text(c,line,cx-paint.measureText(line)/2,cy+dp(50),Color.parseColor("#5C6B5F"),14);
            c.restore(); return;
        }
        double lat0=points.get(0).lat,lon0=points.get(0).lon,cos=Math.cos(Math.toRadians(lat0));
        double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,minY=Double.MAX_VALUE,maxY=-Double.MAX_VALUE;
        for(RunSession.Point p:points) { double x=longitudeDelta(p.lon,lon0)*111195*cos,y=(p.lat-lat0)*111195; minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y); }
        double spanX=Math.max(30,maxX-minX),spanY=Math.max(30,maxY-minY),scale=Math.min((w-dp(70))/spanX,(h-dp(100))/spanY);
        double centerX=(minX+maxX)/2,centerY=(minY+maxY)/2;
        routePath.reset(); int segment=-1; float sx=0,sy=0,ex=0,ey=0;
        for(int i=0;i<points.size();i++) {
            RunSession.Point p=points.get(i); float x=(float)(w/2+(longitudeDelta(p.lon,lon0)*111195*cos-centerX)*scale),y=(float)(h/2+dp(8)-((p.lat-lat0)*111195-centerY)*scale);
            if(p.segment!=segment) routePath.moveTo(x,y); else routePath.lineTo(x,y);
            segment=p.segment; if(i==0){sx=x;sy=y;} ex=x;ey=y;
        }
        paint.setStyle(Paint.Style.STROKE); paint.setColor(Color.parseColor("#164B38")); paint.setStrokeWidth(dp(4)); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); c.drawPath(routePath,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); c.drawCircle(sx,sy,dp(7),paint); paint.setColor(Color.parseColor("#164B38")); c.drawCircle(sx,sy,dp(4),paint);
        c.drawCircle(ex,ey,dp(8),paint); paint.setColor(Color.parseColor("#DCEF7B")); c.drawCircle(ex,ey,dp(4),paint);
        double meters=niceScale(dp(65)/scale); float length=(float)(meters*scale);
        paint.setColor(Color.parseColor("#5C6B5F")); paint.setStrokeWidth(dp(2)); c.drawLine(dp(18),h-dp(23),dp(18)+length,h-dp(23),paint);
        text(c,meters>=1000?String.format(Locale.US,"%.1f km",meters/1000):Math.round(meters)+" m",dp(18),h-dp(32),Color.parseColor("#5C6B5F"),10);
        c.restore();
    }
    private static double longitudeDelta(double lon,double origin) { return ((lon-origin+540)%360)-180; }
    private static double niceScale(double n) { double base=Math.pow(10,Math.floor(Math.log10(n))),v=n/base; return base*(v>=5?5:v>=2?2:1); }
}
