package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/md5"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

// Reverse-engineered from the bundled `ufi_req` binary (arm64, UPX-packed).
//
// This is a small CLI that sends a signed request to the UFI-TOOLS HTTP server:
//   - Authorization: sha256(password) (lowercase hex)
//   - kano-t: unix millis
//   - kano-sign: custom HMAC(MD5)+SHA256 signature (see API_Doc.md)
//
// Observed behaviour to match the original binary:
//   - Missing required flags prints usage to stderr and exits 0.
//   - On network/parse errors prints {"error":"..."} + newline to stdout and exits 0.
//   - On non-2xx prints {"error":"HTTP <code> <status>"} + newline to stdout and exits 0.
//   - On success prints the raw response body bytes to stdout (no extra newline).

const kanoSecretKey = "minikano_kOyXz0Ciz4V7wR0IeKmJFYFQ20jd"

func sha256HexLower(s string) string {
	sum := sha256.Sum256([]byte(s))
	return hex.EncodeToString(sum[:])
}

// hmacSignature implements the same algorithm as KanoUtils.HmacSignature / requests.js:
//   md5 = HMAC-MD5(secret, raw)
//   part1 = md5[:8], part2 = md5[8:]
//   sig = SHA256( SHA256(part1) || SHA256(part2) )
func hmacSignature(secret, raw string) string {
	mac := hmac.New(md5.New, []byte(secret))
	_, _ = mac.Write([]byte(raw))
	sum := mac.Sum(nil) // 16 bytes
	mid := len(sum) / 2
	p1 := sum[:mid]
	p2 := sum[mid:]
	s1 := sha256.Sum256(p1)
	s2 := sha256.Sum256(p2)
	combined := append(s1[:], s2[:]...)
	final := sha256.Sum256(combined)
	return hex.EncodeToString(final[:])
}

func printJSONError(msg string) {
	_ = json.NewEncoder(os.Stdout).Encode(map[string]string{
		"error": msg,
	})
}

func buildURL(host, endpoint string) (fullURL string, signPath string, err error) {
	ep := strings.TrimSpace(endpoint)
	if ep == "" {
		return "", "", fmt.Errorf("empty endpoint")
	}

	// Full URL mode.
	if strings.HasPrefix(ep, "http://") || strings.HasPrefix(ep, "https://") {
		u, perr := url.Parse(ep)
		if perr != nil {
			return "", "", perr
		}
		// Sign with decoded path (matches original: u.Path is decoded; request keeps RawPath).
		return u.String(), u.Path, nil
	}

	// Path mode.
	if !strings.HasPrefix(ep, "/") {
		ep = "/" + ep
	}

	// The original binary expects host like "192.168.0.1:2333" (no scheme).
	// We accept both, but normalize to a base URL.
	h := strings.TrimSpace(host)
	if h == "" {
		h = "192.168.0.1:2333"
	}

	// If user passes a URL, keep the host part; otherwise treat as host:port.
	if strings.Contains(h, "://") {
		hu, perr := url.Parse(h)
		if perr != nil || hu.Host == "" {
			// Fall back to the original behaviour: prefixing "http://" will likely fail,
			// but we keep this path lenient.
		} else {
			h = hu.Host
		}
	}

	base := "http://" + h
	u, perr := url.Parse(base + ep)
	if perr != nil {
		return "", "", perr
	}
	return u.String(), u.Path, nil
}

func usage() {
	_, _ = fmt.Fprintln(os.Stderr, "ufi_req - MiniKano签名请求工具\n")
	_, _ = fmt.Fprintln(os.Stderr, "用法：")
	_, _ = fmt.Fprintln(os.Stderr, "  ufi_req -host 192.168.1.1 -pass 123456 -X POST -e /api/xxx -d '{\"command\":\"ls\"}'")
	_, _ = fmt.Fprintln(os.Stderr, "  ufi_req -host 192.168.1.1 -pass 123456 -X GET  -e \"/api/AT?command=AT&slot=0\"\n")
	_, _ = fmt.Fprintln(os.Stderr, "参数：")
	flag.PrintDefaults()
}

func main() {
	method := flag.String("X", "GET", "HTTP 方法：GET/POST/PUT/DELETE...")
	body := flag.String("d", "", "请求体(JSON字符串)。GET 一般不需要。例：'{\"command\":\"ls\"}'")
	endpoint := flag.String("e", "", "请求路径或完整URL，如 \"/api/xxx\" (必填)")
	host := flag.String("host", "192.168.0.1:2333", "目标地址，比如 \"192.168.0.1\" 或 \"192.168.0.1:2333\" (选填)")
	pass := flag.String("pass", "", "密码明文，用于生成 Authorization=sha256(password) (必填)")
	timeoutSec := flag.Int("t", 10, "超时秒数 (默认 15)")
	flag.Usage = usage
	flag.Parse()

	if strings.TrimSpace(*endpoint) == "" || strings.TrimSpace(*pass) == "" {
		flag.Usage()
		return
	}

	fullURL, signPath, err := buildURL(*host, *endpoint)
	if err != nil {
		printJSONError("URL 解析失败: " + err.Error())
		return
	}

	m := strings.ToUpper(strings.TrimSpace(*method))
	if m == "" {
		m = "GET"
	}

	ts := time.Now().UnixMilli()
	raw := "minikano" + m + signPath + strconv.FormatInt(ts, 10)
	sign := hmacSignature(kanoSecretKey, raw)

	var reqBody io.Reader
	if m != http.MethodGet && strings.TrimSpace(*body) != "" {
		reqBody = bytes.NewBufferString(*body)
	}

	req, err := http.NewRequest(m, fullURL, reqBody)
	if err != nil {
		printJSONError("请求构造失败: " + err.Error())
		return
	}

	req.Header.Set("Authorization", sha256HexLower(*pass))
	req.Header.Set("Kano-T", strconv.FormatInt(ts, 10))
	req.Header.Set("Kano-Sign", sign)
	if reqBody != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	client := &http.Client{
		Timeout: time.Duration(*timeoutSec) * time.Second,
	}

	resp, err := client.Do(req)
	if err != nil {
		printJSONError("响应失败: " + err.Error())
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		// Original prints resp.Status ("418 I'm a teapot", ...).
		printJSONError("HTTP " + resp.Status)
		return
	}

	b, err := io.ReadAll(resp.Body)
	if err != nil {
		printJSONError("读取响应失败: " + err.Error())
		return
	}
	_, _ = os.Stdout.Write(b)
}

