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
        System.out.println("PASS: "+checks+" core assertions");
    }
}
