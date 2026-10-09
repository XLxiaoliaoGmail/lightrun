package cn.lightrun.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure running state and GPS filter; monotonic clocks drive duration. Coordinates are WGS84. */
public final class RunSession {
    public static final class Point {
        public final double lat, lon;
        public final long time;
        public final float accuracy;
        public final int segment;
        public Point(double lat, double lon, long time, float accuracy, int segment) {
            this.lat = lat; this.lon = lon; this.time = time; this.accuracy = accuracy; this.segment = segment;
        }
    }
    public String id = UUID.randomUUID().toString();
    public long startedAt, endedAt, accumulatedMs, activeSince;
    public double distanceM;
    public long steps;
    public boolean stepsRecorded;
    public int announcedKilometer;
    public boolean active, interrupted;
    public int segment;
    public final List<Point> points = new ArrayList<>();
    private boolean newSegment = true;
    public RunSession(long wallClock) { startedAt = wallClock; }
    public void resume(long monotonicMs) {
        if (!active) { active = true; activeSince = monotonicMs; newSegment = true; interrupted = false; }
    }
    public long duration(long now) { return accumulatedMs + (active ? Math.max(0, now - activeSince) : 0); }
    public void pause(long now) { accumulatedMs = duration(now); active = false; newSegment = true; }
    public int takeKilometerMilestone() {
        if(!active||!Double.isFinite(distanceM)||distanceM<0)return 0;
        int kilometer=(int)(distanceM/1000);
        if(kilometer<=announcedKilometer)return 0;
        announcedKilometer=kilometer;return kilometer;
    }
    public boolean add(double lat, double lon, float accuracy, long wallTime, long fixAgeMs) {
        if (!active || !Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180
                || !Float.isFinite(accuracy) || accuracy <= 0 || accuracy > 40 || fixAgeMs < 0 || fixAgeMs > 10000) return false;
        Point last = points.isEmpty() ? null : points.get(points.size() - 1);
        if (last != null && wallTime <= last.time) return false;
        double step = last == null ? 0 : meters(last.lat, last.lon, lat, lon);
        if (!newSegment && last != null) {
            double seconds = (wallTime - last.time) / 1000.0;
            if (seconds > 30) newSegment = true;
            else {
                // Ignore stationary drift, duplicate fixes and impossible jumps for a running app.
                if (step < Math.max(3, Math.min(10, (accuracy + last.accuracy) * .2)) || step / seconds > 12) return false;
                distanceM += step;
            }
        }
        if (newSegment && last != null) segment++;
        points.add(new Point(lat, lon, wallTime, accuracy, segment));
        newSegment = false;
        return true;
    }
    public static double meters(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.toRadians(lat2-lat1), b = Math.toRadians(lon2-lon1);
        double h = Math.sin(a/2)*Math.sin(a/2) + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(b/2)*Math.sin(b/2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(Math.min(1,h)), Math.sqrt(Math.max(0,1-h)));
    }
}
