package com.example.thesis.service;

import java.nio.file.Path;

/**
 * 通过权限校验后的可下载文件。
 */
public record DownloadFile(Path path, String fileName) {
}
