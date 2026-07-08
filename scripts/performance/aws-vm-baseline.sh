#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${OUT_DIR:-docs/performance}"
DATE="${DATE:-$(date +%F)}"
OUT_FILE="${OUT_FILE:-${OUT_DIR}/${DATE}-aws-vm-baseline.md}"
DISK_MB="${DISK_MB:-1024}"
CPU_SECONDS="${CPU_SECONDS:-10}"
BENCH_FILE="${BENCH_FILE:-/tmp/rtb-vm-baseline.bin}"

mkdir -p "$OUT_DIR"

section() {
  printf '\n## %s\n\n' "$1" >> "$OUT_FILE"
}

code_block() {
  printf '```text\n' >> "$OUT_FILE"
  "$@" >> "$OUT_FILE" 2>&1 || true
  printf '\n```\n' >> "$OUT_FILE"
}

run_section() {
  local title="$1"
  local command="$2"
  section "$title"
  printf 'Command:\n\n```bash\n%s\n```\n\nOutput:\n\n' "$command" >> "$OUT_FILE"
  code_block bash -lc "$command"
}

metadata() {
  local path="$1"
  local token
  token="$(curl -fsS -m 2 -X PUT \
    "http://169.254.169.254/latest/api/token" \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 21600" 2>/dev/null || true)"

  if [ -z "$token" ]; then
    echo "unavailable"
    return
  fi

  curl -fsS -m 2 \
    -H "X-aws-ec2-metadata-token: ${token}" \
    "http://169.254.169.254/latest/${path}" 2>/dev/null || echo "unavailable"
}

cat > "$OUT_FILE" <<EOF
# AWS VM Baseline - ${DATE}

This document records the raw EC2 host baseline before interpreting RTB application performance.

## Summary

| Item | Value |
|---|---|
| Collected at | $(date -u +"%Y-%m-%dT%H:%M:%SZ") |
| Instance type | $(metadata meta-data/instance-type) |
| Availability zone | $(metadata meta-data/placement/availability-zone) |
| Instance id | $(metadata meta-data/instance-id) |
| AMI id | $(metadata meta-data/ami-id) |
| Kernel | $(uname -r) |
| vCPU count | $(getconf _NPROCESSORS_ONLN 2>/dev/null || nproc 2>/dev/null || echo unknown) |

## Interpretation Notes

- Treat this as a host baseline, not an RTB application benchmark.
- Use it to explain whether later SSP/DSP limits are likely CPU, memory, disk, socket, file descriptor, or thread related.
- Do not compare results across regions, AMIs, kernel versions, or instance families without recording those differences.
EOF

run_section "EC2 Identity Document" "token=\$(curl -fsS -m 2 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 21600' 2>/dev/null || true); if [ -n \"\$token\" ]; then curl -fsS -m 2 -H \"X-aws-ec2-metadata-token: \$token\" http://169.254.169.254/latest/dynamic/instance-identity/document; else echo 'unavailable'; fi"
run_section "OS And Kernel" "uname -a; printf '\\n'; cat /etc/os-release 2>/dev/null || true; printf '\\n'; uptime"
run_section "CPU Topology" "lscpu 2>/dev/null || true"
run_section "Memory" "free -h 2>/dev/null || true; printf '\\n'; cat /proc/meminfo 2>/dev/null | sed -n '1,30p' || true"
run_section "Disk Layout" "lsblk -o NAME,TYPE,SIZE,FSTYPE,MODEL,MOUNTPOINTS 2>/dev/null || true; printf '\\n'; df -hT 2>/dev/null || df -h"
run_section "Kernel Limits For Load Tests" "ulimit -a; printf '\\n'; sysctl fs.file-max kernel.threads-max kernel.pid_max net.core.somaxconn net.ipv4.ip_local_port_range net.ipv4.tcp_tw_reuse 2>/dev/null || true; printf '\\n'; ss -s 2>/dev/null || true"
run_section "Docker Runtime" "docker version 2>/dev/null || true; printf '\\n'; docker compose version 2>/dev/null || true; printf '\\n'; docker info 2>/dev/null | sed -n '1,80p' || true"
run_section "CPU Micro Benchmark" "if command -v openssl >/dev/null 2>&1; then openssl speed -elapsed -seconds ${CPU_SECONDS} sha256; else echo 'openssl not found'; fi"
run_section "Disk Sequential Write" "rm -f '${BENCH_FILE}'; dd if=/dev/zero of='${BENCH_FILE}' bs=1M count=${DISK_MB} conv=fdatasync status=progress"
run_section "Disk Sequential Read" "dd if='${BENCH_FILE}' of=/dev/null bs=1M status=progress; rm -f '${BENCH_FILE}'"

section "Next Step"
cat >> "$OUT_FILE" <<'EOF'
Run the RTB target-system baseline only after this host baseline is captured:

```bash
docker compose -f docker-compose.perf.yml up --build -d ssp prometheus
RPS=100 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-capacity
```

For capacity measurement, prefer a separate load-generator EC2 in the same region and call the target EC2 private IP.
EOF

echo "Wrote ${OUT_FILE}"
