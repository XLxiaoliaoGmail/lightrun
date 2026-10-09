package cn.lightrun.app;

import android.app.job.*;
import android.content.*;

/** Best-effort daily check, scheduled by Android; never installs or downloads an APK. */
public final class UpdateJobService extends JobService {
    private static final int JOB_ID=110;
    private volatile boolean stopped;
    public static void schedule(Context context) {
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(!UpdateChecker.preferences(context).getBoolean("autoUpdate",true)){scheduler.cancel(JOB_ID);return;}
        if(scheduler.getPendingJob(JOB_ID)!=null)return;
        scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(UpdateChecker.INTERVAL).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        stopped=false;
        return UpdateChecker.check(this,false,(info,status)->{if(!stopped)jobFinished(params,false);});
    }
    @Override public boolean onStopJob(JobParameters params){stopped=true;return false;}
}
