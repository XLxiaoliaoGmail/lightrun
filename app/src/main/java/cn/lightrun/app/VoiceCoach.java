package cn.lightrun.app;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;

/** Offline by default. System compatibility mode requires an explicit user preference. */
public final class VoiceCoach {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private TextToSpeech tts;
    private boolean ready,closed,focused;
    private String pending;
    private boolean compatibility;
    private Bundle parameters;
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
        compatibility=UpdateChecker.preferences(context).getBoolean("systemVoice",false);
        status="正在检查中文语音";
        tts=new TextToSpeech(context,result->main.post(()->initialize(result)));
    }
    private void initialize(int result) {
        if(closed||tts==null)return;
        if(result!=TextToSpeech.SUCCESS){status="系统语音引擎不可用";pending=null;return;}
        try {
            parameters=new Bundle();
            boolean selected=compatibility?selectSystemVoice():selectOfflineVoice();
            if(!selected){status=compatibility?"系统引擎未能提供中文语音，请检查系统试听":"无法识别可用的离线中文音色，可尝试系统音色兼容模式";pending=null;return;}
            tts.setAudioAttributes(attributes);tts.setSpeechRate(1f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id){}
                public void onDone(String id){main.post(()->releaseFocus());}
                @Override public void onError(String id){onError(id,TextToSpeech.ERROR);}
                @Override public void onError(String id,int error){main.post(()->{ready=false;status="语音播放失败（"+error+"），请检查系统语音设置";releaseFocus();});}
            });
            ready=true;status=compatibility?"系统中文音色已就绪 · 离线能力由系统引擎决定":"离线中文语音已就绪";
            String text=pending;pending=null;if(text!=null)speak(text);
        } catch(RuntimeException e){status="系统语音引擎不可用";pending=null;}
    }
    private static boolean chinese(Locale locale) {
        if(locale==null)return false;
        String language=locale.getLanguage();
        return "zh".equalsIgnoreCase(language)||"zho".equalsIgnoreCase(language)||"chi".equalsIgnoreCase(language);
    }
    private static boolean local(Voice voice) {
        return voice!=null&&chinese(voice.getLocale())&&!voice.isNetworkConnectionRequired()
                &&(voice.getFeatures()==null||!voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
    }
    private static boolean mainland(Voice voice) {
        String country=voice.getLocale().getCountry();return "CN".equalsIgnoreCase(country)||"CHN".equalsIgnoreCase(country);
    }
    private boolean chooseOffline() {
        // Keep the user's preferred Chinese voice when the engine exposes it as local.
        Voice current=tts.getVoice();
        if(local(current)&&tts.setVoice(current)==TextToSpeech.SUCCESS)return true;
        Set<Voice> voices=tts.getVoices();List<Voice> candidates=new ArrayList<>();
        if(voices!=null)for(Voice voice:voices)if(local(voice))candidates.add(voice);
        candidates.sort(Comparator.comparingInt((Voice v)->mainland(v)?1:0).thenComparingInt(Voice::getQuality).reversed());
        for(Voice voice:candidates)if(tts.setVoice(voice)==TextToSpeech.SUCCESS)return true;
        return false;
    }
    @SuppressWarnings("deprecation")
    private boolean selectOfflineVoice() {
        if(chooseOffline())return true;
        // Some engines populate their voice list only after a language capability query.
        tts.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE);
        if(chooseOffline())return true;
        Set<Voice> voices=tts.getVoices();
        // Legacy embedded synthesis is used only when there is no conflicting modern metadata.
        if(voices!=null&&!voices.isEmpty())return false;
        Set<String> features=tts.getFeatures(Locale.SIMPLIFIED_CHINESE);
        if(features==null||!features.contains(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS))return false;
        if(!chinese(tts.getLanguage())&&tts.setLanguage(Locale.SIMPLIFIED_CHINESE)<TextToSpeech.LANG_AVAILABLE)return false;
        parameters.putBoolean(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS,true);
        parameters.putBoolean(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS,false);
        return true;
    }
    @SuppressWarnings("deprecation")
    private boolean selectSystemVoice() {
        // Preserve the system preset. This opt-in path does not claim to enforce offline synthesis.
        if(chinese(tts.getLanguage()))return true;
        return tts.setLanguage(Locale.SIMPLIFIED_CHINESE)>=TextToSpeech.LANG_AVAILABLE;
    }
    public void speak(String text) {
        if(closed)return;
        if(tts==null){pending=text;prepare();return;}
        if(!ready){
            pending=text;
            if(!"正在检查中文语音".equals(status)){tts.shutdown();tts=null;prepare();}
            return;
        }
        if(audio.getMode()!=AudioManager.MODE_NORMAL)return; // Do not interrupt a call.
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){status="音频正忙，本次播报已跳过";return;}
        focused=true;
        if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,parameters,"kilometer")!=TextToSpeech.SUCCESS){ready=false;status="语音播放失败，请检查系统语音设置";releaseFocus();}
    }
    public static String kilometer(int kilometers,double meters,long durationMs) {
        long seconds=Math.max(0,durationMs/1000),pace=Math.round(seconds/Math.max(.001,meters/1000));
        return String.format(Locale.CHINA,"已跑%d公里，用时%d分%d秒，平均配速%d分%d秒每公里。",kilometers,seconds/60,seconds%60,pace/60,pace%60);
    }
    private void releaseFocus(){if(focused){audio.abandonAudioFocusRequest(focus);focused=false;}}
    public void stop(){pending=null;if(tts!=null)tts.stop();releaseFocus();}
    public void close(){closed=true;stop();if(tts!=null){tts.shutdown();tts=null;}}
}
