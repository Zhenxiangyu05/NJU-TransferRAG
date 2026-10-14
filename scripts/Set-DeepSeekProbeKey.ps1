param([switch]$Remove)
$ErrorActionPreference = 'Stop'
if ($Remove) {
    [Environment]::SetEnvironmentVariable('DEEPSEEK_API_KEY', $null, 'User')
    Write-Host 'DEEPSEEK_API_KEY removed from the local user environment.'
    exit 0
}
# Interactive masked input: never place a real key in this script, a command argument or chat.
# User environment is not a secrets vault; remove the value after the two approved probes.
$secret = Read-Host 'DeepSeek official API Key (masked)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    [Environment]::SetEnvironmentVariable('DEEPSEEK_API_KEY',
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer), 'User')
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $secret.Dispose()
}
Write-Host 'DEEPSEEK_API_KEY is available for the two approved local probes. No key was printed.'
