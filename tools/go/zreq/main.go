package main

import (
	"bytes"
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
	"crypto/sha256"
)

// Reverse-engineered (clean-room reimplementation) for the bundled `zreq` binary.
//
// The original `zreq` is an Android/arm64 Go binary (UPX-packed) with functions:
//   main.getLD, main.login, main.getRD, main.getUFIInfo, main.processAD, main.sha256Hex, ...
//
// It performs a ZTE goform login by:
//   1) GET LD
//   2) passwordHash = SHA256( SHA256(password) + LD )   (uppercase hex)
//   3) POST goformId=LOGIN ... password=passwordHash
//   4) parse Set-Cookie (cookie for later requests)
//   5) (optional) compute AD token:
//        parsed = SHA256(wa_inner_version + cr_version)
//        AD = SHA256(parsed + RD)
//
// Output here is JSON: {"success":true,"cookie":"...","ad":"..."}.

func sha256HexUpper(s string) string {
	sum := sha256.Sum256([]byte(s))
	const hexdigits = "0123456789ABCDEF"
	out := make([]byte, 0, len(sum)*2)
	for _, b := range sum[:] {
		out = append(out, hexdigits[b>>4], hexdigits[b&0x0f])
	}
	return string(out)
}

func nowMillis() int64 { return time.Now().UnixMilli() }

func httpGet(client *http.Client, fullURL string, headers map[string]string) ([]byte, http.Header, int, error) {
	req, err := http.NewRequest(http.MethodGet, fullURL, nil)
	if err != nil {
		return nil, nil, 0, err
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, nil, 0, err
	}
	defer resp.Body.Close()
	b, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, nil, resp.StatusCode, err
	}
	return b, resp.Header, resp.StatusCode, nil
}

func httpPostForm(client *http.Client, fullURL string, form url.Values, headers map[string]string) ([]byte, http.Header, int, error) {
	body := form.Encode()
	req, err := http.NewRequest(http.MethodPost, fullURL, bytes.NewBufferString(body))
	if err != nil {
		return nil, nil, 0, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, nil, 0, err
	}
	defer resp.Body.Close()
	b, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, nil, resp.StatusCode, err
	}
	return b, resp.Header, resp.StatusCode, nil
}

func getLD(client *http.Client, base string, headers map[string]string) (string, error) {
	u := base + "/goform/goform_get_cmd_process?isTest=false&cmd=LD&_=" + strconv.FormatInt(nowMillis(), 10)
	b, _, code, err := httpGet(client, u, headers)
	if err != nil {
		return "", err
	}
	if code < 200 || code >= 300 {
		return "", fmt.Errorf("GET LD HTTP %d", code)
	}
	var obj map[string]any
	if err := json.Unmarshal(b, &obj); err != nil {
		return "", err
	}
	ld, _ := obj["LD"].(string)
	if ld == "" {
		return "", fmt.Errorf("LD missing")
	}
	return ld, nil
}

func login(client *http.Client, base, password string, headers map[string]string) (cookie string, err error) {
	ld, err := getLD(client, base, headers)
	if err != nil {
		return "", err
	}
	pwdHash := sha256HexUpper(sha256HexUpper(password) + ld)

	form := url.Values{}
	form.Set("goformId", "LOGIN")
	form.Set("isTest", "false")
	form.Set("user", "admin")
	form.Set("password", pwdHash)

	b, h, code, err := httpPostForm(client, base+"/goform/goform_set_cmd_process", form, headers)
	if err != nil {
		return "", err
	}
	if code < 200 || code >= 300 {
		return "", fmt.Errorf("LOGIN HTTP %d", code)
	}

	var res map[string]any
	_ = json.Unmarshal(b, &res)
	// On some devices, result == "3" indicates auth failure.
	if fmt.Sprint(res["result"]) == "3" {
		return "", fmt.Errorf("login failed (result=3)")
	}

	// Extract cookie from Set-Cookie.
	setCookies := h.Values("Set-Cookie")
	if len(setCookies) == 0 {
		return "", fmt.Errorf("Set-Cookie missing")
	}
	ck := strings.Split(setCookies[0], ";")[0]
	if ck == "" {
		return "", fmt.Errorf("cookie empty")
	}
	return ck, nil
}

