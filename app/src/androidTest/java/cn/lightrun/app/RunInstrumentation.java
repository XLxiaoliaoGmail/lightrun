package cn.lightrun.app;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import android.widget.Button;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Exercises real Android service, GPS callbacks, wake lock, persistence and GPX. Test APK only. */
public final class RunInstrumentation extends Instrumentation {
    private int checks;
    private TrackingService service;
    private MainActivity activity;
    private LocationManager locations;
    private ServiceConnection connection;
    private void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
    @Override public void onStart() {
        new Thread(()-> {
            Bundle result=new Bundle(); int code=Activity.RESULT_OK;
            try { tests();result.putString("stream","\nPASS: "+checks+" device assertions (API "+Build.VERSION.SDK_INT+")\n"); }
            catch(Throwable t) {code=Activity.RESULT_CANCELED;StringWriter w=new StringWriter();t.printStackTrace(new PrintWriter(w));result.putString("stream","\nFAIL: "+w);}
            finally {
                shell("input keyevent 224");
                if(service!=null)runOnMainSync(()->service.discard());
                if(locations!=null)try{locations.removeTestProvider(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}
                if(connection!=null)getTargetContext().unbindService(connection);
                if(activity!=null)runOnMainSync(()->activity.finish());
            }
            finish(code,result);
        },"device-tests").start();
    }
    private void tests() throws Exception {
        Context context=getTargetContext();
        UpdateChecker.preferences(context).edit().putBoolean("autoUpdate",false).putBoolean("voice",false).apply();
        updateTests();
        Intent intent=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(MainActivity)startActivitySync(intent);waitForIdleSync();
        speechTests();
        CountDownLatch bound=new CountDownLatch(1);
        connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=((TrackingService.LocalBinder)b).service();bound.countDown();}public void onServiceDisconnected(ComponentName n){}};
        context.bindService(new Intent(context,TrackingService.class),connection,Context.BIND_AUTO_CREATE);
        check(bound.await(10,TimeUnit.SECONDS),"service binding");
        runOnMainSync(()->service.discard());
        List<String> permissions=Arrays.asList(context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions);
        check(permissions.contains("android.permission.INTERNET")&&permissions.contains("android.permission.ACTIVITY_RECOGNITION"),"update and step permissions declared");
        check(!permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES")&&!permissions.contains("android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION"),"no install or silent update permission");
        locations=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        locations.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,3,1);
        locations.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true);
        runOnMainSync(()->context.startForegroundService(new Intent(context,TrackingService.class).setAction(TrackingService.START)));
        Thread.sleep(700);waitForIdleSync();
        check(service.session!=null&&service.session.active,"start via foreground service");
        check(((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).getActiveNotifications().length>0,"foreground notification");
        fix(31,121,5);fix(31.00005,121,5);fix(31.00010,121,5);
        check(service.session.points.size()>=3,"real GPS callback collection");
        check(service.session.distanceM>9&&service.session.distanceM<15,"distance between GPS fixes");
        int count=service.session.points.size();double distance=service.session.distanceM;
        fix(32,122,5);check(service.session.points.size()==count,"GPS jump filtered");
        fix(31.00015,121,100);check(service.session.points.size()==count,"inaccurate GPS filtered");
        shell("input keyevent 223");fix(31.00015,121,5);
        check(service.session.points.size()>count,"GPS continues with screen off");
        shell("input keyevent 224");shell("wm dismiss-keyguard");
        runOnMainSync(()->service.pause());
        service.session.steps=127;service.session.stepsRecorded=true;service.session.announcedKilometer=3;
        new RunStore(context).checkpoint(service.session,SystemClock.elapsedRealtime());
        long elapsed=service.session.duration(SystemClock.elapsedRealtime());
        Thread.sleep(1200);check(service.session.duration(SystemClock.elapsedRealtime())==elapsed,"paused timer frozen");
        int pausedPoints=service.session.points.size();fix(31.001,121,5);check(service.session.points.size()==pausedPoints,"paused GPS stopped");
        RunSession restored=new RunStore(context).restore();check(restored!=null&&!restored.active,"paused checkpoint restore");
        check(restored.points.size()==pausedPoints&&restored.accumulatedMs==elapsed,"checkpoint preserves points and duration");
        check(restored.stepsRecorded&&restored.steps==127&&restored.announcedKilometer==3,"checkpoint preserves steps and voice milestone");
        // Returning to foreground is necessary for Android 14+ location FGS launch.
        context.startActivity(intent);Thread.sleep(600);
        runOnMainSync(()->context.startForegroundService(new Intent(context,TrackingService.class).setAction(TrackingService.START)));
        Thread.sleep(500);double before=service.session.distanceM;
        fix(31.001,121,5);check(Math.abs(service.session.distanceM-before)<.01,"resume does not bridge paused movement");
        check(service.session.points.get(service.session.points.size()-1).segment==1,"new GPX segment after resume");
        new RunStore(context).checkpoint(service.session,SystemClock.elapsedRealtime());
        restored=new RunStore(context).restore();check(restored.interrupted&&!restored.active,"active checkpoint becomes paused on recovery");
        final boolean[] saved={false};String id=service.session.id;
        runOnMainSync(()->saved[0]=service.finish());check(saved[0]&&service.session==null,"finish saves and clears active session");
        RunStore store=new RunStore(context);List<RunSession> history=store.history();RunSession savedRun=null;
        for(RunSession s:history)if(s.id.equals(id))savedRun=s;
        check(savedRun!=null&&savedRun.endedAt>0,"history save");check(store.restore()==null,"saved current removed");
        check(savedRun.stepsRecorded&&savedRun.steps==127,"history preserves steps");
        ByteArrayOutputStream gpx=new ByteArrayOutputStream();RunStore.gpx(savedRun,gpx);
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        org.w3c.dom.Document xml=factory.newDocumentBuilder().parse(new ByteArrayInputStream(gpx.toByteArray()));
        check(xml.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1","trkpt").getLength()==savedRun.points.size(),"valid GPX points");
        check(xml.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1","trkseg").getLength()==2,"GPX pause segments");
        store.delete(savedRun);check(store.history().size()==history.size()-1,"history deletion");
        File legacy=new File(context.getFilesDir(),"runs/legacy-v1.json");
        String old="{\"version\":1,\"id\":\"legacy-v1\",\"startedAt\":1,\"endedAt\":2,\"durationMs\":1000,\"distanceM\":100,\"active\":false,\"points\":[]}";
        try(OutputStream output=new FileOutputStream(legacy)){output.write(old.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        RunSession oldRun=null;for(RunSession r:store.history())if("legacy-v1".equals(r.id))oldRun=r;
        check(oldRun!=null&&!oldRun.stepsRecorded&&oldRun.steps==0,"version 1 record loads without invented steps");store.delete(oldRun);
        check(VoiceCoach.kilometer(2,2000,720000).equals("已跑2公里，用时12分0秒，平均配速6分0秒每公里。"),"Chinese kilometer speech uses average pace");
        check(distance>0,"nonzero run distance");
    }
    private String manifest(int code,String name,String sha) throws Exception {
        return new org.json.JSONObject().put("schema",1).put("applicationId","cn.lightrun.app").put("versionCode",code).put("versionName",name)
                .put("notes","测试更新").put("sha256",sha).put("giteeUrl","https://gitee.com/XLxiaoliao/lightrun/releases/tag/v"+name)
                .put("githubUrl","https://github.com/XLxiaoliaoGmail/lightrun/releases/tag/v"+name).toString();
    }
    private void speechTests() throws Exception {
        String original=android.provider.Settings.Secure.getString(getTargetContext().getContentResolver(),"tts_default_synth");
        shell("settings put secure tts_default_synth cn.lightrun.app.test");
        CountDownLatch synthesized=new CountDownLatch(1);String[] received={"",""};
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){received[0]=i.getStringExtra("voice");received[1]=i.getStringExtra("text");synthesized.countDown();}};
        if(Build.VERSION.SDK_INT>=33)getTargetContext().registerReceiver(receiver,new IntentFilter("cn.lightrun.TEST_SYNTHESIZE"),Context.RECEIVER_EXPORTED);
        else getTargetContext().registerReceiver(receiver,new IntentFilter("cn.lightrun.TEST_SYNTHESIZE"));
        VoiceCoach[] coach={null};
        try {
            runOnMainSync(()->{coach[0]=new VoiceCoach(getTargetContext());coach[0].speak(VoiceCoach.kilometer(1,1000,360000));});
            check(synthesized.await(12,TimeUnit.SECONDS),"Android TTS synthesis completes: "+coach[0].status);
            check("test-zh-offline".equals(received[0]),"TTS chooses offline voice even when online voice has higher quality");
            check(received[1].contains("已跑1公里"),"kilometer text delivered to Android TTS service");
        } finally {
            runOnMainSync(()->{if(coach[0]!=null)coach[0].close();});
            getTargetContext().unregisterReceiver(receiver);
            shell(original==null?"settings delete secure tts_default_synth":"settings put secure tts_default_synth "+original);
        }
    }
    private void invalid(String json,String label){try{UpdateInfo.parse(json);check(false,label);}catch(Exception expected){check(true,label);}}
    private void updateTests() throws Exception {
        String hash=new String(new char[64]).replace('\0','a'),older=manifest(2,"1.1.0",hash),newer=manifest(3,"1.2.0",hash);
        UpdateInfo g=UpdateInfo.parse(older),h=UpdateInfo.parse(newer);
        check(g.code==2&&g.version.equals("1.1.0"),"valid update manifest");
        check(UpdateInfo.newest(g,h)==h,"newest version across two sources");
        check(UpdateInfo.newest(g,null)==g&&UpdateInfo.newest(null,h)==h,"either source alone remains usable");
        check(UpdateInfo.newest(g,UpdateInfo.parse(manifest(2,"1.1.0",new String(new char[64]).replace('\0','b'))))==null,"conflicting mirror checksums rejected");
        invalid(older.replace("cn.lightrun.app","other.app"),"wrong application rejected");
        invalid(new org.json.JSONObject(older).put("giteeUrl","http://gitee.com/XLxiaoliao/lightrun/releases/tag/v1.1.0").toString(),"cleartext update URL rejected");
        invalid(new org.json.JSONObject(older).put("giteeUrl","https://gitee.com.evil.invalid/XLxiaoliao/lightrun/releases/tag/v1.1.0").toString(),"untrusted update host rejected");
        invalid(new org.json.JSONObject(older).put("giteeUrl","https://gitee.com/XLxiaoliao/lightrun/releases/tag/v1.0.0").toString(),"mismatched release URL rejected");
        invalid(manifest(0,"1.1.0",hash),"invalid update version rejected");
        invalid(manifest(2,"1.1.0","bad"),"invalid APK checksum rejected");
    }
    private void fix(double lat,double lon,float accuracy) throws Exception {
        Location p=new Location(LocationManager.GPS_PROVIDER);p.setLatitude(lat);p.setLongitude(lon);p.setAccuracy(accuracy);p.setTime(System.currentTimeMillis());p.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        locations.setTestProviderLocation(LocationManager.GPS_PROVIDER,p);Thread.sleep(2250);waitForIdleSync();
    }
    private void shell(String command) {try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}catch(Exception ignored){}}
}
