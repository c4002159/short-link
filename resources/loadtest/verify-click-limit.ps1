# 验证点击上限：串行功能检查（需要至少一个实例）
# 用法：powershell -File resources/loadtest/verify-click-limit.ps1 [-Node http://127.0.0.1:8003]
param(
    [string]$Node = "http://127.0.0.1:8003",
    [string]$Gid = "tSUBMP",
    [string]$HostHeader = "nurl.ink:8003",
    [string]$OriginUrl = "https://www.bilibili.com"
)

$script:failed = $false
function Check([string]$name, [bool]$ok) {
    if ($ok) { Write-Host "[PASS] $name" -ForegroundColor Green } else { Write-Host "[FAIL] $name" -ForegroundColor Red; $script:failed = $true }
}

function New-Link($limit) {
    $body = @{ originUrl = $OriginUrl; gid = $Gid; createdType = 1; validDateType = 0; describe = "click-limit-test" }
    if ($null -ne $limit) { $body.clickLimit = $limit }
    $resp = Invoke-RestMethod -Uri "$Node/api/short-link/v1/create" -Method Post -ContentType "application/json" -Body ($body | ConvertTo-Json)
    if (-not $resp.success) { throw "create failed: code=$($resp.code)" }
    return ($resp.data.fullShortUrl -replace '^http://', '')   # nurl.ink:8003/xxxx
}

function Update-Link([string]$fullShortUrl, $limit) {
    $body = @{ originUrl = $OriginUrl; fullShortUrl = $fullShortUrl; originGid = $Gid; gid = $Gid; validDateType = 0 }
    if ($null -ne $limit) { $body.clickLimit = $limit }
    $resp = Invoke-RestMethod -Uri "$Node/api/short-link/v1/update" -Method Post -ContentType "application/json" -Body ($body | ConvertTo-Json)
    if (-not $resp.success) { throw "update failed: code=$($resp.code)" }
}

# 返回 "ok"（跳到原链接）、"limited"（跳到 notfound）或其他
function Hit([string]$fullShortUrl) {
    $uri = $fullShortUrl.Substring($fullShortUrl.IndexOf('/') + 1)
    $out = & curl.exe -s -o NUL -D - -H "Host: $HostHeader" "$Node/$uri" --max-time 5
    $loc = (($out | Where-Object { $_ -match '^Location:' } | Select-Object -First 1) -replace '^Location:\s*', '').Trim()
    if ($loc -eq $OriginUrl) { return "ok" } elseif ($loc -like '*notfound*') { return "limited" } else { return "other($loc)" }
}

Write-Host "== 1. 上限为 3 的链接：前 3 次放行，之后拦截"
$limited = New-Link 3
$res = 1..6 | ForEach-Object { Hit $limited }
Write-Host ($res -join " ")
Check "前 3 次跳转到原链接" (($res[0..2] | Where-Object { $_ -eq "ok" }).Count -eq 3)
Check "第 4~6 次被拦截" (($res[3..5] | Where-Object { $_ -eq "limited" }).Count -eq 3)

Write-Host "`n== 2. 不设上限的链接：不受影响"
$unlimited = New-Link $null
$res2 = 1..10 | ForEach-Object { Hit $unlimited }
Check "10 次全部放行" (($res2 | Where-Object { $_ -eq "ok" }).Count -eq 10)

Write-Host "`n== 3. 把已耗尽的链接上限改为 0（取消限制）后恢复访问"
Update-Link $limited 0
Check "取消限制后可以访问" ((Hit $limited) -eq "ok")

Write-Host "`n== 4. 把上限调高后只放行剩余次数（已用次数保留）"
$raise = New-Link 2
1..2 | ForEach-Object { Hit $raise } | Out-Null
Check "上限 2 用完后被拦截" ((Hit $raise) -eq "limited")
Update-Link $raise 4
$res4 = 1..4 | ForEach-Object { Hit $raise }
Write-Host ($res4 -join " ")
Check "上限调到 4：再放行 2 次，之后拦截" (($res4[0..1] | Where-Object { $_ -eq "ok" }).Count -eq 2 -and ($res4[2..3] | Where-Object { $_ -eq "limited" }).Count -eq 2)

Write-Host "`n== 5. 负数上限被拒绝"
$bad = $false
try { $null = New-Link -1 } catch { $bad = $true }
Check "创建时 clickLimit=-1 返回失败" $bad

if ($script:failed) { Write-Host "`n结果：存在失败项" -ForegroundColor Red; exit 1 } else { Write-Host "`n结果：全部通过" -ForegroundColor Green }
