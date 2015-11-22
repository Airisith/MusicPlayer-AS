package com.airisith.util;

import android.app.Application;

import com.airisith.modle.MusicInfo;

import java.util.List;

public class AppGlobalValues extends Application /*implements Thread.UncaughtExceptionHandler*/ {
    private final static String TAG = "AppGlobalValues";
    private boolean startanim = true; //开机动画
    private int playState = Constans.STATE_STOP; // 播放状态
    private int currentListType = Constans.TYPE_LOCAL; // 当前列表
    private long currentPosition = 0; // 播放进度：ms
    private boolean flagAudioFocus = false; // 是否获取到了焦点
    private boolean silentMode = false; // 安静模式
    private List<MusicInfo> currentList = null; // 音乐列表

    @Override
    public void onCreate() {
        super.onCreate();
        //Thread.setDefaultUncaughtExceptionHandler(this);
        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(this);
    }

    public int getPlayState() {
        return playState;
    }

    public void setPlayState(int state) {
        this.playState = state;
    }

    public int getCurrentListType() {
        return currentListType;
    }

    public void setCurrentListType(int currentListType) {
        this.currentListType = currentListType;
    }

    public long getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int mCurrentPosition) {
        this.currentPosition = mCurrentPosition;
    }

    public boolean getStartanim() {
        return startanim;
    }

    public void setStartanim(boolean startanim) {
        this.startanim = startanim;
    }

    public boolean getFlagAudioFocus() {
        return this.flagAudioFocus;
    }

    public void setFlagAudioFocus(boolean get) {
        this.flagAudioFocus = get;
    }

    public boolean getSilentMode() {
        return this.silentMode;
    }

    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    public void setCurrentList(List<MusicInfo> list) {
        this.currentList = list;
    }

    public List<MusicInfo> getCurrentList() {
        return currentList;
    }
}
