package cn.lightrun.app;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** All app-owned windows use this card, including confirmations, details and download progress. */
final class StyledDialog extends Dialog {
    final LinearLayout body,actions;
    private final Activity activity;
    private final LinearLayout card;
    private final boolean large;
    private boolean closing;
    StyledDialog(Activity activity,String title,boolean large) {
        super(activity);this.activity=activity;this.large=large;requestWindowFeature(Window.FEATURE_NO_TITLE);
        card=new LinearLayout(activity){@Override protected void onMeasure(int width,int height){
            int limit=Math.round(availableHeight()*(large?.9f:.86f));
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,large?MeasureSpec.EXACTLY:MeasureSpec.AT_MOST));
        }};
        card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(20),dp(14),dp(20),dp(20));card.setBackground(shape(Color.rgb(245,243,237),28));
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);card.addView(header);
        TextView heading=new TextView(activity);heading.setText(title);heading.setTextColor(Color.rgb(22,75,56));heading.setTextSize(23);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        IconButton close=new IconButton(activity,IconButton.CLOSE,"关闭");header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(v->dismiss());
        ScrollView scroll=new ScrollView(activity);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(10),0,dp(12));scroll.addView(body);
        card.addView(scroll,new LinearLayout.LayoutParams(-1,large?0:-2,1));
        actions=new LinearLayout(activity);actions.setOrientation(LinearLayout.VERTICAL);card.addView(actions);
        setContentView(card);setCanceledOnTouchOutside(true);
        Window window=getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.28f);window.setGravity(Gravity.CENTER);WindowManager.LayoutParams p=window.getAttributes();p.windowAnimations=0;window.setAttributes(p);}
    }
    int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private int availableHeight(){Rect r=new Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(r);return r.height()>0?r.height():activity.getResources().getDisplayMetrics().heightPixels;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    void message(String message){TextView text=new TextView(activity);text.setText(message);text.setTextColor(Color.rgb(91,108,98));text.setTextSize(15);text.setLineSpacing(dp(4),1);body.addView(text);}
    Button action(String title,boolean solid,Runnable click){
        Button b=new Button(activity);b.setText(title);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(solid?Color.WHITE:Color.rgb(22,75,56));b.setBackground(shape(solid?Color.rgb(22,75,56):Color.rgb(229,234,222),18));b.setStateListAnimator(null);b.setMinHeight(dp(48));b.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);actions.addView(b,p);Motion.press(b);b.setOnClickListener(v->{dismiss();if(click!=null)click.run();});return b;
    }
    @Override public void show(){
        closing=false;super.show();Window window=getWindow();if(window!=null){int width=activity.getResources().getDisplayMetrics().widthPixels;window.setLayout(Math.round(width*.9f),large?Math.round(availableHeight()*.9f):WindowManager.LayoutParams.WRAP_CONTENT);}
        if(Motion.enabled()){card.setAlpha(0);card.setScaleX(.96f);card.setScaleY(.96f);card.setTranslationY(dp(12));card.animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setDuration(300).setInterpolator(Motion.EASE).start();}
    }
    @Override public void dismiss(){
        if(closing)return;
        if(!isShowing()||activity.isFinishing()||activity.isDestroyed()||!Motion.enabled()){closeNow();return;}
        closing=true;card.animate().cancel();card.animate().alpha(0).scaleX(.97f).scaleY(.97f).translationY(dp(10)).setDuration(180).setInterpolator(Motion.EASE).withEndAction(()->StyledDialog.super.dismiss()).start();
    }
    void closeNow(){closing=true;card.animate().cancel();super.dismiss();}
}
