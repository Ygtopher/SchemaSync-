@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo   SchemaSync - Standalone Windows Executable Builder
echo ============================================================

REM 1. Locate jpackage
set "JPACKAGE="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\jpackage.exe" (
        set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
    )
)

if not defined JPACKAGE (
    for /f "delims=" %%I in ('where jpackage 2^>nul') do set "JPACKAGE=%%I"
)

if not defined JPACKAGE (
    echo [ERROR] jpackage.exe was not found.
    echo Please make sure JAVA_HOME is set to JDK 21+ or jpackage is in PATH.
    echo Current JAVA_HOME: %JAVA_HOME%
    pause
    exit /b 1
)

echo Found jpackage: %JPACKAGE%

REM 2. Compile latest JAR if requested or missing
set "REBUILD=0"
if "%1"=="--rebuild" set "REBUILD=1"
if "%1"=="-r" set "REBUILD=1"

if not exist "release\SchemaSync.jar" set "REBUILD=1"

if "!REBUILD!"=="1" (
    echo.
    echo [1/3] Compiling and packaging latest SchemaSync.jar with Maven...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Maven build failed.
        pause
        exit /b 1
    )
    if not exist "release" mkdir release
    copy /Y "target\SchemaSync.jar" "release\SchemaSync.jar" >nul
) else (
    echo.
    echo [1/3] Using existing release\SchemaSync.jar (pass --rebuild to recompile)
)

REM 3. Clean prior dist\SchemaSync
echo.
echo [2/3] Cleaning previous dist\SchemaSync...
if exist "dist\SchemaSync" (
    rmdir /S /Q "dist\SchemaSync"
)

REM 4. Build application image with embedded minimal JRE
echo.
echo [3/3] Generating standalone Windows executable bundle...
"%JPACKAGE%" ^
  --type app-image ^
  --name SchemaSync ^
  --input release ^
  --main-jar SchemaSync.jar ^
  --main-class com.dbtool.Main ^
  --dest dist ^
  --app-version 1.1.0 ^
  --vendor "SchemaSync" ^
  --java-options "-Djava.net.preferIPv4Stack=true" ^
  --add-modules java.desktop,java.sql,java.naming,java.management,java.xml,java.logging,java.net.http,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki

if errorlevel 1 (
    echo.
    echo [ERROR] jpackage build failed.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD COMPLETED SUCCESSFULLY!
echo ============================================================
echo   Standalone Executable : dist\SchemaSync\SchemaSync.exe
echo   Testing JAR (untouched): release\SchemaSync.jar
echo.
echo   You can double-click SchemaSync.exe to run the application
echo   without needing Java installed on the machine.
echo ============================================================

endlocal
