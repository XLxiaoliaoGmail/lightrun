package cn.lightrun.app;

import java.util.*;

/** Derived GPS measurements, never fills missing steps, gaps or coordinates. */
public final class RunAnalysis {
    public static final class Sample {
        public final double meters,speed,accuracy;
        public final int segment;
        Sample(double meters,double speed,double accuracy,int segment){this.meters=meters;this.speed=speed;this.accuracy=accuracy;this.segment=segment;}
    }
    public final List<Sample> samples=new ArrayList<>();
    public int segments;
    public double meanAccuracy,worstAccuracy,peakSpeed,gpsMeters;
    public long wallMs,inactiveMs;
    public RunAnalysis(RunSession run) {
        Set<Integer> groups=new HashSet<>();int accurate=0;double accuracySum=0;
        for(int i=0;i<run.points.size();i++) {
            RunSession.Point p=run.points.get(i);groups.add(p.segment);
            if(Float.isFinite(p.accuracy)&&p.accuracy>0){accuracySum+=p.accuracy;accurate++;worstAccuracy=Math.max(worstAccuracy,p.accuracy);}
            if(i==0)continue;
            RunSession.Point previous=run.points.get(i-1);long dt=p.time-previous.time;
            if(previous.segment!=p.segment||dt<=0||dt>30000)continue;
            double distance=RunSession.meters(previous.lat,previous.lon,p.lat,p.lon),speed=distance/dt*3600;
            if(!Double.isFinite(speed)||speed>43.2)continue;
            gpsMeters+=distance;peakSpeed=Math.max(peakSpeed,speed);
            samples.add(new Sample(gpsMeters,speed,p.accuracy,p.segment));
        }
        segments=groups.size();meanAccuracy=accurate==0?0:accuracySum/accurate;
        wallMs=Math.max(Math.max(0,run.accumulatedMs),run.endedAt>run.startedAt?run.endedAt-run.startedAt:0);
        inactiveMs=Math.max(0,wallMs-Math.max(0,run.accumulatedMs));
    }
    public static double cadence(RunSession run){return run.stepsRecorded&&run.accumulatedMs>0?Math.max(0,run.steps)*60000d/run.accumulatedMs:0;}
    public static double stride(RunSession run){return run.stepsRecorded&&run.steps>0?Math.max(0,run.distanceM)/run.steps:0;}
}
