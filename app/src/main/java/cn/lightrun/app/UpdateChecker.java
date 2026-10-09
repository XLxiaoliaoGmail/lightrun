package cn.lightrun.app;

import android.content.*;
import android.os.*;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateChecker {
    public static final long INTERVAL=24*60*60*1000L;
    private static final AtomicBoolean checking=new AtomicBoolean();
    public interface Callback {void done(UpdateInfo info,String status);}
    public static SharedPreferences preferences(Context context){return context.getSharedPreferences("settings",Context.MODE_PRIVATE);}
    public static int installedCode(Context context) {
        try {return context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode;}
        catch(Exception e){return Integer.MAX_VALUE;}
    }
    public static UpdateInfo cached(Context context) {
        try{return UpdateInfo.parse(preferences(context).getString("updateManifest",""));}catch(Exception e){return null;}
    }
    public static boolean busy(){return checking.get();}
    public static boolean check(Context context,boolean manual,Callback callback) {
        Context app=context.getApplicationContext();SharedPreferences prefs=preferences(app);
        if(prefs.getBoolean("running",false))return false;
        long now=System.currentTimeMillis(),last=prefs.getLong("updateAttempt",0);
        if(!manual&&(!prefs.getBoolean("autoUpdate",true)||(now>=last&&now-last<INTERVAL)))return false;
        if(!checking.compareAndSet(false,true))return false;
        prefs.edit().putLong("updateAttempt",now).putString("updateStatus","正在检查 Gitee 和 GitHub…").apply();
        new Thread(()->{
            ExecutorService pool=Executors.newFixedThreadPool(2);
            String status;UpdateInfo best=null;
            try {
                Future<String> gitee=pool.submit(()->fetch("https://gitee.com/api/v5/repos/XLxiaoliao/lightrun/contents/update.json?ref=main"));
                Future<String> github=pool.submit(()->fetch("https://raw.githubusercontent.com/XLxiaoliaoGmail/lightrun/main/update.json"));
                String g=read(gitee),h=read(github);UpdateInfo gi=parse(g),hi=parse(h);
                best=UpdateInfo.newest(gi,hi);
                if(best==null)status=gi!=null&&hi!=null?"两个更新源的校验信息不一致，请稍后重试":"暂时无法连接更新源，跑步功能不受影响";
                else {
                    String source=gi!=null&&hi!=null?"Gitee 和 GitHub":gi!=null?"Gitee（GitHub 暂不可用）":"GitHub（Gitee 暂不可用）";
                    status=best.code>installedCode(app)?"发现新版本 "+best.version+" · "+source:"已是最新版本 · "+source;
                    prefs.edit().putString("updateManifest",best==hi?h:g).putLong("updateSuccess",System.currentTimeMillis()).apply();
                }
            }catch(RuntimeException e){status="更新检查失败，跑步功能不受影响";}
            finally{pool.shutdownNow();checking.set(false);}
            prefs.edit().putString("updateStatus",status).apply();
            UpdateInfo result=best;String message=status;
            if(callback!=null)new Handler(Looper.getMainLooper()).post(()->callback.done(result,message));
        },"update-check").start();
        return true;
    }
    private static String read(Future<String> future){try{return future.get(10,TimeUnit.SECONDS);}catch(Exception e){future.cancel(true);return null;}}
    private static UpdateInfo parse(String text){try{return text==null?null:UpdateInfo.parse(text);}catch(Exception e){return null;}}
    private static String fetch(String address) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(4000);connection.setReadTimeout(4000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","LightRun-Update/1");
        try {
            if(connection.getResponseCode()!=200)throw new IOException("Update source unavailable");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int count;
                while((count=input.read(buffer))!=-1){if(bytes.size()+count>65536)throw new IOException("Oversized response");bytes.write(buffer,0,count);}
                String text=bytes.toString(StandardCharsets.UTF_8.name());
                if(address.startsWith("https://raw.githubusercontent.com/"))return text;
                JSONObject wrapper=new JSONObject(text);
                if(!"base64".equals(wrapper.getString("encoding")))throw new IOException("Invalid content encoding");
                return new String(Base64.decode(wrapper.getString("content"),Base64.DEFAULT),StandardCharsets.UTF_8);
            }
        }finally{connection.disconnect();}
    }
}
