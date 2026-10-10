package cn.lightrun.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

class IconButton extends View {
    static final int GEAR=0,CLOCK=1,CLOSE=2;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape=new Path();
    private final int icon;
    protected boolean update;
    IconButton(Context context,int icon,String label){super(context);this.icon=icon;setContentDescription(label);setClickable(true);setFocusable(true);Motion.press(this);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");}
    @Override protected void onDraw(Canvas c) {
        float d=getResources().getDisplayMetrics().density,x=getWidth()/2f,y=getHeight()/2f;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(22,75,56));
        if(icon==GEAR) {
            shape.reset();for(int i=0;i<32;i++){double a=(i-.5)*Math.PI/16;float r=(i%4==1||i%4==2?12:9)*d;float px=x+(float)Math.cos(a)*r,py=y+(float)Math.sin(a)*r;if(i==0)shape.moveTo(px,py);else shape.lineTo(px,py);}shape.close();
            c.drawPath(shape,paint);paint.setColor(Color.rgb(245,243,237));c.drawCircle(x,y,4*d,paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f*d);paint.setStrokeCap(Paint.Cap.ROUND);
            if(icon==CLOCK){c.drawCircle(x,y,11*d,paint);c.drawLine(x,y-6*d,x,y,paint);c.drawLine(x,y,x+5*d,y+3*d,paint);}
            else {c.drawLine(x-6*d,y-6*d,x+6*d,y+6*d,paint);c.drawLine(x-6*d,y+6*d,x+6*d,y-6*d,paint);}
        }
        if(update){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(186,83,48));c.drawCircle(x+14*d,y-14*d,3*d,paint);}
    }
}
