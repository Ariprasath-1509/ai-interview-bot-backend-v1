@echo off
echo ========================================
echo Quick Git Cleanup - Remove Ignored Files
echo ========================================
echo.

REM Check if in git repo
git rev-parse --git-dir >nul 2>&1
if errorlevel 1 (
    echo ERROR: Not a git repository!
    pause
    exit /b 1
)

echo This will remove all files that match .gitignore patterns from Git tracking
echo Files will remain on your local disk
echo.
echo Press any key to continue or Ctrl+C to cancel...
pause >nul

echo.
echo Removing cached files...
echo.

REM Remove everything from Git's index
git rm -r --cached . 2>nul

echo.
echo Re-adding files (respecting .gitignore)...
echo.

REM Re-add everything (Git will now respect .gitignore)
git add .

echo.
echo ========================================
echo Done! Checking what was removed...
echo ========================================
echo.

git status --short

echo.
echo ========================================
echo Next steps:
echo ========================================
echo 1. Review the changes above (files with 'D' are deleted from Git)
echo 2. Commit the changes:
echo    git commit -m "chore: remove ignored files from Git tracking"
echo 3. Push to remote:
echo    git push origin main
echo.
echo Files are removed from Git but kept locally
echo ========================================
pause
