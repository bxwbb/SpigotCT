package org.bxwbb.MiniWindow;

import org.bxwbb.Util.FileUtil;

import java.util.HashMap;
import java.util.Map;

public class MiniWindowEnum {

    public record MiniWindowInfo(Class<? extends MiniWindow> clazz, String path) {}

    private static final Map<String, MiniWindowInfo> WINDOW_INFO_MAP = new HashMap<>();

    static {
        registerWindow(FileUtil.getLang("miniWindow.startPage.title"), new MiniWindowInfo(StartPage.class, "/SpigotCT/icon/MiniWindowIcon/StartPage.png"));
        registerWindow(FileUtil.getLang("miniWindow.fileManager.title"), new MiniWindowInfo(FileManager.class, "/SpigotCT/icon/MiniWindowIcon/FileManager.png"));
    }

    public static Map<String, MiniWindowInfo> getWindowInfoMap() {
        return WINDOW_INFO_MAP;
    }

    /**
     * 注册窗口
     */
    public static void registerWindow(String name, MiniWindowInfo miniWindowInfo) {
        WINDOW_INFO_MAP.put(name, miniWindowInfo);
    }

    /**
     * 获取窗口
     */
    public static MiniWindowInfo getWindowInfo(String name) {
        return WINDOW_INFO_MAP.get(name);
    }

    /**
     * 删除窗口
     */
    public static void removeWindow(String name) {
        WINDOW_INFO_MAP.remove(name);
    }

}