func getRD(client *http.Client, base, cookie string, headers map[string]string) (string, error) {
	hdr := map[string]string{}
	for k, v := range headers {
		hdr[k] = v
	}
	hdr["Cookie"] = cookie
	u := base + "/goform/goform_get_cmd_process?isTest=false&cmd=RD&_=" + strconv.FormatInt(nowMillis(), 10)
	b, _, code, err := httpGet(client, u, hdr)
	if err != nil {
		return "", err
	}
	if code < 200 || code >= 300 {
		return "", fmt.Errorf("GET RD HTTP %d", code)
	}
	var obj map[string]any
	if err := json.Unmarshal(b, &obj); err != nil {
		return "", err
	}
	rd, _ := obj["RD"].(string)
	if rd == "" {
		return "", fmt.Errorf("RD missing")
	}
	return rd, nil
}

func getUFIInfo(client *http.Client, base string, headers map[string]string) (wa, cr string, err error) {
	u := base + "/goform/goform_get_cmd_process?isTest=false&cmd=Language,cr_version,wa_inner_version&multi_data=1&_=" + strconv.FormatInt(nowMillis(), 10)
	b, _, code, err := httpGet(client, u, headers)
	if err != nil {
		return "", "", err
	}
	if code < 200 || code >= 300 {
		return "", "", fmt.Errorf("GET UFIInfo HTTP %d", code)
	}
	var obj map[string]any
	if err := json.Unmarshal(b, &obj); err != nil {
		return "", "", err
	}
	wa, _ = obj["wa_inner_version"].(string)
	cr, _ = obj["cr_version"].(string)
	if wa == "" || cr == "" {
		return "", "", fmt.Errorf("wa_inner_version/cr_version missing")
	}
	return wa, cr, nil
}

func processAD(client *http.Client, base, cookie string, headers map[string]string) (string, error) {
	wa, cr, err := getUFIInfo(client, base, headers)
	if err != nil {
		return "", err
	}
	parsed := sha256HexUpper(wa + cr)
	rd, err := getRD(client, base, cookie, headers)
	if err != nil {
		return "", err
	}
	return sha256HexUpper(parsed + rd), nil
}

func printOut(v any) {
	enc := json.NewEncoder(os.Stdout)
	enc.SetEscapeHTML(false)
	_ = enc.Encode(v)
}

func main() {
	host := flag.String("host", "192.168.0.1:8080", "目标 goform 地址 (默认 192.168.0.1:8080)")
	pass := flag.String("pass", "", "管理密码明文 (必填)")
	timeoutSec := flag.Int("t", 5, "超时秒数")
	withAD := flag.Bool("ad", true, "是否计算 AD (默认 true)")
	flag.Parse()

	if strings.TrimSpace(*pass) == "" {
		printOut(map[string]any{"success": false, "error": "missing -pass"})
		return
	}

	h := strings.TrimSpace(*host)
	if h == "" {
		h = "192.168.0.1:8080"
	}
	if !strings.Contains(h, "://") {
		h = "http://" + h
	}
	baseURL, err := url.Parse(h)
	if err != nil || baseURL.Host == "" {
		printOut(map[string]any{"success": false, "error": "bad host"})
		return
	}
	base := strings.TrimRight(baseURL.String(), "/")

	client := &http.Client{Timeout: time.Duration(*timeoutSec) * time.Second}

	// mimic browser headers
	commonHeaders := map[string]string{
		"User-Agent": "Mozilla/5.0",
		"Referer":    base + "/index.html",
		"Origin":     base,
		"Host":       baseURL.Host,
	}

	cookie, err := login(client, base, *pass, commonHeaders)
	if err != nil {
		printOut(map[string]any{"success": false, "error": err.Error()})
		return
	}

	out := map[string]any{
		"success": true,
		"cookie":  cookie,
	}

	if *withAD {
		ad, err := processAD(client, base, cookie, commonHeaders)
		if err != nil {
			printOut(map[string]any{"success": false, "error": err.Error()})
			return
		}
		out["ad"] = ad
	}

	printOut(out)
}

