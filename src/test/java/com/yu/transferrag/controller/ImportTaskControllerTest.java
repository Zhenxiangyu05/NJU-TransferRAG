package com.yu.transferrag.controller;
import com.yu.transferrag.dto.DocumentImportResponse;
import com.yu.transferrag.service.ImportTaskStateService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ImportTaskControllerTest {
    ImportTaskStateService state = mock(ImportTaskStateService.class);
    ImportTaskController controller = new ImportTaskController(state);
    @Test void queryReturnsPersistentState() {
        var response = new DocumentImportResponse(); response.setStatus("PROCESSING");
        when(state.view(1,false)).thenReturn(response);
        assertEquals("PROCESSING",controller.get(1).getStatus()); verify(state,never()).retry(anyLong());
    }
    @Test void retryDelegatesToTransactionalStateMachine() {
        var response = new DocumentImportResponse(); response.setStatus("QUEUED");
        when(state.view(1,false)).thenReturn(response);
        assertEquals("QUEUED",controller.retry(1).getStatus());
        var order = inOrder(state); order.verify(state).retry(1); order.verify(state).view(1,false);
    }
}
