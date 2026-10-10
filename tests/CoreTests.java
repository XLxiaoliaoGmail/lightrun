package cn.lightrun.app;

public final class CoreTests {
    private static int checks;
    private static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
    private static void near(double actual,double expected,double delta,String message) { check(Math.abs(actual-expected)<=delta,message+": "+actual); }
    public static void main(String[] args) {
        RunSession s=new RunSession(1000);s.resume(500);check(s.duration(2500)==2000,"monotonic duration");
        check(s.add(31,121,5,1000,0),"first fix");
        check(!s.add(31,121,5,2000,0),"stationary duplicate");
        check(!s.add(31.00001,121,5,3000,0),"GPS drift");
        check(!s.add(32,121,5,4000,0),"teleport rejected");
        check(!s.add(31.0001,121,80,4000,0),"poor accuracy rejected");
        check(!s.add(31.0001,121,5,4000,10001),"stale fix rejected");
        check(!s.add(Double.NaN,121,5,4000,0),"NaN rejected");
        check(!s.add(91,121,5,4000,0),"invalid latitude rejected");
        check(!s.add(31,181,5,4000,0),"invalid longitude rejected");
        check(!s.add(31,121,Float.NaN,4000,0),"invalid accuracy rejected");
        check(s.add(31.0001,121,5,5000,0),"running step accepted");near(s.distanceM,11.1195,.01,"distance");
        check(!s.add(31.0002,121,5,5000,0),"out of order rejected");
        s.pause(3500);check(s.duration(9999)==3000,"pause freezes timer");
        check(!s.add(31.001,121,5,6000,0),"paused fixes ignored");
        s.resume(9000);check(s.add(31.001,121,5,8000,0),"resume starts segment");near(s.distanceM,11.1195,.01,"pause gap excluded");
        check(s.points.get(2).segment==1,"pause split");
        check(s.add(31.002,121,5,50000,0),"signal gap starts segment");near(s.distanceM,11.1195,.01,"signal gap excluded");
        check(s.points.get(3).segment==2,"signal split");
        check(s.duration(12000)==6000,"resumed monotonic duration");
        near(RunSession.meters(0,179.999,0,-179.999),222.39,.1,"date line");
        near(RunSession.meters(0,0,0,180),20015086.8,1,"antipodal points");
        check(Format.duration(65000).equals("01:05"),"duration format");
        check(Format.duration(3661000).equals("1:01:01"),"long duration");
        check(Format.distance(1234).equals("1.23"),"distance format");
        check(Format.pace(1000,300000).equals("5′00″"),"pace units");
        check(Format.pace(5,300000).equals("—"),"short distance pace unavailable");
        StepAccumulator steps=new StepAccumulator();steps.start(1000000000L);
        check(steps.detector(999999999L)==0,"pre-start detector event excluded");
        check(steps.detector(1100000000L)==1,"detected step counted");
        check(steps.detector(1100000000L)==0,"duplicate detector event ignored");
        steps.stop();check(steps.detector(1200000000L)==0,"paused step excluded");
        steps.start(2000000000L);check(steps.detector(1500000000L)==0,"delayed pause event excluded on resume");
        check(steps.detector(2100000000L)==1,"resume step counted");
        steps.start(3000000000L);check(steps.counter(500,3000000000L)==0,"counter initial cumulative baseline excluded");
        check(steps.counter(505,4000000000L)==5,"counter difference counted");
        check(steps.counter(999,3500000000L)==0,"stale counter cannot poison baseline");
        check(steps.counter(507,5000000000L)==2,"counter after stale event");
        check(steps.counter(2,6000000000L)==0,"sensor reset rebaselines without negative steps");
        check(steps.counter(4,7000000000L)==2,"steps after reset");
        check(steps.counter(Float.NaN,8000000000L)==0,"invalid counter value");
        check(steps.counter(-1,8000000000L)==0,"negative counter value");
        check(steps.counter(10004,8000000000L)==0,"impossible counter jump rejected");
        steps.stop();steps.start(9000000000L);check(steps.counter(10020,9000000000L)==0,"resume baseline excludes pause walking");
        check(steps.counter(10022,10000000000L)==2,"counter resume difference");
        RunSession milestones=new RunSession(0);milestones.resume(0);milestones.distanceM=999.99;
        check(milestones.takeKilometerMilestone()==0,"no premature kilometer");
        milestones.distanceM=1000;check(milestones.takeKilometerMilestone()==1,"first kilometer");
        check(milestones.takeKilometerMilestone()==0,"no duplicate announcement");
        milestones.pause(10);milestones.distanceM=2000;check(milestones.takeKilometerMilestone()==0,"no paused announcement");
        milestones.resume(20);check(milestones.takeKilometerMilestone()==2,"resume next kilometer");
        milestones.distanceM=3100;check(milestones.takeKilometerMilestone()==3,"later milestone");
        milestones.distanceM=5000;check(milestones.takeKilometerMilestone()==5,"large increment announces only latest");
        milestones.distanceM=Double.NaN;check(milestones.takeKilometerMilestone()==0,"invalid milestone distance");
        RunSession completed=new RunSession(0);completed.resume(0);completed.distanceM=1005;
        check(completed.pendingKilometerMilestone()==1&&completed.announcedKilometer==0,"queueing is not playback completion");
        check(completed.pendingKilometerMilestone()==1,"failed playback leaves milestone pending");
        completed.completeKilometerMilestone(2);check(completed.announcedKilometer==0,"future kilometer cannot be completed");
        completed.completeKilometerMilestone(1);check(completed.pendingKilometerMilestone()==0,"completed playback prevents duplicates");
        completed.distanceM=2005;completed.pause(10);completed.completeKilometerMilestone(2);
        check(completed.announcedKilometer==1,"late completion after pause cannot consume milestone");
        historyTests();analysisTests();
        System.out.println("PASS: "+checks+" core assertions");
    }
    private static RunSession record(String date,java.time.ZoneId zone,double meters,long steps,boolean measured){RunSession r=new RunSession(java.time.LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli());r.distanceM=meters;r.steps=steps;r.stepsRecorded=measured;r.accumulatedMs=60000;return r;}
    private static void historyTests(){
        java.time.ZoneId zone=java.time.ZoneId.of("Asia/Shanghai");java.time.LocalDate today=java.time.LocalDate.parse("2026-10-10");
        RunSession a=record("2026-10-10",zone,1000,1200,true),b=record("2026-10-10",zone,500,0,false),c=record("2026-10-04",zone,2000,2500,true),d=record("2026-10-03",zone,3000,3000,true),e=record("2026-10-11",zone,4000,4000,true);
        java.util.List<RunSession> list=java.util.Arrays.asList(a,b,c,d,e);HistorySummary h=new HistorySummary(list,today,7,zone);
        check(h.days.size()==7&&h.days.get(0).date.toString().equals("2026-10-04"),"calendar seven day range includes today");
        check(h.runs==3&&h.measuredRuns==2,"out of range and future runs excluded");near(h.meters,3500,0,"seven day distance");check(h.steps==3700&&h.duration==180000,"recorded steps and durations summed");
        check(h.days.get(6).runs==2&&h.days.get(6).stepLabel().startsWith("≥"),"same day combines runs without fabricating missing steps");
        check(h.stepLabel().startsWith("已记录"),"partial aggregate clearly identified");check(h.days.get(1).runs==0&&h.days.get(1).steps==0,"empty days remain present");
        HistorySummary thirty=new HistorySummary(list,today,30,zone);check(thirty.days.size()==30&&thirty.runs==4,"thirty day range");
        HistorySummary unknown=new HistorySummary(java.util.List.of(b),today,7,zone);check(unknown.days.get(6).stepLabel().equals("未记录")&&unknown.stepLabel().equals("步数未记录"),"unknown steps distinct from zero");
        RunSession invalid=record("2026-10-10",zone,Double.NaN,-20,true);invalid.accumulatedMs=-10;HistorySummary valid=new HistorySummary(java.util.List.of(invalid),today,7,zone);check(valid.meters==0&&valid.steps==0&&valid.duration==0,"invalid aggregate values bounded");
        RunSession midnight=new RunSession(java.time.Instant.parse("2026-10-09T16:30:00Z").toEpochMilli());HistorySummary local=new HistorySummary(java.util.List.of(midnight),today,7,zone);check(local.days.get(6).runs==1,"local date rather than UTC date");
        java.time.ZoneId ny=java.time.ZoneId.of("America/New_York");HistorySummary dst=new HistorySummary(java.util.List.of(record("2026-11-01",ny,1,1,true),record("2026-11-02",ny,1,1,true)),java.time.LocalDate.parse("2026-11-02"),7,ny);check(dst.days.get(5).runs==1&&dst.days.get(6).runs==1,"DST does not shift calendar day buckets");
    }
    private static void analysisTests(){
        RunSession r=new RunSession(1000);r.endedAt=121000;r.accumulatedMs=60000;r.distanceM=100;r.stepsRecorded=true;r.steps=120;
        r.points.add(new RunSession.Point(0,0,1000,4,0));r.points.add(new RunSession.Point(.0001,0,6000,6,0));r.points.add(new RunSession.Point(.01,0,16000,8,1));r.points.add(new RunSession.Point(.0101,0,21000,10,1));r.points.add(new RunSession.Point(.0102,0,61001,12,1));r.points.add(new RunSession.Point(1,0,62000,14,1));
        RunAnalysis a=new RunAnalysis(r);check(a.samples.size()==2&&a.segments==2,"speed excludes segment boundaries long gaps and impossible jumps");near(a.gpsMeters,22.239,.02,"analysis distance excludes gaps");near(a.peakSpeed,8.006,.02,"derived speed km per hour");near(a.meanAccuracy,9,0,"mean GPS accuracy");near(a.worstAccuracy,14,0,"worst GPS accuracy");check(a.wallMs==120000&&a.inactiveMs==60000,"active and inactive times");near(RunAnalysis.cadence(r),120,0,"measured step cadence");near(RunAnalysis.stride(r),100d/120,0,"measured mean stride");r.stepsRecorded=false;check(RunAnalysis.cadence(r)==0&&RunAnalysis.stride(r)==0,"missing steps cannot create estimates");
        r.endedAt=2000;RunAnalysis brokenClock=new RunAnalysis(r);check(brokenClock.wallMs==60000&&brokenClock.inactiveMs==0,"wall clock rollback cannot make negative activity chart");
    }
}
