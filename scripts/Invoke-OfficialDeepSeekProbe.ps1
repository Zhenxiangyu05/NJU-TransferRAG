param([ValidateSet('deepseek-v4-pro','deepseek-flash')][string[]]$Models = @('deepseek-v4-pro','deepseek-flash'))
$ErrorActionPreference = 'Stop'
$key = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Process')
if ([string]::IsNullOrWhiteSpace($key)) { $key = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'User') }
if ([string]::IsNullOrWhiteSpace($key)) { throw 'DEEPSEEK_API_KEY unavailable; REQUEST_COUNT=0. Use masked local input, never paste the key in chat.' }
$budget = Join-Path (Split-Path $PSScriptRoot -Parent) 'target/official-probes'
[IO.Directory]::CreateDirectory($budget) | Out-Null
$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$client = [Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(40)
$client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $key)
try {
    foreach ($model in $Models | Select-Object -Unique) {
        # This worktree's approved budget is one attempt per model, even after a failed/empty response.
        $marker = Join-Path $budget ($model + '.attempted')
        $reservation = [IO.File]::Open($marker, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $reservation.Dispose()
        $body = @{ model=$model; messages=@(@{role='user';content='Reply with exactly OK.'});
            stream=$false; max_tokens=256; thinking=@{type='disabled'} } | ConvertTo-Json -Depth 5 -Compress
        $content = [Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, 'application/json')
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $response = $null
        try {
            # One send, no SDK or retry policy; redirects are disabled. Response remains in memory.
            $response = $client.PostAsync('https://api.deepseek.com/chat/completions', $content).GetAwaiter().GetResult()
            $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $watch.Stop()
            $data = $raw | ConvertFrom-Json
            $choice = @($data.choices) | Select-Object -First 1
            $safeCode = [string]$data.error.code
            $safeType = [string]$data.error.type
            if ($safeCode -notmatch '^[A-Za-z0-9_.-]{0,64}$') { $safeCode='REDACTED' }
            if ($safeType -notmatch '^[A-Za-z0-9_.-]{0,64}$') { $safeType='REDACTED' }
            $prompt = [long]$data.usage.prompt_tokens
            $completion = [long]$data.usage.completion_tokens
            $cached = [long]$data.usage.prompt_cache_hit_tokens
            # Official pricing checked 2026-10-14. Peak upper estimate, USD/1M tokens.
            $inputRate = if ($model -eq 'deepseek-flash') { 0.30 } else { 1.32 }
            $cachedRate = if ($model -eq 'deepseek-flash') { 0.006 } else { 0.044 }
            $outputRate = if ($model -eq 'deepseek-flash') { 1.20 } else { 3.96 }
            $summary = [ordered]@{ requested_model=$model; returned_model=[string]$data.model;
                http=[int]$response.StatusCode; latency_ms=$watch.ElapsedMilliseconds; ttft='NOT_MEASURED_NONSTREAMING';
                content_nonempty=(![string]::IsNullOrWhiteSpace([string]$choice.message.content));
                finish_reason=[string]$choice.finish_reason; prompt_tokens=$prompt; completion_tokens=$completion;
                reasoning_tokens=$data.usage.completion_tokens_details.reasoning_tokens;
                error_code=$safeCode; error_type=$safeType;
                estimated_peak_cost_usd=([Math]::Max(0,$prompt-$cached)*$inputRate+$cached*$cachedRate+$completion*$outputRate)/1000000 }
            $summary | ConvertTo-Json -Compress
            # Only the reviewed non-sensitive assessment is persisted; never the completion or reasoning.
            $summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $budget ($model+'.summary.json')) -Encoding utf8
            if ([int]$response.StatusCode -in @(401,403)) { break }
        } catch {
            $watch.Stop()
            [ordered]@{requested_model=$model;http='NO_VALID_COMPLETION';latency_ms=$watch.ElapsedMilliseconds;
                error_type=$_.Exception.GetType().FullName; retry=$false} | ConvertTo-Json -Compress
        } finally {
            if ($response) { $response.Dispose() }
            $content.Dispose(); $raw=$null; $data=$null
        }
    }
} finally { $client.Dispose(); $handler.Dispose(); $key=$null }
