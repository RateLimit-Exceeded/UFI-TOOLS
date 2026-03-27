package main

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
)

// Reverse-engineered from the bundled `sha256` binary (arm64, UPX-packed).
//
// Behaviour:
//   - sha256 <string>  => prints lowercase hex SHA-256 of the argument
//   - otherwise prints: "Usage: sha256 <string>" to stderr and exits with code 2
func main() {
	if len(os.Args) != 2 {
		_, _ = fmt.Fprintln(os.Stderr, "Usage: sha256 <string>")
		os.Exit(2)
	}

	sum := sha256.Sum256([]byte(os.Args[1]))
	_, _ = fmt.Fprintln(os.Stdout, hex.EncodeToString(sum[:]))
}

