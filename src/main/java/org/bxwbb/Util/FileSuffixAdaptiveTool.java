package org.bxwbb.Util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 文件后缀适配工具类
 * 核心能力：将原始文件名适配为目标正则指定的文件后缀（仅处理 .*\\.xxx 格式正则）
 * 适用场景：创建文件时的文件类型选择（如 abc → abc.java、abc.txt → abc.py）
 */
public final class FileSuffixAdaptiveTool {

    private FileSuffixAdaptiveTool() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 适配文件后缀：将原始文件名替换为正则指定的后缀
     * @param originalFileName 原始文件名（如 abc、abc.txt）
     * @param suffixRegex 目标后缀正则（仅支持 .*\\.xxx 格式，如 .*\\.java、.*\\.py）
     * @return 适配后的文件名（如 abc.java、abc.py）
     * @throws IllegalArgumentException 入参为空、正则格式不合法时抛出
     * @throws PatternSyntaxException 正则语法错误时抛出
     */
    public static String adaptFileSuffix(String originalFileName, String suffixRegex) {
        if (originalFileName == null || originalFileName.isEmpty()) {
            throw new IllegalArgumentException("原始文件名不能为空");
        }
        if (suffixRegex == null || suffixRegex.isEmpty()) {
            throw new IllegalArgumentException("目标后缀正则不能为空");
        }

        String targetSuffix = extractTargetSuffix(suffixRegex);
        if (targetSuffix == null) {
            throw new IllegalArgumentException("仅支持 .*\\.xxx 格式的正则（如 .*\\.java），当前正则：" + suffixRegex);
        }

        String nameWithoutSuffix = removeOriginalSuffix(originalFileName);
        return nameWithoutSuffix + targetSuffix;
    }

    /**
     * 从正则中提取目标后缀（如 .*\\.java → .java）
     */
    private static String extractTargetSuffix(String regex) {
        Pattern pattern = Pattern.compile("^\\\\?\\.\\*\\\\?\\.(.+)$");
        Matcher matcher = pattern.matcher(regex);
        if (matcher.matches()) {
            return "." + matcher.group(1);
        }
        return null;
    }

    /**
     * 移除原始文件名的原有后缀（如 abc.txt → abc、abc → abc）
     */
    private static String removeOriginalSuffix(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return (lastDotIndex > 0) ? fileName.substring(0, lastDotIndex) : fileName;
    }
}