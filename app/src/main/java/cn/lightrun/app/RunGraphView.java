package cn.lightrun.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Lightweight local graphs of actual saved measurements; gaps stay disconnected. */
public final class RunGraphView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RunAnalysis analysis;
    private final RunSession run;
    private final int kind;
    public RunGraphView(Context context,RunSession run,RunAnalysis analysis,int kind){super(context);this.run=run;this.analysis=analysis;this.kind=kind;setContentDescription(kind==2?"运动与暂停时长，运动"+Format.duration(run.accumulatedMs)+"，暂停或中断"+Format.duration(analysis.inactiveMs):kind==0?"定位点估算速度变化，峰值"+String.format(Locale.CHINA,"%.1f公里每小时",analysis.peakSpeed):"GPS精度变化，平均"+Math.round(analysis.meanAccuracy)+"米");}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private void label(Canvas c,String text,float x,float y,int color,float size){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(dp(size));paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));c.drawText(text,x,y,paint);}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();int green=Color.rgb(22,75,56),muted=Color.rgb(111,119,108),accent=kind==1?Color.rgb(170,113,58):green;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawRoundRect(0,0,w,h,dp(22),dp(22),paint);
        label(c,kind==0?"速度变化":kind==1?"GPS 精度":"运动与暂停",dp(16),dp(28),green,15);
        if(kind==2){
            float x=dp(64),y=h/2+dp(10),r=Math.min(dp(35),h*.28f);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(11));paint.setColor(Color.rgb(226,232,218));c.drawCircle(x,y,r,paint);
            float fraction=analysis.wallMs>0?(float)Math.min(1,run.accumulatedMs/(double)analysis.wallMs):0;
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(green);c.drawArc(x-r,y-r,x+r,y+r,-90,360*fraction,false,paint);
            paint.setStrokeCap(Paint.Cap.BUTT);label(c,"运动 "+Format.duration(run.accumulatedMs),dp(120),y-dp(4),green,14);
            label(c,"暂停 / 中断 "+Format.duration(analysis.inactiveMs),dp(120),y+dp(24),muted,11);return;
        }
        float left=dp(36),right=w-dp(16),top=dp(53),bottom=h-dp(38);
        double maximum=kind==0?Math.max(1,analysis.peakSpeed*1.15):Math.max(10,analysis.worstAccuracy*1.15);
        for(int i=0;i<=2;i++){float y=bottom-(bottom-top)*i/2;paint.setColor(Color.rgb(230,235,226));paint.setStrokeWidth(dp(1));c.drawLine(left,y,right,y,paint);label(c,String.format(Locale.CHINA,"%.0f",maximum*i/2),dp(9),y+dp(4),muted,9);}
        if(analysis.samples.isEmpty()){label(c,"还没有足够的连续定位点",left,top+dp(35),muted,12);return;}
        path.reset();int previous=-1;float lastX=-1000;double range=Math.max(1,analysis.gpsMeters);
        for(int i=0;i<analysis.samples.size();i++){
            RunAnalysis.Sample sample=analysis.samples.get(i);double value=kind==0?sample.speed:sample.accuracy;
            if(!Double.isFinite(value)||value<0)continue;
            float x=left+(float)(sample.meters/range)*(right-left),y=bottom-(float)(value/maximum)*(bottom-top);
            if(sample.segment!=previous){path.moveTo(x,y);previous=sample.segment;lastX=x;}
            else if(x-lastX>=dp(1)||i==analysis.samples.size()-1){path.lineTo(x,y);lastX=x;}
        }
        paint.setStyle(Paint.Style.STROKE);paint.setColor(accent);paint.setStrokeWidth(dp(2.5f));paint.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,paint);
        label(c,kind==0?"km/h · 连续定位点估算":"米 · 数值越小，精度越高",left,h-dp(12),muted,10);
        String end=String.format(Locale.CHINA,"%.2f km",analysis.gpsMeters/1000);paint.setTextSize(dp(10));label(c,end,right-paint.measureText(end),bottom+dp(16),muted,10);
    }
}
