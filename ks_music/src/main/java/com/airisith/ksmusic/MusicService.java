package com.airisith.ksmusic;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.airisith.lyric.LrcContent;
import com.airisith.lyric.LrcProcess;
import com.airisith.modle.MusicInfo;
import com.airisith.util.AppGlobalValues;
import com.airisith.util.Constans;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@SuppressLint("NewApi")
public class MusicService extends Service {
    private static String TAG = "MusicService";
    private Context gContext;

    /**
     * 播放器
     */
    private static MediaPlayer mediaPlayer = new MediaPlayer(); // 媒体播放器对象
    private String path; // 音乐文件路径

    /**
     * 歌词处理, 更新时间
     */
    private LrcProcess mLrcProcess;
    private List<LrcContent> lrcList = new ArrayList<LrcContent>(); //存放歌词列表对象
    private int index = 0;          //歌词检索值
    private Handler lyricHandler; // 传递歌词显示的handler
    private Runnable upLyricRunnable; // 更新歌词的Runnable
    private Timer upTimeTimer;
    private TimerTask upTimeTask;
    private Handler upTimeHandler;

    /**
     * 全局变量
     */
    private AppGlobalValues appGlobalValues;
    private int listSize = -1;

    /**
     * 其他flag值
     */
    private boolean ducked = false; // 是否被电话打断
    private boolean updateLyric = false; // 是否启动更新歌词定时器

    /**
     * 绑定service的对象
     */
    private final IBinder mBinder = new LocalBinder();

    /**
     * 监听器
     */
    private MediaPlayerPreparedListener onPreparedListener = new MediaPlayerPreparedListener();


    /**
     * 使用定时器，timer.schedule(TimerTask task, long delayTime, long period)
     * 每一秒获取一次歌曲时间，发送给Activity,mediaPlayer返回时间是毫秒
     *
     * @param handler    由Activity传进来的handler，用于将数据发送出去
     * @param startTimer 是否启动timer，否为关闭定时器
     */
    public void updateTime(final Handler handler,
                                  Boolean startTimer) {
        upTimeHandler = handler;
        if (startTimer) {
            try {
                upTimeTask.cancel();
            }catch (Exception e){}
            upTimeTask = new TimerTask() {
                @Override
                public void run() {
                    if (null != upTimeHandler) {
                        Message message = upTimeHandler.obtainMessage();
                        int current = mediaPlayer.getCurrentPosition();
                        int total = mediaPlayer.getDuration();
                        int progress = current * 200 / total;
                        String[] time = formatTime(current, total);
                        message.obj = time;
                        message.arg1 = progress;
                        upTimeHandler.sendMessage(message);
                    }
                }
            };
            upTimeTimer.schedule(upTimeTask, 1000, 1000);
        } else {
            try {
                upTimeTask.cancel();
            } catch (Exception e) {
            }
        }
    }


    /**
     * 实时更新歌词
     * @param mHandler activity传递数据的handler
     * @param startUpdate 是否更新，否为关闭更新
     */
    public List<LrcContent> updateLyric(final Handler mHandler, final Boolean startUpdate){
        mLrcProcess = new LrcProcess();
        //读取歌词文件
        mLrcProcess.readLRC(path);
        //传回处理后的歌词文件
        lrcList = mLrcProcess.getLrcList();
        //upLyricRunnable.
        // 1.生成Message对象，将Runnable对象赋值给callback属性；2.调用sendMessageDelay（Message msg）将消息发送出去
        if (null == upLyricRunnable){
            upLyricRunnable = new UpLyricRunnable(mHandler);
        }
        lyricHandler.post(upLyricRunnable);
        this.updateLyric = startUpdate;
        return lrcList;
    }

    /**
     * 更新歌词的runnable
     */
    private class UpLyricRunnable implements Runnable{
        private Handler mHandler; // Activity传递数据的handler
        @Override
        public void run() {
            if (null != mHandler){
                Message msg = mHandler.obtainMessage();
                msg.arg1 = lrcIndex(mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition());
            }
            if (updateLyric) {
                lyricHandler.postDelayed(this, 100);
            }
        }
        public UpLyricRunnable(Handler mHandler){
            this.mHandler = mHandler;
        }
    };

