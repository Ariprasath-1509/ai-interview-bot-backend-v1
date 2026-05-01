@echo off
echo ========================================
echo Removing tracked files that should be ignored
echo ========================================
echo.

echo Step 1: Removing .idea directories from Git...
git rm -r --cached .idea
git rm -r --cached */.idea 2>nul

echo.
echo Step 2: Removing target directories from Git...
git rm -r --cached target 2>nul
git rm -r --cached */target 2>nul
git rm -r --cached */*/target 2>nul

echo.
echo Step 3: Removing .amazonq directories from Git...
git rm -r --cached .amazonq 2>nul
git rm -r --cached */.amazonq 2>nul

echo.
echo Step 4: Removing *.iml files from Git...
git rm --cached *.iml 2>nul
git rm --cached */*.iml 2>nul
git rm --cached */*/*.iml 2>nul

echo.
echo Step 5: Removing *.class files from Git...
git rm --cached *.class 2>nul
git rm --cached */*.class 2>nul
git rm --cached */*/*.class 2>nul
git rm --cached */*/*/*.class 2>nul
git rm --cached */*/*/*/*.class 2>nul

echo.
echo Step 6: Removing JAR files from Git...
git rm --cached *.jar 2>nul
git rm --cached */*.jar 2>nul
git rm --cached */*/*.jar 2>nul
git rm --cached */*/*/*.jar 2>nul

echo.
echo ========================================
echo Cleanup complete!
echo ========================================
echo.
echo Next steps:
echo 1. Review the changes: git status
echo 2. Commit the changes: git commit -m "Remove ignored files from Git tracking"
echo 3. Push to remote: git push origin main
echo.
echo Note: Files are removed from Git but kept locally
echo ========================================
pause
