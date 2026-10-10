package cn.lightrun.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;

final class ToggleButton extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;
    private float position;
    private ValueAnimator animator;
    private final java.util.function.Consumer<Boolean> changed;
    ToggleButton(Context context,String label,boolean value,java.util.function.Consumer<Boolean> changed){super(context);this.changed=changed;checked=value;position=value?1:0;setContentDescription(label);setClickable(true);setFocusable(true);setOnClickListener(v->toggle());}
    void toggle(){checked=!checked;if(animator!=null)animator.cancel();if(Motion.enabled()){animator=ValueAnimator.ofFloat(position,checked?1:0);animator.setDuration(220);animator.setInterpolator(Motion.EASE);animator.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});animator.start();}else{position=checked?1:0;invalidate();}changed.accept(checked);sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    @Override protected void onDraw(Canvas canvas){float d=getResources().getDisplayMetrics().density,h=30*d,w=50*d,x=(getWidth()-w)/2,y=(getHeight()-h)/2;paint.setColor(checked?Color.rgb(22,75,56):Color.rgb(197,204,193));canvas.drawRoundRect(x,y,x+w,y+h,h/2,h/2,paint);paint.setColor(Color.WHITE);canvas.drawCircle(x+h/2+position*(w-h),y+h/2,12*d,paint);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Switch");info.setCheckable(true);info.setChecked(checked);}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