    /**
     * 将拿到的时间转换成00:00-00:00的形式用于显示
     * @param currentTime 当前时间（ms）
     * @param totalTime 总时间(ms)
     * @return 时间的String形式,如[“00:00”, “00:00”]
     */
    private static String[] formatTime(int currentTime, int totalTime) {
        String current_mStr, current_sStr, total_mStr, total_sStr;
        int current_m = (currentTime / 1000) / 60;
        int current_s = (currentTime / 1000) % 60;
        int total_m = (totalTime / 1000) / 60;
        int total_s = (totalTime / 1000) % 60;
        String[] timeStr = new String[2];
        if (current_m < 10) {
            current_mStr = "0" + current_m;
        } else {
            current_mStr = "" + current_m;
        }
        if (current_s < 10) {
            current_sStr = "0" + current_s;
        } else {
            current_sStr = "" + current_s;
        }
        if (total_m < 10) {
            total_mStr = "0" + total_m;
        } else {
            total_mStr = "" + total_m;
        }
        if (total_s < 10) {
            total_sStr = "0" + total_s;
        } else {
            total_sStr = "" + total_s;
        }
        timeStr[0] = current_mStr + ":" + current_sStr;
        timeStr[1] = total_mStr + ":" + total_sStr;
        return timeStr;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    /**
     * 返回service对象，用于activity来进行音乐控制操作
     */
    public class LocalBinder extends Binder {
        MusicService getService() {
            // 返回Activity所关联的Service对象，这样在Activity里，就可调用Service里的一些公用方法和公用属性
            return MusicService.this;
        }
    }


    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        super.onCreate();
        gContext = getApplicationContext();
        appGlobalValues = (AppGlobalValues) getApplication();
        lyricHandler = new Handler();
        upTimeTimer = new Timer(true);

        // 歌曲结束发送广播
        mediaPlayer.setOnCompletionListener(completionListener);
        // 歌曲准备就绪监听器
        mediaPlayer.setOnPreparedListener(onPreparedListener);

        TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE); // 获取系统服务
        telManager.listen(new MobliePhoneStateListener(),
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int cmd = -1;
        try {
            cmd = intent.getIntExtra("CMD", 0);
        } catch (Exception e) {
        }

        if (-1 == cmd) {
            Log.i(TAG, "onStartCommand:intent is null");
        } else {
            path = intent.getStringExtra("url");
            int currentPositopn = intent.getIntExtra("rate", 0);
            Log.i(TAG, "CMD:" + cmd + ",rate" + currentPositopn);
            if (cmd == Constans.PLAY_CMD) {
                play(currentPositopn);
            } else if (cmd == Constans.PUASE_CMD) {
                pause();
            } else if (cmd == Constans.STOP_CMD) {
                stop();
            }

        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 设置歌曲地址
     * @param url ：歌曲路径
     */
    public void setPath(String url){
        this.path = url;
    }

    /**
     * 播放音乐
     *
     * @param position
     */
    public boolean play(int position) {
        try {
            Log.i(TAG, "Music play()");
            mediaPlayer.reset();// 把各项参数恢复到初始状态
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare(); // 进行缓冲
            onPreparedListener.setPosition(position);
            appGlobalValues.setPlayState(Constans.STATE_PLAY);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 暂停音乐
     * @return 是否暂停成功
     */
    public boolean pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                Log.i(TAG, "Music pause()");
                appGlobalValues.setCurrentPosition(mediaPlayer.getCurrentPosition());
                mediaPlayer.pause();
                appGlobalValues.setPlayState(Constans.STATE_PUASE);
                return true;
            }catch (Exception e){
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * 停止音乐
     */
    public boolean stop() {
        if (mediaPlayer != null) {
            Log.i(TAG, "Music stop()");
            try {
                mediaPlayer.stop();
                appGlobalValues.setPlayState(Constans.STATE_STOP);
                appGlobalValues.setCurrentPosition(0);
                return true;
            }catch (Exception e){
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        if (mediaPlayer != null) {
            stop();
            try {
                mediaPlayer.release();
            } catch (Exception e) {
            }
        }
    }

    /**
     * 根据时间获取歌词显示的索引值
     *
     * @return
     */
    public int lrcIndex(int duration, int currentTime) {
        if (currentTime < duration) {
            for (int i = 0; i < lrcList.size(); i++) {
                if (i < lrcList.size() - 1) {
                    if (currentTime < lrcList.get(i).getLrcTime() && i == 0) {
                        index = i;
                    }
                    if (currentTime > lrcList.get(i).getLrcTime()
                            && currentTime < lrcList.get(i + 1).getLrcTime()) {
                        index = i;
                    }
                }
                if (i == lrcList.size() - 1
                        && currentTime > lrcList.get(i).getLrcTime()) {
                    index = i;
                }
            }
        }
        return index;
    }

    /**
     * 是否正在播放--不准确，使用全局变量
     *
     * @return
     */
    public boolean isPlaying() {
        if (null != mediaPlayer) {
            return mediaPlayer.isPlaying();
        } else {
            return false;
        }
    }

    /**
     * mediaplayer准备就绪监听器
     */
    private class MediaPlayerPreparedListener implements OnPreparedListener {
        private int position = 0;

        @Override
        public void onPrepared(MediaPlayer mp) {
            mediaPlayer.start(); // 开始播放
            if (position > 0) { // 如果音乐不是从头播放
                mediaPlayer.seekTo(position);
                position = 0;
            }
            appGlobalValues.setPlayState(Constans.STATE_PLAY);
        }

        public void setPosition(int mPosition) {
            this.position = mPosition;
        }
    }

    private OnCompletionListener completionListener = new OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            int[] currentInfo =  MusicInfo.getCurrentMusicInfo(gContext);
            int currentList = currentInfo[0];
            int cycleMode = currentInfo[1];
            int currentPositon = currentInfo[2];
            switch (cycleMode){
                case Constans.MODLE_ORDER:
                    if (currentPositon < listSize - 1) {
                        currentPositon = currentPositon + 1;
                    } else {
                        currentPositon = 0;
                    }
                    break;
                case Constans.MODLE_RANDOM:
                    currentPositon = (int) (Math.random() * listSize);
                    break;
                case Constans.MODLE_SINGLE:

                    break;
            }

            Intent intent = new Intent();
            intent.setAction(Constans.ACTION_MUSIC_END);
            sendBroadcast(intent);

        }
    };

    /**
     * 电话监听器类
     *
     * @author
     */
    private class MobliePhoneStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE: // 挂机状态
                    if (ducked) {
                        play((int) appGlobalValues.getCurrentPosition());
                        ducked = false;
                    }
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:    //通话状态
                case TelephonyManager.CALL_STATE_RINGING:    //响铃状态
                    if (mediaPlayer.isPlaying()) {
                        pause();
                        ducked = true;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void setListSize(int listSize){
        this.listSize = listSize;
    }
}
