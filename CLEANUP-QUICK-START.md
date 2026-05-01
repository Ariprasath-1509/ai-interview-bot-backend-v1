# Quick Reference - Git Cleanup

## Choose Your Script

### 🚀 cleanup-git-auto.bat (Recommended for most users)
**Does everything automatically:**
- Removes ignored files from Git
- Commits the changes
- Pushes to remote

```bash
cleanup-git-auto.bat
```

---

### 🎯 cleanup-git-simple.bat (Safe and simple)
**Manual control:**
- Removes ignored files from Git
- Shows you what was removed
- You commit and push manually

```bash
cleanup-git-simple.bat
git commit -m "chore: remove ignored files"
git push origin main
```

---

### 🔍 cleanup-git-comprehensive.bat (Detailed)
**See exactly what's happening:**
- Removes files one type at a time
- Shows progress for each file type
- You commit and push manually

```bash
cleanup-git-comprehensive.bat
git commit -m "chore: remove ignored files"
git push origin main
```

---

## One-Line Manual Command

If you prefer a single command:

```bash
git rm -r --cached . && git add . && git commit -m "chore: remove ignored files" && git push origin main
```

---

## What Gets Removed

✅ `target/` - Maven build output
✅ `.idea/` - IntelliJ IDEA settings
✅ `.amazonq/` - Amazon Q cache
✅ `*.iml` - IntelliJ module files
✅ `*.class` - Compiled Java classes
✅ `*.jar` - JAR files
✅ `*.log` - Log files

❌ Your source code (`.java`, `.xml`, `.yml`, etc.)
❌ Configuration files you need
❌ README and documentation

---

## Verification

After running the script:

```bash
# Check local status
git status

# Check remote (after push)
# Visit your GitHub/GitLab repository
# Verify target/, .idea/, etc. are gone
```

---

## Troubleshooting

**"Nothing to commit"**
→ Files were already removed. You're good!

**"Push failed"**
→ Check your remote: `git remote -v`
→ Try manually: `git push origin main`

**Files still showing up**
→ Make sure `.gitignore` is in the root directory
→ Check `.gitignore` syntax

---

## Need Help?

Read the full guide: `GIT-CLEANUP-README.md`
