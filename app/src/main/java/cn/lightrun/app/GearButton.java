package cn.lightrun.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small settings gear with a full-sized touch target and a subtle update badge. */
public final class GearButton extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path gear=new Path();
    private boolean update;
    public GearButton(Context context){super(context);setContentDescription("设置");setClickable(true);setFocusable(true);}
    public void setUpdate(boolean available){update=available;setContentDescription(available?"设置，有新版本":"设置");invalidate();}
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float scale=getResources().getDisplayMetrics().density,cx=getWidth()/2f,cy=getHeight()/2f;
        gear.reset();for(int i=0;i<32;i++){double angle=(i-.5)*Math.PI/16;float r=(i%4==1||i%4==2?12:9)*scale;float x=cx+(float)Math.cos(angle)*r,y=cy+(float)Math.sin(angle)*r;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();
        paint.setColor(Color.rgb(22,75,56));canvas.drawPath(gear,paint);paint.setColor(Color.rgb(245,243,237));canvas.drawCircle(cx,cy,4*scale,paint);
        if(update){paint.setColor(Color.rgb(186,83,48));canvas.drawCircle(cx+14*scale,cy-14*scale,3*scale,paint);}
    }
}
