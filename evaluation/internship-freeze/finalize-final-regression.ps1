$ErrorActionPreference = "Stop"

$resultPath = Join-Path $PSScriptRoot "final-regression-results.json"
$baselinePath = Join-Path (Split-Path -Parent $PSScriptRoot) "evaluation-results.json"
$previousPath = Join-Path (Split-Path -Parent $PSScriptRoot) "full-regression-after-metadata-year\evaluation-results.json"
$temporaryPath = "$resultPath.tmp"

$results = @(Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json)
$baseline = @(Get-Content -LiteralPath $baselinePath -Raw -Encoding UTF8 | ConvertFrom-Json)
$previous = @(Get-Content -LiteralPath $previousPath -Raw -Encoding UTF8 | ConvertFrom-Json)

$partialReasons = @{
    "TRAG-016" = "回答覆盖平均学分绩4.0及以上和无不及格记录，但遗漏‘无违纪行为’。"
    "TRAG-025" = "列出课程及80分门槛，与2024级证据一致，但没有说明只适用于2024级；Top10同时包含2025级不同规则，缺少适用cohort限定。"
    "TRAG-029" = "四门课程及任选一门与2025级证据一致，但省略cohort限定；2024级第四门为‘信息检索’，不能把2025级列表无条件推广。"
    "TRAG-032" = "回答覆盖结课方式、数学和英语安排，但遗漏‘分流只需五门大类课’。"
    "TRAG-043" = "回答说明应修读目标专业要求的数学层次，但遗漏目标专业若大一修读线性代数，建议上学期提前修读。"
    "TRAG-045" = "回答覆盖准入课可选、可按目标院系安排课程和转失败补课较少，但遗漏工科试验班自带一层次微积分。"
    "TRAG-056" = "回答覆盖多数人选择民法和刑法、大一下的重要性及教材笔记准备，但遗漏结合往年题全面备考。"
    "TRAG-070" = "回答覆盖1分钟自我陈述及提问科目，但遗漏老师轮流提问约3到4分钟。"
    "TRAG-076" = "回答覆盖5门中至少修1门、对外开放4门及不限制后续分流方向，但遗漏课程在鼓楼开设。"
}

$wrongSourceReasons = @{
    "TRAG-066" = "核心的数学物理成绩、第一学期成绩单和第二学期期中成绩由doc13/chunk88支持；但回答又把doc13/chunk87中‘26级光材二次拔尖’的光学题、高考及竞赛考量混入大一转专业面试，发生跨选拔场景污染。"
}

$refusalRootCauses = @{
    "TRAG-003" = "Evidence Window"
    "TRAG-005" = "Retrieval"
    "TRAG-006" = "Source Authority"
    "TRAG-007" = "Source Authority"
    "TRAG-008" = "Answerability"
    "TRAG-009" = "Answerability"
    "TRAG-023" = "Answerability / Cohort ambiguity"
    "TRAG-027" = "Answerability / Cohort ambiguity"
    "TRAG-031" = "Evidence Window"
    "TRAG-036" = "Retrieval"
    "TRAG-037" = "Answerability"
    "TRAG-038" = "Entity Resolution"
    "TRAG-042" = "Retrieval"
    "TRAG-046" = "Entity Resolution"
    "TRAG-060" = "Answerability"
    "TRAG-062" = "Evidence Window"
    "TRAG-064" = "Answerability"
    "TRAG-071" = "Answerability / Nondeterministic"
    "TRAG-073" = "Source Authority"
    "TRAG-077" = "Source Authority"
    "TRAG-080" = "Retrieval"
}

$passIds = @(
    "TRAG-001","TRAG-002","TRAG-004","TRAG-010","TRAG-011","TRAG-012","TRAG-013","TRAG-014","TRAG-015",
    "TRAG-017","TRAG-018","TRAG-019","TRAG-020","TRAG-021","TRAG-022","TRAG-024","TRAG-026","TRAG-028",
    "TRAG-030","TRAG-033","TRAG-034","TRAG-035","TRAG-039","TRAG-040","TRAG-041","TRAG-044","TRAG-047",
    "TRAG-048","TRAG-049","TRAG-050","TRAG-051","TRAG-052","TRAG-053","TRAG-054","TRAG-055","TRAG-057",
    "TRAG-058","TRAG-059","TRAG-061","TRAG-063","TRAG-065","TRAG-067","TRAG-068","TRAG-069","TRAG-072",
    "TRAG-074","TRAG-075","TRAG-078","TRAG-079","TRAG-081","TRAG-082"
)

foreach ($result in $results) {
    $caseId = $result.caseId
    if ($passIds -contains $caseId) {
        $classification = "PASS"
        $rootCause = $null
        $failureReason = $null
    } elseif ($partialReasons.ContainsKey($caseId)) {
        $classification = "PARTIAL"
        $rootCause = if ($caseId -in @("TRAG-025", "TRAG-029")) { "Year / Cohort qualification" } else { "Generation completeness" }
        $failureReason = $partialReasons[$caseId]
    } elseif ($wrongSourceReasons.ContainsKey($caseId)) {
        $classification = "WRONG_SOURCE"
        $rootCause = "Source scope / Generation"
        $failureReason = $wrongSourceReasons[$caseId]
    } elseif ($refusalRootCauses.ContainsKey($caseId)) {
        $classification = "REFUSAL_FALSE_NEGATIVE"
        $rootCause = $refusalRootCauses[$caseId]
        $expected = @($result.expectedFacts) -join "、"
        $failureReason = "原始 Ground Truth 明确包含：$expected；正式接口仍返回知识不足拒答。"
    } elseif ($result.runtimeOutcome -in @("SYSTEM_ERROR", "TECHNICAL_ERROR")) {
        $classification = "SYSTEM_ERROR"
        $rootCause = "System Error"
        $failureReason = "HTTP、模型调用或解析过程未完成。"
    } else {
        throw "缺少语义分类：$caseId"
    }

    $baselineCase = $baseline | Where-Object caseId -eq $caseId | Select-Object -First 1
    $previousCase = $previous | Where-Object caseId -eq $caseId | Select-Object -First 1
    $extra = @{
        classification = $classification
        rootCause = $rootCause
        failureReason = $failureReason
        evaluationPassed = ($classification -eq "PASS")
        baselineClassification = $baselineCase.classification
        previousStableClassification = $previousCase.classification
        baselineRegression = ($baselineCase.classification -eq "PASS" -and $classification -ne "PASS")
        previousStableRegression = ($previousCase.classification -eq "PASS" -and $classification -ne "PASS")
        fixedFromBaseline = ($baselineCase.classification -ne "PASS" -and $classification -eq "PASS")
        improvementFromPreviousStable = ($previousCase.classification -ne "PASS" -and $classification -eq "PASS")
    }
    foreach ($entry in $extra.GetEnumerator()) {
        if ($result.PSObject.Properties.Name -contains $entry.Key) {
            $result.($entry.Key) = $entry.Value
        } else {
            $result | Add-Member -NotePropertyName $entry.Key -NotePropertyValue $entry.Value
        }
    }
}

ConvertTo-Json -InputObject $results -Depth 30 | Set-Content -LiteralPath $temporaryPath -Encoding UTF8
$validated = @(Get-Content -LiteralPath $temporaryPath -Raw -Encoding UTF8 | ConvertFrom-Json)
if ($validated.Count -ne 82 -or @($validated | Where-Object { -not $_.executionComplete }).Count -ne 0) {
    throw "最终结果原子写入校验失败"
}
Move-Item -LiteralPath $temporaryPath -Destination $resultPath -Force
Write-Host "最终语义分类已写入：82/82"
