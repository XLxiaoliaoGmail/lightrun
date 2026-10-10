package cn.lightrun.app;

import android.animation.ValueAnimator;
import android.view.*;
import android.view.animation.PathInterpolator;

final class Motion {
    static final PathInterpolator EASE=new PathInterpolator(.22f,1f,.36f,1f);
    static boolean enabled(){return ValueAnimator.areAnimatorsEnabled();}
    static void enter(View view,int delay,float offset) {
        if(!enabled())return;
        view.setAlpha(0);view.setTranslationY(offset);
        view.animate().alpha(1).translationY(0).setStartDelay(delay).setDuration(360).setInterpolator(EASE).start();
    }
    static void press(View view) {
        view.setOnTouchListener((v,event)->{
            if(!enabled())return false;
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN)v.animate().scaleX(.96f).scaleY(.96f).setStartDelay(0).setDuration(100).setInterpolator(EASE).start();
            else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)v.animate().scaleX(1).scaleY(1).setStartDelay(0).setDuration(240).setInterpolator(EASE).start();
            return false;
        });
    }
}
