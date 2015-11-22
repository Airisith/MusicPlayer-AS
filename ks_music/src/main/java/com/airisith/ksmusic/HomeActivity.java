package com.airisith.ksmusic;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.airisith.database.MusicListDatabase;
import com.airisith.modle.MusicInfo;
import com.airisith.util.AppGlobalValues;
import com.airisith.util.BluToothConnect;
import com.airisith.util.Constans;
import com.airisith.util.MusicList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class HomeActivity extends Activity implements OnTabChangeListener {

    private static final String TAG = "HomeActivity";
    private final String TAB_ID_MINE = "mine";
    private final String TAB_ID_LIB = "musicLib";

    private Context gContex;
    private AppGlobalValues appGlobalValues;

    // 返回键退出延时时间
    private long mExitTime;
    private TabHost tabHost;
    private ListView localListView;
    private ExpandableListView expandableListView;
    private ImageView bcap;
    private TextView bTitle;
    private TextView bTime;
    private TextView bArtis;
    private ImageView bPlay;
    private ImageView bNext;
    private ImageView bOrder;
    private RelativeLayout bInfoLayout;

    private Boolean isShowing = false; // activity是否可见
    private int musicPosition = 0; // 记录歌曲位置
    private int currentListId = 0; // 当前列表Id
    private Intent musicIntent; // 启动service的intent
    private List<String> groupArray = null; // 列表分组group
    private List<MusicInfo> localMusicLists = null; // 本地音乐列表
    private List<MusicInfo> userMusicLists = null; // 用户收藏列表
    private List<MusicInfo> downloadMusicLists = null; // 下载音乐列表
    private List<MusicInfo> currentMusicList = null; // 当前选择的音乐列表
    private HashMap<Integer, List<MusicInfo>> musicLists = null; // 将两个列表包装在map中
    private int cycleMode = Constans.MODLE_ORDER; // 播放模式
    private int playState = Constans.STATE_STOP;

    private MusicInfo currentMusicInfo = null;
    // 循环播放广播接收器
    private MusicCompleteReceiver receiver;

    private MusicService mService = null; // service实例
    private boolean mBound = false;

    @SuppressLint({"HandlerLeak", "UseSparseArrays"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Log.i(TAG, "onCreate");
        gContex = getApplicationContext();
        appGlobalValues = (AppGlobalValues)getApplication();

        initViews();

        // 获取播放状态
        playState = appGlobalValues.getPlayState();

        // 用户和下载音乐列表先设为空对象
        userMusicLists = new ArrayList<MusicInfo>();
        downloadMusicLists = new ArrayList<MusicInfo>();

        // 加载本地音乐库，默认列表为本地列表
        musicLists = new HashMap<Integer, List<MusicInfo>>();
        updataMusicList();

        // 创建Intent对象，准备启动MusicService
        musicIntent = new Intent(getApplicationContext(), MusicService.class);
        startService(musicIntent);

        // 设置列表点击监听器
        expandableListView
                .setOnChildClickListener(new MusicListItemClickListener());
        expandableListView
                .setOnItemLongClickListener(new onItemLongclichedListener());

        // 广播接收器，用于一首歌播放完成后继续播放下一首的动作
        receiver = new MusicCompleteReceiver();
        IntentFilter intentfFilter = new IntentFilter();
        intentfFilter.addAction(Constans.ACTION_MUSIC_END);
        HomeActivity.this.registerReceiver(receiver, intentfFilter);
    }

    private void initViews(){
        expandableListView = (ExpandableListView) findViewById(R.id.home_ExpandingListView);
        bcap = (ImageView) findViewById(R.id.homeb_cap);
        bTitle = (TextView) findViewById(R.id.homeb_title);
        bTime = (TextView) findViewById(R.id.homeb_time);
        bArtis = (TextView) findViewById(R.id.homeb_artist);
        bPlay = (ImageView) findViewById(R.id.homeb_play);
        bNext = (ImageView) findViewById(R.id.homeb_next);
        bOrder = (ImageView) findViewById(R.id.homeb_order);
        bInfoLayout = (RelativeLayout) findViewById(R.id.homeb_infoLayout);

        // tab设置
        tabHost = (TabHost) findViewById(R.id.tabhost);
        tabHost.setup();
        TabHost.TabSpec specMine = tabHost.newTabSpec(TAB_ID_MINE);
        specMine.setContent(R.id.home_tabFirst);
        specMine.setIndicator("我的", null);
        tabHost.addTab(specMine);
        TabHost.TabSpec specLib = tabHost.newTabSpec(TAB_ID_LIB);
        specLib.setContent(R.id.home_tabSecond);
        specLib.setIndicator("音乐库", null);
        tabHost.addTab(specLib);

        // 设置标签背景颜色
        tabHost.getTabWidget().setStripEnabled(false);
        tabHost.getTabWidget().getChildAt(0)
                .setBackgroundColor(Color.alpha(100));
        tabHost.getTabWidget().getChildAt(1)
                .setBackgroundColor(Color.alpha(100));
        updateTab(tabHost); // 初始化标签字体颜色
        tabHost.setOnTabChangedListener(this); // 选择监听器

        // 给底部按钮注册监听器
        bInfoLayout.setOnClickListener(new OnButtomMenuClickedListener());
        bPlay.setOnClickListener(new OnButtomMenuClickedListener());
        bNext.setOnClickListener(new OnButtomMenuClickedListener());
        bOrder.setOnClickListener(new OnButtomMenuClickedListener());

        groupArray = new ArrayList<String>();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        try {
            mService.updateTime(timeHandler, false);
            // 停止接收广播
            unregisterReceiver(receiver);
            stopService(musicIntent);
        } catch (Exception e) {
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }

    @SuppressLint("HandlerLeak")
    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();

        isShowing = true;
        // 绑定Service，绑定后就会调用mConnetion里的onServiceConnected方法
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        //updataMusicList();
        // 获取歌曲播放信息
        int[] state = MusicInfo
                .getCurrentMusicInfo(getApplicationContext());
        // 播放状态
        switch (state[0]){
            case Constans.TYPE_LOCAL:
                currentMusicList = localMusicLists;
                currentListId = Constans.TYPE_USER;
                break;
            case Constans.TYPE_USER:
                currentMusicList = userMusicLists;
                currentListId = Constans.TYPE_USER;
                break;
            case Constans.TYPE_DOWNLOAD:
                currentMusicList = downloadMusicLists;
                currentListId = Constans.TYPE_USER;
                break;
        }

        // 循环模式和位置
        cycleMode = state[1];
        musicPosition = state[2];
        if (null != currentMusicList) {
            Log.i(TAG, "列表:" + currentMusicList.size() + "循环模式:" + cycleMode
                    + "歌曲位置:" + musicPosition + "是否正在播放" + playState +
                    "(1-STATE_PLAY, 2-STATE_PAUSE, 0-STATE-_STOP");
        }

        if ((null != currentMusicList) && (currentMusicList.size() > 0) && (musicPosition <currentMusicList.size())){
            currentMusicInfo = currentMusicList.get(musicPosition);
            Log.i(TAG, "当前歌曲:" + currentMusicInfo.getAbbrTitle());
            bTitle.setText(currentMusicInfo.getAbbrTitle());
            bArtis.setText(currentMusicInfo.getArtist());
            bcap.setImageBitmap(currentMusicInfo.getAlbum_bitmap());

            switch (playState){
                case Constans.STATE_STOP:
                    bPlay.setImageResource(R.drawable.play);
                    bTime.setText("00:00-" + currentMusicInfo.getDurationStr());
                    break;
                case Constans.STATE_PUASE:
                    bPlay.setImageResource(R.drawable.play);
                    mService.updateTime(timeHandler, true);
                    break;
                case Constans.STATE_PLAY:
                    mService.updateTime(timeHandler, true);
                    bPlay.setImageResource(R.drawable.puase);
                    break;
            }
        }

        IntentFilter intentfFilter = new IntentFilter();
        intentfFilter.addAction(Constans.ACTION_MUSIC_END);
        registerReceiver(receiver, intentfFilter);
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        isShowing = false;
        try {
            mService.updateTime(timeHandler, false);
        } catch (Exception e) {
        }
        if (mBound){
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.i(TAG, "onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);
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
     * 更新Tab标签的颜色，和字体的颜色
     *
     * @param tabHost
     */
    @SuppressLint("InlinedApi")
    private void updateTab(TabHost tabHost) {
        for (int i = 0; i < tabHost.getTabWidget().getChildCount(); i++) {
            View view = tabHost.getTabWidget().getChildAt(i);
            TextView tv = (TextView) tabHost.getTabWidget().getChildAt(i)
                    .findViewById(android.R.id.title);
            tv.setTextSize(20);
            tv.setTypeface(Typeface.SERIF, 2); // 设置字体和风格
            if (tabHost.getCurrentTab() == i) {// 选中
                tv.setTextColor(this.getResources().getColorStateList(
                        android.R.color.holo_blue_bright));
            } else {// 不选中
                tv.setTextColor(this.getResources().getColorStateList(
                        android.R.color.white));
            }
        }
    }

    /**
     * 设置TabHost监听器
     *
     * @author Administrator
     */
    @Override
    public void onTabChanged(String tabId) {
        updateTab(tabHost);
        if (tabId.equals(TAB_ID_MINE)) {
//			// 更新本地list
//			List<MusicInfo> localLists = MusicList
//					.getLocaMusicInfos(getApplicationContext());
//			List<MusicInfo> userLists = MusicList
//					.getMusicsFromeProvider(getApplicationContext());
//			localMusicLists = localLists;
//			userMusicLists = userLists;
//		
//			MusicList.setListAdpter(getApplicationContext(),
//					expandableListView, musicLists, groupArray);
        }
    }

    /**
     * 歌曲命令
     *
     * @param musicInfos  歌曲列表信息
     * @param playCommand 播放命令：play，puase，stop
     * @param position    歌曲位于列表中的位置
     * @param rate        播放的位置，整个歌曲时间定为100, 如果为负数，则表示继续从当前位置播放
     */
    private void MusicCommad(List<MusicInfo> musicInfos, int playCommand,
                             int position, int rate) {
        if ((musicInfos != null && 0 != musicInfos.size())
                && (Constans.ACTIVITY_CHANGED_CMD != playCommand)) {
            MusicInfo musicInfo = musicInfos.get(position);
            currentMusicInfo = musicInfo;

            if (mBound) {
                Log.i(TAG, "当前第" + position + "首:" + musicInfo.getUrl().toString());
                mService.setPath(musicInfo.getUrl());
                switch (playCommand){
                    case Constans.PLAY_CMD:
                        if(true == mService.play(rate)){
                            bPlay.setImageResource(R.drawable.puase);
                            playState = Constans.STATE_PLAY;
                        }
                        break;
                    case Constans.PUASE_CMD:
                        if(mService.pause()){
                            bPlay.setImageResource(R.drawable.play);
                            playState = Constans.STATE_PUASE;                     }
                        break;
                    case Constans.STOP_CMD:
                        if(mService.stop()){
                            bPlay.setImageResource(R.drawable.play);
                            playState = Constans.STATE_STOP;
                       }
                        break;
                }
            }

            bTitle.setText(musicInfo.getAbbrTitle());
            bArtis.setText(musicInfo.getArtist());
            bcap.setImageBitmap(musicInfo.getAlbum_bitmap());
            mService.updateTime(timeHandler, isShowing);

            MusicInfo.putCurrentMusicInfo(getApplicationContext(),
                    currentListId, cycleMode, musicPosition);
            Log.i(TAG, "保存歌曲信息：list,mode,position:" + currentListId + cycleMode
                    + musicPosition);
        }
    }

    /**
     * 屏蔽返回键原来的功能，以免程序异常退出出错
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_HOME:
                Log.i(TAG, "KEYCODE_HOME");
                return true;
            case KeyEvent.KEYCODE_BACK:
                Log.i(TAG, "KEYCODE_BACK");
                if ((System.currentTimeMillis() - mExitTime) > 2000) {
                    Object mHelperUtils;
                    Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                    mExitTime = System.currentTimeMillis();

                } else {
                    try {
                        mService.updateTime(timeHandler, false);
                        // 停止接收广播
                        HomeActivity.this.unregisterReceiver(receiver);
                        stopService(musicIntent);
                    } catch (Exception e) {
                    }
                    // finish();
                    System.exit(0); // 退出整个程序，否则service，MusicView未退出，下次打开再次启动service会出错
                }
                return true;
            case KeyEvent.KEYCODE_CALL:
                Log.i(TAG, "KEYCODE_CALL");
                return true;
            case KeyEvent.KEYCODE_SYM:
                Log.i(TAG, "KEYCODE_SYM");
                return true;
            case KeyEvent.KEYCODE_STAR:
                Log.i(TAG, "KEYCODE_STAR");
                return true;
        }
        Log.i(TAG, "return super.onKeyDown(keyCode, event);");
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 刷新歌曲列表
     */
    private void updataMusicList() {
        MusicList.getLocalist(getApplicationContext(), listLoadCompleteHandler);
    }

    /**
     * 本地列表歌曲长按弹出对话框
     *
     * @param position
     */
    private void localListDialog(final int position) {
        AlertDialog.Builder builder = new Builder(HomeActivity.this);
        builder.setPositiveButton("收藏", new AlertDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MusicListDatabase.insertMusic(getApplicationContext(), localMusicLists.get(position));
                updataMusicList();
            }
        });
        builder.setNegativeButton("删除", new AlertDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.setNeutralButton("分享", new AlertDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String filePath = localMusicLists.get(position).getUrl();
                new BluToothConnect(getApplicationContext(), filePath).sendFile();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 收藏列表弹出对话框
     *
     * @param position
     */
    private void userListDialog(final int position) {
        AlertDialog.Builder builder = new Builder(HomeActivity.this);
        builder.setPositiveButton("移除", new AlertDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MusicListDatabase.deleteMusic(getApplicationContext(), userMusicLists.get(position));
                updataMusicList();
            }
        });
        builder.setNegativeButton("分享", new AlertDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String filePath = userMusicLists.get(position).getUrl();
                new BluToothConnect(getApplicationContext(), filePath).sendFile();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * expandableListView的子列表Item点击事件监听器
     *
     * @author Administrator
     */
    private class MusicListItemClickListener implements OnChildClickListener {

        @Override
        public boolean onChildClick(ExpandableListView parent, View v,
                                    int groupPosition, int childPosition, long id) {

            if (0 == groupPosition) {
                currentMusicList = localMusicLists;
                currentListId = 0;
                Log.i(TAG, "选中localMusicLists");
            } else if (1 == groupPosition) {
                currentMusicList = userMusicLists;
                currentListId = 1;
                Log.i(TAG, "选中userMusicLists");
            } else if (2 == groupPosition) {
                currentMusicList = downloadMusicLists;
                currentListId = 2;
                Log.i(TAG, "选中downloadMusicLists");
            }
            musicPosition = childPosition;
            MusicCommad(currentMusicList, Constans.PLAY_CMD, musicPosition, 0);
            return false;
        }
    }

    /**
     * expandableListView的子列表Item长点击事件监听器,
     * 前提是在adapter中分别对groupView和childView设置了tag
     *
     * @author Administrator
     */
    private class onItemLongclichedListener implements OnItemLongClickListener {

        @Override
        public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
                                       int arg2, long arg3) {
            int groupPos = (Integer) arg1.getTag(R.layout.musiclist_group); // 参数值是在setTag时使用的对应资源id号
            int childPos = (Integer) arg1.getTag(R.layout.musiclist_item);
            if (childPos == -1) {// 长按的是父项
                // 根据groupPos判断长按的是哪个父项，做相应处理（弹框等）
            } else {
                // 根据groupPos及childPos判断长按的是哪个父项下的哪个子项，然后做相应处理。
                switch (groupPos) {
                    case 0: //第一个列表，本地列表
                        localListDialog(childPos);
                        break;
                    case 1: //第二个列表，收藏列表
                        userListDialog(childPos);
                        break;
                    case 2: //第三个列表，下载列表

                        break;


                    default:
                        break;
                }
            }
            // 这里设置为true，否则长按也会触发点击事件
            return true;
        }
    }

    /**
     * 音乐播放结束广播接收器，继续播放
     *
     * @author Administrator
     */
    private class MusicCompleteReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constans.MODLE_ORDER == cycleMode) {
                if (null == currentMusicList || 0 ==currentMusicList.size()){
                    return;
                }
                if (musicPosition < currentMusicList.size() - 1) {
                    musicPosition = musicPosition + 1;
                } else {
                    musicPosition = 0;
                }
            } else if (Constans.MODLE_RANDOM == cycleMode) {
                musicPosition = (int) (Math.random() * currentMusicList.size());
            } else {

            }
            MusicCommad(currentMusicList, Constans.PLAY_CMD, musicPosition, 0);
        }
    }

    /**
     * 底部按钮的监听器
     *
     * @author Administrator
     */
    private class OnButtomMenuClickedListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.homeb_play:
                    if (Constans.STATE_PLAY == playState) {
                        MusicCommad(currentMusicList, Constans.PUASE_CMD,
                                musicPosition, 0);
                    } else if (Constans.STATE_PUASE == playState) {
                        MusicCommad(currentMusicList, Constans.PLAY_CMD,
                                musicPosition, -1);
                    } else {
                        MusicCommad(currentMusicList, Constans.PLAY_CMD,
                                musicPosition, 0);
                    }
                    break;
                case R.id.homeb_next:
                    if (Constans.MODLE_RANDOM == cycleMode) {
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
                            0);

                    break;
                case R.id.homeb_infoLayout:
                    Intent intent = new Intent(getApplicationContext(),
                            MusicView.class);
                    intent.putExtra("SERVICE_STATE", playState);
                    startActivity(intent);
                    break;
                case R.id.homeb_order:
                    if (Constans.MODLE_ORDER == cycleMode) {
                        cycleMode = Constans.MODLE_RANDOM;
                        bOrder.setImageResource(R.drawable.random);
                    } else if (Constans.MODLE_RANDOM == cycleMode) {
                        cycleMode = Constans.MODLE_SINGLE;
                        bOrder.setImageResource(R.drawable.single);
                    } else {
                        cycleMode = Constans.MODLE_ORDER;
                        bOrder.setImageResource(R.drawable.order);
                    }

                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 接收service发送的消息，实时更新时间
     */
    private Handler timeHandler =  new Handler() {

        @Override
        public void handleMessage(Message msg) {
            try {
                String[] time = (String[]) msg.obj;
                bTime.setText(time[0] + "-" + time[1]);
            } catch (Exception e) {
            }
        }

    };


    private Handler listLoadCompleteHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.i(TAG, "音乐加载完毕");

            Map<String, List<MusicInfo>> listMap = (Map<String, List<MusicInfo>>)msg.obj;

            localMusicLists = listMap.get(MusicList.KEY_LIST_LOCAL);
            userMusicLists = listMap.get(MusicList.KEY_LIST_PROVIDER);

            currentMusicList = localMusicLists;
            appGlobalValues.setCurrentList(localMusicLists);
            currentListId = 0;

            musicLists.put(0, localMusicLists);
            musicLists.put(1, userMusicLists);
            musicLists.put(2, downloadMusicLists);

            // expandableListView设置
            expandableListView.setGroupIndicator(null); // 设置 属性 GroupIndicator
            // 去掉默认向下的箭头
            expandableListView.setCacheColorHint(0); // 设置拖动列表的时候防止出现黑色背景
            // 此处顺序需要和List中顺序一样
            groupArray.add(">本地列表" + "(" + localMusicLists.size() + ")");
            groupArray.add(">我的收藏" + "(" + userMusicLists.size() + ")");
            groupArray.add(">下载歌曲" + "(" + downloadMusicLists.size() + ")");
            // 将音乐加载到列表,并设置监听器
            MusicList.setListAdpter(getApplicationContext(), expandableListView,
                    musicLists, groupArray);
        }
    };
}
