#define MyAppName "SchemaSync"
#define MyAppVersion "1.2.0"
#define MyAppPublisher "NSENGIYUMVA Christophe"
#define MyAppURL "https://github.com/Ygtopher/SchemaSync-"
#define MyAppExeName "SchemaSync.exe"
#define MyAppCopyright "Copyright (C) 2026 NSENGIYUMVA Christophe. All rights reserved."

[Setup]
AppId={{D37E8A61-2F19-4C98-8AA2-892D014F91CE}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
AppCopyright={#MyAppCopyright}
DefaultDirName={autopf}\{#MyAppName}
DisableProgramGroupPage=yes
OutputDir=d:\projects\DBTOOL\release
OutputBaseFilename=SchemaSync-Setup
SetupIconFile=d:\projects\DBTOOL\src\main\resources\icons\app.ico
UninstallDisplayIcon={app}\app.ico
UninstallDisplayName={#MyAppName}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "d:\projects\DBTOOL\release\SchemaSync.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "d:\projects\DBTOOL\src\main\resources\icons\app.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\app.ico"; AppUserModelID: "SchemaSync.DatabaseTool"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\app.ico"; Tasks: desktopicon; AppUserModelID: "SchemaSync.DatabaseTool"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
function IsJavaInstalled(): Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec('cmd.exe', '/c "java -version"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
  if not Result then
  begin
    Result := FileExists(ExpandConstant('{env:JAVA_HOME}\bin\java.exe'));
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
  MsiPath: String;
  DownloadCmd: String;
begin
  if CurStep = ssPostInstall then
  begin
    if not IsJavaInstalled() then
    begin
      if MsgBox('SchemaSync requires Java 11 or higher to run.' + #13#10 + #13#10 +
                'Would you like the installer to automatically download and install Java (Eclipse Temurin JRE 21) now?',
                mbConfirmation, MB_YESNO) = IDYES then
      begin
        MsiPath := ExpandConstant('{tmp}\temurin_jre.msi');
        DownloadCmd := '-ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $wc = New-Object Net.WebClient; $wc.Headers.Add(''User-Agent'',''Mozilla/5.0''); $wc.DownloadFile(''https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk'', ''' + MsiPath + ''')"';
        
        WizardForm.StatusLabel.Caption := 'Downloading Java Runtime Environment (Adoptium Temurin JRE)...';
        if Exec('powershell.exe', DownloadCmd, '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0) and FileExists(MsiPath) then
        begin
          WizardForm.StatusLabel.Caption := 'Installing Java Runtime Environment...';
          Exec('msiexec.exe', '/i "' + MsiPath + '" /passive /norestart ADDLOCAL="FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome"', '', SW_SHOW, ewWaitUntilTerminated, ResultCode);
        end
        else
        begin
          MsgBox('Could not automatically download Java. Please download and install Java 11+ manually from https://adoptium.net', mbInformation, MB_OK);
        end;
      end;
    end;
  end;
end;
