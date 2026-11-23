package com.ssok.server.domain.ai.vector;

public enum VectorSyncOperation {
    /** 벡터 삭제 */
    DELETE,
    /** 북마크 이동에 따른 벡터 메타데이터(space_id) 갱신 */
    UPDATE_SPACE
}
