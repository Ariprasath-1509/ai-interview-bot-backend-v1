@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Git Repository Cleanup Script
echo ========================================
echo This script will remove tracked files that should be ignored
echo Files will be removed from Git but kept on your local disk
echo ========================================
echo.

REM Check if we're in a git repository
git rev-parse --git-dir >nul 2>&1
if errorlevel 1 (
    echo ERROR: Not a git repository!
    echo Please run this script from the root of your git repository.
    pause
    exit /b 1
)

echo Current directory: %CD%
echo.
echo Press any key to continue or Ctrl+C to cancel...
pause >nul

echo.
echo ========================================
echo Starting cleanup...
echo ========================================

REM Remove .idea directories
echo.
echo [1/8] Removing .idea directories...
for /d /r %%i in (.idea) do (
    if exist "%%i" (
        echo   Removing: %%i
        git rm -r --cached "%%i" 2>nul
    )
)

REM Remove target directories
echo.
echo [2/8] Removing target directories...
for /d /r %%i in (target) do (
    if exist "%%i" (
        echo   Removing: %%i
        git rm -r --cached "%%i" 2>nul
    )
)

REM Remove .amazonq directories
echo.
echo [3/8] Removing .amazonq directories...
for /d /r %%i in (.amazonq) do (
    if exist "%%i" (
        echo   Removing: %%i
        git rm -r --cached "%%i" 2>nul
    )
)

REM Remove .iml files
echo.
echo [4/8] Removing *.iml files...
for /r %%i in (*.iml) do (
    if exist "%%i" (
        echo   Removing: %%i
        git rm --cached "%%i" 2>nul
    )
)

REM Remove .class files
echo.
echo [5/8] Removing *.class files...
for /r %%i in (*.class) do (
    if exist "%%i" (
        echo   Removing: %%i
        git rm --cached "%%i" 2>nul
    )
)

REM Remove JAR files
echo.
echo [6/8] Removing *.jar files...
for /r %%i in (*.jar) do (
    if exist "%%i" (
        echo   Removing: %%i
        git rm --cached "%%i" 2>nul
    )
)

REM Remove WAR files
echo.
echo [7/8] Removing *.war files...
for /r %%i in (*.war) do (
    if exist "%%i" (
        echo   Removing: %%i
        git rm --cached "%%i" 2>nul
    )
)

REM Remove log files
echo.
echo [8/8] Removing *.log files...
for /r %%i in (*.log) do (
    if exist "%%i" (
        echo   Removing: %%i
        git rm --cached "%%i" 2>nul
    )
)

echo.
echo ========================================
echo Cleanup complete!
echo ========================================
echo.
echo Checking status...
git status --short

echo.
echo ========================================
echo Next steps:
echo ========================================
echo 1. Review the changes above
echo 2. If everything looks good, commit:
echo    git commit -m "chore: remove ignored files from Git tracking"
echo 3. Push to remote:
echo    git push origin main
echo.
echo Note: Files are removed from Git tracking but kept locally
echo Your .gitignore will prevent them from being tracked again
echo ========================================
echo.
pause
