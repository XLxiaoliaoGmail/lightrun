package cn.lightrun.app;

/** All coordinates are synthetic test fixtures, not recorded personal locations. */
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
        System.out.println("PASS: "+checks+" core assertions");
    }
}
