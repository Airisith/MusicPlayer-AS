package com.airisith.ksmusic;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.airisith.database.MusicListDatabase;
import com.airisith.lyric.LrcView;
import com.airisith.modle.MusicInfo;
import com.airisith.util.AppGlobalValues;
import com.airisith.util.Constans;
import com.airisith.util.MusicList;

import java.util.List;

public class MusicView extends Activity {
    // 歌词控件
    public static LrcView lrcView;
    private final String TAG = "MusicView";
    private int musicPosition = 0;
    private int playState = Constans.STATE_STOP;
    private int playMode = Constans.MODLE_ORDER;
    private List<MusicInfo> currentMusicList = null;
    private Intent musicIntent = null;
    private MusicInfo currentMusicInfo = null;
    private boolean turnTOback = false;
    private Handler timeHandler; // 实时更新歌曲时间
    private Handler lrcHandler; // 更新歌词Handler
    private MusicCompleteReceiver receiver;// 循环播放广播接收器
    private RelativeLayout topBackLayout;
    private TextView likeTextView;
    private TextView menuTextView;
    private TextView titelTextView;
    private TextView artisTextView;
    private TextView timeTextView;
    private ImageView orderImageView;
    private ImageView lastImageView;
    private ImageView playImageView;
    private ImageView nextImageView;
    private ImageView listImageView;
    private SeekBar seekBar;
    private ImageView albumView;
    private Boolean lrcAndAlbum = true; //默认显示图片和歌词
    private MusicService mService = null; // service实例
    private boolean mBound = false;

    private int currentListId = 0;

