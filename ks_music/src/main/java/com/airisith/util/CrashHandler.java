package com.airisith.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;

import com.airisith.ksmusic.MusicService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Date;
import java.text.SimpleDateFormat;

/**
 * Created by Administrator on 2015/10/28.
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static CrashHandler INSTANCE = null;// CrashHandler实例
    private Thread.UncaughtExceptionHandler mDefaultHandler;// 系统默认的UncaughtException处理类
    private Context mContext;// 程序的Context对象

    /**
     * 保证只有一个CrashHandler实例
     */
    private CrashHandler() {

    }

    /**
     * 获取CrashHandler实例 ,单例模式
     */
    public static CrashHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CrashHandler();
        }
        return INSTANCE;
    }

    /**
     * 初始化
     *
     * @param context
     */
    public void init(Context context) {
        mContext = context;
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();// 获取系统默认的UncaughtException处理器
        Thread.setDefaultUncaughtExceptionHandler(this);// 设置该CrashHandler为程序的默认处理器
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (!handleException(ex) && mDefaultHandler != null) {
            // 如果自定义的没有处理则让系统默认的异常处理器来处理
            mDefaultHandler.uncaughtException(thread, ex);
        } else {
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            StartApp(mContext);

        }
    }


    public void StartApp(Context context) {
        Intent intent = new Intent(mContext.getApplicationContext(), MusicService.class);
        PendingIntent restartIntent = PendingIntent.getActivity(
                mContext.getApplicationContext(), 0, intent,
                0);
        //退出程序
        AlarmManager mgr = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000,
                restartIntent); // 1秒钟后重启应用
        android.os.Process.killProcess(android.os.Process
                .myPid());
    }

    /**
     * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
     *
     * @param ex 异常信息
     * @return true 如果处理了该异常信息;否则返回false.
     */
    public boolean handleException(Throwable ex) {
        if (ex == null || mContext == null)
            return false;
        final String crashReport = getCrashReport(mContext, ex);
        Log.e("error", crashReport);
        new Thread() {
            public void run() {
                Looper.prepare();
                File file = save2File(crashReport);
                //Toast.makeText(mContext, "很抱歉,程序出现异常,正在重启...", Toast.LENGTH_SHORT).show();
                //sendAppCrashReport(mContext, crashReport, file);
                Looper.loop();
            }
        }.start();

        return true;
    }

    private File save2File(String crashReport) {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy_MM_dd_HH_mm_ss ");
        Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
        String date = formatter.format(curDate);

        String fileName = "crash-" + date + ".log";
        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            try {
                // 获取sd卡的绝对路径
                File dir = new File(Environment.getExternalStorageDirectory()
                        .getAbsolutePath() + File.separator + "tpms_crash");
                if (!dir.exists())
                    dir.mkdirs();
                File file = new File(dir + "/" + fileName);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(crashReport.toString().getBytes());
                fos.close();
                return file;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 获取APP崩溃异常报告
     *
     * @param ex
     * @return
     */
    private String getCrashReport(Context context, Throwable ex) {
        PackageInfo pinfo = getPackageInfo(context);
        StringBuffer exceptionStr = new StringBuffer();
        exceptionStr.append("Version: " + pinfo.versionName + "("
                + pinfo.versionCode + ")\n");
        exceptionStr.append("Android: " + android.os.Build.VERSION.RELEASE
                + "(" + android.os.Build.MODEL + ")\n");
        exceptionStr.append("Exception: " + ex.getMessage() + "\n");
        //StackTraceElement[] elements = ex.getStackTrace();
        Writer writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        ex.printStackTrace(pw);
        Throwable cause = ex.getCause();
        // 循环着把所有的异常信息写入writer中
        while (cause != null) {
            cause.printStackTrace(pw);
            cause = cause.getCause();
        }
        pw.close();// 记得关闭
        String result = writer.toString();
        exceptionStr.append(result);
        return exceptionStr.toString();
    }

    /**
     * 获取App安装包信息
     *
     * @return
     */
    private PackageInfo getPackageInfo(Context context) {
        PackageInfo info = null;
        try {
            info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
        }
        if (info == null)
            info = new PackageInfo();
        return info;
    }
}