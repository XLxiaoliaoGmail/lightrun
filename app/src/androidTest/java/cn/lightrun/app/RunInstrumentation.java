package cn.lightrun.app;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.net.Uri;
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
        downloadTests(context);
        Intent intent=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(MainActivity)startActivitySync(intent);waitForIdleSync();
        homeTests();
        speechTests();
        CountDownLatch bound=new CountDownLatch(1);
        connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=((TrackingService.LocalBinder)b).service();bound.countDown();}public void onServiceDisconnected(ComponentName n){}};
        context.bindService(new Intent(context,TrackingService.class),connection,Context.BIND_AUTO_CREATE);
        check(bound.await(10,TimeUnit.SECONDS),"service binding");
        runOnMainSync(()->service.discard());
        List<String> permissions=Arrays.asList(context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions);
        check(permissions.contains("android.permission.INTERNET")&&permissions.contains("android.permission.ACTIVITY_RECOGNITION"),"update and step permissions declared");
        check(permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES")&&!permissions.contains("android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION"),"user-confirmed installation only");
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
        String originalLocale=android.provider.Settings.Secure.getString(getTargetContext().getContentResolver(),"tts_default_locale");
        shell("settings put secure tts_default_synth cn.lightrun.app.test");
        shell("settings put secure tts_default_locale cn.lightrun.app.test:zh_CN");
        try {
            for(String mode:new String[]{"modern","alias","legacy","lazy","unknown","missing"})speechCase(mode,"test-zh-offline");
            speechCase("cloud","test-zh-online");
        } finally {
            UpdateChecker.preferences(getTargetContext()).edit().remove("systemVoice").apply();
            shell("settings delete global lightrun_test_tts_mode");
            shell(original==null?"settings delete secure tts_default_synth":"settings put secure tts_default_synth "+original);
            shell(originalLocale==null?"settings delete secure tts_default_locale":"settings put secure tts_default_locale "+originalLocale);
        }
    }
    private void speechCase(String mode,String expectedVoice) throws Exception {
        shell("settings put global lightrun_test_tts_mode "+mode);
        // An old disabled compatibility preference must not disable the system preset.
        UpdateChecker.preferences(getTargetContext()).edit().putBoolean("systemVoice",false).apply();
        CountDownLatch synthesized=new CountDownLatch(1);String[] received={"",""};boolean[] local={false};
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){received[0]=i.getStringExtra("voice");received[1]=i.getStringExtra("text");local[0]=i.getBooleanExtra("embedded",false);synthesized.countDown();}};
        if(Build.VERSION.SDK_INT>=33)getTargetContext().registerReceiver(receiver,new IntentFilter("cn.lightrun.TEST_SYNTHESIZE"),Context.RECEIVER_EXPORTED);
        else getTargetContext().registerReceiver(receiver,new IntentFilter("cn.lightrun.TEST_SYNTHESIZE"));
        VoiceCoach[] coach={null};
        try {
            runOnMainSync(()->{coach[0]=new VoiceCoach(getTargetContext());coach[0].speak(VoiceCoach.kilometer(1,1000,360000));});
            check(synthesized.await(12,TimeUnit.SECONDS),mode+": Android TTS synthesis completes: "+coach[0].status);
            check(expectedVoice.equals(received[0]),mode+": preserves system voice");
            check(received[1].contains("已跑1公里")&&!local[0],mode+": Chinese text without forced embedded synthesis");
            check(coach[0].status.equals("中文语音已就绪"),mode+": readiness does not depend on voice-list metadata");
        } finally {
            runOnMainSync(()->{if(coach[0]!=null)coach[0].close();});
            getTargetContext().unregisterReceiver(receiver);
        }
    }
    private void invalid(String json,String label){try{UpdateInfo.parse(json);check(false,label);}catch(Exception expected){check(true,label);}}
    private void updateTests() throws Exception {
        String hash=new String(new char[64]).replace('\0','a'),older=manifest(2,"1.1.0",hash),newer=manifest(3,"1.2.0",hash);
        UpdateInfo g=UpdateInfo.parse(older);
        check(g.code==2&&g.version.equals("1.1.0"),"valid update manifest");
        org.json.JSONObject single=new org.json.JSONObject(newer);single.remove("githubUrl");
        check(UpdateInfo.parse(single.toString()).code==3,"GitHub field is optional");
        check(UpdateInfo.parse(new org.json.JSONObject(newer).put("githubUrl","https://invalid.example").toString()).code==3,"legacy mirror field is ignored");
        invalid(older.replace("cn.lightrun.app","other.app"),"wrong application rejected");
        invalid(new org.json.JSONObject(older).put("giteeUrl","http://gitee.com/XLxiaoliao/lightrun/releases/tag/v1.1.0").toString(),"cleartext update URL rejected");
        invalid(new org.json.JSONObject(older).put("giteeUrl","https://gitee.com.evil.invalid/XLxiaoliao/lightrun/releases/tag/v1.1.0").toString(),"untrusted update host rejected");
        invalid(new org.json.JSONObject(older).put("giteeUrl","https://gitee.com/XLxiaoliao/lightrun/releases/tag/v1.0.0").toString(),"mismatched release URL rejected");
        invalid(manifest(0,"1.1.0",hash),"invalid update version rejected");
        invalid(manifest(2,"1.1.0","bad"),"invalid APK checksum rejected");
    }
    private interface Attempt {void run() throws Exception;}
    private void rejected(Attempt attempt,String label){boolean rejected=false;try{attempt.run();}catch(Exception expected){rejected=true;}check(rejected,label);}
    private void downloadTests(Context context) throws Exception {
        check(UpdateDownload.allowed("https://gitee.com/api/v5/repos/test",false),"metadata HTTPS allowed");
        check(UpdateDownload.allowed("https://foruda.gitee.com/asset.apk",true)&&!UpdateDownload.allowed("https://foruda.gitee.com/asset.apk",false),"attachment CDN restricted to downloads");
        for(String address:new String[]{"http://gitee.com/a","https://gitee.com.evil.invalid/a","https://user@gitee.com/a","https://gitee.com:443/a","https://github.com/a"})check(!UpdateDownload.allowed(address,true),"untrusted URL rejected: "+address);
        byte[] fixture=new byte[40000];new Random(1).nextBytes(fixture);ByteArrayOutputStream out=new ByteArrayOutputStream();long[] progress={0};
        UpdateDownload.transfer(new ByteArrayInputStream(fixture),out,fixture.length,()->{},count->progress[0]=count);
        check(Arrays.equals(fixture,out.toByteArray())&&progress[0]==fixture.length,"download preserves bytes and reports progress");
        rejected(()->UpdateDownload.transfer(new ByteArrayInputStream(fixture),new ByteArrayOutputStream(),fixture.length+1,()->{},n->{}),"truncated APK rejected");
        rejected(()->UpdateDownload.transfer(new ByteArrayInputStream(fixture),new ByteArrayOutputStream(),fixture.length-1,()->{},n->{}),"oversized APK rejected");
        ByteArrayOutputStream cancelled=new ByteArrayOutputStream();
        rejected(()->UpdateDownload.transfer(new ByteArrayInputStream(fixture),cancelled,fixture.length,()->{throw new IOException("cancel");},n->{}),"cancel interrupts transfer");
        check(cancelled.size()==0,"cancel does not write more bytes");
        android.content.pm.PackageInfo installed=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
        File own=new File(context.getApplicationInfo().sourceDir);String ownHash=UpdateDownload.hash(own);
        UpdateInfo expected=UpdateInfo.parse(manifest(installed.versionCode,installed.versionName,ownHash));
        UpdateDownload.verify(context,own,expected);check(true,"installed APK passes hash/package/version/signature validation");
        rejected(()->UpdateDownload.verify(context,own,UpdateInfo.parse(manifest(installed.versionCode,installed.versionName,new String(new char[64]).replace('\0','a')))),"wrong hash rejected");
        rejected(()->UpdateDownload.verify(context,own,UpdateInfo.parse(manifest(installed.versionCode+1,installed.versionName,ownHash))),"wrong version rejected");
        File other=new File(getContext().getApplicationInfo().sourceDir);String otherHash=UpdateDownload.hash(other);
        rejected(()->UpdateDownload.verify(context,other,UpdateInfo.parse(manifest(installed.versionCode,installed.versionName,otherHash))),"wrong package rejected even with matching hash");
        File apk=UpdateDownload.apk(context);apk.getParentFile().mkdirs();try(OutputStream file=new FileOutputStream(apk)){file.write(fixture);}
        Uri uri=UpdateApkProvider.uri(context);
        try(InputStream in=context.getContentResolver().openInputStream(uri)){check(in!=null&&in.read()==(fixture[0]&255),"provider reads private APK");}
        rejected(()->{try(OutputStream ignored=context.getContentResolver().openOutputStream(uri)){}},"provider denies writing");
        rejected(()->{try(InputStream ignored=context.getContentResolver().openInputStream(Uri.parse("content://cn.lightrun.app.updates/../files/current.json"))){}},"provider denies other paths");
        android.content.pm.ProviderInfo provider=context.getPackageManager().resolveContentProvider(uri.getAuthority(),0);
        check(provider!=null&&!provider.exported&&provider.grantUriPermissions,"provider requires explicit temporary read grant");
        Intent install=MainActivity.installIntent(context);
        check("content".equals(install.getData().getScheme())&&UpdateApkProvider.MIME.equals(install.getType())&&(install.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,"installer opens content APK with read permission");
        check(install.getClipData()!=null&&uri.equals(install.getClipData().getItemAt(0).getUri()),"installer URI grant survives intent forwarding");apk.delete();
    }
    private List<View> views(View root){List<View> result=new ArrayList<>();result.add(root);if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)result.addAll(views(group.getChildAt(i)));}return result;}
    private void homeTests() throws Exception {
        runOnMainSync(()->{
            List<View> home=views(activity.findViewById(android.R.id.content));
            check(home.stream().noneMatch(v->v instanceof ScrollView),"homepage has no scrolling container");
            GearButton gear=null;Button start=null;RouteView route=null;for(View v:home){if(v instanceof GearButton)gear=(GearButton)v;if(v instanceof Button&&"开跑".contentEquals(((Button)v).getText()))start=(Button)v;if(v instanceof RouteView)route=(RouteView)v;}
            check(gear!=null&&gear.getWidth()>0&&gear.getHeight()>0,"gear settings is visible");
            android.graphics.Rect bounds=new android.graphics.Rect();check(start!=null&&start.getGlobalVisibleRect(bounds)&&bounds.height()==start.getHeight(),"start button fits viewport");
            check(route!=null&&route.getHeight()>0,"route fits remaining viewport");gear.performClick();
            StringBuilder labels=new StringBuilder();for(View v:views(activity.findViewById(android.R.id.content)))if(v instanceof TextView)labels.append(((TextView)v).getText()).append('\n');
            check(labels.indexOf("兼容模式")==-1&&labels.indexOf("Gitee")==-1&&labels.indexOf("GitHub")==-1,"settings has no compatibility toggle or update-source labels");activity.onBackPressed();
        });waitForIdleSync();
    }
    private void fix(double lat,double lon,float accuracy) throws Exception {
        Location p=new Location(LocationManager.GPS_PROVIDER);p.setLatitude(lat);p.setLongitude(lon);p.setAccuracy(accuracy);p.setTime(System.currentTimeMillis());p.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        locations.setTestProviderLocation(LocationManager.GPS_PROVIDER,p);Thread.sleep(2250);waitForIdleSync();
    }
    private void shell(String command) {try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}catch(Exception ignored){}}
}
