package cn.lightrun.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** One bar per calendar day. Combined mode uses distance height and a separate steps label. */
public final class DailyChartView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private HistorySummary summary;
    private int mode,selected;
    private float progress=1;
    private ValueAnimator animator;
    private java.util.function.Consumer<HistorySummary.Day> selection;
    public DailyChartView(Context context){super(context);setClickable(true);setFocusable(true);}
    public void setSelectionListener(java.util.function.Consumer<HistorySummary.Day> listener){selection=listener;}
    public void setData(HistorySummary data,int mode){
        summary=data;this.mode=mode;selected=data.days.size()-1;
        setMinimumWidth((int)dp(data.days.size()*38+36));requestLayout();
        setContentDescription("最近"+data.days.size()+"天，"+Format.distance(data.meters)+"公里，"+data.stepLabel()+"。左右滑动查看更多日期，点击柱子查看每日数值。");
        if(selection!=null)selection.accept(data.days.get(selected));
        if(animator!=null)animator.cancel();
        if(Motion.enabled()){animator=ValueAnimator.ofFloat(0,1);animator.setDuration(420);animator.setInterpolator(Motion.EASE);animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animator.start();}else{progress=1;invalidate();}
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private void label(Canvas c,String text,float x,float y,int color,float size,Paint.Align align){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(dp(size));paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextAlign(align);c.drawText(text,x,y,paint);}
    private String shortSteps(long value){return value>=10000?String.format(Locale.CHINA,"%.1f万",value/10000d):String.valueOf(value);}
    @Override protected void onDraw(Canvas c){
        if(summary==null)return;
        float w=getWidth(),h=getHeight(),left=dp(36),top=dp(36),bottom=h-dp(mode==0?58:40),width=w-left-dp(10),slot=width/summary.days.size();
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(0,0,w,h,dp(22),dp(22),paint);
        double maximum=1;for(HistorySummary.Day day:summary.days)maximum=Math.max(maximum,mode==1?day.steps:day.meters/1000);
        maximum=Math.ceil(maximum*1.15);
        for(int i=0;i<=3;i++){float y=bottom-(bottom-top)*i/3;paint.setColor(Color.rgb(232,236,228));paint.setStrokeWidth(dp(1));c.drawLine(left,y,w-dp(10),y,paint);label(c,mode==1?shortSteps(Math.round(maximum*i/3)):String.format(Locale.CHINA,"%.1f",maximum*i/3),left-dp(6),y+dp(3),Color.rgb(126,136,126),9,Paint.Align.RIGHT);}
        label(c,mode==1?"步":"公里",dp(10),dp(20),Color.rgb(111,119,108),10,Paint.Align.LEFT);
        for(int i=0;i<summary.days.size();i++){
            HistorySummary.Day day=summary.days.get(i);float x=left+(i+.5f)*slot,bw=Math.min(dp(21),slot*.46f);
            if(i==selected){paint.setColor(Color.rgb(238,242,232));c.drawRoundRect(x-slot*.47f,dp(26),x+slot*.47f,h-dp(5),dp(10),dp(10),paint);}
            double value=mode==1?day.steps:day.meters/1000;float bh=(float)(value/maximum)*(bottom-top)*progress;
            if(value>0){paint.setColor(i==selected?Color.rgb(22,75,56):Color.rgb(88,137,106));c.drawRoundRect(x-bw/2,bottom-bh,x+bw/2,bottom,dp(5),dp(5),paint);}
            String number=mode==1?(day.runs>0&&day.measuredRuns==0?"未记录":(day.measuredRuns<day.runs?"≥":"")+shortSteps(day.steps)):String.format(Locale.CHINA,"%.2f",value);
            label(c,number,x,Math.max(top-dp(8),bottom-bh-dp(7)),Color.rgb(22,75,56),10,Paint.Align.CENTER);
            label(c,day.date.getMonthValue()+"/"+day.date.getDayOfMonth(),x,bottom+dp(20),Color.rgb(111,119,108),10,Paint.Align.CENTER);
            if(mode==0)label(c,day.runs>0&&day.measuredRuns==0?"未记录":(day.measuredRuns<day.runs?"≥":"")+shortSteps(day.steps)+"步",x,bottom+dp(38),Color.rgb(111,119,108),9,Paint.Align.CENTER);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(summary==null)return false;
        if(event.getAction()==MotionEvent.ACTION_UP){selected=Math.max(0,Math.min(summary.days.size()-1,(int)((event.getX()-dp(36))/(getWidth()-dp(46))*summary.days.size())));invalidate();HistorySummary.Day day=summary.days.get(selected);if(selection!=null)selection.accept(day);announceForAccessibility(day.date+"，"+Format.distance(day.meters)+"公里，"+day.stepLabel());performClick();}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
