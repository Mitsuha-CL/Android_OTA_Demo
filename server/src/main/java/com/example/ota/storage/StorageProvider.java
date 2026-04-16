package com.example.ota.storage;

import java.io.IOException;
import java.io.InputStream;

public interface StorageProvider {

    String store(String versionCode, InputStream inputStream, long fileSize) throws IOException;

    InputStream getInputStream(String versionCode) throws IOException;

    long getFileSize(String versionCode) throws IOException;

    void delete(String versionCode) throws IOException;

    String getDownloadUrl(String versionCode);
}
