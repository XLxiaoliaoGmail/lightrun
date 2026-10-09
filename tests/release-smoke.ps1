param([string]$Device='emulator-5554', [string]$Adb='adb')
$ErrorActionPreference='Stop'
$adb=$Adb
$work=Join-Path (Split-Path $PSScriptRoot -Parent) 'build/host-tests'
New-Item -ItemType Directory -Force $work | Out-Null
# Coordinates below are synthetic fixtures, not a person's recorded location.
$checks=0
function DeviceCommand([string[]]$Arguments) { & $adb -s $Device @Arguments | Out-Null; if($LASTEXITCODE -ne 0){throw "ADB command failed: $Arguments"} }
function UI {
    DeviceCommand @('shell','uiautomator','dump','/sdcard/smoke.xml')
    DeviceCommand @('pull','/sdcard/smoke.xml',"$work\release-smoke.xml")
    [xml](Get-Content -Raw "$work\release-smoke.xml")
}
function Tap([string]$Text,[string]$Id='') {
    $xml=UI
    $node=$xml.SelectNodes('//node') | Where-Object {if($Id){$_.'resource-id' -eq $Id}else{$_.text -eq $Text}} | Select-Object -First 1
    if(-not $node){throw "Control not found: $Text $Id"}
    $numbers=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    DeviceCommand @('shell','input','tap',"$([int](($numbers[0]+$numbers[2])/2))","$([int](($numbers[1]+$numbers[3])/2))")
}
function Expect([string]$Text) {
    for($attempt=0;$attempt -lt 4;$attempt++) {
        $xml=UI
        if($xml.SelectNodes('//node') | Where-Object {$_.text.Contains($Text)}){$script:checks++;return}
        Start-Sleep -Milliseconds 500
    }
    throw "Expected UI text: $Text"
}
DeviceCommand @('shell','pm','clear','cn.lightrun.app')
DeviceCommand @('shell','am','start','-W','-n','cn.lightrun.app/.MainActivity')
Expect '开跑'
Tap '开跑'
Tap '' 'com.android.permissioncontroller:id/permission_deny_button'
Expect '需要精确位置'
Tap '稍后'
Expect '准备好了'
Tap '开跑'
Tap '' 'com.android.permissioncontroller:id/permission_location_accuracy_radio_coarse'
Tap '' 'com.android.permissioncontroller:id/permission_allow_foreground_only_button'
Expect '需要精确位置'
Tap '稍后'
DeviceCommand @('shell','pm','grant','cn.lightrun.app','android.permission.ACCESS_FINE_LOCATION')
DeviceCommand @('shell','pm','grant','cn.lightrun.app','android.permission.ACCESS_COARSE_LOCATION')
DeviceCommand @('shell','settings','put','secure','location_mode','0')
Tap '开跑'
Tap '' 'com.android.permissioncontroller:id/permission_deny_button'
Tap '' 'com.android.permissioncontroller:id/permission_deny_button'
Expect '开启手机定位'
Tap '取消'
DeviceCommand @('shell','settings','put','secure','location_mode','3')
Tap '开跑'
Expect '暂停'
DeviceCommand @('emu','geo','fix','121','31')
Start-Sleep -Milliseconds 2300
DeviceCommand @('emu','geo','fix','121','31.00005')
Start-Sleep -Milliseconds 2300
DeviceCommand @('emu','geo','fix','121','31.00010')
Start-Sleep -Milliseconds 5500
DeviceCommand @('shell','am','force-stop','cn.lightrun.app')
DeviceCommand @('shell','am','start','-W','-n','cn.lightrun.app/.MainActivity')
Expect '记录已恢复'
Expect '继续跑'
Tap '结束并保存'
Tap '结束并保存'
Expect '我的跑步'
# Saved record opens details, providing the system document export picker.
$xml=UI
$card=$xml.SelectNodes('//node') | Where-Object {$_.'content-desc' -like '*跑步记录'} | Select-Object -First 1
if(-not $card){throw 'Saved history card not found'}
$b=[regex]::Matches($card.bounds,'\d+') | ForEach-Object {[int]$_.Value}
DeviceCommand @('shell','input','tap',"$([int](($b[0]+$b[2])/2))","$([int](($b[1]+$b[3])/2))")
Expect '跑步记录'
Expect '个轨迹点'
Tap '导出 GPX 轨迹'
Expect '轻跑_'
DeviceCommand @('shell','input','keyevent','4')
DeviceCommand @('shell','input','keyevent','4')
DeviceCommand @('shell','input','keyevent','4')
"PASS: $checks release UI assertions; permission denial, approximate location, GPS off, notification denied, start, forced-stop recovery, history, export picker." | Tee-Object "$work\release-smoke-result.txt"
