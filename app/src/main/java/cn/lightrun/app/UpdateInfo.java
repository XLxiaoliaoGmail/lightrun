package cn.lightrun.app;

import org.json.JSONObject;
import java.net.URI;

/** Strict, credential-free public update manifest shared by both mirrors. */
public final class UpdateInfo {
    public final int code;
    public final String version,notes,sha256;
    private UpdateInfo(int code,String version,String notes,String sha) {
        this.code=code;this.version=version;this.notes=notes;sha256=sha;
    }
    public static UpdateInfo parse(String text) throws Exception {
        if(text.length()>32768)throw new IllegalArgumentException("Oversized manifest");
        JSONObject j=new JSONObject(text);
        if(j.getInt("schema")!=1||!"cn.lightrun.app".equals(j.getString("applicationId")))throw new IllegalArgumentException("Wrong application");
        long code=j.getLong("versionCode");String name=j.getString("versionName"),sha=j.getString("sha256"),notes=j.getString("notes");
        if(code<1||code>Integer.MAX_VALUE||!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!sha.matches("[a-f0-9]{64}")||notes.length()>4000)
            throw new IllegalArgumentException("Invalid version");
        String gitee=j.getString("giteeUrl");
        validate(gitee,"gitee.com","/XLxiaoliao/lightrun/releases/tag/v"+name);
        return new UpdateInfo((int)code,name,notes,sha);
    }
    private static void validate(String value,String host,String path) throws Exception {
        URI uri=new URI(value);
        if(!"https".equals(uri.getScheme())||!host.equals(uri.getHost())||!path.equals(uri.getPath())
                ||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
            throw new IllegalArgumentException("Unexpected release URL");
    }
}
