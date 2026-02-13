package org.bxwbb.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 文件剪贴板工具类：支持文件/文本/文件路径的剪贴板操作，跨平台兼容Windows/macOS/Linux
 * 核心功能：
 * 1. 复制单个/多个文件到剪贴板（文件管理器可粘贴）
 * 2. 复制文件路径/文本到剪贴板
 * 3. 从剪贴板读取文件/文本/文件路径
 * 4. 清空剪贴板、检查剪贴板内容类型
 */
public class ClipboardUtil {
    private static final Logger log = LoggerFactory.getLogger(ClipboardUtil.class);
    // 系统剪贴板实例（懒加载+线程安全）
    private static volatile Clipboard SYSTEM_CLIPBOARD;
    // 空Transferable（用于清空剪贴板）
    private static final Transferable EMPTY_TRANSFERABLE = new StringSelection("");

    // ===================== 私有构造器：禁止实例化 =====================
    private ClipboardUtil() {
        throw new UnsupportedOperationException("工具类禁止实例化");
    }

    // ===================== 核心工具方法：获取系统剪贴板（懒加载） =====================
    private static Clipboard getSystemClipboard() {
        if (SYSTEM_CLIPBOARD == null) {
            synchronized (ClipboardUtil.class) {
                if (SYSTEM_CLIPBOARD == null) {
                    try {
                        SYSTEM_CLIPBOARD = Toolkit.getDefaultToolkit().getSystemClipboard();
                    } catch (HeadlessException e) {
                        log.error("获取系统剪贴板失败 - 当前环境为无图形界面（Headless）模式", e);
                        throw new RuntimeException("无法访问系统剪贴板（Headless环境）", e);
                    }
                }
            }
        }
        return SYSTEM_CLIPBOARD;
    }

    // ===================== 功能1：文件剪贴板操作（核心原有功能增强） =====================

    /**
     * 将单个文件复制到系统剪贴板（文件管理器可粘贴）
     *
     * @param file 要复制的文件（支持文件/目录，自动校验存在性）
     * @return 是否复制成功
     */
    public static boolean copyFileToClipboard(File file) {
        if (file == null) {
            log.error("复制文件到剪贴板失败 - 文件对象为null");
            return false;
        }
        if (!file.exists()) {
            log.error("复制文件到剪贴板失败 - 文件/目录不存在，路径={}", file.getAbsolutePath());
            return false;
        }

        List<File> fileList = Collections.singletonList(file);
        return copyFilesToClipboard(fileList);
    }

    /**
     * 将多个文件/目录复制到系统剪贴板（文件管理器可粘贴）
     *
     * @param fileList 要复制的文件列表（非空，自动过滤不存在的文件）
     * @return 是否复制成功
     */
    public static boolean copyFilesToClipboard(List<File> fileList) {
        // 空列表校验
        if (fileList == null || fileList.isEmpty()) {
            log.error("复制文件到剪贴板失败 - 文件列表为空");
            return false;
        }

        // 过滤有效文件（存在的文件/目录）
        List<File> validFiles = fileList.stream()
                .filter(Objects::nonNull)
                .filter(File::exists)
                .toList();

        if (validFiles.isEmpty()) {
            log.error("复制文件到剪贴板失败 - 文件列表中无有效文件（均不存在）");
            return false;
        }

        try {
            // 构建文件传输对象（防御性拷贝，避免外部修改）
            Transferable transferable = new FileTransferable(new ArrayList<>(validFiles));
            // 设置到剪贴板（添加剪贴板所有者，支持回调）
            getSystemClipboard().setContents(transferable, (clipboard, contents) ->
                    log.warn("剪贴板内容已被覆盖 - 原文件列表：{}", validFiles.stream().map(File::getAbsolutePath).toList()));
            return true;
        } catch (HeadlessException e) {
            log.error("复制文件到剪贴板失败 - 当前环境为无图形界面（Headless）模式", e);
            return false;
        } catch (SecurityException e) {
            log.error("复制文件到剪贴板失败 - 权限不足，无法访问剪贴板", e);
            return false;
        } catch (Exception e) {
            log.error("复制文件到剪贴板失败 - 未知异常", e);
            return false;
        }
    }

