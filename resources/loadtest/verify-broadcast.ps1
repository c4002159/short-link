# 验证本地缓存失效广播：需要同时运行两个实例（8003 和 8004）
# 用法：powershell -File resources/loadtest/verify-broadcast.ps1
param(
    [string]$UriSuffix = "HnCzd",
    [string]$Gid = "tSUBMP",
    [string]$HostHeader = "nurl.ink:8003",
    [string]$NodeA = "http://127.0.0.1:8003",
    [string]$NodeB = "http://127.0.0.1:8004",
    [string]$OriginalUrl = "https://www.bilibili.com",
    [string]$ChangedUrl = "https://www.example.com"
)

$fullShortUrl = "$HostHeader/$UriSuffix"

function Get-RedirectTarget([string]$node) {
    $out = & curl.exe -s -o NUL -D - -H "Host: $HostHeader" "$node/$UriSuffix" --max-time 5
    $line = $out | Where-Object { $_ -match '^Location:' } | Select-Object -First 1
    if ($line) { return ($line -replace '^Location:\s*', '').Trim() } else { return "(none)" }
}

function Update-Link([string]$url) {
    $body = @{ originUrl = $url; fullShortUrl = $fullShortUrl; originGid = $Gid; gid = $Gid; validDateType = 0 } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "$NodeA/api/short-link/v1/update" -Method Post -ContentType "application/json" -Body $body
    if (-not $resp.success) { throw "update failed: code=$($resp.code)" }
}

function Check([string]$name, [bool]$ok) {
    if ($ok) { Write-Host "[PASS] $name" -ForegroundColor Green } else { Write-Host "[FAIL] $name" -ForegroundColor Red; $script:failed = $true }
}

$script:failed = $false

Write-Host "== 1. 两个节点都访问一次，写入各自的本地缓存"
Update-Link $OriginalUrl
Start-Sleep -Milliseconds 500
$a1 = Get-RedirectTarget $NodeA; $b1 = Get-RedirectTarget $NodeB
Write-Host "A: $a1`nB: $b1"
Check "A 初始指向原链接" ($a1 -eq $OriginalUrl)
Check "B 初始指向原链接" ($b1 -eq $OriginalUrl)

Write-Host "`n== 2. 在 A 上修改链接，立刻检查 A 和 B"
Update-Link $ChangedUrl
$a2 = Get-RedirectTarget $NodeA
$sw = [Diagnostics.Stopwatch]::StartNew()
$b2 = Get-RedirectTarget $NodeB
while ($b2 -ne $ChangedUrl -and $sw.ElapsedMilliseconds -lt 5000) {
    Start-Sleep -Milliseconds 50
    $b2 = Get-RedirectTarget $NodeB
}
Write-Host "A: $a2`nB: $b2 （B 生效耗时约 $($sw.ElapsedMilliseconds) ms）"
Check "A 立即看到新链接（本节点失效）" ($a2 -eq $ChangedUrl)
Check "B 在 5 秒内看到新链接（广播失效）" ($b2 -eq $ChangedUrl)

Write-Host "`n== 3. 恢复原链接"
Update-Link $OriginalUrl
Start-Sleep -Milliseconds 300
Check "A 已恢复" ((Get-RedirectTarget $NodeA) -eq $OriginalUrl)
Check "B 已恢复" ((Get-RedirectTarget $NodeB) -eq $OriginalUrl)

if ($script:failed) { Write-Host "`n结果：存在失败项" -ForegroundColor Red; exit 1 } else { Write-Host "`n结果：全部通过" -ForegroundColor Green }
