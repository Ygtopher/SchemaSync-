#define MyAppName "SchemaSync"
#define MyAppVersion "1.6.9"
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
Source: "d:\projects\DBTOOL\release\SchemaSync.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "d:\projects\DBTOOL\src\main\resources\icons\app.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{code:GetJavaWExe}"; Parameters: "-Duser.timezone=UTC -jar ""{app}\SchemaSync.jar"""; WorkingDir: "{app}"; IconFilename: "{app}\app.ico"; AppUserModelID: "SchemaSync.DatabaseTool"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{code:GetJavaWExe}"; Parameters: "-Duser.timezone=UTC -jar ""{app}\SchemaSync.jar"""; WorkingDir: "{app}"; IconFilename: "{app}\app.ico"; Tasks: desktopicon; AppUserModelID: "SchemaSync.DatabaseTool"

[Run]
Filename: "{code:GetJavaWExe}"; Parameters: "-Duser.timezone=UTC -jar ""{app}\SchemaSync.jar"""; WorkingDir: "{app}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall shellexec

[Code]
function FindAdoptiumJava(): String;
var
  FindRec: TFindRec;
  BaseDir: String;
begin
  Result := '';
  BaseDir := ExpandConstant('{commonpf}\Eclipse Adoptium\');
  if DirExists(BaseDir) then
  begin
    if FindFirst(BaseDir + '*', FindRec) then
    begin
      try
        repeat
          if (FindRec.Attributes and FILE_ATTRIBUTE_DIRECTORY <> 0) and
             (FindRec.Name <> '.') and (FindRec.Name <> '..') then
          begin
            if FileExists(BaseDir + FindRec.Name + '\bin\javaw.exe') then
            begin
              Result := BaseDir + FindRec.Name + '\bin\javaw.exe';
              exit;
            end;
          end;
        until not FindNext(FindRec);
      finally
        FindClose(FindRec);
      end;
    end;
  end;
end;

function FindJavaInProgramFiles(): String;
var
  FindRec: TFindRec;
  BaseDir: String;
begin
  Result := '';
  BaseDir := ExpandConstant('{commonpf}\Java\');
  if DirExists(BaseDir) then
  begin
    if FindFirst(BaseDir + '*', FindRec) then
    begin
      try
        repeat
          if (FindRec.Attributes and FILE_ATTRIBUTE_DIRECTORY <> 0) and
             (FindRec.Name <> '.') and (FindRec.Name <> '..') then
          begin
            if FileExists(BaseDir + FindRec.Name + '\bin\javaw.exe') then
            begin
              Result := BaseDir + FindRec.Name + '\bin\javaw.exe';
              exit;
            end;
          end;
        until not FindNext(FindRec);
      finally
        FindClose(FindRec);
      end;
    end;
  end;
end;

function GetJavaWExe(Param: String): String;
var
  JavaHome: String;
  FoundPath: String;
  AdoptiumPath: String;
  PfJavaPath: String;
begin
  AdoptiumPath := FindAdoptiumJava();
  if AdoptiumPath <> '' then
  begin
    Result := AdoptiumPath;
    exit;
  end;

  JavaHome := GetEnv('JAVA_HOME');
  if (JavaHome <> '') and FileExists(JavaHome + '\bin\javaw.exe') then
  begin
    Result := JavaHome + '\bin\javaw.exe';
    exit;
  end;

  PfJavaPath := FindJavaInProgramFiles();
  if PfJavaPath <> '' then
  begin
    Result := PfJavaPath;
    exit;
  end;

  FoundPath := FileSearch('javaw.exe', GetEnv('PATH'));
  if (FoundPath <> '') and FileExists(FoundPath) then
  begin
    Result := FoundPath;
    exit;
  end;

  if FileExists(ExpandConstant('{commoncf}\Oracle\Java\javapath\javaw.exe')) then
  begin
    Result := ExpandConstant('{commoncf}\Oracle\Java\javapath\javaw.exe');
    exit;
  end;
  if FileExists('C:\Program Files\Common Files\Oracle\Java\javapath\javaw.exe') then
  begin
    Result := 'C:\Program Files\Common Files\Oracle\Java\javapath\javaw.exe';
    exit;
  end;
  if FileExists('C:\Program Files (x86)\Common Files\Oracle\Java\javapath\javaw.exe') then
  begin
    Result := 'C:\Program Files (x86)\Common Files\Oracle\Java\javapath\javaw.exe';
    exit;
  end;

  Result := 'javaw.exe';
end;

function IsJava11OrHigherInstalled(): Boolean;
var
  ResultCode: Integer;
  CheckCmd: String;
begin
  Result := False;
  CheckCmd := '-ExecutionPolicy Bypass -Command "$v = & java -version 2>&1 | Out-String; if ($v -match ''version [\""'']?(\d+)'') { if ([int]$matches[1] -ge 11) { exit 0 } } elseif ($v -match ''version [\""'']?1\.(\d+)'') { if ([int]$matches[1] -ge 11) { exit 0 } }; exit 1"';
  if Exec('powershell.exe', CheckCmd, '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0) then
  begin
    Result := True;
    exit;
  end;

  if (FindAdoptiumJava() <> '') or (FindJavaInProgramFiles() <> '') then
  begin
    Result := True;
    exit;
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
  MsiPath: String;
  DownloadCmd: String;
begin
  Result := '';
  NeedsRestart := False;

  if not IsJava11OrHigherInstalled() then
  begin
    if MsgBox('SchemaSync requires Java (version 11 or higher) to run.' + #13#10 + #13#10 +
              'No compatible Java runtime was detected on this computer.' + #13#10 + #13#10 +
              'Would you like Setup to automatically download and install Eclipse Temurin JRE 21 now?' + #13#10 +
              '(This runtime is digitally signed by Eclipse Foundation and fully trusted by Windows 11 Smart App Control)',
              mbConfirmation, MB_YESNO) = IDYES then
    begin
      MsiPath := ExpandConstant('{tmp}\temurin_jre_21.msi');
      DownloadCmd := '-ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $wc = New-Object Net.WebClient; $wc.Headers.Add(''User-Agent'',''Mozilla/5.0''); $wc.DownloadFile(''https://api.adoptium.net/v3/installer/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk'', ''' + MsiPath + ''')"';
      
      WizardForm.StatusLabel.Caption := 'Downloading Java Runtime Environment (Adoptium Temurin JRE 21)...';
      if Exec('powershell.exe', DownloadCmd, '', SW_SHOW, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0) and FileExists(MsiPath) then
      begin
        WizardForm.StatusLabel.Caption := 'Installing Java Runtime Environment...';
        Exec('msiexec.exe', '/i "' + MsiPath + '" /passive /norestart ADDLOCAL="FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome"', '', SW_SHOW, ewWaitUntilTerminated, ResultCode);
      end
      else
      begin
        MsgBox('Could not automatically download Java. Please download and install Java 11+ manually from https://adoptium.net', mbInformation, MB_OK);
      end;
    end
    else
    begin
      MsgBox('Note: SchemaSync requires Java 11 or higher. You can install it manually anytime from https://adoptium.net', mbInformation, MB_OK);
    end;
  end;
end;
