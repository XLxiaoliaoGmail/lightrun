param([string[]]$Tasks = @('assembleRelease','lintRelease'))
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
    & "$PSScriptRoot/gradlew.bat" --no-daemon --console=plain @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle exited with code $LASTEXITCODE" }
} finally { Pop-Location }
