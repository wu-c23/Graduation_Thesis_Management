#!/usr/bin/env bash
# Stop 钩子：一轮任务结束后，如果工作区有变更，自动提交并推送到 GitHub。
# 只在 main 分支、且已配置 origin 时生效；无变更时静默退出。

set -u

# 消费 hook 的 stdin，避免写入端 SIGPIPE
cat >/dev/null 2>&1 || true

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$PROJECT_DIR" 2>/dev/null || exit 0

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0
git remote get-url origin >/dev/null 2>&1 || exit 0

BRANCH="$(git branch --show-current 2>/dev/null || true)"
[ "$BRANCH" = "main" ] || exit 0

HAS_CHANGES=0
[ -n "$(git status --porcelain)" ] && HAS_CHANGES=1

AHEAD=0
if git rev-parse --verify '@{u}' >/dev/null 2>&1; then
  AHEAD="$(git rev-list --count '@{u}..HEAD' 2>/dev/null || echo 0)"
fi

# 无未提交变更，也没有待推送提交
if [ "$HAS_CHANGES" = "0" ] && [ "$AHEAD" = "0" ]; then
  exit 0
fi

if [ "$HAS_CHANGES" = "1" ]; then
  git add -A || {
    echo '{"systemMessage":"自动同步失败：git add 出错"}'
    exit 0
  }

  if ! git diff --cached --quiet; then
    TS="$(date '+%Y-%m-%d %H:%M:%S')"
    if ! git commit -q -m "chore(auto): 同步代码变更 ${TS}"; then
      echo '{"systemMessage":"自动同步失败：git commit 出错，请检查 Git 状态"}'
      exit 0
    fi
  fi
fi

if git push origin main >/dev/null 2>&1; then
  echo '{"systemMessage":"已自动提交并推送到 GitHub（main）"}'
else
  echo '{"systemMessage":"已自动提交，但推送到 GitHub 失败，请检查网络或凭据"}'
fi

exit 0
