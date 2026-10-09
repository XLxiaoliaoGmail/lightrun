package cn.lightrun.app;

/** Per-active-segment step accounting. Never estimates steps from GPS distance. */
public final class StepAccumulator {
    private long startNanos, lastNanos, counter = -1;
    private boolean active;
    public void start(long nowNanos) { active=true;startNanos=nowNanos;lastNanos=nowNanos-1;counter=-1; }
    public void stop() { active=false;counter=-1; }
    public long detector(long timestamp) {
        if(!accept(timestamp))return 0;
        lastNanos=timestamp;return 1;
    }
    public long counter(float value,long timestamp) {
        if(!Float.isFinite(value)||value<0||!accept(timestamp))return 0;
        long current=(long)value,previous=counter,elapsed=timestamp-lastNanos;
        counter=current;lastNanos=timestamp;
        if(previous<0||current<previous)return 0; // Initial baseline or sensor reset.
        long delta=current-previous;
        return delta<=12+6*(elapsed/1000000000L)?delta:0;
    }
    private boolean accept(long timestamp) {return active&&timestamp>=startNanos&&timestamp>lastNanos;}
}
