package cn.lightrun.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public final class RunStore {
    private final File root, current;
    public RunStore(Context context) {
        root = new File(context.getFilesDir(), "runs");
        if (!root.exists()) root.mkdirs();
        current = new File(root, "current.json");
    }
    public void checkpoint(RunSession s, long now) throws IOException { write(current, encode(s, now)); }
    public void discard() { new AtomicFile(current).delete(); }
    public void save(RunSession s, long now) throws IOException {
        write(new File(root, s.id + ".json"), encode(s, now));
        discard();
    }
    public void delete(RunSession s) { new AtomicFile(new File(root, s.id + ".json")).delete(); }
    public RunSession restore() throws IOException {
        if (!current.exists() && !new File(current.getPath()+".bak").exists()) return null;
        RunSession s = read(current);
        s.interrupted = s.active || s.interrupted;
        s.active = false; // Never silently restart GPS after a killed process.
        return s;
    }
    public List<RunSession> history() throws IOException {
        List<RunSession> runs = new ArrayList<>();
        File[] files = root.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("current.json"));
        if (files == null) throw new IOException("无法读取记录目录");
        for (File file : files) runs.add(read(file));
        runs.sort((a,b) -> Long.compare(b.startedAt, a.startedAt));
        return runs;
    }
    private static JSONObject encode(RunSession s, long now) throws IOException {
        try {
            JSONObject j = new JSONObject();
            j.put("version",2).put("id",s.id).put("startedAt",s.startedAt).put("endedAt",s.endedAt)
                    .put("durationMs",s.duration(now)).put("distanceM",s.distanceM).put("active",s.active)
                    .put("interrupted",s.interrupted).put("segment",s.segment)
                    .put("steps",s.steps).put("stepsRecorded",s.stepsRecorded).put("announcedKilometer",s.announcedKilometer);
            JSONArray points = new JSONArray();
            for (RunSession.Point p:s.points) points.put(new JSONArray().put(p.lat).put(p.lon).put(p.time).put(p.accuracy).put(p.segment));
            j.put("points",points); return j;
        } catch (Exception e) { throw new IOException("记录编码失败",e); }
    }
    private static RunSession read(File file) throws IOException {
        try (InputStream input = new AtomicFile(file).openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer,0,count);
            JSONObject j = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            RunSession s = new RunSession(j.getLong("startedAt"));
            s.id=j.getString("id"); s.endedAt=j.getLong("endedAt"); s.accumulatedMs=j.getLong("durationMs");
            s.distanceM=j.getDouble("distanceM"); s.active=j.getBoolean("active");
            s.interrupted=j.optBoolean("interrupted"); s.segment=j.optInt("segment");
            s.steps=Math.max(0,j.optLong("steps"));s.stepsRecorded=j.optBoolean("stepsRecorded");
            s.announcedKilometer=Math.max((int)(s.distanceM/1000),j.optInt("announcedKilometer"));
            JSONArray points=j.getJSONArray("points");
            for(int i=0;i<points.length();i++) { JSONArray p=points.getJSONArray(i); s.points.add(new RunSession.Point(p.getDouble(0),p.getDouble(1),p.getLong(2),(float)p.getDouble(3),p.getInt(4))); }
            return s;
        } catch(Exception e) { throw new IOException("记录读取失败，请保留应用数据",e); }
    }
    private static void write(File file, JSONObject data) throws IOException {
        AtomicFile atomic=new AtomicFile(file); FileOutputStream stream=null;
        try { stream=atomic.startWrite(); stream.write(data.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(stream); }
        catch(IOException e) { if(stream!=null) atomic.failWrite(stream); throw e; }
    }
    public static void gpx(RunSession s, OutputStream stream) throws IOException {
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US); date.setTimeZone(TimeZone.getTimeZone("UTC"));
        BufferedWriter w=new BufferedWriter(new OutputStreamWriter(stream,StandardCharsets.UTF_8));
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"LightRun\" xmlns=\"http://www.topografix.com/GPX/1/1\"><metadata><time>"+date.format(new Date(s.startedAt))+"</time></metadata><trk><name>轻跑</name>\n");
        int segment=-1;
        for(RunSession.Point p:s.points) {
            if(segment!=p.segment) { if(segment!=-1) w.write("</trkseg>\n"); w.write("<trkseg>\n"); segment=p.segment; }
            w.write(String.format(Locale.US,"<trkpt lat=\"%.8f\" lon=\"%.8f\"><time>%s</time></trkpt>\n",p.lat,p.lon,date.format(new Date(p.time))));
        }
        if(segment!=-1) w.write("</trkseg>\n");
        w.write("</trk></gpx>\n"); w.flush();
    }
}
