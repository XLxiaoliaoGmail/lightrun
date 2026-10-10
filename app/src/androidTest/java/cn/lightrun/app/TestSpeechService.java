package cn.lightrun.app;

import android.media.AudioFormat;
import android.speech.tts.*;
import java.util.*;

/** Test APK only: records synthesis requests and returns a short PCM tone, not spoken Chinese. */
public final class TestSpeechService extends TextToSpeechService {
    private boolean queried;
    private static int failureCount;
    private String mode(){return android.provider.Settings.Global.getString(getContentResolver(),"lightrun_test_tts_mode");}
    protected int onIsLanguageAvailable(String language,String country,String variant){queried=true;return TextToSpeech.LANG_COUNTRY_AVAILABLE;}
    protected int onLoadLanguage(String language,String country,String variant){return TextToSpeech.LANG_COUNTRY_AVAILABLE;}
    protected String[] onGetLanguage(){return new String[]{"zho","CHN",""};}
    protected void onStop(){}
    public List<Voice> onGetVoices(){
        String mode=mode();
        if("legacy".equals(mode)||"unknown".equals(mode)||("lazy".equals(mode)&&!queried))return Collections.emptyList();
        Voice online=new Voice("test-zh-online",Locale.SIMPLIFIED_CHINESE,Voice.QUALITY_VERY_HIGH,Voice.LATENCY_VERY_LOW,true,Collections.emptySet());
        if("cloud".equals(mode))return Collections.singletonList(online);
        Locale locale="alias".equals(mode)?new Locale("zho","CHN"):Locale.SIMPLIFIED_CHINESE;
        Set<String> features="missing".equals(mode)?Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED):Collections.emptySet();
        Voice offline=new Voice("test-zh-offline",locale,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,features);
        if("reject".equals(mode))return Arrays.asList(new Voice("test-zh-broken",locale,Voice.QUALITY_VERY_HIGH,Voice.LATENCY_NORMAL,false,features),offline,online);
        return Arrays.asList(offline,online);
    }
    @SuppressWarnings("deprecation")
    protected Set<String> onGetFeaturesForLanguage(String language,String country,String variant){
        return "legacy".equals(mode())?Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS):Collections.emptySet();
    }
    public int onIsValidVoiceName(String name){return name.startsWith("test-zh-")&&!"test-zh-broken".equals(name)?TextToSpeech.SUCCESS:TextToSpeech.ERROR;}
    public int onLoadVoice(String name){return "test-zh-broken".equals(name)?TextToSpeech.ERROR:onIsValidVoiceName(name);}
    public String onGetDefaultVoiceNameFor(String language,String country,String variant){return "cloud".equals(mode())?"test-zh-online":"reject".equals(mode())?"test-zh-broken":"test-zh-offline";}
    protected void onSynthesizeText(SynthesisRequest request,SynthesisCallback callback) {
        sendBroadcast(new android.content.Intent("cn.lightrun.TEST_SYNTHESIZE").setPackage("cn.lightrun.app")
                .putExtra("voice",request.getVoiceName()).putExtra("text",request.getCharSequenceText().toString())
                .putExtra("embedded",request.getParams().getBoolean("embeddedTts",false)));
        String mode=mode();
        if("always-fail".equals(mode)||("fail-once".equals(mode)&&failureCount++==0)){
            callback.error(TextToSpeech.ERROR_SYNTHESIS);callback.done();return;
        }
        if("slow".equals(mode))try{Thread.sleep(700);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        callback.start(16000,AudioFormat.ENCODING_PCM_16BIT,1);
        byte[] buffer=new byte[3200];
        for(int i=0;i<1600;i++){short value=(short)(Math.sin(i*2*Math.PI*440/16000)*1500);buffer[2*i]=(byte)value;buffer[2*i+1]=(byte)(value>>8);}
        callback.audioAvailable(buffer,0,buffer.length);callback.done();
    }
}
