package cn.lightrun.app;
import java.util.Locale;
public final class Format {
    private Format() { }
    public static String duration(long ms) { long s=ms/1000; return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,(s/60)%60,s%60):String.format(Locale.US,"%02d:%02d",s/60,s%60); }
    public static String distance(double m) { return String.format(Locale.US,"%.2f",m/1000); }
    public static String pace(double m,long ms) {
        if(m<50 || ms<=0) return "—";
        long seconds=Math.round(ms/m);
        return String.format(Locale.US,"%d′%02d″",seconds/60,seconds%60);
    }
}
