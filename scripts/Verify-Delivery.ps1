param(
    [string]$ReferenceApk = (Join-Path $PSScriptRoot '../../InkHide_3.0.5.apk'),
    [string]$CandidateApk = (Join-Path $PSScriptRoot '../app/build/outputs/apk/debug/app-debug.apk'),
    [string]$DeliveryDirectory = (Join-Path $PSScriptRoot '../../inkveil-delivery'),
    [string]$BuildToolsDirectory = 'D:/SoftData/scoop/apps/android-clt/current/build-tools/36.0.0'
)

$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false)
$reference = (Resolve-Path -LiteralPath $ReferenceApk).Path
$candidate = (Resolve-Path -LiteralPath $CandidateApk).Path
$delivery = (Resolve-Path -LiteralPath $DeliveryDirectory).Path
$python = (Get-Command python.exe -CommandType Application | Select-Object -First 1).Source
$expected = '1571EE9D7A589DF17569DBD61D867C04352144CD3E272A88DF23A42BD80E67AC'
if ((Get-FileHash -LiteralPath $reference -Algorithm SHA256).Hash -ne $expected) {
    throw '原APK哈希变化，停止交付验证。'
}
$baseline = Join-Path $delivery 'BASELINE.apk'
if ((Get-FileHash -LiteralPath $baseline -Algorithm SHA256).Hash -ne $expected) {
    throw '已绑定基线哈希变化，停止交付验证。'
}
$runId = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runDirectory = Join-Path $delivery ('runs/' + $runId)
$null = New-Item -ItemType Directory -Path $runDirectory
$records = [Collections.Generic.List[object]]::new()

function Invoke-Recorded {
    param(
        [string]$Label,
        [string]$Executable,
        [string[]]$Parameters,
        [string]$WorkingDirectory = $delivery,
        [int[]]$AllowedExitCodes = @(0)
    )
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.WorkingDirectory = $WorkingDirectory
    foreach ($parameter in $Parameters) {
        $startInfo.ArgumentList.Add($parameter)
    }
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = $utf8
    $startInfo.StandardErrorEncoding = $utf8
    $startInfo.Environment['INKVEIL_PYTHON'] = $python.Replace('\', '/')
    $process = [Diagnostics.Process]::Start($startInfo)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $record = [ordered]@{
        label = $Label
        executable = $Executable
        parameters = $Parameters
        workingDirectory = $WorkingDirectory
        stdout = $stdout
        stderr = $stderr
        exitCode = $process.ExitCode
    }
    $records.Add($record)
    [IO.File]::WriteAllText((Join-Path $runDirectory ($Label + '.stdout.txt')), $stdout, $utf8)
    [IO.File]::WriteAllText((Join-Path $runDirectory ($Label + '.stderr.txt')), $stderr, $utf8)
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $runDirectory ($Label + '.json')) -Encoding utf8
    if ($process.ExitCode -notin $AllowedExitCodes) {
        throw ($Label + ' 失败，退出码：' + $process.ExitCode + '；stderr：' + $stderr)
    }
    return $record
}

$modified = Join-Path $delivery 'MODIFIED_FILE.apk'
$patch = Join-Path $delivery 'DIFF_FILE.patch'
$verification = Join-Path $delivery 'VERIFICATION.txt'
$rollback = Join-Path $delivery 'ROLLBACK.sh'
foreach ($path in @($delivery, $baseline, $modified, $patch, $verification, $rollback)) {
    if (Test-Path -LiteralPath $path) {
        $item = Get-Item -LiteralPath $path
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw ('交付角色路径不能为重解析点：' + $path)
        }
    }
}
$null = Get-Item -LiteralPath $rollback
Copy-Item -LiteralPath $candidate -Destination $modified -Force
$aapt = Join-Path $BuildToolsDirectory 'aapt2.exe'
$baselineRecord = Invoke-Recorded -Label 'BASELINE' -Executable $aapt -Parameters @('dump', 'badging', $baseline)
$modifiedRecord = Invoke-Recorded -Label 'MODIFIED' -Executable $aapt -Parameters @('dump', 'badging', $modified)
$null = Invoke-Recorded -Label 'SIGNATURE' -Executable (Join-Path $env:JAVA_HOME 'bin/java.exe') -Parameters @('-jar', (Join-Path $BuildToolsDirectory 'lib/apksigner.jar'), 'verify', '--verbose', '--print-certs', $modified)
$null = Invoke-Recorded -Label 'ALIGNMENT' -Executable (Join-Path $BuildToolsDirectory 'zipalign.exe') -Parameters @('-c', '-p', '4', $modified)

