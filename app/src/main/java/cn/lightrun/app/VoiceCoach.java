package cn.lightrun.app;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.io.File;
import java.util.*;

/** System Chinese synthesis; playback belongs to the tracking foreground service. Main thread only. */
public final class VoiceCoach {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private TextToSpeech tts;
    private MediaPlayer player;
    private File speechFile;
    private boolean ready,closed,focused;
    private int generation,sequence,failures;
    private String utterance;
    private Request request;
    private Runnable timeout,retry;
    public String status="尚未检查中文语音";
    private final AudioAttributes attributes=new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final AudioFocusRequest focus;
    private static final class Request {
        final String text,label;
        final Runnable completed;
        final long deadline=SystemClock.elapsedRealtime()+90000;
        Request(String text,String label,Runnable completed){this.text=text;this.label=label;this.completed=completed;}
    }
    public VoiceCoach(Context context) {
        this.context=context.getApplicationContext();audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change->{
                    // A duck request need not cancel spoken navigation. A real loss must release playback.
                    if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                        if(request!=null&&player!=null)failed("声音被其他应用打断",false);
                },main).build();
    }
    public void prepare() {
        if(closed||tts!=null)return;
        status="正在检查中文语音";
        int engine=++generation;
        tts=new TextToSpeech(context,result->main.post(()->initialize(engine,result)));
    }
    private void initialize(int engine,int result) {
        if(closed||engine!=generation||tts==null)return;
        try {
            if(result!=TextToSpeech.SUCCESS||!selectSystemVoice())throw new IllegalStateException();
            tts.setSpeechRate(1f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id){}
                public void onDone(String id){main.post(()->{if(current(engine,id))play();});}
                @Override public void onError(String id){onError(id,TextToSpeech.ERROR);}
                @Override public void onError(String id,int error){main.post(()->{if(current(engine,id))failed("语音生成失败（"+error+"）",true);});}
                @Override public void onStop(String id,boolean interrupted){main.post(()->{if(current(engine,id))failed("语音生成被中断",true);});}
            });
            ready=true;status="中文语音已就绪";
            if(request!=null)attempt();
        } catch(RuntimeException e){
            status="系统引擎未能提供中文语音，请检查系统试听";
            if(request!=null)failed("中文语音引擎不可用",true);
            else {generation++;ready=false;tts.shutdown();tts=null;}
        }
    }
    private boolean current(int engine,String id){return !closed&&request!=null&&engine==generation&&id.equals(utterance);}
    private static boolean chinese(Locale locale) {
        if(locale==null)return false;
        String language=locale.getLanguage();
        return "zh".equalsIgnoreCase(language)||"zho".equalsIgnoreCase(language)||"chi".equalsIgnoreCase(language);
    }
    @SuppressWarnings("deprecation")
    private boolean selectSystemVoice() {
        // Preserve the system preset, including engines with incomplete voice-list metadata.
        if(chinese(tts.getLanguage()))return true;
        return tts.setLanguage(Locale.SIMPLIFIED_CHINESE)>=TextToSpeech.LANG_AVAILABLE;
    }
    public void speak(String text){submit(text,"试听",null);}
    public void announce(int kilometer,String text,Runnable completed){submit(text,kilometer+" 公里",completed);}
    private void submit(String text,String label,Runnable completed) {
        if(closed)return;
        stop();failures=0;request=new Request(text,label,completed);record("等待播报");attempt();
    }
    private void attempt() {
        cancelTimers();
        if(request==null||closed)return;
        if(SystemClock.elapsedRealtime()>=request.deadline){giveUp("等待声音播放超时");return;}
        if(audio.getMode()!=AudioManager.MODE_NORMAL){waitForAudio("通话或通信占用音频，稍后重试");return;}
        if(!ready){prepare();armTimeout(30000,()->failed("语音引擎响应超时",true));return;}
        try {
            speechFile=File.createTempFile("lightrun-voice-",".wav",context.getCacheDir());
            utterance="lightrun-"+generation+"-"+(++sequence);
            record("正在生成语音");armTimeout(30000,()->failed("语音生成超时",true));
            // onDone here means the file is ready, not that the user has heard it.
            if(tts.synthesizeToFile(request.text,new Bundle(),speechFile,utterance)!=TextToSpeech.SUCCESS)
                failed("系统引擎拒绝生成语音",true);
        }catch(Exception e){failed("无法生成语音文件",true);}
    }
    private void play() {
        utterance=null;cancelTimers();
        if(request==null)return;
        if(audio.getMode()!=AudioManager.MODE_NORMAL){waitForAudio("通话或通信占用音频，稍后重试");return;}
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){waitForAudio("音频正忙，稍后重试");return;}
        focused=true;
        try {
            player=new MediaPlayer();MediaPlayer playing=player;
            playing.setAudioAttributes(attributes);playing.setWakeMode(context,PowerManager.PARTIAL_WAKE_LOCK);
            playing.setDataSource(speechFile.getAbsolutePath());
            playing.setOnPreparedListener(p->{
                if(player!=p||request==null)return;
                try {
                    p.start();record(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0?"正在播放（媒体音量为零）":"正在播放");
                }catch(RuntimeException e){failed("无法开始声音播放",false);}
            });
            playing.setOnCompletionListener(p->{
                if(player!=p||request==null)return;
                Request done=request;record(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0?"播放完成（媒体音量为零）":"播放完成");
                request=null;cleanup();if(done.completed!=null)done.completed.run();
            });
            playing.setOnErrorListener((p,what,extra)->{if(player==p)failed("声音播放失败（"+what+"）",false);return true;});
            armTimeout(45000,()->failed("声音播放超时",false));playing.prepareAsync();
        }catch(Exception e){failed("无法开始声音播放",false);}
    }
    private void waitForAudio(String reason) {
        // Discard the old file before retrying synthesis; keep only the latest milestone.
        cleanup();record(reason);retry=()->{retry=null;attempt();};main.postDelayed(retry,5000);
    }
    private void failed(String reason,boolean resetEngine) {
        cleanup();
        if(resetEngine){generation++;ready=false;if(tts!=null)tts.shutdown();tts=null;}
        if(request==null)return;
        if(++failures>=3||SystemClock.elapsedRealtime()>=request.deadline){giveUp(reason);return;}
        record(reason+"，稍后重试");retry=()->{retry=null;attempt();};main.postDelayed(retry,3000);
    }
    private void giveUp(String reason){record(reason+"，本次未播完");request=null;cleanup();}
    private void record(String result){
        if(request!=null)context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit()
                .putString("lastVoiceResult","最近播报："+request.label+" · "+result).apply();
    }
    public String diagnostic(){return context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("lastVoiceResult","");}
    private void armTimeout(long delay,Runnable action){
        if(timeout!=null)main.removeCallbacks(timeout);
        long remaining=request==null?delay:Math.max(1,request.deadline-SystemClock.elapsedRealtime());
        timeout=()->{timeout=null;action.run();};main.postDelayed(timeout,Math.min(delay,remaining));
    }
    private void cancelTimers(){if(timeout!=null)main.removeCallbacks(timeout);if(retry!=null)main.removeCallbacks(retry);timeout=null;retry=null;}
    private void releaseFocus(){if(focused){focused=false;audio.abandonAudioFocusRequest(focus);}}
    private void cleanup(){
        utterance=null;cancelTimers();
        if(player!=null){MediaPlayer old=player;player=null;old.release();}
        releaseFocus();
        if(tts!=null)tts.stop();
        if(speechFile!=null){speechFile.delete();speechFile=null;}
    }
    public void stop(){if(request!=null)record("已停止");request=null;cleanup();}
    public void close(){closed=true;generation++;stop();if(tts!=null){tts.shutdown();tts=null;}ready=false;}
    public static String kilometer(int kilometers,double meters,long durationMs) {
        long seconds=Math.max(0,durationMs/1000),pace=Math.round(seconds/Math.max(.001,meters/1000));
        return String.format(Locale.CHINA,"已跑%d公里，用时%d分%d秒，平均配速%d分%d秒每公里。",kilometers,seconds/60,seconds%60,pace/60,pace%60);
    }
}
