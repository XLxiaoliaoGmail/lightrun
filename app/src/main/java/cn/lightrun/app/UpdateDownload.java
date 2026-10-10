package cn.lightrun.app;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** User-initiated download into private cache; no browser, token, or silent installation. */
public final class UpdateDownload {
    private static final long LIMIT=100*1024*1024L;
    public static volatile String state="idle",message="";
    public static volatile long received,total;
    public static volatile UpdateInfo info;
    private static volatile boolean cancelled;
    private static volatile HttpURLConnection connection;
    public static boolean busy(){return state.equals("connecting")||state.equals("downloading")||state.equals("verifying");}
    public static File apk(Context context){return new File(context.getCacheDir(),"updates/update.apk");}
    public static synchronized void start(Context context,UpdateInfo update) {
        if(busy())return;
        Context app=context.getApplicationContext();info=update;cancelled=false;received=total=0;message="正在准备下载…";state="connecting";
        new Thread(()->download(app,update),"update-download").start();
    }
    public static void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    private static void ensureActive() throws IOException{if(cancelled)throw new IOException("Cancelled");}
    private static void download(Context app,UpdateInfo update) {
        File target=apk(app),partial=new File(target.getParentFile(),"update.part");
        try {
            if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Cache unavailable");
            if(target.exists()&&!target.delete())throw new IOException("Cannot replace APK");
            HttpURLConnection c=open(address(update),true);
            try {
                total=c.getContentLengthLong();if(total<1||total>LIMIT)throw new IOException("Invalid attachment size");
                state="downloading";message="正在下载更新…";
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial)){
                    transfer(in,out,total,()->{ensureActive();},count->received=count);out.getFD().sync();
                }
            } finally {c.disconnect();connection=null;}
            ensureActive();state="verifying";message="正在校验安装包…";
            verify(app,partial,update);ensureActive();
            if(!partial.renameTo(target))throw new IOException("Cannot finalize APK");
            connection=null;message="下载完成，安装包已校验";state="ready";
        } catch(Exception e){partial.delete();target.delete();connection=null;message=cancelled?"下载已取消":"下载失败或安装包校验未通过，请重试";state=cancelled?"cancelled":"failed";}
    }
    interface Check {void run() throws IOException;}
    interface Progress {void update(long count);}
    static void transfer(InputStream in,OutputStream out,long expected,Check check,Progress progress) throws IOException {
        if(expected<1||expected>LIMIT)throw new IOException("Invalid size");
        byte[] buffer=new byte[16384];long count=0;int n;
        while((n=in.read(buffer))!=-1){check.run();count+=n;if(count>expected)throw new IOException("Oversized APK");out.write(buffer,0,n);progress.update(count);}
        check.run();if(count!=expected)throw new IOException("Incomplete APK");
    }
    static String address(UpdateInfo update){return "https://gitee.com/XLxiaoliao/lightrun/releases/download/v"+update.version+"/lightrun-"+update.version+".apk";}
    static boolean allowed(String address,boolean binary) {
        try {URI u=new URI(address);return "https".equals(u.getScheme())&&u.getUserInfo()==null&&u.getPort()==-1&&u.getFragment()==null
                &&("gitee.com".equals(u.getHost())||(binary&&"foruda.gitee.com".equals(u.getHost())));}
        catch(Exception e){return false;}
    }
    private static HttpURLConnection open(String address,boolean binary) throws IOException {
        for(int redirects=0;redirects<=4;redirects++){
            ensureActive();if(!allowed(address,binary))throw new IOException("Untrusted download URL");
            HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();connection=c;
            c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","LightRun-Update/2");
            try {
                int code=c.getResponseCode();
                if(code==200)return c;
                if(binary&&(code==301||code==302||code==303||code==307||code==308)){
                    String location=c.getHeaderField("Location");if(location==null)throw new IOException("Missing redirect");address=new URL(new URL(address),location).toString();c.disconnect();continue;
                }
                throw new IOException("HTTP failure");
            } catch(IOException e){c.disconnect();throw e;}
        }
        throw new IOException("Too many redirects");
    }
    static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
    }
    @SuppressWarnings("deprecation")
    static void verify(Context context,File file,UpdateInfo expected) throws Exception {
        if(!hash(file).equals(expected.sha256))throw new IOException("Hash mismatch");
        PackageManager pm=context.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo incoming=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),installed=pm.getPackageInfo(context.getPackageName(),flags);
        if(incoming==null||!context.getPackageName().equals(incoming.packageName)||incoming.versionCode!=expected.code
                ||!expected.version.equals(incoming.versionName)||incoming.applicationInfo==null||incoming.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("Wrong APK");
        Signature[] a=Build.VERSION.SDK_INT>=28&&incoming.signingInfo!=null?incoming.signingInfo.getApkContentsSigners():incoming.signatures;
        Signature[] b=Build.VERSION.SDK_INT>=28&&installed.signingInfo!=null?installed.signingInfo.getApkContentsSigners():installed.signatures;
        if(a==null||b==null||a.length==0||a.length!=b.length||!new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b))))throw new IOException("Signing identity mismatch");
    }
}
