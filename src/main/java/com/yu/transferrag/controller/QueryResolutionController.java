package com.yu.transferrag.controller;

import com.yu.transferrag.dto.QueryRewriteResult;
import com.yu.transferrag.service.QueryRewriteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test/query-resolution")
public class QueryResolutionController {

    private final QueryRewriteService queryRewriteService;

    public QueryResolutionController(QueryRewriteService queryRewriteService) {
        this.queryRewriteService = queryRewriteService;
    }

    @GetMapping
    public Map<String, Object> resolve(@RequestParam String query) {
        QueryRewriteResult result = queryRewriteService.rewriteWithContext(query);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("originalQuery", result.originalQuery());
        response.put("normalizedQuery", result.rewrittenQuery());
        response.put("matchedEntities", result.matchedEntities());
        response.put("resolvedEntities", result.resolvedEntities());
        response.put("resolvedDepartments", result.departments());
        response.put("resolvedMajors", result.majors());
        response.put("explicitYear", result.explicitYear());
        response.put("resolvedYear", result.resolvedYear());
        response.put("multiYearQuery", result.multiYearQuery());
        response.put("ambiguousEntities", result.ambiguousEntities());
        response.put("finalDepartmentFilter", describeDepartmentFilter(result));
        return response;
    }

    private String describeDepartmentFilter(QueryRewriteResult result) {
        if (result.departments().isEmpty()) {
            return "<none>";
        }
        return "department IN " + result.departments()
                + " OR (scope=GLOBAL AND sourceType=OFFICIAL AND chunkDepartment IN "
                + result.departments() + ")";
    }

}
