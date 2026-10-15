param([switch]$Execute,[switch]$ResumeAfterFixtureFix)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
if(-not $Execute){throw 'No paid calls made. Requires explicitly approved -Execute; maximum six question attempts.'}
if((Test-Path (Join-Path $taskRoot 'target/conversation-live-20261015.json')) -and -not $ResumeAfterFixtureFix){throw 'Attempt ledger already exists. No automatic rerun is permitted.'}
$taskReadCode=@'
import pathlib,subprocess,json
try:
 pid=subprocess.check_output(['systemctl','show','nju-transfer-rag.service','-p','MainPID','--value'],text=True).strip()
 env=dict(x.split('=',1) for x in pathlib.Path('/proc/'+pid+'/environ').read_bytes().decode().split('\0') if '=' in x)
 selected={k:v for k,v in env.items() if k in ['AI_BASE_URL','AI_API_KEY','AI_EMBEDDING_MODEL','AI_CHAT_PROVIDER','DEEPSEEK_API_KEY','DEEPSEEK_CHAT_MODEL','APP_AI_CHAT_DEEPSEEK_BASE_URL','SPRING_AI_OPENAI_BASE_URL','SPRING_AI_OPENAI_API_KEY','SPRING_AI_OPENAI_EMBEDDING_BASE_URL','SPRING_AI_OPENAI_EMBEDDING_API_KEY','SPRING_AI_OPENAI_EMBEDDING_MODEL']}
 assert selected['AI_CHAT_PROVIDER']=='deepseek-official' and selected['AI_EMBEDDING_MODEL']=='bge-m3'
 print(json.dumps(selected))
except Exception:
 raise SystemExit(1)
'@
try {
    $taskRemote="sudo -n python3 -c '"+$taskReadCode.Replace("'","'\''")+"'"
    # Capture only in memory; never pipe this result to console or a file.
    $taskPrivate=ssh -o BatchMode=yes -o ConnectTimeout=10 nju-rag $taskRemote
    if($LASTEXITCODE){throw 'Could not safely acquire existing isolated provider configuration.'}
    $taskPrivate=$taskPrivate -join "`n"
    $taskClasspath=(Get-Content (Join-Path $taskRoot 'target/live-classpath.txt') -Raw).Trim()
    $taskProcess=[Diagnostics.ProcessStartInfo]::new();$taskProcess.FileName='C:\Users\yu205\.jdks\ms-21.0.11\bin\java.exe'
    $taskProcess.WorkingDirectory=$taskRoot
    $taskProcess.ArgumentList.Add('-cp');$taskProcess.ArgumentList.Add((Join-Path $taskRoot 'target/live-classes')+';'+(Join-Path $taskRoot 'target/classes')+';'+$taskClasspath)
    $taskProcess.ArgumentList.Add('com.yu.transferrag.conversation.ConversationLiveAcceptance')
    if($ResumeAfterFixtureFix){$taskProcess.ArgumentList.Add('--resume-after-fixture-fix')}
    $taskProcess.UseShellExecute=$false
    foreach($taskName in @($taskProcess.Environment.Keys)){if($taskName -match '^(AI_|DEEPSEEK_|SPRING_AI_|DB_PASSWORD$|NJU_REDIS_PASSWORD$|RAG_RATE_LIMIT_HMAC_SECRET$|APP_AI_|APP_CACHE_|APP_RAG_|APP_IMPORT_|OPENAI_)'){$taskProcess.Environment.Remove($taskName)|Out-Null}}
    $taskProcess.RedirectStandardInput=$true;$taskProcess.RedirectStandardOutput=$true;$taskProcess.RedirectStandardError=$true
    $taskChild=[Diagnostics.Process]::Start($taskProcess);$taskOut=$taskChild.StandardOutput.ReadToEndAsync();$taskErr=$taskChild.StandardError.ReadToEndAsync()
    $taskChild.StandardInput.Write($taskPrivate);$taskChild.StandardInput.Close();$taskPrivate=$null
    $taskChild.WaitForExit()
    # Only known harness messages are permitted out. Do not print arbitrary SDK errors or stacks.
    foreach($taskLine in ($taskOut.Result -split "`r?`n")){if($taskLine -match '^(SAFE_CASE=|SAFE_CACHE=|LIVE_ACCEPTANCE_)'){Write-Output $taskLine}}
    if($taskChild.ExitCode){throw 'Live acceptance stopped; do not repeat or add paid requests.'}
} finally {$taskPrivate=$null;$taskReadCode=$null}