    private AppGlobalValues appGlobalValues; // 全局变量
    private Context gContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);

        appGlobalValues = (AppGlobalValues)getApplication();
        gContext = getApplicationContext();

        topBackLayout = (RelativeLayout) findViewById(R.id.musicTop_backLayout);
        likeTextView = (TextView) findViewById(R.id.music_like);
        menuTextView = (TextView) findViewById(R.id.music_menu);
        titelTextView = (TextView) findViewById(R.id.musicTop_title);
        artisTextView = (TextView) findViewById(R.id.musicTop_artist);
        timeTextView = (TextView) findViewById(R.id.music_timeText);
        orderImageView = (ImageView) findViewById(R.id.music_order);
        lastImageView = (ImageView) findViewById(R.id.music_last);
        playImageView = (ImageView) findViewById(R.id.music_play);
        nextImageView = (ImageView) findViewById(R.id.music_next);
        listImageView = (ImageView) findViewById(R.id.music_list);
        seekBar = (SeekBar) findViewById(R.id.music_progressbar);
        lrcView = (LrcView) findViewById(R.id.music_lrcShowView);
        albumView = (ImageView) findViewById(R.id.music_ablum);

        musicIntent = new Intent(gContext, MusicService.class);
        musicIntent.putExtra("Activity", "MusicView");
        try {
            playState = getIntent().getIntExtra("SERVICE_STATE",
                    Constans.STATE_STOP);
        } catch (Exception e) {
        }

        // 获取音乐列表
        currentMusicList = appGlobalValues.getCurrentList();
        switch (currentListId){
            case Constans.TYPE_LOCAL:
                Log.i(TAG, "当前列表-localMusicLists");
                break;
            case Constans.TYPE_USER:
                Log.i(TAG, "当前列表-userMusicLists");
                break;
            case Constans.TYPE_DOWNLOAD:
                Log.i(TAG, "当前列表-downloadMusicLists");
                break;
            case Constans.TYPE_NET:
                Log.i(TAG, "当前列表-lonetMusicLists");
                break;
        }

        // 广播接收器，用于一首歌播放完成后继续播放下一首的动作
        receiver = new MusicCompleteReceiver();
        IntentFilter intentfFilter = new IntentFilter();
        intentfFilter.addAction(Constans.ACTION_MUSIC_END);
        MusicView.this.registerReceiver(receiver, intentfFilter);

        // 各个View设置监听器
        OnbuttomItemClickeListener onbuttomItemClickeListener = new OnbuttomItemClickeListener();
        topBackLayout.setOnClickListener(onbuttomItemClickeListener);
        likeTextView.setOnClickListener(onbuttomItemClickeListener);
        menuTextView.setOnClickListener(onbuttomItemClickeListener);
        orderImageView.setOnClickListener(onbuttomItemClickeListener);
        lastImageView.setOnClickListener(onbuttomItemClickeListener);
        playImageView.setOnClickListener(onbuttomItemClickeListener);
        nextImageView.setOnClickListener(onbuttomItemClickeListener);
        listImageView.setOnClickListener(onbuttomItemClickeListener);
        // 进度条拖拽
        seekBar.setOnSeekBarChangeListener(new OnProgressChagedListener());
        // 歌词和图片的显示
        OnLrcOrAlbumClickedListerner onLrcOrAlbumClickedListerner = new OnLrcOrAlbumClickedListerner();
        lrcView.setOnClickListener(onLrcOrAlbumClickedListerner);
        albumView.setOnClickListener(onLrcOrAlbumClickedListerner);

        lrcView.setAnimation(AnimationUtils.loadAnimation(this, R.anim.anim));
    }

    @Override
    protected void onDestroy() {
        Log.w(TAG, "onDestroy");
        super.onDestroy();
        try {
            mService.updateTime(timeHandler, false);
            // 停止接收广播
            unregisterReceiver(receiver);
            stopService(musicIntent);
        } catch (Exception e) {
        }
    }

    @Override
    protected void onPause() {
        Log.w(TAG, "onPause");
        turnTOback = true;
        super.onPause();
    }

    @Override
    protected void onStart() {
        Log.w(TAG, "onStart");
        super.onStart();
        // 绑定Service，绑定后就会调用mConnetion里的onServiceConnected方法
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        turnTOback = false;
        // 通知service，显示歌词
        sendBroadcast(new Intent(Constans.ACTION_LYRIC_START));

        try {
            int[] state = MusicInfo
                    .getCurrentMusicInfo(gContext);
            currentListId = state[0];
            playMode = state[1];
            musicPosition = state[2];
        } catch (Exception e) {
        }
        try {
            // 获取歌曲播放信息
            int[] state = MusicInfo
                    .getCurrentMusicInfo(gContext);
            currentMusicList = appGlobalValues.getCurrentList();
            playMode = state[1];
            musicPosition = state[2];
        } catch (Exception e) {
        }
        Log.w(TAG, "列表:" + currentMusicList.size() + "循环模式:" + playMode
                + "歌曲位置:" + musicPosition);
        try {
            currentMusicInfo = currentMusicList.get(musicPosition);
            Log.w(TAG, "歌曲:" + currentMusicInfo.getAbbrTitle());
        } catch (Exception e) {
        }
        try {
            Log.w(TAG, "是否正在播放" + playState);
            // 更新时间，接收由MusicService中的子线程发送的消息
            timeHandler = new UpdateInfoHandler();
            titelTextView.setText(currentMusicInfo.getAbbrTitle());
            artisTextView.setText(currentMusicInfo.getArtist());
            if (Constans.STATE_STOP == playState) {
                playImageView.setImageResource(R.drawable.play);
                timeTextView.setText("00:00/"
                        + currentMusicInfo.getDurationStr());
            } else if (Constans.STATE_PUASE == playState) {
                playImageView.setImageResource(R.drawable.play);
                mService.updateTime(timeHandler, true);
                upDataAlbum(true);
                lrcAndAlbum = true;
            } else {
                mService.updateTime(timeHandler, true);
                playImageView.setImageResource(R.drawable.puase);
                // bcap.setImageBitmap(currentMusicInfo.getAlbum_bitmap());
                titelTextView.setText(currentMusicInfo.getAbbrTitle());
                artisTextView.setText(currentMusicInfo.getAbbrArtist());
                upDataAlbum(true);
                lrcAndAlbum = true;
            }

        } catch (Exception e) {
        }
        try {
            IntentFilter intentfFilter = new IntentFilter();
            intentfFilter.addAction(Constans.ACTION_MUSIC_END);
            MusicView.this.registerReceiver(receiver, intentfFilter);
        } catch (Exception e) {
        }
    }

    @Override
    protected void onStop() {
        Log.w(TAG, "onStop");
        turnTOback = true;
        try {
            mService.updateTime(timeHandler, false);
        } catch (Exception e) {
        }
        if (mBound){
            unbindService(mConnection);
        }
        unregisterReceiver(receiver); // 停止接收广播，转由HomeActivity接收
        sendBroadcast(new Intent(Constans.ACTION_LYRIC_START));
        super.onStop();
    }

    /** 定交ServiceConnection，用于绑定Service的*/
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // 已经绑定了LocalService，强转IBinder对象，调用方法得到LocalService对象
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /**
     * 歌曲命令
     *
     * @param musicInfos  歌曲列表信息
     * @param playCommand 播放命令：play，puase，stop
     * @param position    歌曲位于列表中的位置
     * @param rate        播放的位置，整个歌曲时间定为100, 如果为负数，则表示继续从当前位置播放
     * @param upTime      是否更新时间
     */
    private void MusicCommad(List<MusicInfo> musicInfos, int playCommand,
                             int position, int rate, Boolean upTime) {
        if ((musicInfos != null)
                && (Constans.ACTIVITY_CHANGED_CMD != playCommand)) {
            MusicInfo musicInfo = musicInfos.get(position);
            currentMusicInfo = musicInfo;
            Log.w(TAG, "开始播放第" + position + "首歌");
            Log.w(TAG, musicInfo.getUrl().toString());
            musicIntent.putExtra("url", musicInfo.getUrl());
            musicIntent.putExtra("CMD", playCommand);
            musicIntent.putExtra("rate", rate);
            startService(musicIntent); // 启动服务
            titelTextView.setText(musicInfo.getAbbrTitle());
            artisTextView.setText(musicInfo.getArtist());
            playImageView.setImageResource(R.drawable.puase);
            upDataAlbum(lrcAndAlbum);
            if (false == turnTOback) {
                mService.updateTime(timeHandler, upTime);
            }

            MusicInfo.putCurrentMusicInfo(gContext,
                    currentListId, playMode, musicPosition);
            Log.w(TAG, "保存歌曲信息：list,mode,position:" + currentListId + playMode
                    + musicPosition);
        }
    }

    /**
     * 切换lrc界面，在有图片和无图片之间切换
     *
     * @param visible
     */
    private void upDataAlbum(Boolean visible) {
        if (visible) {
            albumView.setImageBitmap(MusicList.getAlbum(gContext, currentMusicInfo));
            albumView.setBackgroundResource(R.drawable.album_back);
            lrcView.setViewType(LrcView.SINGLE_TYPE);
        } else {
            albumView.setImageBitmap(null);
            albumView.setBackgroundResource(0);
            lrcView.setViewType(LrcView.LIST_TYPE);
        }
    }

    /**
     * 点击列表图标弹出的音乐列表
     */
    public void musicListItemDialog() {

        int size = currentMusicList.size();
        String[] musics = new String[size];
        for (int position = 0; position < size; position++) {
            String musicTitle = currentMusicList.get(position).getAbbrTitle();
            musics[position] = musicTitle;
        }

//		WindowManager m = getWindowManager();
//		Display d = m.getDefaultDisplay(); // 获取屏幕宽、高用,结果不起作用
//		int sWidth = d.getWidth();
//		int sHeight = d.getHeight();

        // 使用自定义的对话框
        Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater) MusicView.this
                .getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.music_musiclist,
                (ViewGroup) findViewById(R.id.music_listLayout));
        layout.setBackgroundColor(Color.TRANSPARENT);
