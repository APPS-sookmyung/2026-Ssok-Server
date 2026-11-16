package com.ssok.server.common.exception;

/** 이미 스페이스에 가입한 사용자를 다시 초대할 때 발생합니다. */
public class DuplicateSpaceMemberException extends RuntimeException {

    public DuplicateSpaceMemberException(String message) {
        super(message);
    }
}
