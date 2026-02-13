package org.bxwbb.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 文件信息格式化工具方法：传入Path返回美观的详细信息字符串
 */
public class PathInfoFormatter {
    // 日期格式化（统一展示格式）
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA);
    // 文件大小格式化（保留2位小数）
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.00");
    // 分隔线（用于美化格式）
    private static final String SEPARATOR = "┌──────────────────────────────────────────────────────────────┐";
    private static final String SEPARATOR_MID = "│ ";
    private static final String SEPARATOR_END = "└──────────────────────────────────────────────────────────────┘";

    /**
     * 核心方法：传入Path对象，返回格式化的文件/文件夹详细信息字符串
     * @param path 目标文件/文件夹的Path对象
     * @return 美观格式化的详细信息字符串（异常时返回错误提示）
     */
    public static String getFormattedPathInfo(Path path) {
        // 空值校验
        if (path == null) {
            return formatError("传入的Path对象为null");
        }

        StringBuilder infoSb = new StringBuilder();
        try {
            // 1. 基础信息（必选）
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            String fileName = path.getFileName() == null ? "未知名称" : path.getFileName().toString();
            String absolutePath = path.toAbsolutePath().normalize().toString();
            String fileType = Files.isDirectory(path) ? "文件夹" : (Files.isRegularFile(path) ? "普通文件" : "特殊文件");
            String sizeStr = getReadableFileSize(attrs.size(), Files.isDirectory(path));

            // 2. 拼接头部
            infoSb.append(SEPARATOR).append("\n");
            infoSb.append(SEPARATOR_MID).append(String.format("%-58s", "文件/文件夹详细信息")).append("│\n");
            infoSb.append(SEPARATOR_MID).append(String.format("%-58s", "")).append("│\n"); // 空行

            // 3. 核心信息（左对齐标签，右对齐值，优化视觉效果）
            appendInfoLine(infoSb, "名称", fileName);
            appendInfoLine(infoSb, "完整路径", absolutePath);
            appendInfoLine(infoSb, "类型", fileType);
            appendInfoLine(infoSb, "大小", sizeStr);
            appendInfoLine(infoSb, "创建时间", DATE_FORMAT.format(new Date(attrs.creationTime().toMillis())));
            appendInfoLine(infoSb, "最后修改时间", DATE_FORMAT.format(new Date(attrs.lastModifiedTime().toMillis())));
            appendInfoLine(infoSb, "最后访问时间", DATE_FORMAT.format(new Date(attrs.lastAccessTime().toMillis())));

            // 4. 状态信息
            appendInfoLine(infoSb, "是否隐藏", Files.isHidden(path) ? "是" : "否");
            appendInfoLine(infoSb, "是否符号链接", Files.isSymbolicLink(path) ? "是" : "否");
            appendInfoLine(infoSb, "可读", Files.isReadable(path) ? "是" : "否");
            appendInfoLine(infoSb, "可写", Files.isWritable(path) ? "是" : "否");
            appendInfoLine(infoSb, "可执行", Files.isExecutable(path) ? "是" : "否");

            // 5. 扩展信息（跨平台兼容）
            try {
                // 文件所有者
                String owner = Files.getFileAttributeView(path, FileOwnerAttributeView.class)
                        .getOwner().getName();
                appendInfoLine(infoSb, "所有者", owner);

                // Linux/Mac 专属：文件权限
                if (isUnixLikeSystem()) {
                    String permissions = PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
                    appendInfoLine(infoSb, "文件权限", permissions);
                }
            } catch (Exception e) {
                // 扩展信息获取失败不影响核心信息展示
                appendInfoLine(infoSb, "扩展信息", "部分信息获取失败：" + e.getMessage());
            }

            // 6. 拼接尾部
            infoSb.append(SEPARATOR_END);

        } catch (IOException e) {
            // 核心信息获取失败，返回错误提示
            return formatError("获取文件信息失败：" + e.getMessage());
        }

        return infoSb.toString();
    }

    // ========== 辅助方法：格式化单行信息（对齐优化） ==========
    private static void appendInfoLine(StringBuilder sb, String label, String value) {
        // 标签左对齐（占8个字符），值左对齐（占48个字符），整体用│包裹
        String line = String.format("%-8s：%-48s", label, truncateValue(value, 48));
        sb.append(SEPARATOR_MID).append(line).append("│\n");
    }

    // ========== 辅助方法：文件大小转换为易读格式（区分文件/文件夹） ==========
    public static String getReadableFileSize(long bytes, boolean isDirectory) {
        // 文件夹返回"--"（因为length()对文件夹无意义）
        if (isDirectory) {
            return "--";
        }
        if (bytes <= 0) {
            return "0 B";
        }
        final String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        double size = bytes / Math.pow(1024, digitGroups);
        return SIZE_FORMAT.format(size) + " " + units[digitGroups];
    }

    // ========== 辅助方法：截断超长值（避免格式错乱） ==========
    public static String truncateValue(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "未知" : value;
        }
        // 超长值截断并加省略号
        return value.substring(0, maxLength - 3) + "...";
    }

    // ========== 辅助方法：格式化错误信息 ==========
    public static String formatError(String errorMsg) {
        return SEPARATOR + "\n" +
                SEPARATOR_MID + String.format("%-58s", "获取信息失败") + "│\n" +
                SEPARATOR_MID + String.format("%-58s", errorMsg) + "│\n" +
                SEPARATOR_END;
    }

    // ========== 辅助方法：判断是否为Linux/Mac系统 ==========
    public static boolean isUnixLikeSystem() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return osName.contains("linux") || osName.contains("mac") || osName.contains("unix");
    }

}