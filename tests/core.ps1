$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$classes=Join-Path $root 'build/core-tests'
New-Item -ItemType Directory -Force $classes | Out-Null
$javac=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/javac'}else{'javac'}
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java'}else{'java'}
& $javac -encoding UTF-8 -d $classes "$root/app/src/main/java/cn/lightrun/app/RunSession.java" "$root/app/src/main/java/cn/lightrun/app/Format.java" "$PSScriptRoot/CoreTests.java"
if($LASTEXITCODE -ne 0){throw 'Core tests did not compile'}
& $java -cp $classes cn.lightrun.app.CoreTests
if($LASTEXITCODE -ne 0){throw 'Core tests failed'}
