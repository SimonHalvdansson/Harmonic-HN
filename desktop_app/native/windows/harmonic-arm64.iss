; jpackage 21 builds ARM64 application images, but its MSI authoring assumes x64.
; Inno Setup installs the complete native image, including Java and the AppCDS archive.
#ifndef AppImage
  #error AppImage must name the packaged Harmonic directory
#endif
#ifndef AppVersion
  #error AppVersion must match the packaged desktop metadata
#endif
#ifndef InstallerOutput
  #error InstallerOutput must name the installer output directory
#endif

[Setup]
AppId={{7F608F45-0D48-48A7-9560-710A9DF65E30}
AppName=Harmonic (ARM64)
AppVersion={#AppVersion}
AppPublisher=Simon Halvdansson
DefaultDirName={autopf}\Harmonic ARM64
DefaultGroupName=Harmonic ARM64
PrivilegesRequired=lowest
ArchitecturesAllowed=arm64
ArchitecturesInstallIn64BitMode=arm64
MinVersion=10.0.22000
OutputDir={#InstallerOutput}
OutputBaseFilename=Harmonic-{#AppVersion}-windows-arm64
SetupIconFile=..\..\icons\harmonic.ico
UninstallDisplayIcon={app}\Harmonic.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no

[InstallDelete]
; Only replace application payload. Settings, credentials and downloaded models
; live outside the installation directory and must survive upgrades/uninstallation.
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Harmonic (ARM64)"; Filename: "{app}\Harmonic.exe"; WorkingDir: "{app}"

[Run]
Filename: "{app}\Harmonic.exe"; Description: "Launch Harmonic"; Flags: nowait postinstall skipifsilent
