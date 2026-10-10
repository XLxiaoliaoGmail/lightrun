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
        prefs.edit().putLong("updateAttempt",now).putString("updateStatus","正在检查更新…").apply();
        new Thread(()->{
            String status;UpdateInfo best=null;
            try {
                String text=fetch("https://gitee.com/api/v5/repos/XLxiaoliao/lightrun/contents/update.json?ref=main");
                best=UpdateInfo.parse(text);
                status=best.code>installedCode(app)?"发现新版本 "+best.version:"已是最新版本";
                prefs.edit().putString("updateManifest",text).putLong("updateSuccess",System.currentTimeMillis()).apply();
            }catch(Exception e){status="暂时无法检查更新，请稍后重试";}
            finally{checking.set(false);}
            prefs.edit().putString("updateStatus",status).apply();
            UpdateInfo result=best;String message=status;
            if(callback!=null)new Handler(Looper.getMainLooper()).post(()->callback.done(result,message));
        },"update-check").start();
        return true;
    }
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
                JSONObject wrapper=new JSONObject(text);
                if(!"base64".equals(wrapper.getString("encoding")))throw new IOException("Invalid content encoding");
                return new String(Base64.decode(wrapper.getString("content"),Base64.DEFAULT),StandardCharsets.UTF_8);
            }
        }finally{connection.disconnect();}
    }
}
