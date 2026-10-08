package com.yu.transferrag.repository;

import com.yu.transferrag.entity.*;

public interface DocumentReadinessView {
    Long getDocumentId();
    DocumentRole getDocumentRole();
    Long getTaskId();
    ImportTaskKind getTaskKind();
    ImportTaskStatus getStatus();
}
