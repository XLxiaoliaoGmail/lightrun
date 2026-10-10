package cn.lightrun.app;

import java.time.*;
import java.util.*;

/** Calendar-day totals of recorded runs, grouped by their local start date. */
public final class HistorySummary {
    public static final class Day {
        public final LocalDate date;
        public double meters;
        public long steps,duration;
        public int runs,measuredRuns;
        Day(LocalDate date){this.date=date;}
        public String stepLabel(){return runs>0&&measuredRuns==0?"未记录":(measuredRuns<runs?"≥":"")+String.format(Locale.CHINA,"%,d",steps)+" 步";}
    }
    public final List<Day> days=new ArrayList<>();
    public double meters;
    public long steps,duration;
    public int runs,measuredRuns;
    public HistorySummary(List<RunSession> records,LocalDate today,int count,ZoneId zone) {
        if(count!=7&&count!=30)throw new IllegalArgumentException("Unsupported period");
        LocalDate first=today.minusDays(count-1);
        for(int i=0;i<count;i++)days.add(new Day(first.plusDays(i)));
        for(RunSession run:records) {
            LocalDate date=Instant.ofEpochMilli(run.startedAt).atZone(zone).toLocalDate();
            long index=java.time.temporal.ChronoUnit.DAYS.between(first,date);
            if(index<0||index>=count)continue;
            Day day=days.get((int)index);day.runs++;runs++;
            double distance=Double.isFinite(run.distanceM)?Math.max(0,run.distanceM):0;
            day.meters+=distance;meters+=distance;
            long time=Math.max(0,run.accumulatedMs);day.duration+=time;duration+=time;
            if(run.stepsRecorded){day.measuredRuns++;measuredRuns++;long value=Math.max(0,run.steps);day.steps+=value;steps+=value;}
        }
    }
    public String stepLabel(){return runs>0&&measuredRuns==0?"步数未记录":(measuredRuns<runs?"已记录 ":"")+String.format(Locale.CHINA,"%,d 步",steps);}
}
