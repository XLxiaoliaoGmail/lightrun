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

/** Exercises real Android APIs using synthetic GPS fixtures. Test APK only; no personal routes. */
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
        Intent intent=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(MainActivity)startActivitySync(intent);waitForIdleSync();
        CountDownLatch bound=new CountDownLatch(1);
        connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=((TrackingService.LocalBinder)b).service();bound.countDown();}public void onServiceDisconnected(ComponentName n){}};
        context.bindService(new Intent(context,TrackingService.class),connection,Context.BIND_AUTO_CREATE);
        check(bound.await(10,TimeUnit.SECONDS),"service binding");
        runOnMainSync(()->service.discard());
        check(context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.length==6,"only six required permissions; no internet");
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
        long elapsed=service.session.duration(SystemClock.elapsedRealtime());
        Thread.sleep(1200);check(service.session.duration(SystemClock.elapsedRealtime())==elapsed,"paused timer frozen");
        int pausedPoints=service.session.points.size();fix(31.001,121,5);check(service.session.points.size()==pausedPoints,"paused GPS stopped");
        RunSession restored=new RunStore(context).restore();check(restored!=null&&!restored.active,"paused checkpoint restore");
        check(restored.points.size()==pausedPoints&&restored.accumulatedMs==elapsed,"checkpoint preserves points and duration");
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
        ByteArrayOutputStream gpx=new ByteArrayOutputStream();RunStore.gpx(savedRun,gpx);
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        org.w3c.dom.Document xml=factory.newDocumentBuilder().parse(new ByteArrayInputStream(gpx.toByteArray()));
        check(xml.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1","trkpt").getLength()==savedRun.points.size(),"valid GPX points");
        check(xml.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1","trkseg").getLength()==2,"GPX pause segments");
        store.delete(savedRun);check(store.history().size()==history.size()-1,"history deletion");
        check(distance>0,"nonzero run distance");
    }
    private void fix(double lat,double lon,float accuracy) throws Exception {
        Location p=new Location(LocationManager.GPS_PROVIDER);p.setLatitude(lat);p.setLongitude(lon);p.setAccuracy(accuracy);p.setTime(System.currentTimeMillis());p.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        locations.setTestProviderLocation(LocationManager.GPS_PROVIDER,p);Thread.sleep(2250);waitForIdleSync();
    }
    private void shell(String command) {try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}catch(Exception ignored){}}
}
