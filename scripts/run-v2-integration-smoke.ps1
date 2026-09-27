<#!
Runs an isolated, manual V2 smoke test against real MySQL 8, Qdrant and the
configured OpenAI-compatible embedding/chat endpoints. It never targets the
production database or collection, and it is intentionally outside Maven's
default test lifecycle.

Required environment variables: AI_BASE_URL, AI_API_KEY, AI_CHAT_MODEL,
AI_EMBEDDING_MODEL. No values are printed by this script.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$requiredVariables = 'AI_BASE_URL', 'AI_API_KEY', 'AI_CHAT_MODEL', 'AI_EMBEDDING_MODEL'
foreach ($name in $requiredVariables) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
    throw "Missing required environment variable: $name"
  }
}

$runId = [guid]::NewGuid().ToString('N').Substring(0, 12)
$mysqlContainer = "nju-rag-v2-mysql-$runId"
$qdrantContainer = "nju-rag-v2-qdrant-$runId"
$mysqlPort = 33306
$qdrantRestPort = 16335
$qdrantGrpcPort = 16336
$serverPort = 18080
$database = "v2_smoke_$runId"
$collection = "v2_smoke_chunks_$runId"
$testPassword = "smoke_$runId"
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "nju-rag-v2-$runId"
$logPath = Join-Path $tempRoot 'application.log'
$appProcess = $null

function Invoke-Docker {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
  & docker @Arguments
  if ($LASTEXITCODE -ne 0) { throw "Docker command failed: docker $Arguments" }
}

function Wait-Http([string]$Url) {
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 -Uri $Url
      if ($response.StatusCode -eq 200) { return }
    } catch { }
    Start-Sleep -Seconds 2
  }
  throw "Application did not become healthy: $Url"
}

