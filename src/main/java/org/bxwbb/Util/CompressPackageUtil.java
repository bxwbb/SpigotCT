package org.bxwbb.Util;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class CompressPackageUtil {

    private static final Logger log = LoggerFactory.getLogger(CompressPackageUtil.class);

    public static void UnCompression(Path filePath, Path targetPath) {
        System.out.println(filePath);
        try (InputStream fileStream = new BufferedInputStream(new FileInputStream(filePath.toFile()));
             CompressorInputStream compressedStream = new CompressorStreamFactory().createCompressorInputStream(fileStream)) {
            int data;
            while ((data = compressedStream.read()) != -1) {
                System.out.print((char) data);
            }
        } catch (IOException e) {
            log.error("解压文件时发生错误 - {} >> {} -> ", filePath, targetPath, e);
        } catch (CompressorException e) {
            log.error("不支持的解压格式 - {}", filePath);
        }
    }

    public static void main(String[] args) {
        UnCompression(Path.of("F:", "Tiny.Pasture(Danjipai.com).7z"), Path.of(""));
    }

}
