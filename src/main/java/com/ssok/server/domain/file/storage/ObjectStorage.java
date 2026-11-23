package com.ssok.server.domain.file.storage;

/** 비공개 파일 저장소. 키·저장 경로는 외부 API 응답에 노출하지 않는다. */
public interface ObjectStorage {
    String storageId();
    void put(String key, byte[] content);
    byte[] read(String key, int maxBytes);
    void delete(String key);
}