//		layout.setLayoutParams(new ViewGroup.LayoutParams(sWidth/3, sHeight/3));
        builder.setView(layout);


        // 创建对话框并设置其属性
        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        window.setGravity(Gravity.RIGHT | Gravity.BOTTOM);

//		WindowManager.LayoutParams lp = window.getAttributes();
//		lp.width = (int) (d.getHeight() * 0.5);
//		lp.height = (int) (d.getWidth() * 0.5);
//		window.setAttributes(lp);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);

        // 播放歌曲并让对话框消失
        ListView musicListView = (ListView) layout
                .findViewById(R.id.musc_musiclistview);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                gContext, R.layout.music_musiclist_item, musics);
        musicListView.setAdapter(adapter);
        musicListView
                .setOnItemClickListener(new OnMusiclistItemClickedListener(dialog));

        dialog.show();
    }

    /**
     * 对话框显示歌曲信息
     */
    public void getMusicInfoDialog() {
        String title = "歌名：" + currentMusicInfo.getAbbrTitle();
        String art = "歌手：" + currentMusicInfo.getAbbrArtist();
        int startIndex = currentMusicInfo.getUrl().indexOf(".");//获取文件后缀位置
        String type = startIndex > 0 ? currentMusicInfo.getUrl().substring(startIndex + 1) : "未知";
        String typeStr = "格式：" + type;
        String time = "时长：" + currentMusicInfo.getDurationStr();
        new AlertDialog.Builder(this).setTitle("歌曲信息").setItems(
                new String[]{title, art, typeStr, time}, null).show();
    }

    /**
     * 音乐播放结束广播接收器，继续播放
     *
     * @author Administrator
     */
    private class MusicCompleteReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constans.MODLE_ORDER == playMode) {
                if (musicPosition < currentMusicList.size() - 1) {
                    musicPosition = musicPosition + 1;
                } else {
                    musicPosition = 0;
                }
            } else if (Constans.MODLE_RANDOM == playMode) {
                musicPosition = (int) (Math.random() * currentMusicList.size());
            } else {

            }
            playState = Constans.STATE_PLAY;
            MusicCommad(currentMusicList, Constans.PLAY_CMD, musicPosition, 0,
                    true);
        }
    }

    /**
     * 接收service发送的消息，实时更新时间
     *
     * @author Administrator
     */
    @SuppressLint("HandlerLeak")
    private class UpdateInfoHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            try {
                String[] time = (String[]) msg.obj;
                int progress = msg.what;
                timeTextView.setText(time[0] + "/" + time[1]);
                seekBar.setProgress(progress);
            } catch (Exception e) {
            }
        }

    }

    /**
     * 按钮监听器
     *
     * @author Administrator
     */
    private class OnbuttomItemClickeListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.musicTop_backLayout:
                    Intent intent = new Intent(gContext,
                            HomeActivity.class);
                    intent.putExtra("SERVICE_STATE", playState);
                    unregisterReceiver(receiver); // 停止接收广播，转由HomeActivity接收
                    startActivity(intent);
                    break;
                case R.id.music_like:
                    MusicListDatabase.insertMusic(gContext, currentMusicInfo);
                    if (Constans.TYPE_USER == currentListId){
                        // 重新加载user列表
                    }
                    break;
                case R.id.music_menu:
                    getMusicInfoDialog();
                    break;
                case R.id.music_order:
                    if (Constans.MODLE_ORDER == playMode) {
                        playMode = Constans.MODLE_RANDOM;
                        orderImageView.setImageResource(R.drawable.random);
                    } else if (Constans.MODLE_RANDOM == playMode) {
                        playMode = Constans.MODLE_SINGLE;
                        orderImageView.setImageResource(R.drawable.single);
                    } else {
                        playMode = Constans.MODLE_ORDER;
                        orderImageView.setImageResource(R.drawable.order);
                    }
                    break;
                case R.id.music_last:
                    if (Constans.MODLE_RANDOM == playMode) {
                        musicPosition = (int) (Math.random() * currentMusicList
                                .size());
                    } else {
                        if (musicPosition > 0) {
                            musicPosition = musicPosition - 1;
                        } else {
                            musicPosition = currentMusicList.size() - 1;
                        }
                    }
                    playState = Constans.STATE_PLAY;
                    MusicCommad(currentMusicList, Constans.PLAY_CMD, musicPosition,
                            0, true);
                    break;
                case R.id.music_play:
                    if (Constans.STATE_PLAY == playState) {
                        MusicCommad(currentMusicList, Constans.PUASE_CMD,
                                musicPosition, 0, true);
                        playImageView.setImageResource(R.drawable.play);
                        playState = Constans.STATE_PUASE;
                    } else if (Constans.STATE_PUASE == playState) {
                        MusicCommad(currentMusicList, Constans.PLAY_CMD,
                                musicPosition, -1, true);
                        playImageView.setImageResource(R.drawable.puase);
                        playState = Constans.STATE_PLAY;
                    } else {
                        MusicCommad(currentMusicList, Constans.PLAY_CMD,
                                musicPosition, 0, true);
                        playImageView.setImageResource(R.drawable.puase);
                        playState = Constans.STATE_PLAY;
                    }
                    break;

                case R.id.music_next:
                    if (Constans.MODLE_RANDOM == playMode) {
                        musicPosition = (int) (Math.random() * currentMusicList
                                .size());
                    } else {
                        if (musicPosition < currentMusicList.size() - 1) {
                            musicPosition = musicPosition + 1;
                        } else {
                            musicPosition = 0;
                        }
                    }
                    playState = Constans.STATE_PLAY;
                    MusicCommad(currentMusicList, Constans.PLAY_CMD, musicPosition,
                            0, true);
                    break;
                case R.id.music_list:
                    musicListItemDialog();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 进度条拖拽事件
     *
     * @author Administrator
     */
    private class OnProgressChagedListener implements OnSeekBarChangeListener {

        private float progressRate; // 之前设置最大为200

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
            if (fromUser) {
                this.progressRate = (float) progress / 200;
                Log.w(TAG, "进度：" + progressRate);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int rate = (int) (progressRate * 100);
            MusicCommad(currentMusicList, Constans.PLAY_CMD, musicPosition,
                    rate, true);
        }

    }

    /**
     * 点击歌词切换图片
     */
    private class OnLrcOrAlbumClickedListerner implements OnClickListener {

        @Override
        public void onClick(View v) {

            if (lrcAndAlbum) {
                lrcAndAlbum = false;
            } else {
                lrcAndAlbum = true;
            }
            upDataAlbum(lrcAndAlbum);
        }
    }

    /**
     * 列表点击事件
     *
     * @author Administrator
     */
    private class OnMusiclistItemClickedListener implements OnItemClickListener {
        private AlertDialog dialog;

        public OnMusiclistItemClickedListener(AlertDialog dialog) {
            this.dialog = dialog;
        }

        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int position,
                                long id) {
            musicPosition = position;
            playState = Constans.STATE_PLAY;
            MusicCommad(currentMusicList, Constans.PLAY_CMD, musicPosition, 0,
                    true);
            dialog.dismiss();
        }
    }

}
