package org.bxwbb.Util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bxwbb.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtil {

    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);
    // 项目核心保存目录
    public static String ROOT_PATH = "F:\\McServer\\Plugin\\SpigotCT\\src";
    // 项目核心读取目录列表
    public static List<String> READ_PATH_LIST = List.of(
            "F:\\McServer\\Plugin\\SpigotCT\\res"
    );

    public static final ExecutorService FILE_IO_EXECUTOR;

    static {
        FILE_IO_EXECUTOR = Executors.newFixedThreadPool(3, new ThreadFactory() {
            private int threadCount = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "FileOperation-Thread-" + (++threadCount));
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((t, e) -> log.error("文件操作线程异常 -> ", e));
                return thread;
            }
        });
    }

    // 语言缓存键值对
    public final static Map<String, String> LANG_MAP = new java.util.HashMap<>();
    // 使用的语言
    public static String LANG_NAME;
    public static File languageJsonFile;
    public static ObjectMapper objectMapper = new ObjectMapper();
    // 文件图标缓存
    public static Map<String, String> FILE_ICON_MAP;
    // 文件夹图标缓存
    public static Map<String, String> FOLDER_ICON_MAP;
    // 默认文件图标
    public static String DEFAULT_FILE_ICON;
    // 默认文件夹图标
    public static String[] DEFAULT_FOLDER_ICON = new String[2];
    // 根文件夹图标
    public static String ROOT_FOLDER_ICON;

    public static void shutdown() {
        FILE_IO_EXECUTOR.shutdown();
    }

    public static void setLang(String langName) {
        log.info("将语言设置为 - {}", langName);
        LANG_NAME = langName;
        for (String s : READ_PATH_LIST) {
            languageJsonFile = new File(s + "/language/" + langName + ".json");
            if (languageJsonFile.isFile()) {
                if (languageJsonFile.canRead()) {
                    log.info("找到了可以读取的语言文件 - {}", languageJsonFile.getPath());
                    break;
                } else {
                    log.warn("找到了语言文件，但是无法读取 - {}", languageJsonFile.getPath());
                    log.info("继续寻找新的语言文件");
                }
            }
            log.warn("没有找到语言文件 - {}", languageJsonFile.getPath());
        }
        if (languageJsonFile == null) log.error("查询结束，未找到语言文件");
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(?<!\\\\)\\{(\\d+)}");

    /**
     * 多语言获取核心方法：索引占位符+可变参数+转义花括号+索引匹配警告
     *
     * @param key    语言编码ID（如abc.hello）
     * @param params 可变长参数，按索引匹配{0}{1}{2}，支持任意数量
     * @return 解析替换后的文本，异常返回key本身
     */
    public static String getLang(String key, String... params) {
        if (LANG_MAP.containsKey(key)) {
            return parseTemplate(key, LANG_MAP.get(key), params);
        }

        if (languageJsonFile == null || !languageJsonFile.exists() || !languageJsonFile.isFile() || !languageJsonFile.canRead()) {
            log.warn("语言文件损坏/不存在，未知的键 - {}", key);
            return key;
        }

        try (JsonParser jsonParser = objectMapper.createParser(languageJsonFile)) {
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jsonParser.getCurrentName();
                if (key.equals(fieldName)) {
                    jsonParser.nextToken();
                    String template = jsonParser.getText();
                    LANG_MAP.put(key, template);
                    return parseTemplate(key, template, params);
                }
            }
        } catch (IOException e) {
            log.warn("语言文件解析运行时异常，键 - {}，异常信息 -> {}", key, e.toString());
            return key;
        }

        log.warn("语言文件中未找到指定键 - {}", key);
        return key;
    }

    /**
     * 核心模板解析：1.替换占位符 2.校验索引匹配 3.转义花括号还原 4.索引缺失打WARN
     *
     * @param key      语言编码ID（用于日志定位）
     * @param template 原始模板（如：你好{0}，再见{1}；我是\\{0}\\，你好{1}）
     * @param params   可变参数数组
     * @return 最终解析后的文本
     */
    private static String parseTemplate(String key, String template, String... params) {
        if (params == null || params.length == 0) {
            return unescapeBrace(template);
        }

        String result = template;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String fullPlaceholder = matcher.group(0);
            int index = Integer.parseInt(matcher.group(1));

            if (index < params.length) {
                String paramValue = params[index] == null ? "" : params[index];
                result = result.replace(fullPlaceholder, paramValue);
            } else {
                log.warn("语言键 - {}，模板占位符索引{}无对应参数，保留原占位符", key, index);
            }
        }

        return unescapeBrace(result);
    }

    /**
     * 转义花括号还原：处理\\{ → {，\\} → }，不影响其他正常\转义（若有）
     */
    private static String unescapeBrace(String text) {
        return text.replace("\\{", "{").replace("\\}", "}");
    }

    /**
     * 弹出文件夹选择框，单选文件夹，返回选中的File对象
     *
     * @param parent      父容器（JFrame/JDialog/Component），弹窗随父容器居中；传null则屏幕居中
     * @param dialogTitle 选择框弹窗标题（如：请选择语言文件所在文件夹）
     * @param initDir     初始打开路径（如new File(".")=项目根目录，传null则用系统默认路径）
     * @return 选中的文件夹File对象；用户取消选择/操作异常则返回null
     */
    public static File chooseSingleFolder(Component parent, String dialogTitle, File initDir) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

        if (dialogTitle != null && !dialogTitle.isBlank()) {
            fileChooser.setDialogTitle(dialogTitle);
        } else {
            fileChooser.setDialogTitle("请选择文件夹");
        }

        if (initDir != null && initDir.exists() && initDir.isDirectory()) {
            fileChooser.setCurrentDirectory(initDir);
        }

        int result = fileChooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            log.info("用户选择了文件（夹） - {}", fileChooser.getSelectedFile().getPath());
            return fileChooser.getSelectedFile();
        }
        log.error("用户没有选择文件");
        return null;
    }

    public static File chooseSingleFolder(Component parent) {
        return chooseSingleFolder(parent, null, null);
    }

    public static File chooseSingleFolder(Component parent, String dialogTitle) {
        return chooseSingleFolder(parent, dialogTitle, null);
    }

    /**
     * 单层遍历文件夹
     */
    public static List<File> listFilesOnce(File folder) {
        if (!folder.exists() || !folder.isDirectory()) {
            log.error("文件夹不存在 - {}", folder.getPath());
            return null;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            log.warn("文件夹为空 - {}", folder.getPath());
            return null;
        }
        List<File> ret = new ArrayList<>();
        int index = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                ret.add(index++, file);
            } else if (file.isFile()) {
                ret.add(file);
            }
        }

        return ret;
    }

    public static void loadFileIcon() {
        File file = FileUtil.loadFile("define\\file_icon.json");
        FILE_ICON_MAP = new HashMap<>();
        FOLDER_ICON_MAP = new HashMap<>();
        if (file != null) {
            try (InputStream is = new FileInputStream(file)) {
                JSONObject jsonObject = JSON.parseObject(is, StandardCharsets.UTF_8, JSONObject.class);
                if (jsonObject.containsKey("project_folder")) {
                    ROOT_FOLDER_ICON = jsonObject.getString("project_folder");
                }
                if (jsonObject.containsKey("default_file")) {
                    DEFAULT_FILE_ICON = jsonObject.getString("default_file");
                }
                if (jsonObject.containsKey("default_folder")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("default_folder");
                    DEFAULT_FOLDER_ICON[0] = jsonArray.getString(0);
                    DEFAULT_FOLDER_ICON[1] = jsonArray.getString(1);
                }
                if (jsonObject.containsKey("define_files")) {
                    JSONObject files = jsonObject.getJSONObject("define_files");
                    for (String s : files.keySet()) {
                        FILE_ICON_MAP.put(s, files.getString(s));
                    }
                }
            } catch (Exception e) {
                log.error("输入流解析JSON失败 - {}", e.getMessage());
            }
        }
    }

    public static Image getFileIcon(File file, int width, int height, boolean isRoot, boolean open) {
        if (FILE_ICON_MAP == null || FOLDER_ICON_MAP == null) loadFileIcon();
        String key;
        if (file.isFile()) {
            key = file.getName();
            if (FILE_ICON_MAP != null) {
                for (String s : FILE_ICON_MAP.keySet()) {
                    if (Pattern.matches(s, key)) {
                        Image icon = new ImageIcon(Objects.requireNonNull(loadFile(FILE_ICON_MAP.get(s))).getPath()).getImage();
                        icon = icon.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                        return icon;
                    }
                }
                log.warn("未知的文件图标索引 - {}", key);
                Image icon = new ImageIcon(Objects.requireNonNull(loadFile(DEFAULT_FILE_ICON)).getPath()).getImage();
                icon = icon.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                return icon;
            } else {
                log.error("文件图标索引加载失败");
                Image icon = new ImageIcon(Objects.requireNonNull(Main.class.getResource("/SpigotCT/icon/FailLoad.png"))).getImage();
                icon = icon.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                return icon;
            }
        } else {
            key = file.getName();
            if (FOLDER_ICON_MAP != null) {
                if (isRoot) {
                    Image icon = new ImageIcon(Objects.requireNonNull(loadFile(ROOT_FOLDER_ICON)).getPath()).getImage();
                    icon = icon.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                    return icon;
                } else if (FOLDER_ICON_MAP.containsKey(key)) {
                    Image icon = new ImageIcon(Objects.requireNonNull(loadFile(FOLDER_ICON_MAP.get(key))).getPath()).getImage();
                    icon = icon.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                    return icon;
                } else {
//                    log.warn("未知的文件夹图标索引 - {}", key);
                    Image icon;
                    if (open) {
                        icon = new ImageIcon(Objects.requireNonNull(loadFile(DEFAULT_FOLDER_ICON[0])).getPath()).getImage();
                    } else {
                        icon = new ImageIcon(Objects.requireNonNull(loadFile(DEFAULT_FOLDER_ICON[1])).getPath()).getImage();
                    }
                    icon = icon.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                    return icon;
                }
            } else {
                log.error("文件夹图标索引加载失败");
                Image icon = new ImageIcon(Objects.requireNonNull(Main.class.getResource("/SpigotCT/icon/FailLoad.png"))).getImage();
                icon = icon.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                return icon;
            }
        }
    }

    /**
     * 加载指定的文件
     */
    public static File loadFile(String path) {
        if (!path.startsWith("\\")) path = "\\" + path;
        for (String s : READ_PATH_LIST) {
            File file = new File(s + path);
            if (file.exists() && file.isFile()) {
                return file;
            } else if (file.exists() && file.isDirectory()) {
                return file;
            }
        }
        log.error("加载文件失败 - {}", path);
        return null;
    }

}
