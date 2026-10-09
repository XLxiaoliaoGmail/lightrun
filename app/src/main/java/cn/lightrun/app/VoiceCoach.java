package cn.lightrun.app;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.util.Locale;
import java.util.Set;

/** Uses only a locally installed Chinese voice; never falls back to cloud synthesis. */
public final class VoiceCoach {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private TextToSpeech tts;
    private boolean ready,closed,focused;
    private String pending;
    public String status="尚未检查离线中文语音";
    private final AudioAttributes attributes=new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final AudioFocusRequest focus;
    public VoiceCoach(Context context) {
        this.context=context;audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change->{if(change<0)stop();},main).build();
    }
    public void prepare() {
        if(closed||tts!=null)return;
        status="正在检查离线中文语音";
        tts=new TextToSpeech(context,result->main.post(()->initialize(result)));
    }
    private void initialize(int result) {
        if(closed||tts==null)return;
        if(result!=TextToSpeech.SUCCESS){status="系统语音引擎不可用";pending=null;return;}
        try {
            Set<Voice> voices=tts.getVoices();Voice chosen=null;
            if(voices!=null)for(Voice voice:voices) {
                if(!"zh".equals(voice.getLocale().getLanguage())||voice.isNetworkConnectionRequired()
                        ||(voice.getFeatures()!=null&&voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))continue;
                if(chosen==null||("CN".equals(voice.getLocale().getCountry())&&!"CN".equals(chosen.getLocale().getCountry()))
                        ||(voice.getLocale().getCountry().equals(chosen.getLocale().getCountry())&&voice.getQuality()>chosen.getQuality()))chosen=voice;
            }
            if(chosen==null||tts.setVoice(chosen)!=TextToSpeech.SUCCESS){status="未安装离线中文语音，请在系统语音设置中安装";pending=null;return;}
            tts.setAudioAttributes(attributes);tts.setSpeechRate(1f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id){}
                public void onDone(String id){main.post(()->releaseFocus());}
                @Override public void onError(String id){main.post(()->{status="语音播放失败，请检查系统语音设置";releaseFocus();});}
            });
            ready=true;status="离线中文语音已就绪";
            String text=pending;pending=null;if(text!=null)speak(text);
        } catch(RuntimeException e){status="系统语音引擎不可用";pending=null;}
    }
    public void speak(String text) {
        if(closed)return;
        if(tts==null){pending=text;prepare();return;}
        if(!ready){
            pending=text;
            if(!"正在检查离线中文语音".equals(status)){tts.shutdown();tts=null;prepare();}
            return;
        }
        if(audio.getMode()!=AudioManager.MODE_NORMAL)return; // Do not interrupt a call.
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){status="音频正忙，本次播报已跳过";return;}
        focused=true;
        if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"kilometer")!=TextToSpeech.SUCCESS){status="语音播放失败";releaseFocus();}
    }
    public static String kilometer(int kilometers,double meters,long durationMs) {
        long seconds=Math.max(0,durationMs/1000),pace=Math.round(seconds/Math.max(.001,meters/1000));
        return String.format(Locale.CHINA,"已跑%d公里，用时%d分%d秒，平均配速%d分%d秒每公里。",kilometers,seconds/60,seconds%60,pace/60,pace%60);
    }
    private void releaseFocus(){if(focused){audio.abandonAudioFocusRequest(focus);focused=false;}}
    public void stop(){pending=null;if(tts!=null)tts.stop();releaseFocus();}
    public void close(){closed=true;stop();if(tts!=null){tts.shutdown();tts=null;}}
}
