package cn.lightrun.app;

import android.media.AudioFormat;
import android.speech.tts.*;
import java.util.*;

/** Test APK only: records synthesis requests and returns a short PCM tone, not spoken Chinese. */
public final class TestSpeechService extends TextToSpeechService {
    protected int onIsLanguageAvailable(String language,String country,String variant){return TextToSpeech.LANG_COUNTRY_AVAILABLE;}
    protected int onLoadLanguage(String language,String country,String variant){return TextToSpeech.LANG_COUNTRY_AVAILABLE;}
    protected String[] onGetLanguage(){return new String[]{"zho","CHN",""};}
    protected void onStop(){}
    public List<Voice> onGetVoices(){return Arrays.asList(
            new Voice("test-zh-offline",Locale.SIMPLIFIED_CHINESE,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()),
            new Voice("test-zh-online",Locale.SIMPLIFIED_CHINESE,Voice.QUALITY_VERY_HIGH,Voice.LATENCY_VERY_LOW,true,Collections.emptySet()));}
    public int onIsValidVoiceName(String name){return name.startsWith("test-zh-")?TextToSpeech.SUCCESS:TextToSpeech.ERROR;}
    public int onLoadVoice(String name){return onIsValidVoiceName(name);}
    public String onGetDefaultVoiceNameFor(String language,String country,String variant){return "test-zh-offline";}
    protected void onSynthesizeText(SynthesisRequest request,SynthesisCallback callback) {
        sendBroadcast(new android.content.Intent("cn.lightrun.TEST_SYNTHESIZE").setPackage("cn.lightrun.app")
                .putExtra("voice",request.getVoiceName()).putExtra("text",request.getCharSequenceText().toString()));
        callback.start(16000,AudioFormat.ENCODING_PCM_16BIT,1);
        byte[] buffer=new byte[3200];
        for(int i=0;i<1600;i++){short value=(short)(Math.sin(i*2*Math.PI*440/16000)*1500);buffer[2*i]=(byte)value;buffer[2*i+1]=(byte)(value>>8);}
        callback.audioAvailable(buffer,0,buffer.length);callback.done();
    }
}
