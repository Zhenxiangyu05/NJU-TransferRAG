package com.yu.transferrag.controller;

import com.yu.transferrag.dto.DocumentImportResponse;
import com.yu.transferrag.service.ImportTaskStateService;
import org.springframework.web.bind.annotation.*;

/** Admin-only under the same protected proxy as document import; never expose /api/import-tasks publicly. */
@RestController
@RequestMapping("/api/import-tasks")
public class ImportTaskController {
    private final ImportTaskStateService state;
    public ImportTaskController(ImportTaskStateService state) { this.state = state; }
    @GetMapping("/{id}")
    public DocumentImportResponse get(@PathVariable long id) { return state.view(id, false); }
    @PostMapping("/{id}/retry")
    public DocumentImportResponse retry(@PathVariable long id) {
        state.retry(id);
        return state.view(id, false);
    }
}
