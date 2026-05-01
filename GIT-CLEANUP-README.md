# Git Cleanup Instructions

## Problem
Files like `target/`, `.idea/`, `.amazonq/`, `*.iml`, `*.class`, and `*.jar` are being tracked by Git even though they're in `.gitignore`.

## Why This Happens
These files were committed to Git **before** `.gitignore` was added or updated. Git only ignores **untracked** files, not files that are already being tracked.

## Solution

### Option 1: Simple Cleanup (Recommended)
Run the simple cleanup script that removes all cached files and re-adds them:

```bash
cleanup-git-simple.bat
```

This script:
1. Removes all files from Git's index (staging area)
2. Re-adds all files, now respecting `.gitignore`
3. Shows you what was removed

### Option 2: Comprehensive Cleanup
If you want more control and see exactly what's being removed:

```bash
cleanup-git-comprehensive.bat
```

This script:
1. Finds and removes specific file types one by one
2. Shows progress for each file type
3. Gives you detailed feedback

### Option 3: Manual Cleanup
If you prefer to do it manually:

```bash
# Remove all cached files
git rm -r --cached .

# Re-add everything (respecting .gitignore)
git add .

# Check what was removed
git status

# Commit the changes
git commit -m "chore: remove ignored files from Git tracking"

# Push to remote
git push origin main
```

## What Gets Removed

The following files/directories will be removed from Git tracking:

- **Build output**: `target/`, `build/`, `out/`
- **IDE files**: `.idea/`, `*.iml`, `.vscode/`
- **Amazon Q**: `.amazonq/`
- **Compiled files**: `*.class`
- **Package files**: `*.jar`, `*.war`, `*.ear`
- **Logs**: `*.log`, `logs/`
- **OS files**: `.DS_Store`, `Thumbs.db`

## Important Notes

✅ **Files remain on your local disk** - They're only removed from Git tracking

✅ **Future commits won't include them** - `.gitignore` will prevent them from being tracked again

✅ **Safe operation** - You can always restore files from Git history if needed

⚠️ **Large repository warning** - If your repo is very large, this might take a few minutes

## After Cleanup

1. **Verify the changes**:
   ```bash
   git status
   ```

2. **Commit the cleanup**:
   ```bash
   git commit -m "chore: remove ignored files from Git tracking"
   ```

3. **Push to remote**:
   ```bash
   git push origin main
   ```

4. **Verify on GitHub/GitLab**:
   - Check that `target/`, `.idea/`, etc. are no longer in the repository
   - Your local files should still be there

## Troubleshooting

### "Nothing to commit" after running script
This means all ignored files were already removed or never tracked. You're good!

### Files still showing up after commit
Make sure your `.gitignore` is in the root directory and properly formatted.

### Want to keep some files
Edit `.gitignore` before running the cleanup script to remove patterns you want to keep.

## Prevention

To prevent this in the future:

1. **Always add `.gitignore` first** before making your first commit
2. **Use templates**: GitHub provides `.gitignore` templates for Java/Maven projects
3. **Check before committing**: Run `git status` to see what will be committed

## Need Help?

If you encounter issues:
1. Check `git status` to see what's staged
2. Use `git reset` to unstage changes if needed
3. Your files are safe locally - Git only removes tracking, not files
