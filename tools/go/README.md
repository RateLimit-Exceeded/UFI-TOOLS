# Go 工具源码（替代原仓库内的预编译二进制）

本项目原本在 `app/src/main/assets/shell/` 下直接提交了若干可执行文件（二进制），其中一部分缺少源码或不便审计。

当前目录提供这些工具的源码，并通过 **Gradle 在打包 APK 时自动编译**到 `assets/shell/*`（见 `app/build.gradle.kts` 的 `buildGoShellTools` 任务），从而做到：

- 仓库不再提交这些可执行二进制（便于审计/溯源）
- APK 仍然包含运行所需的 `assets/shell/*` 工具（运行时由 `ADBService.resetFilesFromAssets` 复制到 `filesDir` 并 `chmod +x`）

| 二进制 | 对应源码 | 说明 |
|---|---|---|
| `sha256` | `sha256/main.go` | `sha256 <string>` 输出 SHA-256 小写 hex（stderr 输出 usage，退出码 2） |
| `ufi_req` | `ufi_req/main.go` | MiniKano 请求签名客户端：生成 `Authorization / kano-t / kano-sign` 并发起 HTTP 请求 |
| `zreq` | `zreq/main.go` | （clean-room 重写）ZTE goform 登录校验：获取 `LD`、计算登录哈希、拿 cookie，并可选计算 `AD` |
| `sendat` | `sendat/main.go` | AT 指令发送工具（原仓库已提供源码，这里仅做“构建资产化”） |

## 构建示例（示意）

如需手动编译（与 Gradle 任务一致），可在任意主机上交叉编译：

```bash
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -buildvcs=false -ldflags="-s -w" -o sha256  ./sha256/main.go
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -buildvcs=false -ldflags="-s -w" -o ufi_req ./ufi_req/main.go
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -buildvcs=false -ldflags="-s -w" -o zreq    ./zreq/main.go
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -buildvcs=false -ldflags="-s -w" -o sendat  ./sendat/main.go
```
