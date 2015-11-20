package com.airisith.util;

public class Constans {
    //广播消息
    public final static String ACTION_MUSIC_END = "com.ksmusic.musicEnd";
    public final static String ACTION_LYRIC_START = "com.ksmusic.lyricStart";
    public final static String ACTION_LYRIC_STOP = "com.ksmusic.lyricStop";

    //Activity的名字
    public final static String ACTIVITY_HOME = "HomeActivity";
    public final static String ACTIVITY_MUSIC = "MusicView";

    //playCommand常量
    public final static int PLAY_CMD = 0x01;
    public final static int PUASE_CMD = 0x02;
    public final static int STOP_CMD = 0x00;

    //循环状态
    public final static int MODLE_ORDER = 0x01; // 顺序播放
    public final static int MODLE_RANDOM = 0x02; // 随机
    public final static int MODLE_SINGLE = 0x03; // 单曲循环

    // 播放状态
    public final static int STATE_PLAY = 0x01;
    public final static int STATE_PUASE = 0x02;
    public final static int STATE_STOP = 0x00;

    public final static int ACTIVITY_CHANGED_CMD = 9;

    // 保存数据的名字
    public final static String PREFERENCES_NAME_MUSIC_STATE = "musicstate";
    public final static String PREFERENCES_ITEM_LIST_TYPE = "MUSIC_LIST_TYPE"; // 列表类型
    public final static String PREFERENCES_ITEM_CYCLE_MODLE = "CYCLE_MODE"; // 循环模式
    public final static String PREFERENCES_ITEM_CURRENT_POSITION = "CURRENT_POSITION"; // 当前位置

    // 定义两种类型：本地音乐类型，网络音乐类型
    public final static int TYPE_LOCAL = 0x100;
    public final static int TYPE_NET = 0x101;

    // intent参数
    public final static String INTENT_NAME_URL = "com.ksmusic.url"; // 歌曲url
    public final static String INTENT_NAME_CMD = "com.ksmusic.cmd"; // 控制命令
    public final static String INTENT_NAME_RATE = "com.ksmusic.rate"; // 进度

}