try {
  New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
  Push-Location $repoRoot
  try {
    $env:JAVA_HOME = 'C:\Users\yu205\.jdks\ms-21.0.11'
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    .\mvnw.cmd package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Maven package failed' }
  } finally {
    Pop-Location
  }

  Invoke-Docker run --rm -d --name $mysqlContainer `
    -e "MYSQL_ROOT_PASSWORD=$testPassword" -e "MYSQL_DATABASE=$database" `
    -p "127.0.0.1:$mysqlPort`:3306" mysql:8.0 | Out-Null
  Invoke-Docker run --rm -d --name $qdrantContainer `
    -p "127.0.0.1:$qdrantRestPort`:6333" -p "127.0.0.1:$qdrantGrpcPort`:6334" qdrant/qdrant | Out-Null

  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    & docker exec -e "MYSQL_PWD=$testPassword" $mysqlContainer mysqladmin -uroot ping --silent 2>$null
    if ($LASTEXITCODE -eq 0) { break }
    if ($attempt -eq 59) { throw 'Isolated MySQL did not become ready' }
    Start-Sleep -Seconds 2
  }

  $jar = Get-ChildItem (Join-Path $repoRoot 'target') -Filter 'Transfer-RAG-*.jar' |
    Where-Object Name -NotLike '*.original' | Select-Object -First 1
  if (-not $jar) { throw 'Built Spring Boot JAR was not found' }

  $oldEnvironment = @{}
  $overrides = @{
    'DB_PASSWORD' = $testPassword
    'SPRING_DATASOURCE_URL' = "jdbc:mysql://127.0.0.1:$mysqlPort/$database`?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
    'SPRING_DATASOURCE_USERNAME' = 'root'
    'SPRING_JPA_HIBERNATE_DDL_AUTO' = 'update'
    'SPRING_AI_VECTORSTORE_QDRANT_HOST' = '127.0.0.1'
    'SPRING_AI_VECTORSTORE_QDRANT_PORT' = "$qdrantGrpcPort"
    'SPRING_AI_VECTORSTORE_QDRANT_COLLECTION_NAME' = $collection
    'APP_QDRANT_REST_PORT' = "$qdrantRestPort"
    'APP_UPLOAD_DIR' = (Join-Path $tempRoot 'uploads')
    'SERVER_PORT' = "$serverPort"
    'SERVER_ADDRESS' = '127.0.0.1'
    'APP_RAG_CANONICAL_FIRST_ENABLED' = 'true'
  }
  foreach ($entry in $overrides.GetEnumerator()) {
    $oldEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key)
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value)
  }

  $appProcess = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') `
    -ArgumentList @('-jar', $jar.FullName) -PassThru -NoNewWindow `
    -RedirectStandardOutput $logPath -RedirectStandardError (Join-Path $tempRoot 'application-error.log')
  Wait-Http "http://127.0.0.1:$serverPort/api/test/vector-store"

  Get-Content -Raw (Join-Path $repoRoot 'src/main/resources/sql/v2-canonical-evidence-schema.sql') |
    & docker exec -i -e "MYSQL_PWD=$testPassword" $mysqlContainer mysql -uroot $database
  if ($LASTEXITCODE -ne 0) { throw 'V2 migration failed in isolated MySQL' }

  $evidenceA = Join-Path $tempRoot 'evidence-a.txt'
  $evidenceB = Join-Path $tempRoot 'evidence-b.txt'
  Set-Content -NoNewline -Encoding utf8 $evidenceA '软件学院转专业面试时长为120分钟。'
  Set-Content -NoNewline -Encoding utf8 $evidenceB '软件学院转专业笔试合格线为60分，申请材料必须提交成绩单。'
  $baseUrl = "http://127.0.0.1:$serverPort"
  $responseA = curl.exe -fsS -X POST "$baseUrl/api/documents/import" `
    -F "file=@$evidenceA" -F 'title=Smoke Evidence A' -F 'department=软件学院' `
    -F 'year=2026' -F 'sourceType=OFFICIAL' -F 'scope=DEPARTMENT' | ConvertFrom-Json
  $responseB = curl.exe -fsS -X POST "$baseUrl/api/documents/import" `
    -F "file=@$evidenceB" -F 'title=Smoke Evidence B' -F 'department=软件学院' `
    -F 'year=2026' -F 'sourceType=OFFICIAL' -F 'scope=DEPARTMENT' | ConvertFrom-Json

  $canonical = @{
    title = 'Smoke Canonical Card'
    department = '软件学院'
    year = 2026
    scope = 'DEPARTMENT'
    sections = @(@{
      section = '考核要求'
      policyYear = 2026
      cohortYear = 2025
      department = '软件学院'
      major = '软件工程'
      facts = @(
        @{ text = '面试时长为120分钟。'; evidenceRefs = @(@{ sourceDocumentId = $responseA.documentId; page = $null; evidenceText = '原始资料明确写明面试时长为120分钟。' }) },
        @{ text = '笔试合格线为60分。'; evidenceRefs = @(@{ sourceDocumentId = $responseB.documentId; page = $null; evidenceText = '原始资料明确写明笔试合格线为60分。' }) }
      )
    })
  }
  $canonicalResponse = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/documents/canonical" `
    -ContentType 'application/json; charset=utf-8' -Body ($canonical | ConvertTo-Json -Depth 10)
  if ($canonicalResponse.documentRole -ne 'CANONICAL' -or $canonicalResponse.chunkCount -ne 1 -or $canonicalResponse.evidenceRefCount -ne 2) {
    throw 'Canonical import response did not contain expected role/chunk/ref counts'
  }

  $scrollRequest = @{ limit = 10; with_payload = $true; with_vector = $true; filter = @{ must = @(@{ key = 'documentId'; match = @{ value = "$($canonicalResponse.documentId)" } }) } } | ConvertTo-Json -Depth 10
  $qdrantResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$qdrantRestPort/collections/$collection/points/scroll" `
    -ContentType 'application/json' -Body $scrollRequest
  $canonicalPoint = @($qdrantResponse.result.points)[0]
  if (-not $canonicalPoint -or $canonicalPoint.payload.documentRole -ne 'CANONICAL' `
    -or $canonicalPoint.payload.section -ne '考核要求' -or @($canonicalPoint.vector).Count -ne 1024) {
    throw 'Canonical Qdrant payload or vector dimension verification failed'
  }

  $canonicalAsk = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/rag/ask" -ContentType 'application/json; charset=utf-8' `
    -Body (@{ question = '软件学院转专业的面试时长和笔试合格线分别是多少？' } | ConvertTo-Json)
  $citationIds = @($canonicalAsk.sources | ForEach-Object documentId)
  if ($canonicalAsk.answer -eq '根据当前知识库资料无法确定。' -or $citationIds -contains $canonicalResponse.documentId `
    -or -not ($citationIds -contains $responseA.documentId) -or -not ($citationIds -contains $responseB.documentId)) {
    throw 'Canonical retrieval did not return evidence-backed citations'
  }

  $fallbackAsk = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/rag/ask" -ContentType 'application/json; charset=utf-8' `
    -Body (@{ question = '软件学院转专业申请材料需要提交什么？' } | ConvertTo-Json)
  if ($fallbackAsk.answer -eq '根据当前知识库资料无法确定。' -or -not (@($fallbackAsk.sources | ForEach-Object documentId) -contains $responseB.documentId)) {
    throw 'Evidence fallback did not return the uncovered evidence document'
  }

  $unknownAsk = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/rag/ask" -ContentType 'application/json; charset=utf-8' `
    -Body (@{ question = '食堂今天供应什么菜？' } | ConvertTo-Json)
  if ($unknownAsk.answer -ne '根据当前知识库资料无法确定。') {
    throw 'Fail-closed verification failed'
  }

  Write-Output "Integration smoke passed: canonicalDocumentId=$($canonicalResponse.documentId), canonicalChunkCount=$($canonicalResponse.chunkCount), evidenceRefCount=$($canonicalResponse.evidenceRefCount), vectorDimension=1024"
} finally {
  if ($appProcess -and -not $appProcess.HasExited) { Stop-Process -Id $appProcess.Id -Force }
  foreach ($name in 'DB_PASSWORD','SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_JPA_HIBERNATE_DDL_AUTO','SPRING_AI_VECTORSTORE_QDRANT_HOST','SPRING_AI_VECTORSTORE_QDRANT_PORT','SPRING_AI_VECTORSTORE_QDRANT_COLLECTION_NAME','APP_QDRANT_REST_PORT','APP_UPLOAD_DIR','SERVER_PORT','SERVER_ADDRESS','APP_RAG_CANONICAL_FIRST_ENABLED') {
    if ($oldEnvironment -and $oldEnvironment.ContainsKey($name)) {
      [Environment]::SetEnvironmentVariable($name, $oldEnvironment[$name])
    }
  }
  & docker stop $mysqlContainer 2>$null | Out-Null
  & docker stop $qdrantContainer 2>$null | Out-Null
  if (Test-Path $tempRoot) { Remove-Item -Recurse -Force $tempRoot }
}
