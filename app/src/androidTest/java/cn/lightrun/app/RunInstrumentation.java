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
        activity=(MainActivity)startActivitySync(intent);Thread.sleep(500);waitForIdleSync();
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
        Thread.sleep(1600);waitForIdleSync();
        check(service.session!=null&&service.session.active,"start via foreground service");
        runOnMainSync(()->{
            List<View> running=views(activity.findViewById(android.R.id.content));
            check(running.stream().anyMatch(v->v instanceof RouteView&&v.getHeight()>0),"run start reveals trajectory UI");
            check(running.stream().noneMatch(v->v instanceof ScrollView),"running home stays one screen");
        });
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
        backgroundSpeechTests(context);
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
        runOnMainSync(()->{clickLabel(activity.findViewById(android.R.id.content),"结束并保存");clickLabel(activity.findViewById(android.R.id.content),"结束并保存");saved[0]=service.session==null;});
        check(saved[0]&&service.session==null,"finish saves and clears active session");Thread.sleep(600);waitForIdleSync();
        runOnMainSync(()->{check(views(activity.findViewById(android.R.id.content)).stream().anyMatch(v->v instanceof TextView&&"跑步记录".contentEquals(((TextView)v).getText())),"save opens the completed run detail immediately");activity.onBackPressed();activity.onBackPressed();});Thread.sleep(500);waitForIdleSync();
        RunStore store=new RunStore(context);List<RunSession> history=store.history();RunSession savedRun=null;
        for(RunSession s:history)if(s.id.equals(id))savedRun=s;
        check(savedRun!=null&&savedRun.endedAt>0,"history save");check(store.restore()==null,"saved current removed");
        check(savedRun.stepsRecorded&&savedRun.steps==127,"history preserves steps");
        historyUiTests();
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
    private void backgroundSpeechTests(Context context) throws Exception {
        double realDistance=service.session.distanceM;
        int realPoints=service.session.points.size();
        String original=android.provider.Settings.Secure.getString(context.getContentResolver(),"tts_default_synth");
        String originalLocale=android.provider.Settings.Secure.getString(context.getContentResolver(),"tts_default_locale");
        shell("settings put secure tts_default_synth cn.lightrun.app.test");
        shell("settings put secure tts_default_locale cn.lightrun.app.test:zh_CN");
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){requests.incrementAndGet();}};
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,new IntentFilter("cn.lightrun.TEST_SYNTHESIZE"),Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver,new IntentFilter("cn.lightrun.TEST_SYNTHESIZE"));
        try {
            UpdateChecker.preferences(context).edit().putBoolean("voice",true).apply();
            runOnMainSync(()->{service.resetVoice();service.voiceSettingsChanged(true);});
            shell("settings put global lightrun_test_tts_mode slow");
            shell("input keyevent 223");
            check(!((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isInteractive(),"voice milestone test runs with screen off");
            runOnMainSync(()->{service.session.distanceM=999;service.session.announcedKilometer=0;});
            fix(31.00020,121,5);
            await(()->service.session.announcedKilometer==1,15000,"screen-off kilometer completes app-owned playback");
            check(requests.get()==1,"one synthesis for one screen-off kilometer");
            check(service.voiceStatus().contains("1 公里 · 播放完成"),"persistent playback result available after screen-off");
            fix(31.00025,121,5);check(requests.get()==1,"GPS updates do not repeat completed kilometer");
            shell("settings put global lightrun_test_tts_mode fail-once");
            runOnMainSync(()->service.session.distanceM=1999);
            fix(31.00030,121,5);
            check(service.session.announcedKilometer==1,"synthesis failure must not consume kilometer");
            await(()->service.session.announcedKilometer==2,18000,"failed kilometer retries successfully while screen off");
            check(requests.get()==3,"one failed attempt and one successful retry");
            shell("settings put global lightrun_test_tts_mode always-fail");
            runOnMainSync(()->service.session.distanceM=2999);
            fix(31.00035,121,5);
            await(()->service.voiceStatus().contains("本次未播完"),18000,"permanent engine failure has explicit result");
            check(service.session.announcedKilometer==2,"permanent failure is not reported as played");
            check(requests.get()==6,"retry bounded to three attempts");
            fix(31.00040,121,5);check(requests.get()==6,"new GPS fixes cannot create unlimited failed retries");
            shell("settings put global lightrun_test_tts_mode slow");
            runOnMainSync(()->service.session.distanceM=3999);
            // Inject a valid GPS callback without waiting, then pause before synthesis finishes.
            runOnMainSync(()->{
                Location p=new Location(LocationManager.GPS_PROVIDER);p.setLatitude(31.00045);p.setLongitude(121);p.setAccuracy(5);
                p.setTime(System.currentTimeMillis());p.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());service.onLocationChanged(p);service.pause();
            });
            Thread.sleep(1500);check(service.session.announcedKilometer==2,"pause cancels pending playback and completion");
            check(service.voiceStatus().contains("已停止"),"pause records cancelled speech");
            File[] cache=context.getCacheDir().listFiles((dir,name)->name.startsWith("lightrun-voice-"));
            check(cache!=null&&cache.length==0,"completed/failed/cancelled speech files are removed");
        } finally {
            context.unregisterReceiver(receiver);shell("input keyevent 224");shell("wm dismiss-keyguard");
            UpdateChecker.preferences(context).edit().putBoolean("voice",false).apply();
            runOnMainSync(()->{
                // Undo injected kilometer distances before the existing persistence/GPS tests.
                double added=0;
                for(int i=realPoints;i<service.session.points.size();i++) {
                    RunSession.Point a=service.session.points.get(i-1),b=service.session.points.get(i);
                    if(a.segment==b.segment)added+=RunSession.meters(a.lat,a.lon,b.lat,b.lon);
                }
                service.session.distanceM=realDistance+added;service.resetVoice();
            });
            shell("settings delete global lightrun_test_tts_mode");
            shell(original==null?"settings delete secure tts_default_synth":"settings put secure tts_default_synth "+original);
            shell(originalLocale==null?"settings delete secure tts_default_locale":"settings put secure tts_default_locale "+originalLocale);
        }
    }
    private void await(java.util.function.BooleanSupplier condition,long limit,String message) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+limit;
        while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<deadline){Thread.sleep(100);waitForIdleSync();}
        check(condition.getAsBoolean(),message);
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
        check(UpdateDownload.address(g).equals("https://gitee.com/XLxiaoliao/lightrun/releases/download/v1.1.0/lightrun-1.1.0.apk"),"download resolves a binary file rather than a release page");
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
    private List<View> views(View root){List<View> result=new ArrayList<>();if(root.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)return result;result.add(root);if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)result.addAll(views(group.getChildAt(i)));}return result;}
    private void clickLabel(View root,String label){for(View v:views(root))if(v instanceof TextView&&label.contentEquals(((TextView)v).getText())){v.performClick();return;}throw new AssertionError("Missing button: "+label);}
    private void historyUiTests() throws Exception {
        runOnMainSync(()->{for(View v:views(activity.findViewById(android.R.id.content)))if(v instanceof ClockButton){v.performClick();break;}});Thread.sleep(500);waitForIdleSync();
        runOnMainSync(()->{
            View root=activity.findViewById(android.R.id.content);DailyChartView chart=null;View card=null;
            for(View v:views(root)){if(v instanceof DailyChartView)chart=(DailyChartView)v;if(v.isClickable()&&v.getContentDescription()!=null&&v.getContentDescription().toString().endsWith("跑步记录"))card=v;}
            check(chart!=null&&chart.getContentDescription().toString().contains("最近7天"),"history defaults to seven day chart");
            check(views(root).stream().noneMatch(v->v instanceof HorizontalScrollView),"history has no horizontal scroll container");
            check(chart.getWidth()<=((View)chart.getParent()).getWidth(),"chart width fits history page");
            clickLabel(root,"最近 30 天");check(chart.getContentDescription().toString().contains("最近30天"),"thirty day selection");
            clickLabel(root,"仅步数");clickLabel(root,"仅距离");clickLabel(root,"距离 + 步数");clickLabel(root,"最近 7 天");
            check(card!=null,"history list follows chart");card.performClick();
        });Thread.sleep(500);waitForIdleSync();
        runOnMainSync(()->{
            View root=activity.findViewById(android.R.id.content);
            check(views(root).stream().filter(v->v instanceof RunGraphView).count()==3,"full detail page has activity speed and precision graphs");
            check(views(root).stream().anyMatch(v->v instanceof RouteView),"detail includes route");
            check(views(root).stream().anyMatch(v->v instanceof TextView&&"平均步频".contentEquals(((TextView)v).getText())),"detail includes measured cadence");
            check(views(root).stream().noneMatch(v->v instanceof DailyChartView),"detail replaces history rather than floating above it");
            clickLabel(root,"删除这次记录");check(views(activity.findViewById(android.R.id.content)).stream().noneMatch(v->v instanceof RouteView),"delete confirmation is a separate page");
            clickLabel(activity.findViewById(android.R.id.content),"保留");check(views(activity.findViewById(android.R.id.content)).stream().anyMatch(v->v instanceof RouteView),"cancel delete returns to detail");
            activity.onBackPressed();activity.onBackPressed();
        });Thread.sleep(500);waitForIdleSync();
        for(int days:new int[]{7,30})for(int mode:new int[]{0,1,2})verifyChart(days,mode,240);
    }
    private void verifyChart(int count,int mode,int width) throws Exception {
        DailyChartView[] chart={null};float density=activity.getResources().getDisplayMetrics().density;
        java.time.LocalDate today=java.time.LocalDate.now();java.time.ZoneId zone=java.time.ZoneId.systemDefault();List<RunSession> fixture=new ArrayList<>();
        for(int i=0;i<count;i++){RunSession run=new RunSession(today.minusDays(i).atStartOfDay(zone).toInstant().toEpochMilli());run.distanceM=20000;run.steps=1000;run.stepsRecorded=true;fixture.add(run);}
        runOnMainSync(()->{chart[0]=new DailyChartView(activity);chart[0].setData(new HistorySummary(fixture,today,count,zone),mode);int w=Math.round(width*density),h=Math.round(264*density);chart[0].measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));chart[0].layout(0,0,w,h);});
        Thread.sleep(550);runOnMainSync(()->{
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(chart[0].getWidth(),chart[0].getHeight(),android.graphics.Bitmap.Config.ARGB_8888);chart[0].draw(new android.graphics.Canvas(image));int green=0,gold=0,previous=0,y=Math.round(180*density);
            for(int x=0;x<image.getWidth();x++){int color=image.getPixel(x,y),r=android.graphics.Color.red(color),g=android.graphics.Color.green(color),b=android.graphics.Color.blue(color);int kind=g>r+15&&g>b+8?1:r>g+15&&g>b+30?2:0;if(kind==1&&previous!=1)green++;if(kind==2&&previous!=2)gold++;previous=kind;}
            check(green==(mode==1?0:count),count+" day distance bars all fit narrow viewport");check(gold==(mode==2?0:count),count+" day step bars all fit narrow viewport");image.recycle();
            check(chart[0].getMinimumWidth()==0,"chart never demands horizontal overflow");
            HistorySummary.Day[] chosen={null};chart[0].setSelectionListener(day->chosen[0]=day);long now=SystemClock.uptimeMillis();android.view.MotionEvent tap=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_UP,0,80*density,0);chart[0].onTouchEvent(tap);tap.recycle();check(chosen[0].date.equals(today.minusDays(count-1)),"left edge selects first date");tap=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_UP,chart[0].getWidth(),80*density,0);chart[0].onTouchEvent(tap);tap.recycle();check(chosen[0].date.equals(today),"right edge selects last date");
        });
    }
    private void homeTests() throws Exception {
        runOnMainSync(()->{
            List<View> home=views(activity.findViewById(android.R.id.content));
            check(home.stream().noneMatch(v->v instanceof ScrollView),"homepage has no scrolling container");
            GearButton gear=null;Button start=null;RouteView route=null;for(View v:home){if(v instanceof GearButton)gear=(GearButton)v;if(v instanceof Button&&"开跑".contentEquals(((Button)v).getText()))start=(Button)v;if(v instanceof RouteView)route=(RouteView)v;}
            check(gear!=null&&gear.getWidth()>0&&gear.getHeight()>0,"gear settings is visible");
            android.graphics.Rect bounds=new android.graphics.Rect();check(start!=null&&start.getGlobalVisibleRect(bounds)&&Math.abs(bounds.height()-start.getHeight())<=2,"start button fits viewport");
            check(route==null&&home.stream().noneMatch(v->v instanceof TextView&&((TextView)v).getText().toString().contains("运动时长")),"idle home contains no timer or route");
            check(start.getWidth()==start.getHeight(),"idle start is a circle");
            check(home.stream().anyMatch(v->v instanceof ClockButton),"history clock matches gear icon style");gear.performClick();
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
