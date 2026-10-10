package cn.lightrun.app;

import android.content.Context;

/** Small settings gear with a full-sized touch target and a subtle update badge. */
public final class GearButton extends IconButton {
    public GearButton(Context context){super(context,GEAR,"设置");}
    public void setUpdate(boolean available){update=available;setContentDescription(available?"设置，有新版本":"设置");invalidate();}
}
