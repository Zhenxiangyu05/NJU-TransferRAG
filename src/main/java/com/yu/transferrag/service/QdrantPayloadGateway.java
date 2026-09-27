package com.yu.transferrag.service;

import java.util.List;
import java.util.Map;

/** Internal REST boundary for payload-only Qdrant maintenance. */
public interface QdrantPayloadGateway {

    List<QdrantPoint> scrollAllPoints();

    void setPayload(List<Object> pointIds, Map<String, Object> payload);

    record QdrantPoint(Object id, Map<String, Object> payload) {
    }
}
