package com.yu.transferrag.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** One safe error contract for disabled Evidence task writes. */
public class ImportTasksDisabledException extends ResponseStatusException {
    public static final String CODE = "IMPORT_TASKS_DISABLED";
    public static final String USER_MESSAGE = "Evidence 异步导入暂未启用，任务写入和重试已暂停；已有任务仍可查询。";

    public ImportTasksDisabledException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, USER_MESSAGE);
    }
}
