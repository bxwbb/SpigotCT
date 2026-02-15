package org.bxwbb.Util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bxwbb.Main;
import org.bxwbb.Util.Task.ControllableThreadPool;
import org.bxwbb.Util.Task.ControllableThreadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FileUtil {

    public static final ControllableThreadPool FILE_IO_EXECUTOR;
    // 语言缓存键值对
    public final static Map<String, String> LANG_MAP = new java.util.HashMap<>();
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(?<!\\\\)\\{(\\d+)}");
    // 项目核心保存目录
    public static String ROOT_PATH = "F:\\McServer\\Plugin\\SpigotCT\\src";
    // 项目核心读取目录列表
    public static List<String> READ_PATH_LIST = List.of(
            "F:\\McServer\\Plugin\\SpigotCT\\res"
    );
    // 空文件夹图标
    public static String EMPTY_FOLDER_ICON;
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

    static {
        FILE_IO_EXECUTOR = new ControllableThreadPool(
                5,
                20,
                30L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1000)
        );
    }

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
                if (jsonObject.containsKey("empty_folder")) {
                    EMPTY_FOLDER_ICON = jsonObject.getString("empty_folder");
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
                } else if (isEmptyFolder(file)) {
                    Image icon = new ImageIcon(Objects.requireNonNull(loadFile(EMPTY_FOLDER_ICON)).getPath()).getImage();
                    icon = icon.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                    return icon;
                } else {
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

    /**
     * 获取加载中的图标
     */
    public static ImageIcon getLoadingIcon() {
        Image icon = new ImageIcon(Objects.requireNonNull(Main.class.getResource("/SpigotCT/icon/Loading.png"))).getImage();
        icon = icon.getScaledInstance(18, 18, Image.SCALE_SMOOTH);
        return new ImageIcon(icon);
    }

    public static boolean isEmptyFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) return true;
        File[] files = folder.listFiles();
        if (files == null) {
            return false;
        }
        return files.length == 0;
    }

    public static void openFile(File file) throws IOException {
        if (file == null || !file.exists()) return;
        Desktop.getDesktop().open(file);
    }

    /**
     * 异步递归统计文件夹内所有文件数量（包含子文件夹及子文件夹内的所有文件）
     * 操作在FILE_IO_EXECUTOR线程池中执行，避免阻塞主线程
     *
     * @param file     要统计的文件/文件夹对象
     * @param callback 统计完成后的回调（结果通过回调返回）
     */
    public static String countAllFilesAsync(File file, CountFileCallback callback) {
        // 空回调防护
        CountFileCallback safeCallback = callback == null ? (count) -> {
        } : callback;

        ControllableThreadTask<Void> task = new ControllableThreadTask<>() {
            @Override
            protected Void doWork() {
                int result = countAllFilesSync(file);
                // 执行回调（如需在Swing UI线程执行回调，可添加SwingUtilities.invokeLater）
                safeCallback.onCountCompleted(result);
                return null;
            }
        };

        // 提交任务到文件IO线程池
        return FILE_IO_EXECUTOR.submit(task);
    }

    /**
     * 同步版本：递归统计文件夹内所有文件数量（核心逻辑，供异步方法调用）
     *
     * @param file 要统计的文件/文件夹对象
     * @return 统计结果：
     * - 0：空文件夹（无任何文件/子文件夹）
     * - >0：文件夹内的总文件数量（包含所有层级子文件）
     * - -1：file不是文件夹/不存在/无法访问/权限不足等异常情况
     */
    public static int countAllFilesSync(File file) {
        // 1. 基础校验：文件不存在/不是文件夹 → 返回-1
        if (file == null || !file.exists() || !file.isDirectory()) {
            log.warn("统计文件数量失败：文件不存在或不是文件夹 - {}", file == null ? "null" : file.getPath());
            return -1;
        }

        // 2. 权限校验：无法读取文件夹 → 返回-1
        if (!file.canRead()) {
            log.warn("统计文件数量失败：无读取权限 - {}", file.getPath());
            return -1;
        }

        // 3. 递归统计核心逻辑
        return countFilesRecursively(file);
    }

    /**
     * 递归统计指定文件夹下的所有文件数量（修复漏统计、异常处理问题）
     *
     * @param folder 目标文件夹
     * @return 文件夹下所有文件的总数（仅统计文件，不含目录）
     */
    private static int countFilesRecursively(File folder) {
        // 前置校验：文件夹不存在/不是目录，直接返回0
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return 0;
        }

        int totalCount = 0;
        // 替换listFiles()为更鲁棒的遍历方式，添加文件过滤器
        File[] files = folder.listFiles(pathname -> {
            // 跳过符号链接（可选，根据需求调整）
            return !Files.isSymbolicLink(pathname.toPath());
        });

        // 处理listFiles返回null的情况（权限不足、文件被占用等）
        if (files == null) {
            log.warn("警告：无法访问目录 {}（权限不足/文件被占用）", folder.getAbsolutePath());
            return 0;
        }

        for (File f : files) {
            try {
                // 确保文件/目录存在（避免遍历过程中文件被删除）
                if (!f.exists()) {
                    continue;
                }

                if (f.isFile()) {
                    totalCount++;
                } else if (f.isDirectory()) {
                    // 递归统计子目录中的文件
                    totalCount += countFilesRecursively(f);
                }
            } catch (SecurityException e) {
                // 捕获权限异常，避免程序中断，仅打印日志
                log.warn("警告：无权限访问 {} - {}", f.getAbsolutePath(), e.getMessage());
            }
        }

        return totalCount;
    }

    /**
     * 统计文件数量的回调接口（用于异步返回结果）
     */
    @FunctionalInterface
    public interface CountFileCallback {
        /**
         * 统计完成回调
         *
         * @param count 统计结果（0=空文件夹，>0=文件总数，-1=异常）
         */
        void onCountCompleted(int count);
    }

    /**
     * 单位转换
     *
     * @param bytes 字节数
     * @return 转换后的字符串
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s",
                bytes / Math.pow(1024, digitGroups),
                units[digitGroups]);
    }

    /**
     * 根据路径获取图片Icon
     *
     * @param path   图片路径
     * @param width  图片宽度
     * @param height 图片高度
     * @return 图片Icon
     */
    public static ImageIcon getImageIconToPath(String path, int width, int height) {
        return new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    /**
     * 根据路径获取图片Icon
     *
     * @param path 图片路径
     * @return 图片Icon
     */
    public static ImageIcon getImageIconToPath(String path) {
        return getImageIconToPath(path, 20, 20);
    }

    /**
     * 逐级创建文件夹：从路径顶级节点开始，依次检查/创建每一级目录
     * 特性：仅输出错误日志（SLF4J），无成功/过程日志，极简且符合生产级日志规范
     *
     * @param fullPath 要创建的完整文件夹路径（支持Windows/Linux/Mac）
     * @return true=创建成功（或路径已存在），false=创建失败
     */
    public static boolean createFoldersStepByStep(String fullPath) {
        // 初始化SLF4J Logger（建议放到类级别，此处为完整独立方法）
        Logger log = LoggerFactory.getLogger(FileUtil.class); // 替换为你的实际类名

        // 1. 空路径/空白路径防护（仅输出错误日志）
        if (fullPath == null || fullPath.trim().isEmpty()) {
            log.error("创建文件夹失败 - 传入路径为空");
            return false;
        }

        // 2. 标准化路径 + 根节点校验
        Path standardPath = Paths.get(fullPath).normalize();
        Path rootPath = standardPath.getRoot();
        if (rootPath == null) {
            log.error("创建文件夹失败 - 无法识别路径的根节点 - {}", fullPath);
            return false;
        }

        // 3. 初始化当前遍历路径（根节点）
        File currentDir = rootPath.toFile();
        Path relativePath = rootPath.relativize(standardPath);
        int pathCount = relativePath.getNameCount();

        // 4. 逐级遍历创建
        for (int i = 0; i < pathCount; i++) {
            String currentNode = relativePath.getName(i).toString();
            currentDir = new File(currentDir, currentNode);

            if (currentDir.exists()) {
                // 存在但不是文件夹（冲突）→ 输出错误日志
                if (!currentDir.isDirectory()) {
                    log.error("创建文件夹失败 - 路径冲突（存在同名文件） - {}", currentDir.getAbsolutePath());
                    return false;
                }
                // 存在且是文件夹 → 无日志，直接进入下一级
            } else {
                // 不存在 → 尝试创建，失败则输出错误日志
                boolean created = currentDir.mkdir();
                if (!created) {
                    log.error("创建文件夹失败 - 无法创建层级 - {}", currentDir.getAbsolutePath());
                    return false;
                }
                // 创建成功 → 无日志
            }
        }

        // 所有层级处理完成 → 无日志，直接返回成功
        return true;
    }

    public static void createFile(Path path, String name) {
        try {
            FileUtil.createFoldersStepByStep(path.toString());
            Files.createFile(path.resolve(name));
        } catch (IOException e) {
            log.error("文件创建时发生错误 -> ", e);
        }
    }

    /**
     * 校验文件夹名称是否合法（跨平台通用）
     */
    public static boolean isValidFolderName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        // 不能以 . 或 空格 开头/结尾
        if (name.startsWith(".") || name.startsWith(" ")
                || name.endsWith(" ")) {
            return false;
        }
        // 禁止的非法字符
        if (name.matches(".*[\\\\/:*?\"<>|].*")) {
            return false;
        }
        // 长度限制
        return name.length() <= 255;
    }

    /**
     * 【单方法】跨平台设置文件/文件夹隐藏/取消隐藏
     * 支持 Windows/Linux/macOS，无需额外调用其他方法
     *
     * @param path   目标文件/文件夹路径（如 D:/test.txt、/home/user/test）
     * @param hidden true=隐藏，false=取消隐藏
     * @return 操作是否成功
     * @throws IllegalArgumentException 路径为空/不存在时抛出
     * @throws IOException              权限不足/操作失败时抛出
     */
    public static boolean setFileHidden(String path, boolean hidden) throws IllegalArgumentException, IOException {
        // ========== 1. 基础参数校验 ==========
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空！");
        }
        File target = new File(path.trim());
        if (!target.exists()) {
            throw new IllegalArgumentException("目标文件/文件夹不存在：" + path);
        }
        Path targetPath = target.toPath();
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            DosFileAttributeView dosView = Files.getFileAttributeView(targetPath, DosFileAttributeView.class);
            if (dosView == null) {
                throw new IOException("当前 Windows 系统不支持 DOS 文件属性操作");
            }
            dosView.setHidden(hidden);
            return dosView.readAttributes().isHidden() == hidden;
        } else if (osName.contains("linux") || osName.contains("mac") || osName.contains("unix")) {
            String parentPath = target.getParent() == null ? "." : target.getParent();
            String originalName = target.getName();

            // 隐藏逻辑：添加 . 前缀 + 最小权限
            if (hidden) {
                if (!originalName.startsWith(".")) {
                    String hiddenName = "." + originalName;
                    File hiddenFile = new File(parentPath, hiddenName);
                    if (hiddenFile.exists()) Files.deleteIfExists(hiddenFile.toPath());
                    if (!target.renameTo(hiddenFile)) {
                        throw new IOException("重命名失败，无法隐藏：" + path);
                    }
                    // 设置最小权限（仅所有者可读）
                    Set<PosixFilePermission> minPerms = new HashSet<>();
                    minPerms.add(PosixFilePermission.OWNER_READ);
                    if (hiddenFile.isDirectory()) minPerms.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(hiddenFile.toPath(), minPerms);
                } else {
                    // 已隐藏，仅更新权限
                    Set<PosixFilePermission> minPerms = new HashSet<>();
                    minPerms.add(PosixFilePermission.OWNER_READ);
                    if (target.isDirectory()) minPerms.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(targetPath, minPerms);
                }
            } else {
                if (originalName.startsWith(".")) {
                    String showName = originalName.substring(1);
                    File showFile = new File(parentPath, showName);
                    if (showFile.exists()) Files.deleteIfExists(showFile.toPath());
                    if (!target.renameTo(showFile)) {
                        throw new IOException("重命名失败，无法取消隐藏：" + path);
                    }
                    // 恢复默认权限
                    Set<PosixFilePermission> defaultPerms = new HashSet<>();
                    defaultPerms.add(PosixFilePermission.OWNER_READ);
                    defaultPerms.add(PosixFilePermission.OWNER_WRITE);
                    defaultPerms.add(PosixFilePermission.OWNER_EXECUTE);
                    defaultPerms.add(PosixFilePermission.GROUP_READ);
                    defaultPerms.add(PosixFilePermission.GROUP_EXECUTE);
                    defaultPerms.add(PosixFilePermission.OTHERS_READ);
                    defaultPerms.add(PosixFilePermission.OTHERS_EXECUTE);
                    Files.setPosixFilePermissions(showFile.toPath(), defaultPerms);
                } else {
                    // 已显示，仅恢复权限
                    Set<PosixFilePermission> defaultPerms = new HashSet<>();
                    defaultPerms.add(PosixFilePermission.OWNER_READ);
                    defaultPerms.add(PosixFilePermission.OWNER_WRITE);
                    defaultPerms.add(PosixFilePermission.OWNER_EXECUTE);
                    defaultPerms.add(PosixFilePermission.GROUP_READ);
                    defaultPerms.add(PosixFilePermission.GROUP_EXECUTE);
                    defaultPerms.add(PosixFilePermission.OTHERS_READ);
                    defaultPerms.add(PosixFilePermission.OTHERS_EXECUTE);
                    Files.setPosixFilePermissions(targetPath, defaultPerms);
                }
            }
            return true;
        } else {
            if (hidden) {
                // 仅设置最小权限
                Set<PosixFilePermission> minPerms = new HashSet<>();
                minPerms.add(PosixFilePermission.OWNER_READ);
                if (target.isDirectory()) minPerms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(targetPath, minPerms);
            } else {
                // 恢复默认权限
                Set<PosixFilePermission> defaultPerms = new HashSet<>();
                defaultPerms.add(PosixFilePermission.OWNER_READ);
                defaultPerms.add(PosixFilePermission.OWNER_WRITE);
                defaultPerms.add(PosixFilePermission.OWNER_EXECUTE);
                defaultPerms.add(PosixFilePermission.GROUP_READ);
                defaultPerms.add(PosixFilePermission.GROUP_EXECUTE);
                defaultPerms.add(PosixFilePermission.OTHERS_READ);
                defaultPerms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(targetPath, defaultPerms);
            }
            return true;
        }
    }

    /**
     * 将文件/文件夹移至系统回收站（废纸篓）
     *
     * @param path 目标文件/文件夹路径
     * @return 操作是否成功
     * @throws IllegalArgumentException 路径为空/不存在时抛出
     * @throws IOException              系统命令执行失败/权限不足时抛出
     */
    public static boolean moveToRecycleBin(String path) throws IOException {
        File target = new File(path);
        String absolutePath = target.getAbsolutePath();
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (!target.exists()) {
            throw new IOException("目标文件/文件夹不存在 - " + absolutePath);
        }

        if (os.contains("win")) {
            if (Desktop.isDesktopSupported()) {
                try {
                    return Desktop.getDesktop().moveToTrash(target);
                } catch (Exception e) {
                    log.error("移动文件夹到回收站失败 ->", e);
                    return false;
                }
            } else {
                log.error("系统不支持此功能，根据设置不执行强制删除");
                return false;
            }
        } else if (os.contains("mac") || os.contains("linux")) {
            return !target.exists();
        } else {
            throw new IOException("不支持的操作系统：" + os);
        }
    }

    /**
     * 弹出确认框后，将文件/文件夹移至系统回收站
     *
     * @param path   目标文件/文件夹路径
     * @param parent 弹窗父组件（用于定位弹窗，传null则居中显示）
     * @return 操作是否成功（取消返回false，确认后执行成功返回true，失败抛异常）
     * @throws IllegalArgumentException 路径为空/不存在时抛出
     * @throws IOException              系统命令执行失败/权限不足时抛出
     */
    public static boolean deleteWithConfirm(String path, Component parent) throws IllegalArgumentException, IOException {
        // 1. 基础参数校验（提前校验，避免无效弹窗）
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空！");
        }
        File target = new File(path.trim());
        if (!target.exists()) {
            throw new IllegalArgumentException("目标文件/文件夹不存在：" + path);
        }

        // 2. 弹出系统风格的确认对话框
        String title = FileUtil.getLang("tip.question");
        String message = FileUtil.getLang("tip.delete", target.getName());
        // 弹窗选项：YES=确认，NO=取消，图标为警告型
        int confirmResult = JOptionPane.showConfirmDialog(
                parent,          // 父组件（弹窗定位到该组件旁，传null则居中）
                message,         // 提示信息
                title,           // 弹窗标题
                JOptionPane.YES_NO_OPTION, // 按钮类型：确认/取消
                JOptionPane.WARNING_MESSAGE // 图标类型：警告
        );

        // 3. 根据用户选择执行操作
        if (confirmResult == JOptionPane.YES_OPTION) {
            // 用户点击「确认」→ 调用回收站删除方法
            return moveToRecycleBin(path);
        } else {
            // 用户点击「取消」/ 关闭弹窗 → 返回false，不执行操作
            return false;
        }
    }

    /**
     * 复制文件/文件夹（含子内容）到目标路径
     * 复制文件夹时：先创建源文件夹本身，再复制内部内容（如A→B → B/A/内容）
     *
     * @param sourcePath 源路径A（文件/文件夹）
     * @param destPath   目标路径B（文件夹/文件路径）
     * @return 是否复制成功
     * @throws IllegalArgumentException 路径为空/源路径不存在/参数非法时抛出
     * @throws IOException              复制失败/权限不足/文件被占用时抛出
     */
    public static boolean copyFileOrDir(String sourcePath, String destPath) throws IllegalArgumentException, IOException {
        // 1. 基础参数校验
        if (sourcePath == null || sourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("源路径不能为空！");
        }
        if (destPath == null || destPath.trim().isEmpty()) {
            throw new IllegalArgumentException("目标路径不能为空！");
        }

        Path source = Paths.get(sourcePath.trim()).toAbsolutePath().normalize();
        Path dest = Paths.get(destPath.trim()).toAbsolutePath().normalize();

        if (!Files.exists(source)) {
            throw new IllegalArgumentException("源文件/文件夹不存在：" + sourcePath);
        }

        // ========== 防无限递归检测 ==========
        if (Files.isDirectory(source) && Files.isDirectory(dest) && dest.startsWith(source)) {
            throw new IllegalArgumentException("禁止将文件夹复制到自身的子目录中（会导致无限递归）：" +
                    "源=" + source + "，目标=" + dest);
        }

        // 2. 区分文件/文件夹执行复制
        if (Files.isRegularFile(source)) {
            // 复制文件（逻辑不变）
            copySingleFile(source, dest);
        } else if (Files.isDirectory(source)) {
            // ========== 关键修复：先拼接源文件夹名称到目标路径 ==========
            // 最终目标文件夹 = 传入的目标路径 + 源文件夹名称（如 B + A → B/A）
            Path finalDestDir = dest.resolve(source.getFileName());
            // 复制文件夹（先创建A文件夹，再复制内部内容）
            copyDirectory(source, finalDestDir);
            // 更新dest为最终目标文件夹（用于结果校验）
            dest = finalDestDir;
        } else {
            throw new IOException("不支持的文件类型：" + sourcePath);
        }

        // 3. 校验复制结果
        return Files.exists(dest);
    }

    /**
     * 辅助方法：复制单个文件（逻辑不变）
     */
    private static void copySingleFile(Path source, Path dest) throws IOException {
        if (Files.isDirectory(dest)) {
            dest = dest.resolve(source.getFileName());
        }

        Path parentDir = dest.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Files.copy(
                source,
                dest,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
        );
    }

    /**
     * 辅助方法：复制文件夹（先创建文件夹本身，再复制内部内容）
     */
    private static void copyDirectory(Path sourceDir, Path destDir) throws IOException {
        // 1. 先创建源文件夹本身（核心：确保目标路径下有同名文件夹）
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }

        // 2. 遍历源文件夹内部内容，复制到目标文件夹
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // 跳过源文件夹本身（只处理子目录）
                if (Objects.equals(dir.normalize(), sourceDir.normalize())) {
                    return FileVisitResult.CONTINUE;
                }

                // 跳过目标文件夹（防循环）
                if (Objects.equals(dir.normalize(), destDir.normalize())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                // 构建子目录路径（如 sourceDir/sub → destDir/sub）
                Path targetSubDir = destDir.resolve(sourceDir.relativize(dir));
                if (!Files.exists(targetSubDir)) {
                    Files.createDirectories(targetSubDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // 复制文件到目标文件夹对应位置（如 sourceDir/file.txt → destDir/file.txt）
                Path targetFile = destDir.resolve(sourceDir.relativize(file));
                if (!Files.exists(targetFile.getParent())) {
                    Files.createDirectories(targetFile.getParent());
                }
                Files.copy(
                        file,
                        targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                );
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("跳过无法访问的文件：" + file + "，原因：" + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

}