param(
    [string]$InstallerDirectory = "desktop_app/build/compose/binaries/main-release/exe"
)

$ErrorActionPreference = 'Stop'
$installers = @(Get-ChildItem $InstallerDirectory -Filter '*-windows-arm64.exe')
if ($installers.Count -ne 1) { throw 'Expected one Windows ARM64 installer' }
$diagnostics = Join-Path $env:RUNNER_TEMP 'harmonic-arm64-verification'
$installDirectory = Join-Path $diagnostics 'installed app'
New-Item -ItemType Directory -Force $diagnostics | Out-Null
if (Test-Path $installDirectory) { throw "Verification directory already exists: $installDirectory" }
$oldJavaOptions = $env:JAVA_TOOL_OPTIONS
$app = $null
$originalImage = Join-Path (Get-Location) 'desktop_app/build/compose/binaries/main-release/app/Harmonic'
$hiddenImage = "$originalImage-verification"
$imageHidden = $false
$userFile = Join-Path $installDirectory 'user-file-to-preserve.txt'
try {
    $setup = Start-Process $installers[0].FullName -Wait -PassThru -ArgumentList @(
        '/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/SP-',
        "/DIR=`"$installDirectory`"", "/LOG=`"$(Join-Path $diagnostics 'install.log')`""
    )
    if ($setup.ExitCode -ne 0) { throw "Installer failed: $($setup.ExitCode)" }
    python -B -c 'import sys; sys.path.insert(0, ".github/scripts"); from pathlib import Path; from package_windows_arm64 import verify_image; verify_image(Path(sys.argv[1]))' $installDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Installed launcher/runtime are not native ARM64' }

    # Installation must register an uninstaller and a Start menu shortcut.
    $registration = @(Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*' |
        Where-Object { $_.DisplayName -eq 'Harmonic (ARM64)' -and
            $_.InstallLocation.TrimEnd('\') -eq $installDirectory })
    if ($registration.Count -ne 1) { throw 'Missing installed application registration' }
    $shortcut = Join-Path ([Environment]::GetFolderPath('Programs')) 'Harmonic ARM64\Harmonic (ARM64).lnk'
    if (-not (Test-Path $shortcut)) { throw 'Missing Start menu shortcut' }

    # Reinstallation must replace obsolete payload without deleting other files.
    Set-Content $userFile 'preserve this file'
    $obsolete = Join-Path $installDirectory 'app/obsolete-upgrade-fixture.jar'
    Set-Content $obsolete 'obsolete payload'
    $upgrade = Start-Process $installers[0].FullName -Wait -PassThru -ArgumentList @(
        '/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/SP-',
        "/DIR=`"$installDirectory`"", "/LOG=`"$(Join-Path $diagnostics 'upgrade.log')`""
    )
    if ($upgrade.ExitCode -ne 0 -or (Test-Path $obsolete) -or
        (Get-Content $userFile) -ne 'preserve this file') { throw 'Installer upgrade failed' }
    Write-Host 'ARM64 installation registration, shortcut and upgrade passed'

    # Hide the source image so an absolute reference to its original runtime/JARs
    # cannot make installation checks pass accidentally on the build machine.
    if (Test-Path $hiddenImage) { throw "Image verification path already exists: $hiddenImage" }
    Move-Item $originalImage $hiddenImage
    $imageHidden = $true

    # This also catches timestamp/path changes made by the installer that invalidate CDS.
    python .github/scripts/verify-desktop-appcds.py $installDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Installed AppCDS archive cannot be loaded' }

    # Training mode runs the real window with an in-memory graph and exits by itself.
    # It must never read/write the runner's production app settings or credentials.
    $env:JAVA_TOOL_OPTIONS = '-Dcompose.aot.training-run=true -Xshare:on'
    $app = Start-Process (Join-Path $installDirectory 'Harmonic.exe') -PassThru `
        -RedirectStandardOutput (Join-Path $diagnostics 'app.stdout.log') `
        -RedirectStandardError (Join-Path $diagnostics 'app.stderr.log')
    $windowObserved = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while (-not $app.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        $app.Refresh()
        if ($app.MainWindowHandle -ne [IntPtr]::Zero) { $windowObserved = $true }
        Start-Sleep -Milliseconds 100
    }
    if (-not $app.HasExited) { throw 'Installed app did not finish its training launch' }
    $app.WaitForExit()
    if ($app.ExitCode -ne 0 -or -not $windowObserved) {
        throw "Installed app did not open and exit cleanly: exit=$($app.ExitCode), window=$windowObserved"
    }
    Write-Host 'Installed ARM64 application opened its window and exited successfully'
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldJavaOptions
    if ($app -and -not $app.HasExited) {
        Stop-Process -Id $app.Id -Force
        $app.WaitForExit()
    }
    if ($imageHidden) { Move-Item $hiddenImage $originalImage }
    $uninstaller = Join-Path $installDirectory 'unins000.exe'
    if (Test-Path $uninstaller) {
        $uninstall = Start-Process $uninstaller -Wait -PassThru -ArgumentList @(
            '/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART',
            "/LOG=`"$(Join-Path $diagnostics 'uninstall.log')`""
        )
        if ($uninstall.ExitCode -ne 0 -or (Test-Path (Join-Path $installDirectory 'Harmonic.exe'))) {
            throw "Uninstallation failed: $($uninstall.ExitCode)"
        }
        if ((Test-Path $userFile) -and (Get-Content $userFile) -ne 'preserve this file') {
            throw 'Uninstallation changed a user-owned file'
        }
        Write-Host 'ARM64 installer uninstallation passed'
    }
}
