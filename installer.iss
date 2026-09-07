#ifndef MyAppVersion
  #define MyAppVersion "1.1.0"
#endif

#define MyAppName "Video Downloader by Zubair Abbas"
#define MyAppExeName "Video Downloader by Zubair Abbas.exe"

[Setup]
AppId={{7B229DE5-60DE-4AEC-B254-9B6438EF1D64}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=Zubair Abbas
DefaultDirName={autopf}\Video Downloader by Zubair Abbas
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=release
OutputBaseFilename=VideoDownloaderSetup-v{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
CloseApplications=yes
RestartApplications=no

[Files]
Source: "dist\Video Downloader by Zubair Abbas\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"; Flags: unchecked

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent
