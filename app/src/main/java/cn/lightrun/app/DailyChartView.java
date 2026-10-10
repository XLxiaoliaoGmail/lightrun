package cn.lightrun.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** All calendar days fit the viewport. Distance and steps have independent, labelled scales. */
public final class DailyChartView extends View {
    static final int DISTANCE=Color.rgb(22,75,56),STEPS=Color.rgb(166,125,54);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private HistorySummary summary;
    private int mode,selected;
    private float progress=1;
    private ValueAnimator animator;
    private java.util.function.Consumer<HistorySummary.Day> selection;
    public DailyChartView(Context context){super(context);setClickable(true);setFocusable(true);}
    public void setSelectionListener(java.util.function.Consumer<HistorySummary.Day> listener){selection=listener;}
    public void setData(HistorySummary data,int mode){
        summary=data;this.mode=mode;selected=data.days.size()-1;setMinimumWidth(0);
        setContentDescription("最近"+data.days.size()+"天，"+Format.distance(data.meters)+"公里，"+data.stepLabel()+"。"+(mode==0?"每天两根柱子，绿色距离、金色步数，各自刻度。":"")+"全部日期同屏，点击日期查看每日数值。");
        if(selection!=null)selection.accept(data.days.get(selected));
        if(animator!=null)animator.cancel();
        if(Motion.enabled()){animator=ValueAnimator.ofFloat(0,1);animator.setDuration(420);animator.setInterpolator(Motion.EASE);animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animator.start();}else{progress=1;invalidate();}
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private void label(Canvas c,String text,float x,float y,int color,float size,Paint.Align align){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(dp(size));paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextAlign(align);c.drawText(text,x,y,paint);}
    private String shortSteps(long value){return value>=10000?String.format(Locale.CHINA,"%.1f万",value/10000d):String.valueOf(value);}
    private double scale(boolean steps){double max=0;for(HistorySummary.Day d:summary.days)max=Math.max(max,steps?d.steps:d.meters/1000);if(max==0)return steps?1000:1;double magnitude=Math.pow(10,Math.floor(Math.log10(max)));return Math.ceil(max*1.1/magnitude)*magnitude;}
    private float left(){return dp(40);}
    private float right(){return getWidth()-dp(mode==0?40:12);}
    private void bar(Canvas c,float center,float width,float bottom,float height,int color){if(height<=0)return;paint.setColor(color);paint.setStyle(Paint.Style.FILL);float radius=Math.min(dp(4),width/2);c.drawRoundRect(center-width/2,bottom-height,center+width/2,bottom,radius,radius,paint);}
    @Override protected void onDraw(Canvas c){
        if(summary==null||getWidth()<=left()+dp(50))return;
        float w=getWidth(),h=getHeight(),top=dp(44),bottom=h-dp(40),left=left(),right=right(),slot=(right-left)/summary.days.size();
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(0,0,w,h,dp(22),dp(22),paint);
        boolean distance=mode!=1,steps=mode!=2;double distanceMax=scale(false),stepsMax=scale(true);
        if(distance)label(c,"● 距离 · 公里",dp(12),dp(23),DISTANCE,11,Paint.Align.LEFT);
        if(steps)label(c,"● 步数 · 步",mode==0?w-dp(12):dp(12),dp(23),STEPS,11,mode==0?Paint.Align.RIGHT:Paint.Align.LEFT);
        for(int i=0;i<=3;i++){
            float y=bottom-(bottom-top)*i/3;paint.setColor(Color.rgb(232,236,228));paint.setStrokeWidth(dp(1));c.drawLine(left,y,right,y,paint);
            if(distance)label(c,String.format(Locale.CHINA,"%.1f",distanceMax*i/3),left-dp(5),y+dp(3),DISTANCE,9,Paint.Align.RIGHT);
            if(steps)label(c,shortSteps(Math.round(stepsMax*i/3)),mode==0?right+dp(5):left-dp(5),y+dp(3),STEPS,9,mode==0?Paint.Align.LEFT:Paint.Align.RIGHT);
        }
        for(int i=0;i<summary.days.size();i++){
            HistorySummary.Day day=summary.days.get(i);float x=left+(i+.5f)*slot;
            if(i==selected){paint.setColor(Color.rgb(237,240,230));c.drawRoundRect(x-slot*.48f,top-dp(5),x+slot*.48f,bottom+dp(3),Math.min(dp(6),slot/4),Math.min(dp(6),slot/4),paint);}
            float bw=Math.min(dp(mode==0?13:21),slot*(mode==0?.30f:.58f)),offset=mode==0?slot*.19f:0;
            float dh=(float)(day.meters/1000/distanceMax)*(bottom-top)*progress,sh=(float)(day.steps/stepsMax)*(bottom-top)*progress;
            if(distance)bar(c,x-offset,bw,bottom,dh,DISTANCE);
            if(steps)bar(c,x+offset,bw,bottom,sh,STEPS);
            if(summary.days.size()==7&&mode!=0){String number=mode==1?(day.runs>0&&day.measuredRuns==0?"未记录":(day.measuredRuns<day.runs?"≥":"")+shortSteps(day.steps)):String.format(Locale.CHINA,"%.2f",day.meters/1000);label(c,number,x,Math.max(top-dp(6),bottom-(mode==1?sh:dh)-dp(6)),mode==1?STEPS:DISTANCE,10,Paint.Align.CENTER);}
            boolean date=summary.days.size()==7||i==0||i==summary.days.size()-1||i%5==0;
            if(date)label(c,day.date.getMonthValue()+"/"+day.date.getDayOfMonth(),x,bottom+dp(20),Color.rgb(111,119,108),summary.days.size()==7?10:9,Paint.Align.CENTER);
        }
    }
    private void select(int index){selected=Math.max(0,Math.min(summary.days.size()-1,index));invalidate();HistorySummary.Day day=summary.days.get(selected);if(selection!=null)selection.accept(day);announceForAccessibility(day.date+"，"+Format.distance(day.meters)+"公里，"+day.stepLabel());}
    @Override public boolean onTouchEvent(MotionEvent event){if(summary==null)return false;if(event.getAction()==MotionEvent.ACTION_UP){select((int)((event.getX()-left())/(right()-left())*summary.days.size()));performClick();}return true;}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(summary!=null&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){select(selected+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1));return true;}return super.onKeyDown(key,event);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
