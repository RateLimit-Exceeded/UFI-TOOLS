---
name: manual-port-fake-merge
description: 在不直接 merge 上游分支的前提下，手动移植指定修复/特性到目标分支，并通过 `git merge -s ours` 创建“假的合并记录”来对齐合并关系（历史审计友好，且可避免二进制/构建产物冲突）。本仓库常见场景：从 `http-server-version` 移植到 `1007`。
---

# 手动搬运 + 假合并（ours）

## 目标

- 从 `<upstream-remote>/<upstream-branch>` 选择性搬运改动到当前分支（不直接 merge 上游文件变更）。
- 通过 “ours merge” 生成一个带双父节点的合并提交，用于**同步合并状态**（假合并记录）。

## 本仓库默认分支（UFI-TOOLS）

- 目标分支：`1007`
- 上游分支：`http-server-version`
- 上游 remote：优先使用 `upstream`（若未配置则用 `origin`）
- 典型方向：`upstream/http-server-version` → `1007`（先手动 port，再 `-s ours` 假合并）

## 推荐流程（默认安全）

1. 确保工作区干净
   - `git status -sb`
   - 不要把无关产物带进提交（常见：`.gradle/**.lock`、工具生成的 `.ace-tool/` 等）。

2. 拉取目标远程分支（网络不稳定时用浅拉）
   - `git fetch <upstream-remote> <upstream-branch>`
   - 如果出现 early EOF / timeout：
     - `git fetch --no-tags --depth=50 <upstream-remote> <upstream-branch>`

3. 定位要搬运的提交
   - `git log <upstream-remote>/<upstream-branch> --oneline --grep="<关键字>"`
   - 或先看分支差异规模：
     - `git rev-list --count HEAD..<upstream-remote>/<upstream-branch>`

4. 审核提交影响范围（先分清“源码 vs 产物”）
   - `git show --name-status <sha>`
   - 重点检查是否包含二进制/构建产物（例如 `app/release/**`）。一般**不要**把这些当作“特性搬运”的一部分。

5. 判断当前分支是否已包含修复（避免重复搬运）
   - 优先用 `mcp__ACE__search_context` 定位相关状态/逻辑（例如：网络调试状态、`adbIsReady`、相关 API 路由）。
   - 再用 `git blame`/`git log -p` 进行确认。

6. 手动搬运改动（避免 cherry-pick 带来的二进制冲突）
   - 方案 A（推荐）：只取指定文件的内容变更（不引入提交历史）
     - `git checkout <sha> -- path/to/file1 path/to/file2`
     - 或单文件：`git show <sha>:path/to/file > path/to/file`
   - 方案 B：按 diff 手工改代码（适合需要顺便适配当前分支上下文的场景）
   - JS 资源注意：
     - 若仓库存在 `app/frontEnd/public/script/*.js` 源码 + `app/src/main/assets/script/*.js` 产物，优先改源码并通过前端构建脚本同步产物，避免直接手改混淆后的 bundle。

7. 提交“手动搬运”的代码变更（只包含需要的文件）
   - 示例信息：`chore: port <sha> from <upstream-branch> (manual)`
   - 避免把 `app/release/**`、`.gradle/**`、`.ace-tool/**` 一起提交。

8. 创建“假的合并记录”（ours merge，不引入文件变更）
   - `git merge --no-ff -s ours <upstream-remote>/<upstream-branch> -m "chore: fake merge <upstream-branch> (manual port)"`

9. 校验假合并是否正确
   - `git show -s --pretty=raw HEAD`（应出现 2 行 `parent`）
   - 确认 merge commit 本身不带额外文件改动：
     - `git diff --stat HEAD^1..HEAD`（应为空）

## 常见坑与处理

- `git cherry-pick` 遇到 APK/baseline profile 等二进制冲突：
  - 换用 “只 checkout 指定路径” 的方式（见第 6 步）。
  - 若已进入冲突状态且确定放弃该次尝试：
    - `git cherry-pick --abort`（若可用）
    - 或 `git reset --hard HEAD`（会丢弃未保存改动，谨慎）

- 工作区被工具写入 `.ace-tool/`：
  - 提交前删除，或团队统一后加入 `.gitignore`。

- `.gradle/**.lock` 意外变更：
  - `git restore .gradle/**` 后再继续。
