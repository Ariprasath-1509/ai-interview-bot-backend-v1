@echo off
echo ========================================
echo Git Cleanup - All-in-One Script
echo ========================================
echo.
echo This script will:
echo 1. Remove ignored files from Git tracking
echo 2. Commit the changes
echo 3. Push to remote repository
echo.
echo Files will remain on your local disk
echo ========================================
echo.

REM Check if in git repo
git rev-parse --git-dir >nul 2>&1
if errorlevel 1 (
    echo ERROR: Not a git repository!
    pause
    exit /b 1
)

REM Check for uncommitted changes
git diff-index --quiet HEAD --
if errorlevel 1 (
    echo WARNING: You have uncommitted changes!
    echo Please commit or stash them before running this script.
    echo.
    git status --short
    echo.
    pause
    exit /b 1
)

echo Current branch:
git branch --show-current
echo.
echo Press any key to continue or Ctrl+C to cancel...
pause >nul

echo.
echo ========================================
echo Step 1: Removing ignored files from Git
echo ========================================
echo.

REM Remove everything from Git's index
git rm -r --cached . >nul 2>&1

REM Re-add everything (respecting .gitignore)
git add .

echo.
echo Files removed from Git tracking:
echo.
git status --short | findstr /B "D "

echo.
echo ========================================
echo Step 2: Committing changes
echo ========================================
echo.

git commit -m "chore: remove ignored files from Git tracking (target, .idea, .amazonq, *.class, *.jar)"

if errorlevel 1 (
    echo.
    echo No changes to commit - ignored files were already removed!
    echo.
    pause
    exit /b 0
)

echo.
echo Commit successful!

echo.
echo ========================================
echo Step 3: Pushing to remote
echo ========================================
echo.

git push origin main

if errorlevel 1 (
    echo.
    echo ERROR: Push failed!
    echo Please check your remote configuration and try manually:
    echo   git push origin main
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo SUCCESS! Cleanup complete
echo ========================================
echo.
echo Summary:
echo - Ignored files removed from Git tracking
echo - Changes committed
echo - Pushed to remote repository
echo.
echo Your local files are safe and untouched
echo Future commits will respect .gitignore
echo ========================================
pause
