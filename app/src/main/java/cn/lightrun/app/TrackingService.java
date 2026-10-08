package cn.lightrun.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.io.IOException;

public final class TrackingService extends Service implements LocationListener {
    public static final String START="cn.lightrun.START";
    public final class LocalBinder extends Binder { public TrackingService service() { return TrackingService.this; } }
    private final LocalBinder binder=new LocalBinder();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LocationManager locations;
    private RunStore store;
    private PowerManager.WakeLock wakeLock;
    private long lastCheckpoint, lastFix, lastWakeRenew;
    private boolean foreground;
    public RunSession session;
    public String error;
    public String gpsStatus="等待 GPS 定位";
    private final Runnable ticker=new Runnable() {
        @Override public void run() {
            if(session!=null && session.active) {
                long now=SystemClock.elapsedRealtime();
                if(wakeLock!=null && now-lastWakeRenew>=300000) {wakeLock.acquire(600000);lastWakeRenew=now;}
                if(now-lastCheckpoint>=5000) checkpoint();
                if(lastFix>0 && now-lastFix>15000) gpsStatus="GPS 信号较弱，请到开阔处";
                if(foreground) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1, notification());
            }
            handler.postDelayed(this,5000);
        }
    };
    @Override public void onCreate() {
        super.onCreate(); store=new RunStore(this); locations=(LocationManager)getSystemService(LOCATION_SERVICE);
        try { session=store.restore(); } catch(IOException e) { error=e.getMessage(); }
        NotificationChannel channel=new NotificationChannel("running","跑步记录",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("锁屏时持续记录轨迹"); channel.setShowBadge(false);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        handler.post(ticker);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && START.equals(intent.getAction())) begin();
        return START_NOT_STICKY;
    }
    private void begin() {
        if(session!=null && session.active) return;
        if(error!=null) return;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) { gpsStatus="请授予精确位置权限"; stopSelf(); return; }
        if(!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) { gpsStatus="请开启手机定位"; stopSelf(); return; }
        if(session==null) session=new RunSession(System.currentTimeMillis());
        session.resume(SystemClock.elapsedRealtime()); lastFix=0; gpsStatus="等待 GPS 定位";
        try {
            startForeground(1,notification()); foreground=true;
            wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LightRun:GPS");
            wakeLock.setReferenceCounted(false); wakeLock.acquire(600000); lastWakeRenew=SystemClock.elapsedRealtime();
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,0,this,Looper.getMainLooper());
            checkpoint();
        } catch(RuntimeException e) { gpsStatus="无法启动定位，请检查权限"; pause(); }
    }
    public void pause() {
        if(session==null) return;
        session.pause(SystemClock.elapsedRealtime());
        locations.removeUpdates(this);
        if(wakeLock!=null && wakeLock.isHeld()) wakeLock.release();
        checkpoint();
        if(foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground=false; }
        stopSelf();
    }
    public boolean finish() {
        if(session==null) return false;
        pause(); session.endedAt=System.currentTimeMillis();
        try { store.save(session,SystemClock.elapsedRealtime()); session=null; error=null; return true; }
        catch(IOException e) { error="保存失败："+e.getMessage(); return false; }
    }
    public void discard() { pause(); store.discard(); session=null; error=null; }
    private void checkpoint() {
        if(session==null) return;
        try { store.checkpoint(session,SystemClock.elapsedRealtime()); lastCheckpoint=SystemClock.elapsedRealtime(); error=null; }
        catch(IOException e) { error="写入失败，请先结束跑步并检查手机存储空间"; }
    }
    @Override public void onLocationChanged(Location location) {
        if(session==null || !session.active) return;
        long now=SystemClock.elapsedRealtime();
        long age=(SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos())/1000000;
        if(age>=0 && age<=10000 && location.hasAccuracy() && location.getAccuracy()<=40) {
            lastFix=now; gpsStatus="GPS 已定位 · 精度约 "+Math.round(location.getAccuracy())+" 米";
        } else { gpsStatus="GPS 信号较弱，请到开阔处"; }
        if(session.add(location.getLatitude(),location.getLongitude(),location.hasAccuracy()?location.getAccuracy():999,location.getTime(),age)
                && now-lastCheckpoint>=5000) checkpoint();
    }
    @Override public void onProviderDisabled(String provider) { gpsStatus="定位已关闭，请重新开启"; }
    @Override public void onProviderEnabled(String provider) { gpsStatus="等待 GPS 定位"; }
    @Override public void onStatusChanged(String provider,int status,Bundle extras) { }
    private Notification notification() {
        Intent open=new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String text=session==null?"正在定位":String.format(java.util.Locale.CHINA,"%.2f 公里 · %s",session.distanceM/1000,Format.duration(session.duration(SystemClock.elapsedRealtime())));
        Notification.Builder builder=new Notification.Builder(this,"running").setSmallIcon(R.drawable.ic_run).setContentTitle("轻跑 · 正在记录")
                .setContentText(text).setContentIntent(intent).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE);
        if(Build.VERSION.SDK_INT>=31)builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        if(session!=null && session.active) { session.interrupted=true; pause(); }
        if(locations!=null) locations.removeUpdates(this);
        if(wakeLock!=null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
