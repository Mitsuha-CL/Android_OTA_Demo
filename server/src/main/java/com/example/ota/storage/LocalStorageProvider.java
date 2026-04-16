package com.example.ota.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@ConditionalOnProperty(name = "ota.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageProvider implements StorageProvider {

    @Value("${ota.storage.local-path:./data/apk}")
    private String basePath;

    @Override
    public String store(String versionCode, InputStream inputStream, long fileSize) throws IOException {
        File dir = new File(basePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, "app_v" + versionCode + ".apk");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            inputStream.transferTo(fos);
        }
        return file.getAbsolutePath();
    }

    @Override
    public InputStream getInputStream(String versionCode) throws IOException {
        File file = getFile(versionCode);
        return new FileInputStream(file);
    }

    @Override
    public long getFileSize(String versionCode) throws IOException {
        return getFile(versionCode).length();
    }

    @Override
    public void delete(String versionCode) throws IOException {
        getFile(versionCode).delete();
    }

    @Override
    public String getDownloadUrl(String versionCode) {
        return "/api/v1/apk/download/" + versionCode;
    }

    private File getFile(String versionCode) throws IOException {
        File file = new File(basePath, "app_v" + versionCode + ".apk");
        if (!file.exists()) {
            throw new IOException("APK not found: " + file.getAbsolutePath());
        }
        return file;
    }
}
