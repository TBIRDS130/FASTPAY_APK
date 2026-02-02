#!/bin/bash
# Paste and run this on your Hostinger VPS (terminal) to check system and explore.
# Usage: copy the commands below, paste in SSH session, or: bash explore-hostinger-vps.sh

echo "=== SYSTEM ==="
uname -a
echo ""
echo "=== USER / HOME ==="
whoami
id
echo "HOME=$HOME"
echo ""
echo "=== SHELL / PATH ==="
echo "SHELL=$SHELL"
echo "PATH=$PATH"
echo ""
echo "=== PACKAGE MANAGER? ==="
command -v apt && echo "apt: yes" || echo "apt: no"
command -v yum && echo "yum: yes" || echo "yum: no"
command -v sudo && echo "sudo: yes" || echo "sudo: no"
echo ""
echo "=== INSTALLED ==="
command -v git  && git --version  || echo "git: not found"
command -v java && java -version 2>&1 || echo "java: not found"
command -v javac && javac -version 2>&1 || echo "javac: not found"
echo ""
echo "=== DISK ==="
df -h
echo ""
echo "=== MEMORY ==="
free -h 2>/dev/null || true
echo ""
echo "=== CURRENT DIR / REPO ==="
pwd
ls -la
echo ""
echo "=== FASTPAY_APK (if present) ==="
ls -la FASTPAY_APK 2>/dev/null || ls -la ~/FASTPAY_APK 2>/dev/null || echo "FASTPAY_APK not in current dir or ~"
echo ""
echo "=== OS RELEASE ==="
cat /etc/os-release 2>/dev/null || true
