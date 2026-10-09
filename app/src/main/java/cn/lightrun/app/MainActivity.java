package cn.lightrun.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int GREEN=Color.rgb(22,75,56),BG=Color.rgb(245,243,237),MUTED=Color.rgb(111,119,108);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TrackingService service;
    private boolean bound,visible,pendingStart;
    private String page="home";
    private LinearLayout content;
    private TextView timer,distance,pace,status,subtitle,steps,updateLink,updateStatus,voiceStatus;
    private Button download;
    private Button primary,finish;
    private RouteView route;
    private RunSession detail,export;
    private RunStore store;
    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder binder) { service=((TrackingService.LocalBinder)binder).service(); refresh(); }
        @Override public void onServiceDisconnected(ComponentName name) { service=null; refresh(); }
    };
    private final Runnable pulse=new Runnable() { @Override public void run() { refresh(); if(visible)handler.postDelayed(this,1000); } };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store=new RunStore(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(Build.VERSION.SDK_INT>=27?BG:GREEN);
        if(Build.VERSION.SDK_INT>=27)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(saved!=null) pendingStart=saved.getBoolean("pendingStart");
        showHome();
        if(saved!=null) {
            try {
                List<RunSession> runs=store.history();
                for(RunSession s:runs) {if(s.id.equals(saved.getString("exportId")))export=s;if(s.id.equals(saved.getString("detailId")))detail=s;}
                if("detail".equals(saved.getString("page"))&&detail!=null)showDetail(detail);
                else if("history".equals(saved.getString("page")))showHistory();
                else if("settings".equals(saved.getString("page")))showSettings();
            } catch(IOException e) {toast(e.getMessage());}
        }
        bound=bindService(new Intent(this,TrackingService.class),connection,BIND_AUTO_CREATE);
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putBoolean("pendingStart",pendingStart);state.putString("page",page);if(detail!=null)state.putString("detailId",detail.id);if(export!=null)state.putString("exportId",export.id); }
    @Override protected void onResume() { super.onResume(); visible=true; handler.removeCallbacks(pulse); handler.post(pulse);UpdateJobService.schedule(this);handler.postDelayed(()->{if(visible)UpdateChecker.check(this,false,(info,message)->refresh());},1000); }
    @Override protected void onPause() { visible=false; handler.removeCallbacks(pulse); super.onPause(); }
    @Override protected void onDestroy() { if(bound)unbindService(connection); super.onDestroy(); }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color,int radius) { GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;
    }
    private Button button(String title,boolean solid) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(17);b.setTextColor(solid?Color.WHITE:GREEN);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setBackground(background(solid?GREEN:Color.rgb(229,234,222),18));
        b.setMinHeight(dp(56));b.setMinimumHeight(dp(56));b.setPadding(dp(16),dp(10),dp(16),dp(10)); return b;
    }
    private void base() {
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(BG);
        shell.setOnApplyWindowInsetsListener((v,insets)-> {v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);shell.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setGravity(Gravity.CENTER_HORIZONTAL);scroll.addView(outer);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(20),dp(24),dp(24));
        int width=getResources().getDisplayMetrics().widthPixels; outer.addView(content,new LinearLayout.LayoutParams(Math.min(width,dp(600)),-2));setContentView(shell);shell.requestApplyInsets();
    }
    private void gap(int n) { Space space=new Space(this);content.addView(space,new LinearLayout.LayoutParams(1,dp(n))); }
    private void header(String title,String action,Runnable click) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);content.addView(row);
        row.addView(text(title,28,GREEN,true),new LinearLayout.LayoutParams(0,-2,1));
        Button b=button(action,false);b.setTextSize(14);row.addView(b,new LinearLayout.LayoutParams(-2,dp(48)));b.setOnClickListener(v->click.run());
    }
    private void stats() {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);content.addView(row);
        distance=metric(row,"距离 · 公里","0.00");pace=metric(row,"平均配速 · /公里","—");
        steps=text("本次步数 · —",15,GREEN,true);steps.setPadding(dp(6),dp(12),0,0);content.addView(steps);
    }
    private TextView metric(LinearLayout row,String label,String value) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(16),dp(12),dp(16));box.setBackground(background(Color.WHITE,18));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(3),0,dp(3),0);row.addView(box,params);
        box.addView(text(label,12,MUTED,false));TextView t=text(value,29,GREEN,true);box.addView(t);return t;
    }
    private void addRoute(RunSession s) {
        route=new RouteView(this);route.setRun(s);
        int h=Math.max(200,Math.min(290,Math.round(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density*.3f)));
        content.addView(route,new LinearLayout.LayoutParams(-1,dp(h)));
    }
    private void showHome() {
        page="home";detail=null;base();header("轻跑","历史记录",this::showHistory);
        gap(8);subtitle=text("打开，即刻出发。",14,MUTED,false);content.addView(subtitle);gap(24);
        status=text("●  准备好了",13,GREEN,true);content.addView(status);gap(8);
        timer=text("00:00",54,GREEN,true);content.addView(timer);content.addView(text("运动时长",12,MUTED,false));gap(20);
        stats();gap(20);
        primary=button("开跑",true);content.addView(primary,new LinearLayout.LayoutParams(-1,dp(64)));primary.setOnClickListener(v-> { if(service!=null&&service.session!=null&&service.session.active)service.pause();else requestStart();refresh(); });
        gap(10);finish=button("结束并保存",false);content.addView(finish,new LinearLayout.LayoutParams(-1,dp(56)));finish.setOnClickListener(v->finishDialog());
        gap(16);addRoute(null);
        gap(14);TextView foot=text("离线记录 · 无广告 · 数据只存本机",12,MUTED,false);foot.setGravity(Gravity.CENTER);content.addView(foot);
        gap(6);TextView help=text("使用与隐私说明",12,GREEN,false);help.setGravity(Gravity.CENTER);help.setPadding(0,dp(12),0,dp(12));help.setOnClickListener(v->help());content.addView(help);refresh();
        updateLink=text("设置与更新 · v1.1.1",13,GREEN,true);updateLink.setGravity(Gravity.CENTER);updateLink.setPadding(0,dp(12),0,dp(12));updateLink.setOnClickListener(v->showSettings());content.addView(updateLink);refresh();
    }
    private void refresh() {
        if(page.equals("settings")) {
            updateStatus.setText(UpdateChecker.preferences(this).getString("updateStatus","每天最多自动检查一次，可随时手动检查。"));
            voiceStatus.setText(service==null?"正在连接语音服务":service.voiceStatus());
            UpdateInfo info=UpdateChecker.cached(this);download.setVisibility(info!=null&&info.code>UpdateChecker.installedCode(this)?View.VISIBLE:View.GONE);
            return;
        }
        if(!page.equals("home")||timer==null)return;
        RunSession s=service==null?null:service.session;long duration=s==null?0:s.duration(SystemClock.elapsedRealtime());
        timer.setText(Format.duration(duration));distance.setText(Format.distance(s==null?0:s.distanceM));pace.setText(Format.pace(s==null?0:s.distanceM,duration));route.setRun(s);
        primary.setEnabled(service!=null);primary.setText(s==null?"开跑":s.active?"暂停":"继续跑");finish.setVisibility(s==null?View.GONE:View.VISIBLE);
        if(service!=null&&service.error!=null)status.setText(String.format(Locale.CHINA,"●  %s",service.error));
        else status.setText(s==null?"●  准备好了":s.active?"●  "+service.gpsStatus:s.interrupted?"●  记录已恢复 · 点继续跑重新定位":"●  已暂停");
        subtitle.setText(s!=null&&s.active?"专注脚下，剩下的交给轻跑。":"打开，即刻出发。");
        steps.setText("本次步数 · "+(s!=null&&s.stepsRecorded?String.format(Locale.CHINA,"%,d 步",s.steps):"—")
                +(s!=null&&service!=null&&!"正在计步".equals(service.stepStatus)?"\n"+service.stepStatus:""));
        if(updateLink!=null){UpdateInfo update=UpdateChecker.cached(this);updateLink.setText(update!=null&&update.code>UpdateChecker.installedCode(this)?"发现新版本 "+update.version+" · 查看更新":"设置与更新 · v1.1.1");}
    }
    private void requestStart() {
        if(service==null)return;
        if(service.error!=null) { toast(service.error);return; }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            pendingStart=true;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},100);return;
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked",false)) {
            pendingStart=true;getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);return;
        }
        if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED
                &&!getPreferences(MODE_PRIVATE).getBoolean("stepsAsked",false)) {
            pendingStart=true;getPreferences(MODE_PRIVATE).edit().putBoolean("stepsAsked",true).apply();
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},102);return;
        }
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this).setTitle("开启手机定位").setMessage("轻跑需要 GPS 记录轨迹，请开启定位后再次点开跑。")
                    .setPositiveButton("去开启",(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("取消",null).show();return;
        }
        pendingStart=false;startForegroundService(new Intent(this,TrackingService.class).setAction(TrackingService.START));handler.postDelayed(this::refresh,150);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==100&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            pendingStart=false;
            new AlertDialog.Builder(this).setTitle("需要精确位置").setMessage("跑步轨迹需要精确位置。请在权限设置中选择“使用期间允许”，并开启“精确位置”。")
                    .setPositiveButton("去设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("稍后",null).show();
        } else if(pendingStart) handler.postDelayed(this::requestStart,250);
    }
    private void finishDialog() {
        if(service==null||service.session==null)return;
        new AlertDialog.Builder(this).setTitle("结束这次跑步？").setMessage("轨迹与运动数据将保存在手机里。")
                .setPositiveButton("结束并保存",(d,w)-> {if(service.finish()) {toast("已保存到历史记录");showHistory();}else{toast(service.error);refresh();}})
                .setNeutralButton("放弃记录",(d,w)->new AlertDialog.Builder(this).setTitle("放弃这次记录？").setMessage("这次轨迹将被删除。")
                        .setPositiveButton("放弃",(d2,w2)->{service.discard();refresh();}).setNegativeButton("保留",null).show())
                .setNegativeButton("继续保留",null).show();
    }
    private void showHistory() {
        page="history";base();header("我的跑步","返回",this::showHome);gap(10);content.addView(text("每一段路，都值得记住。",14,MUTED,false));gap(24);
        try {
            List<RunSession> runs=store.history();
            if(runs.isEmpty()) {gap(50);content.addView(text("还没有跑步记录",22,GREEN,true));gap(10);content.addView(text("点开跑，记录你的第一段路。",14,MUTED,false));}
            SimpleDateFormat date=new SimpleDateFormat("MM月dd日  HH:mm",Locale.CHINA);
            for(RunSession s:runs) {
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(20),dp(18),dp(20));card.setBackground(background(Color.WHITE,18));
                card.addView(text(date.format(new Date(s.startedAt)),14,MUTED,false));
                TextView summary=text(Format.distance(s.distanceM)+" 公里",26,GREEN,true);summary.setPadding(0,dp(10),0,dp(8));card.addView(summary);
                card.addView(text(Format.duration(s.accumulatedMs)+"  ·  "+Format.pace(s.distanceM,s.accumulatedMs)+" /公里",14,MUTED,false));
                if(s.stepsRecorded)card.addView(text(String.format(Locale.CHINA,"%,d 步",s.steps),13,MUTED,false));
                card.setContentDescription(date.format(new Date(s.startedAt))+" 跑步记录");card.setOnClickListener(v->showDetail(s));card.setFocusable(true);content.addView(card,new LinearLayout.LayoutParams(-1,-2));gap(12);
            }
        } catch(IOException e) {content.addView(text(e.getMessage(),16,GREEN,false));}
    }
    private void showDetail(RunSession s) {
        page="detail";detail=s;base();header("跑步记录","返回",this::showHistory);gap(10);
        content.addView(text(new SimpleDateFormat("yyyy年MM月dd日 HH:mm",Locale.CHINA).format(new Date(s.startedAt)),14,MUTED,false));gap(24);
        content.addView(text(Format.duration(s.accumulatedMs),48,GREEN,true));content.addView(text("运动时长",12,MUTED,false));gap(20);stats();distance.setText(Format.distance(s.distanceM));pace.setText(Format.pace(s.distanceM,s.accumulatedMs));
        steps.setText(s.stepsRecorded?String.format(Locale.CHINA,"本次步数 · %,d 步",s.steps):"本次步数 · 未记录");
        gap(16);addRoute(s);gap(16);content.addView(text(s.points.size()+" 个轨迹点 · WGS84 坐标",12,MUTED,false));gap(20);
        Button save=button("导出 GPX 轨迹",true);content.addView(save,new LinearLayout.LayoutParams(-1,dp(56)));save.setEnabled(!s.points.isEmpty());save.setOnClickListener(v->{export=s;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/gpx+xml");
            i.putExtra(Intent.EXTRA_TITLE,"轻跑_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date(s.startedAt))+".gpx");startActivityForResult(i,200);});
        gap(12);Button delete=button("删除这次记录",false);content.addView(delete,new LinearLayout.LayoutParams(-1,dp(56)));delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("删除记录？").setMessage("删除后无法恢复。").setPositiveButton("删除",(d,w)->{store.delete(s);showHistory();}).setNegativeButton("保留",null).show());
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==200&&result==RESULT_OK&&data!=null&&data.getData()!=null&&export!=null) {
            try(OutputStream stream=getContentResolver().openOutputStream(data.getData(),"wt")) {if(stream==null)throw new IOException("无法打开导出文件");RunStore.gpx(export,stream);toast("GPX 轨迹已导出");}
            catch(IOException e) {toast("导出失败："+e.getMessage());}
        }
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("轻跑 · 简单地跑")
                .setMessage("1. 在室外开启手机定位，授予精确位置权限后点开跑。首次定位可能需要几十秒。\n\n2. 锁屏后会继续记录。OPPO 等手机请在应用电池设置中允许后台活动；不要强行停止应用。\n\n3. 暂停期间不计时、不计距离、不累计步数。定位间断超过 30 秒会分段。距离是 GPS 估算值，弱信号可能少记。\n\n4. 步数来自手机传感器，需要身体活动权限；无传感器时仍可记录轨迹。传感器延迟、暂停或中断可能少记步数。旧记录不会凭距离补算步数。\n\n5. 整公里播报使用系统离线中文语音，没有语音包时请在系统语音设置安装；可在设置中关闭播报。\n\n6. 轨迹图不含街道底图。运动记录和步数只存本机，不收集账号、不上传轨迹。只有检查更新会连接 Gitee 和 GitHub；网站会收到普通网络请求（包括 IP），不发送位置或设备标识。下载入口交给浏览器处理，不自动安装。\n\n7. 异常中断后恢复为暂停，通常最多丢失约 5 秒未保存数据。覆盖更新保留记录；卸载会删除，重要记录请先导出 GPX。")
                .setPositiveButton("知道了",null).show();
    }
    private boolean running(){return service!=null&&service.session!=null&&service.session.active;}
    private void showSettings() {
        page="settings";base();header("设置与更新","返回",this::showHome);gap(20);
        SharedPreferences prefs=UpdateChecker.preferences(this);
        Switch voice=new Switch(this);voice.setText("整公里语音播报");voice.setTextColor(GREEN);voice.setTextSize(17);voice.setChecked(prefs.getBoolean("voice",true));content.addView(voice);
        voice.setOnCheckedChangeListener((b,enabled)->{prefs.edit().putBoolean("voice",enabled).apply();if(service!=null)service.voiceSettingsChanged(enabled);});
        gap(12);voiceStatus=text("",13,MUTED,false);content.addView(voiceStatus);gap(12);
        Button preview=button("试听离线中文语音",false);content.addView(preview);preview.setOnClickListener(v->{if(running()){toast("请暂停后试听语音");return;}if(service!=null){service.previewVoice();handler.postDelayed(this::refresh,500);}});
        gap(12);Button speech=button("系统语音设置",false);content.addView(speech);speech.setOnClickListener(v->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(ActivityNotFoundException e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(ActivityNotFoundException ignored){toast("请打开手机设置，搜索文字转语音");}}});
        gap(20);Switch automatic=new Switch(this);automatic.setText("自动检查更新");automatic.setTextColor(GREEN);automatic.setTextSize(17);automatic.setChecked(prefs.getBoolean("autoUpdate",true));content.addView(automatic);
        automatic.setOnCheckedChangeListener((b,enabled)->{prefs.edit().putBoolean("autoUpdate",enabled).apply();UpdateJobService.schedule(this);if(enabled&&!running())UpdateChecker.check(this,false,(info,message)->refresh());});
        gap(10);content.addView(text("每天最多自动检查一次，系统可能延后后台检查。分别连接 Gitee 和 GitHub；不上传运动数据、不自动下载或安装。",13,MUTED,false));
        gap(12);updateStatus=text("",13,MUTED,false);content.addView(updateStatus);gap(16);
        Button check=button("立即检查更新",true);content.addView(check);check.setOnClickListener(v->{
            if(running()){toast("请结束跑步后检查更新");return;}
            if(UpdateChecker.busy()){toast("正在检查，请稍等");return;}
            UpdateChecker.check(this,true,(info,message)->{if(isFinishing()||isDestroyed())return;refresh();if(!visible||running())return;if(info!=null&&info.code>UpdateChecker.installedCode(this))showUpdate(info);else toast(message);});refresh();
        });
        gap(12);download=button("查看新版与下载入口",false);content.addView(download);download.setOnClickListener(v->{UpdateInfo info=UpdateChecker.cached(this);if(info!=null)showUpdate(info);});
        gap(20);content.addView(text("当前版本 1.1.1 · Android 8.0 及以上",13,MUTED,false));
        gap(12);Button permission=button("身体活动权限设置",false);content.addView(permission);permission.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));refresh();
    }
    private void showUpdate(UpdateInfo info) {
        if(running()){toast("请结束跑步后更新，避免中断记录");return;}
        new AlertDialog.Builder(this).setTitle("轻跑 "+info.version).setMessage(info.notes+"\n\n下载 APK 后覆盖安装，保留运动记录。请勿先卸载旧版。")
                .setPositiveButton("Gitee 下载",(d,w)->openRelease(info.giteeUrl))
                .setNeutralButton("GitHub 下载",(d,w)->openRelease(info.githubUrl)).setNegativeButton("稍后",null).show();
    }
    private void openRelease(String url){if(running())return;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("请先安装浏览器，再打开更新页面");}}
    private void toast(String value) {Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed() {if(page.equals("detail"))showHistory();else if(page.equals("history")||page.equals("settings"))showHome();else super.onBackPressed();}
}