$sourceDirectory = Join-Path $runDirectory 'baseline'
$destinationDirectory = Join-Path $runDirectory 'modified'
$reconstructDirectory = Join-Path $runDirectory 'reconstructed'
foreach ($directory in @($sourceDirectory, $destinationDirectory, $reconstructDirectory)) {
    $null = New-Item -ItemType Directory -Path $directory
}
Copy-Item -LiteralPath $baseline -Destination (Join-Path $sourceDirectory 'TARGET.apk')
Copy-Item -LiteralPath $modified -Destination (Join-Path $destinationDirectory 'TARGET.apk')
$git = (Get-Command git).Source
$diffRecord = Invoke-Recorded -Label 'BINARY_DIFF' -Executable $git -Parameters @('diff', '--no-index', '--binary', '--', 'baseline/TARGET.apk', 'modified/TARGET.apk') -WorkingDirectory $runDirectory -AllowedExitCodes @(1)
$patchContent = $diffRecord.stdout.Replace('a/baseline/TARGET.apk', 'a/TARGET.apk').Replace('b/modified/TARGET.apk', 'b/TARGET.apk')
[IO.File]::WriteAllText($patch, $patchContent, $utf8)
$reconstructed = Join-Path $reconstructDirectory 'TARGET.apk'
Copy-Item -LiteralPath $baseline -Destination $reconstructed
$null = Invoke-Recorded -Label 'APPLY_DIFF' -Executable $git -Parameters @('apply', '--binary', $patch) -WorkingDirectory $reconstructDirectory
$candidateHash = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash
if ((Get-FileHash -LiteralPath $reconstructed -Algorithm SHA256).Hash -ne $candidateHash) {
    throw '二进制补丁未精确重建新APK。'
}

$restored = Join-Path $runDirectory 'ROLLBACK_COPY.apk'
Copy-Item -LiteralPath $modified -Destination $restored
$bash = (Get-Command bash).Source
$null = Invoke-Recorded -Label 'ROLLBACK_EXECUTION' -Executable $bash -Parameters @($rollback.Replace('\', '/'), $restored.Replace('\', '/'))
$rollbackRecord = Invoke-Recorded -Label 'ROLLBACK' -Executable $aapt -Parameters @('dump', 'badging', $restored)
$restoredHash = (Get-FileHash -LiteralPath $restored -Algorithm SHA256).Hash
if ($restoredHash -ne $expected -or $rollbackRecord.stdout -ne $baselineRecord.stdout -or $rollbackRecord.stderr -ne $baselineRecord.stderr) {
    throw '回滚的哈希或同命令输出与基线不一致。'
}
Copy-Item -LiteralPath $candidate -Destination $modified -Force
if ((Get-FileHash -LiteralPath $modified -Algorithm SHA256).Hash -ne $candidateHash) {
    throw '重新应用新版本失败。'
}
if ((Get-FileHash -LiteralPath $reference -Algorithm SHA256).Hash -ne $expected) {
    throw '原APK在验证过程中发生变化。'
}

$summary = [ordered]@{
    run = $runId
    reference = $reference
    changedField = 'AndroidManifest package: com.lu.wxmask272 -> io.github.inkveil; versionName: 3.0.5 -> 0.1.0-dev'
    behaviorCoverage = 'aapt2读取Manifest及包元数据；未安装或运行任一APK，不证明微信实际过滤和入口手势生效'
    hashes = [ordered]@{ baseline = $expected; modified = $candidateHash; reconstructed = (Get-FileHash -LiteralPath $reconstructed -Algorithm SHA256).Hash; rollback = $restoredHash }
    roles = [ordered]@{ MODIFIED_FILE = $modified; DIFF_FILE = $patch; VERIFICATION = $verification; ROLLBACK = $rollback }
    records = $records
}
$summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $runDirectory 'summary.json') -Encoding utf8
$report = [Text.StringBuilder]::new()
$null = $report.AppendLine('运行：' + $runId)
$null = $report.AppendLine('原始参考：' + $reference)
$null = $report.AppendLine('实际改动字段：' + $summary.changedField)
$null = $report.AppendLine('覆盖边界：' + $summary.behaviorCoverage)
$null = $report.AppendLine('哈希：' + ($summary.hashes | ConvertTo-Json -Compress))
foreach ($record in $records) {
    $null = $report.AppendLine('标签：' + $record.label)
    $null = $report.AppendLine('命令：' + $record.executable)
    $null = $report.AppendLine('参数：' + ($record.parameters | ConvertTo-Json -Compress))
    $null = $report.AppendLine('工作目录：' + $record.workingDirectory)
    $null = $report.AppendLine('退出码：' + $record.exitCode)
    $null = $report.AppendLine('stdout（原文开始）：')
    $null = $report.Append($record.stdout)
    $null = $report.AppendLine('stdout（原文结束）：')
    $null = $report.AppendLine('stderr（原文开始）：')
    $null = $report.Append($record.stderr)
    $null = $report.AppendLine('stderr（原文结束）：')
}
$null = $report.AppendLine('补丁精确重建、回滚哈希一致、新版重新应用、原APK未改变：通过。')
[IO.File]::AppendAllText($verification, $report.ToString(), $utf8)
foreach ($path in @($modified, $patch, $verification, $rollback)) {
    $stream = [IO.File]::OpenRead($path)
    try { $null = $stream.ReadByte() } finally { $stream.Dispose() }
    $file = Get-Item -LiteralPath $path
    Write-Output ('已重新打开角色文件：' + $file.FullName + '；字节：' + $file.Length + '；SHA-256：' + (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash)
}
foreach ($record in @($baselineRecord, $modifiedRecord, $rollbackRecord)) {
    Write-Output ($record.label + '：' + ($record.stdout -split '\r?\n')[0] + '；退出码：' + $record.exitCode)
}
Write-Output '验证完成：元数据可读、签名/对齐有效、补丁精确重建、独立副本可回滚；未做真机功能验证。'
