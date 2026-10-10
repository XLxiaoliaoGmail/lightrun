param([string]$Device='emulator-5554', [string]$Adb='adb')
$ErrorActionPreference='Stop'
$adb=$Adb
$work=Join-Path (Split-Path $PSScriptRoot -Parent) 'build/host-tests'
New-Item -ItemType Directory -Force $work | Out-Null
function Dump {
    & $adb -s $Device shell uiautomator dump /sdcard/export-test.xml | Out-Null
    & $adb -s $Device pull /sdcard/export-test.xml "$work\export-test.xml" | Out-Null
    [xml](Get-Content -Raw "$work\export-test.xml")
}
function ClickNode($node) {
    if(-not $node){throw 'Expected UI control is missing'}
    $b=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    & $adb -s $Device shell input tap ([int](($b[0]+$b[2])/2)) ([int](($b[1]+$b[3])/2)) | Out-Null
}
function ClickText([string]$label) { $xml=Dump;ClickNode ($xml.SelectNodes('//node') | Where-Object {$_.text -eq $label} | Select-Object -First 1) }
function ScrollToText([string]$label) {
    for($attempt=0;$attempt -lt 18;$attempt++){
        $xml=Dump
        if($xml.SelectNodes('//node')|Where-Object {$_.text.Contains($label) -and $_.bounds -ne '[0,0][0,0]'}){return}
        $screen=$xml.SelectNodes('//node')|Select-Object -First 1
        $b=[regex]::Matches($screen.bounds,'\d+')|ForEach-Object {[int]$_.Value}
        & $adb -s $Device shell input swipe ([int](($b[0]+$b[2])/2)) ([int]($b[1]+($b[3]-$b[1])*.76)) ([int](($b[0]+$b[2])/2)) ([int]($b[1]+($b[3]-$b[1])*.25)) 250 | Out-Null
    }
    throw "Scroll target missing: $label"
}
$xml=Dump
ClickNode ($xml.SelectNodes('//node') | Where-Object {$_.'content-desc' -eq '历史记录'} | Select-Object -First 1)
ScrollToText '0.01 公里'
$xml=Dump
ClickNode ($xml.SelectNodes('//node') | Where-Object {$_.'content-desc' -like '*跑步记录'} | Select-Object -First 1)
ScrollToText '导出 GPX 轨迹'
ClickText '导出 GPX 轨迹'
$xml=Dump
$fileName=($xml.SelectNodes('//node') | Where-Object {$_.'resource-id' -eq 'android:id/title' -and $_.text -like '*.gpx'} | Select-Object -First 1).text
if(-not $fileName){throw 'Export filename missing'}
ClickNode ($xml.SelectNodes('//node') | Where-Object {$_.'resource-id' -eq 'android:id/button1'} | Select-Object -First 1)
$xml=Dump
& $adb -s $Device pull "/sdcard/Download/$fileName" "$work\release-export.gpx" | Out-Null
if($LASTEXITCODE -ne 0){throw 'GPX file was not saved by document picker'}
[xml]$gpx=Get-Content -Raw "$work\release-export.gpx"
$points=$gpx.SelectNodes("//*[local-name()='trkpt']").Count
if($points -lt 2 -or $gpx.gpx.version -ne '1.1'){throw 'Invalid exported GPX'}
"PASS: release APK exported GPX 1.1 through Android document picker ($points GPS points)." | Tee-Object "$work\export-file-result.txt"
