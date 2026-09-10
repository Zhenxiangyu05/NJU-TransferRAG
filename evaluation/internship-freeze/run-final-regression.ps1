param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$DelayMilliseconds = 800
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$casePath = Join-Path $root "evaluation\test-cases.json"
$resultPath = Join-Path $PSScriptRoot "final-regression-results.json"

function Write-AtomicJson([System.Collections.IEnumerable]$Value, [string]$Path) {
    $temporaryPath = "$Path.tmp"
    ConvertTo-Json -InputObject $Value -Depth 30 | Set-Content -LiteralPath $temporaryPath -Encoding UTF8
    $null = Get-Content -LiteralPath $temporaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Move-Item -LiteralPath $temporaryPath -Destination $Path -Force
}

function Get-Sha256([string]$Text) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $hash = [System.Security.Cryptography.SHA256]::HashData($bytes)
    return [Convert]::ToHexString($hash).ToLowerInvariant()
}

$cases = @(Get-Content -LiteralPath $casePath -Raw -Encoding UTF8 | ConvertFrom-Json)
$results = [System.Collections.ArrayList]::new()
if (Test-Path -LiteralPath $resultPath) {
    $existingResults = @(Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json)
    foreach ($existingResult in $existingResults) {
        $null = $results.Add($existingResult)
    }
}

$completedIds = @($results | Where-Object executionComplete | ForEach-Object caseId)
foreach ($case in $cases) {
    if ($completedIds -contains $case.caseId) {
        continue
    }

    $started = Get-Date
    $httpStatus = $null
    $searchHttpStatus = $null
    $rawResponse = $null
    $search = @()
    $systemError = $null

    try {
        $encoded = [Uri]::EscapeDataString($case.question)
        $searchResponse = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/test/search?query=$encoded&topK=10" -TimeoutSec 90
        $searchHttpStatus = [int]$searchResponse.StatusCode
        $search = @(ConvertFrom-Json $searchResponse.Content)

        $body = @{ question = $case.question } | ConvertTo-Json -Compress
        $askResponse = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$BaseUrl/api/rag/ask" -ContentType "application/json; charset=utf-8" -Body $body -TimeoutSec 180
        $httpStatus = [int]$askResponse.StatusCode
        $rawResponse = ConvertFrom-Json $askResponse.Content
    } catch {
        $systemError = $_.Exception.Message
        if ($_.Exception.Response) {
            $httpStatus = [int]$_.Exception.Response.StatusCode
        }
    }

    $answer = if ($rawResponse) { [string]$rawResponse.answer } else { $null }
    $runtimeOutcome = if ($systemError) {
        "SYSTEM_ERROR"
    } elseif ($answer -eq "当前问答服务暂时不可用，请稍后重试。") {
        "TECHNICAL_ERROR"
    } elseif ($answer -eq "根据当前知识库资料无法确定。") {
        "REFUSAL"
    } elseif ($answer -eq "当前资料包含多套适用于不同年级或 cohort 的规则，请补充你的年级或入学年份。") {
        "AMBIGUOUS"
    } else {
        "ANSWER"
    }

    $classification = switch ($runtimeOutcome) {
        "SYSTEM_ERROR" { "SYSTEM_ERROR" }
        "TECHNICAL_ERROR" { "SYSTEM_ERROR" }
        "REFUSAL" {
            if ($case.expectedBehavior -eq "REFUSAL") { "PASS" } else { "REFUSAL_FALSE_NEGATIVE" }
        }
        "AMBIGUOUS" {
            if ($case.expectedBehavior -eq "CLARIFY_OR_MULTI_COHORT") { "PASS" } else { "AMBIGUOUS" }
        }
        default { "PENDING_SEMANTIC_REVIEW" }
    }

    $evidenceIdentity = @()
    for ($index = 0; $index -lt $search.Count; $index++) {
        $item = $search[$index]
        $evidenceIdentity += [ordered]@{
            rank = $index + 1
            documentId = $item.documentId
            chunkId = $item.chunkId
            score = $item.score
            contentSha256 = Get-Sha256 ([string]$item.content)
        }
    }
    $combinedHash = Get-Sha256 (($evidenceIdentity | ConvertTo-Json -Depth 5 -Compress))

    $record = [ordered]@{}
    foreach ($property in $case.PSObject.Properties) {
        $record[$property.Name] = $property.Value
    }
    $record.executionComplete = $true
    $record.executedAt = (Get-Date).ToString("o")
    $record.httpStatus = $httpStatus
    $record.searchHttpStatus = $searchHttpStatus
    $record.answer = $answer
    $record.sources = @()
    if ($rawResponse) { $record.sources = @($rawResponse.sources) }
    $record.diagnosticSearch = $search
    $record.runtimeOutcome = $runtimeOutcome
    $record.classification = $classification
    $record.failureReason = if ($systemError) { "调用失败：$systemError" } else { $null }
    $record.rawResponse = $rawResponse
    $record.evidenceIdentity = $evidenceIdentity
    $record.evidenceHash = $combinedHash
    $record.latencyMs = [int]((Get-Date) - $started).TotalMilliseconds
    $record.evaluationPassed = if ($classification -eq "PASS") { $true } else { $null }

    $null = $results.Add([pscustomobject]$record)
    Write-AtomicJson $results $resultPath
    Write-Host ("已完成 {0}/{1}：{2}，{3}" -f $results.Count, $cases.Count, $case.caseId, $runtimeOutcome)
    Start-Sleep -Milliseconds $DelayMilliseconds
}

Write-AtomicJson $results $resultPath
Write-Host ("Regression 执行完成：{0}/{1}" -f $results.Count, $cases.Count)
