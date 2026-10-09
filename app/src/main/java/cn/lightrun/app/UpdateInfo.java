package cn.lightrun.app;

import org.json.JSONObject;
import java.net.URI;

/** Strict, credential-free public update manifest shared by both mirrors. */
public final class UpdateInfo {
    public final int code;
    public final String version,notes,giteeUrl,githubUrl,sha256;
    private UpdateInfo(int code,String version,String notes,String gitee,String github,String sha) {
        this.code=code;this.version=version;this.notes=notes;giteeUrl=gitee;githubUrl=github;sha256=sha;
    }
    public static UpdateInfo parse(String text) throws Exception {
        if(text.length()>32768)throw new IllegalArgumentException("Oversized manifest");
        JSONObject j=new JSONObject(text);
        if(j.getInt("schema")!=1||!"cn.lightrun.app".equals(j.getString("applicationId")))throw new IllegalArgumentException("Wrong application");
        long code=j.getLong("versionCode");String name=j.getString("versionName"),sha=j.getString("sha256"),notes=j.getString("notes");
        if(code<1||code>Integer.MAX_VALUE||!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!sha.matches("[a-f0-9]{64}")||notes.length()>4000)
            throw new IllegalArgumentException("Invalid version");
        String gitee=j.getString("giteeUrl"),github=j.getString("githubUrl");
        validate(gitee,"gitee.com","/XLxiaoliao/lightrun/releases/tag/v"+name);
        validate(github,"github.com","/XLxiaoliaoGmail/lightrun/releases/tag/v"+name);
        return new UpdateInfo((int)code,name,notes,gitee,github,sha);
    }
    private static void validate(String value,String host,String path) throws Exception {
        URI uri=new URI(value);
        if(!"https".equals(uri.getScheme())||!host.equals(uri.getHost())||!path.equals(uri.getPath())
                ||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
            throw new IllegalArgumentException("Unexpected release URL");
    }
    public static UpdateInfo newest(UpdateInfo gitee,UpdateInfo github) {
        if(gitee==null)return github;
        if(github==null)return gitee;
        if(gitee.code==github.code&&!gitee.sha256.equals(github.sha256))return null; // Conflicting mirrors: fail closed.
        return github.code>gitee.code?github:gitee;
    }
}
