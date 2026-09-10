param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$casePath = Join-Path $root "evaluation\test-cases.json"
$resultPath = Join-Path $PSScriptRoot "trag-066-final-check-results.json"

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

$case = @(Get-Content -LiteralPath $casePath -Raw -Encoding UTF8 | ConvertFrom-Json) |
    Where-Object caseId -eq "TRAG-066"
if (-not $case) {
    throw "TRAG-066 not found"
}

$runs = [System.Collections.ArrayList]::new()
if (Test-Path -LiteralPath $resultPath) {
    foreach ($existing in @(Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json)) {
        $null = $runs.Add($existing)
    }
}

for ($runNumber = $runs.Count + 1; $runNumber -le 3; $runNumber++) {
    $started = Get-Date
    $encoded = [Uri]::EscapeDataString($case.question)
    $searchResponse = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/test/search?query=$encoded&topK=10" -TimeoutSec 90
    $top10 = @(ConvertFrom-Json $searchResponse.Content)

    $body = @{ question = $case.question } | ConvertTo-Json -Compress
    $askResponse = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$BaseUrl/api/rag/ask" `
        -ContentType "application/json; charset=utf-8" -Body $body -TimeoutSec 180
    $rawResponse = ConvertFrom-Json $askResponse.Content
    $sources = @($rawResponse.sources)

    $evidenceIdentity = @()
    for ($index = 0; $index -lt $top10.Count; $index++) {
        $item = $top10[$index]
        $evidenceIdentity += [ordered]@{
            rank = $index + 1
            documentId = $item.documentId
            chunkId = $item.chunkId
            score = $item.score
            contentSha256 = Get-Sha256 ([string]$item.content)
        }
    }
    $evidenceHash = Get-Sha256 (($evidenceIdentity | ConvertTo-Json -Depth 5 -Compress))

    $record = [ordered]@{
        run = $runNumber
        executedAt = (Get-Date).ToString("o")
        caseId = $case.caseId
        question = $case.question
        expectedFacts = @($case.expectedFacts)
        diagnosticTop3 = @($top10 | Select-Object -First 3)
        diagnosticTop10 = $top10
        evidenceIdentity = $evidenceIdentity
        evidenceHash = $evidenceHash
        answer = [string]$rawResponse.answer
        sources = $sources
        citesChunk87 = [bool](@($sources | Where-Object { $_.documentId -eq 13 -and $_.chunkId -eq 87 }).Count)
        citesChunk88 = [bool](@($sources | Where-Object { $_.documentId -eq 13 -and $_.chunkId -eq 88 }).Count)
        semanticClassification = "PENDING_SEMANTIC_REVIEW"
        semanticReason = $null
        searchHttpStatus = [int]$searchResponse.StatusCode
        askHttpStatus = [int]$askResponse.StatusCode
        latencyMs = [int]((Get-Date) - $started).TotalMilliseconds
    }
    $null = $runs.Add([pscustomobject]$record)
    Write-AtomicJson $runs $resultPath
    Write-Host ("TRAG-066 run {0}/3 complete" -f $runNumber)
    if ($runNumber -lt 3) {
        Start-Sleep -Milliseconds 1000
    }
}

Write-AtomicJson $runs $resultPath