    /**
     * 从剪贴板读取已复制的文件/目录列表
     *
     * @return 不可修改的文件列表（空列表=剪贴板无文件/读取失败）
     */
    public static List<File> getFilesFromClipboard() {
        try {
            Transferable transferable = getSystemClipboard().getContents(null);
            if (transferable == null) {
                log.debug("从剪贴板读取文件失败 - 剪贴板为空");
                return Collections.emptyList();
            }

            // 检查是否支持文件列表格式
            if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                log.debug("从剪贴板读取文件失败 - 剪贴板内容非文件类型");
                return Collections.emptyList();
            }

            // 获取文件列表（类型安全校验）
            Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
            if (!(data instanceof List<?> rawList)) {
                log.warn("从剪贴板读取文件失败 - 数据类型非列表，实际类型：{}", data.getClass().getName());
                return Collections.emptyList();
            }

            // 过滤有效File对象
            List<File> fileList = new ArrayList<>();
            for (Object obj : rawList) {
                if (obj instanceof File file) {
                    fileList.add(file);
                } else {
                    log.warn("剪贴板文件列表包含非File类型数据，类型：{}", obj.getClass().getName());
                }
            }
            return Collections.unmodifiableList(fileList);
        } catch (UnsupportedFlavorException e) {
            log.debug("从剪贴板读取文件失败 - 不支持的数据格式", e);
        } catch (IOException e) {
            log.error("从剪贴板读取文件失败 - IO异常", e);
        } catch (Exception e) {
            log.error("从剪贴板读取文件失败 - 未知异常", e);
        }
        return Collections.emptyList();
    }

    // ===================== 功能2：文本/文件路径剪贴板操作（新增核心功能） =====================

    /**
     * 复制文本到系统剪贴板
     *
     * @param text 要复制的文本（null视为清空剪贴板）
     * @return 是否复制成功
     */
    public static boolean copyTextToClipboard(String text) {
        try {
            Transferable transferable = text == null ? EMPTY_TRANSFERABLE : new StringSelection(text);
            getSystemClipboard().setContents(transferable, null);
            return true;
        } catch (Exception e) {
            log.error("复制文本到剪贴板失败 - 文本：{}", text, e);
            return false;
        }
    }

    /**
     * 复制文件路径到剪贴板（支持单个文件/目录）
     *
     * @param file       目标文件/目录
     * @param isFullPath 是否复制完整路径（true=完整路径，false=仅文件名）
     * @return 是否复制成功
     */
    public static boolean copyFilePathToClipboard(File file, boolean isFullPath) {
        if (file == null || !file.exists()) {
            log.error("复制文件路径失败 - 文件/目录不存在或为null，路径={}",
                    file == null ? "null" : file.getAbsolutePath());
            return false;
        }

        String path = isFullPath ? file.getAbsolutePath() : file.getName();
        return copyTextToClipboard(path);
    }

    /**
     * 从剪贴板读取文本内容
     *
     * @return 剪贴板中的文本（null=无文本/读取失败）
     */
    public static String getTextFromClipboard() {
        try {
            Transferable transferable = getSystemClipboard().getContents(null);
            if (transferable == null) {
                log.debug("从剪贴板读取文本失败 - 剪贴板为空");
                return null;
            }

            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object data = transferable.getTransferData(DataFlavor.stringFlavor);
                return data.toString().trim();
            } else {
                log.debug("从剪贴板读取文本失败 - 剪贴板内容非文本类型");
                return null;
            }
        } catch (UnsupportedFlavorException e) {
            log.debug("从剪贴板读取文本失败 - 不支持的文本格式", e);
        } catch (IOException e) {
            log.error("从剪贴板读取文本失败 - IO异常", e);
        } catch (Exception e) {
            log.error("从剪贴板读取文本失败 - 未知异常", e);
        }
        return null;
    }

    /**
     * 从剪贴板读取文件路径（仅读取第一个文件的路径）
     *
     * @param isFullPath 是否返回完整路径（true=完整路径，false=仅文件名）
     * @return 文件路径（null=剪贴板无文件/读取失败）
     */
    public static String getFilePathFromClipboard(boolean isFullPath) {
        List<File> fileList = getFilesFromClipboard();
        if (fileList.isEmpty()) {
            return null;
        }

        File firstFile = fileList.getFirst();
        return isFullPath ? firstFile.getAbsolutePath() : firstFile.getName();
    }

    // ===================== 功能3：剪贴板辅助操作（新增） =====================

    /**
     * 清空系统剪贴板
     *
     * @return 是否清空成功
     */
    public static boolean clearClipboard() {
        return copyTextToClipboard(null);
    }

    /**
     * 检查剪贴板是否包含文件/目录
     *
     * @return true=包含文件，false=不包含/读取失败
     */
    public static boolean isClipboardHasFiles() {
        try {
            Transferable transferable = getSystemClipboard().getContents(null);
            return transferable != null && transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        } catch (Exception e) {
            log.error("检查剪贴板文件类型失败", e);
            return false;
        }
    }

    /**
     * 检查剪贴板是否包含文本
     *
     * @return true=包含文本，false=不包含/读取失败
     */
    public static boolean isClipboardHasText() {
        try {
            Transferable transferable = getSystemClipboard().getContents(null);
            return transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor);
        } catch (Exception e) {
            log.error("检查剪贴板文本类型失败", e);
            return false;
        }
    }

    // ===================== 内部类：文件Transferable实现（增强） =====================

    /**
     * 自定义Transferable实现：适配文件列表的剪贴板传输
     * 支持Java标准文件列表格式，跨平台兼容
     */
    private record FileTransferable(List<File> fileList) implements Transferable {
        // 支持的DataFlavor（优先javaFileListFlavor）
        private static final DataFlavor[] SUPPORTED_FLAVORS = {DataFlavor.javaFileListFlavor};

        private FileTransferable(List<File> fileList) {
            // 防御性拷贝+不可变处理
            this.fileList = List.copyOf(fileList);
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return SUPPORTED_FLAVORS.clone(); // 返回拷贝，避免外部修改
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            if (flavor == null) {
                return false;
            }
            for (DataFlavor supported : SUPPORTED_FLAVORS) {
                if (supported.equals(flavor)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return new ArrayList<>(fileList); // 返回拷贝，避免外部修改
        }
    }
}