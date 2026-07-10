# AWS VM Baseline - 2026-07-08

This document records the raw EC2 host baseline before interpreting RTB application performance.

## Summary

| Item | Value |
|---|---|
| Collected at | 2026-07-08T10:38:39Z |
| Instance type | m7i-flex.large |
| Availability zone | ap-northeast-2a |
| Instance id | i-02f95d00ce0680b76 |
| AMI id | ami-0b16c9b40a3a70d6d |
| Kernel | 6.1.176-220.360.amzn2023.x86_64 |
| vCPU count | 2 |

## Interpretation Notes

- Treat this as a host baseline, not an RTB application benchmark.
- Use it to explain whether later SSP/DSP limits are likely CPU, memory, disk, socket, file descriptor, or thread related.
- Do not compare results across regions, AMIs, kernel versions, or instance families without recording those differences.

## EC2 Identity Document

Command:

```bash
token=$(curl -fsS -m 2 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 21600' 2>/dev/null || true); if [ -n "$token" ]; then curl -fsS -m 2 -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/dynamic/instance-identity/document; else echo 'unavailable'; fi
```

Output:

```text
{
  "accountId" : "333982363617",
  "architecture" : "x86_64",
  "availabilityZone" : "ap-northeast-2a",
  "billingProducts" : null,
  "devpayProductCodes" : null,
  "marketplaceProductCodes" : null,
  "imageId" : "ami-0b16c9b40a3a70d6d",
  "instanceId" : "i-02f95d00ce0680b76",
  "instanceType" : "m7i-flex.large",
  "kernelId" : null,
  "pendingTime" : "2026-07-08T10:38:18Z",
  "privateIp" : "172.31.0.166",
  "ramdiskId" : null,
  "region" : "ap-northeast-2",
  "version" : "2017-09-30"
}
```

## OS And Kernel

Command:

```bash
uname -a; printf '\n'; cat /etc/os-release 2>/dev/null || true; printf '\n'; uptime
```

Output:

```text
Linux ip-172-31-0-166.ap-northeast-2.compute.internal 6.1.176-220.360.amzn2023.x86_64 #1 SMP PREEMPT_DYNAMIC Thu Jul  2 14:45:57 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux

NAME="Amazon Linux"
VERSION="2023"
ID="amzn"
ID_LIKE="fedora"
VERSION_ID="2023"
PLATFORM_ID="platform:al2023"
PRETTY_NAME="Amazon Linux 2023.12.20260706"
ANSI_COLOR="0;33"
CPE_NAME="cpe:2.3:o:amazon:amazon_linux:2023"
HOME_URL="https://aws.amazon.com/linux/amazon-linux-2023/"
DOCUMENTATION_URL="https://docs.aws.amazon.com/linux/"
SUPPORT_URL="https://aws.amazon.com/premiumsupport/"
BUG_REPORT_URL="https://github.com/amazonlinux/amazon-linux-2023"
VENDOR_NAME="AWS"
VENDOR_URL="https://aws.amazon.com/"
SUPPORT_END="2029-06-30"

 10:38:39 up 0 min,  0 users,  load average: 0.00, 0.00, 0.00

```

## CPU Topology

Command:

```bash
lscpu 2>/dev/null || true
```

Output:

```text
Architecture:                            x86_64
CPU op-mode(s):                          32-bit, 64-bit
Address sizes:                           46 bits physical, 48 bits virtual
Byte Order:                              Little Endian
CPU(s):                                  2
On-line CPU(s) list:                     0,1
Vendor ID:                               GenuineIntel
Model name:                              Intel(R) Xeon(R) Platinum 8488C
CPU family:                              6
Model:                                   143
Thread(s) per core:                      2
Core(s) per socket:                      1
Socket(s):                               1
Stepping:                                8
BogoMIPS:                                4800.00
Flags:                                   fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ss ht syscall nx pdpe1gb rdtscp lm constant_tsc rep_good nopl xtopology nonstop_tsc cpuid tsc_known_freq pni pclmulqdq ssse3 fma cx16 pdcm pcid sse4_1 sse4_2 x2apic movbe popcnt tsc_deadline_timer aes xsave avx f16c rdrand hypervisor lahf_lm abm 3dnowprefetch cpuid_fault invpcid_single ssbd ibrs ibpb stibp ibrs_enhanced fsgsbase tsc_adjust bmi1 avx2 smep bmi2 erms invpcid avx512f avx512dq rdseed adx smap avx512ifma clflushopt clwb avx512cd sha_ni avx512bw avx512vl xsaveopt xsavec xgetbv1 xsaves avx_vnni avx512_bf16 wbnoinvd ida arat avx512vbmi umip pku ospke waitpkg avx512_vbmi2 gfni vaes vpclmulqdq avx512_vnni avx512_bitalg tme avx512_vpopcntdq rdpid cldemote movdiri movdir64b md_clear serialize amx_bf16 avx512_fp16 amx_tile amx_int8 flush_l1d arch_capabilities
Hypervisor vendor:                       KVM
Virtualization type:                     full
L1d cache:                               48 KiB (1 instance)
L1i cache:                               32 KiB (1 instance)
L2 cache:                                2 MiB (1 instance)
L3 cache:                                105 MiB (1 instance)
NUMA node(s):                            1
NUMA node0 CPU(s):                       0,1
Vulnerability Gather data sampling:      Not affected
Vulnerability Indirect target selection: Not affected
Vulnerability Itlb multihit:             Not affected
Vulnerability L1tf:                      Not affected
Vulnerability Mds:                       Not affected
Vulnerability Meltdown:                  Not affected
Vulnerability Mmio stale data:           Not affected
Vulnerability Reg file data sampling:    Not affected
Vulnerability Retbleed:                  Not affected
Vulnerability Spec rstack overflow:      Not affected
Vulnerability Spec store bypass:         Mitigation; Speculative Store Bypass disabled via prctl
Vulnerability Spectre v1:                Mitigation; usercopy/swapgs barriers and __user pointer sanitization
Vulnerability Spectre v2:                Mitigation; Enhanced / Automatic IBRS; IBPB conditional; PBRSB-eIBRS SW sequence; BHI BHI_DIS_S
Vulnerability Srbds:                     Not affected
Vulnerability Tsa:                       Not affected
Vulnerability Tsx async abort:           Not affected
Vulnerability Vmscape:                   Not affected

```

## Memory

Command:

```bash
free -h 2>/dev/null || true; printf '\n'; cat /proc/meminfo 2>/dev/null | sed -n '1,30p' || true
```

Output:

```text
               total        used        free      shared  buff/cache   available
Mem:           7.6Gi       271Mi       7.1Gi       0.0Ki       243Mi       7.1Gi
Swap:             0B          0B          0B

MemTotal:        7970348 kB
MemFree:         7442152 kB
MemAvailable:    7458476 kB
Buffers:            3172 kB
Cached:           220904 kB
SwapCached:            0 kB
Active:            76504 kB
Inactive:         323932 kB
Active(anon):        488 kB
Inactive(anon):   176376 kB
Active(file):      76016 kB
Inactive(file):   147556 kB
Unevictable:           0 kB
Mlocked:               0 kB
SwapTotal:             0 kB
SwapFree:              0 kB
Zswap:                 0 kB
Zswapped:              0 kB
Dirty:             20380 kB
Writeback:             0 kB
AnonPages:        176412 kB
Mapped:            64116 kB
Shmem:               624 kB
KReclaimable:      25672 kB
Slab:              60228 kB
SReclaimable:      25672 kB
SUnreclaim:        34556 kB
KernelStack:        2656 kB
PageTables:         2648 kB
SecPageTables:         0 kB

```

## Disk Layout

Command:

```bash
lsblk -o NAME,TYPE,SIZE,FSTYPE,MODEL,MOUNTPOINTS 2>/dev/null || true; printf '\n'; df -hT 2>/dev/null || df -h
```

Output:

```text
NAME          TYPE SIZE FSTYPE MODEL                      MOUNTPOINTS
nvme0n1       disk  20G        Amazon Elastic Block Store 
├─nvme0n1p1   part  20G xfs                               /
├─nvme0n1p127 part   1M                                   
└─nvme0n1p128 part  10M vfat                              /boot/efi

Filesystem       Type      Size  Used Avail Use% Mounted on
devtmpfs         devtmpfs  4.0M     0  4.0M   0% /dev
tmpfs            tmpfs     3.9G     0  3.9G   0% /dev/shm
tmpfs            tmpfs     1.6G  612K  1.6G   1% /run
/dev/nvme0n1p1   xfs        20G  1.7G   19G   9% /
tmpfs            tmpfs     3.9G   16K  3.9G   1% /tmp
/dev/nvme0n1p128 vfat       10M  1.3M  8.7M  13% /boot/efi
tmpfs            tmpfs     779M     0  779M   0% /run/user/1000

```

## Kernel Limits For Load Tests

Command:

```bash
ulimit -a; printf '\n'; sysctl fs.file-max kernel.threads-max kernel.pid_max net.core.somaxconn net.ipv4.ip_local_port_range net.ipv4.tcp_tw_reuse 2>/dev/null || true; printf '\n'; ss -s 2>/dev/null || true
```

Output:

```text
real-time non-blocking time  (microseconds, -R) unlimited
core file size              (blocks, -c) unlimited
data seg size               (kbytes, -d) unlimited
scheduling priority                 (-e) 0
file size                   (blocks, -f) unlimited
pending signals                     (-i) 30446
max locked memory           (kbytes, -l) unlimited
max memory size             (kbytes, -m) unlimited
open files                          (-n) 65535
pipe size                (512 bytes, -p) 8
POSIX message queues         (bytes, -q) 819200
real-time priority                  (-r) 0
stack size                  (kbytes, -s) 10240
cpu time                   (seconds, -t) unlimited
max user processes                  (-u) unlimited
virtual memory              (kbytes, -v) unlimited
file locks                          (-x) unlimited

fs.file-max = 9223372036854775807
kernel.threads-max = 61869
kernel.pid_max = 4194304
net.core.somaxconn = 4096
net.ipv4.ip_local_port_range = 32768	60999
net.ipv4.tcp_tw_reuse = 2

Total: 142
TCP:   6 (estab 3, closed 1, orphaned 0, timewait 1)

Transport Total     IP        IPv6
RAW	  1         0         1        
UDP	  4         2         2        
TCP	  5         4         1        
INET	  10        6         4        
FRAG	  0         0         0        


```

## Docker Runtime

Command:

```bash
docker version 2>/dev/null || true; printf '\n'; docker compose version 2>/dev/null || true; printf '\n'; docker info 2>/dev/null | sed -n '1,80p' || true
```

Output:

```text



```

## CPU Micro Benchmark

Command:

```bash
if command -v openssl >/dev/null 2>&1; then openssl speed -elapsed -seconds 10 sha256; else echo 'openssl not found'; fi
```

Output:

```text
You have chosen to measure elapsed time instead of user CPU time.
Doing sha256 ops for 10s on 16 size blocks: 69225838 sha256 ops in 10.00s
Doing sha256 ops for 10s on 64 size blocks: 56429743 sha256 ops in 10.01s
Doing sha256 ops for 10s on 256 size blocks: 36414496 sha256 ops in 10.00s
Doing sha256 ops for 10s on 1024 size blocks: 13906326 sha256 ops in 10.00s
Doing sha256 ops for 10s on 8192 size blocks: 2061636 sha256 ops in 10.00s
Doing sha256 ops for 10s on 16384 size blocks: 1044280 sha256 ops in 10.00s
version: 3.5.5
built on: Thu Apr  9 00:00:00 2026 UTC
options: bn(64,64)
compiler: gcc -fPIC -pthread -m64 -Wa,--noexecstack -O2 -ftree-vectorize -flto=auto -ffat-lto-objects -fexceptions -g -grecord-gcc-switches -pipe -Wall -Werror=format-security -Wp,-D_FORTIFY_SOURCE=2 -Wp,-D_GLIBCXX_ASSERTIONS -specs=/usr/lib/rpm/redhat/redhat-hardened-cc1 -fstack-protector-strong -specs=/usr/lib/rpm/redhat/redhat-annobin-cc1  -m64 -march=x86-64-v2 -mtune=generic -fasynchronous-unwind-tables -fstack-clash-protection -fcf-protection -O2 -ftree-vectorize -flto=auto -ffat-lto-objects -fexceptions -g -grecord-gcc-switches -pipe -Wall -Werror=format-security -Wp,-D_FORTIFY_SOURCE=2 -Wp,-D_GLIBCXX_ASSERTIONS -specs=/usr/lib/rpm/redhat/redhat-hardened-cc1 -fstack-protector-strong -specs=/usr/lib/rpm/redhat/redhat-annobin-cc1 -m64 -march=x86-64-v2 -mtune=generic -fasynchronous-unwind-tables -fstack-clash-protection -fcf-protection -Wa,--noexecstack -Wa,--generate-missing-build-notes=yes -specs=/usr/lib/rpm/redhat/redhat-hardened-ld -specs=/usr/lib/rpm/redhat/redhat-annobin-cc1 -DOPENSSL_USE_NODELETE -DL_ENDIAN -DOPENSSL_PIC -DOPENSSL_BUILDING_OPENSSL -DZLIB -DNDEBUG -D_GNU_SOURCE -DPURIFY -DDEVRANDOM="\\"/dev/urandom\\"" -DOPENSSL_PEDANTIC_ZEROIZATION -DREDHAT_FIPS_VENDOR="\\"Amazon Linux 2023 - OpenSSL FIPS Provider\\"" -DREDHAT_FIPS_VERSION="\\"3.5.5-f06cf76f53649b34\\"" -DSYSTEM_CIPHERS_FILE="/etc/crypto-policies/back-ends/opensslcnf.config"
CPUINFO: OPENSSL_ia32cap=0xfffab2035f8bffff:0x1a407f7ef1bf07ab:0x00000030bfc04400:0x0000000000000000:0x0000000000000000
The 'numbers' are in 1000s of bytes per second processed.
type             16 bytes     64 bytes    256 bytes   1024 bytes   8192 bytes  16384 bytes
sha256          110761.34k   360789.57k   932211.10k  1424007.78k  1688892.21k  1710948.35k

```

## Disk Sequential Write

Command:

```bash
rm -f '/tmp/rtb-vm-baseline.bin'; dd if=/dev/zero of='/tmp/rtb-vm-baseline.bin' bs=1M count=1024 conv=fdatasync status=progress
```

Output:

```text
1024+0 records in
1024+0 records out
1073741824 bytes (1.1 GB, 1.0 GiB) copied, 0.225596 s, 4.8 GB/s

```

## Disk Sequential Read

Command:

```bash
dd if='/tmp/rtb-vm-baseline.bin' of=/dev/null bs=1M status=progress; rm -f '/tmp/rtb-vm-baseline.bin'
```

Output:

```text
1024+0 records in
1024+0 records out
1073741824 bytes (1.1 GB, 1.0 GiB) copied, 0.169271 s, 6.3 GB/s

```

## Next Step

Run the RTB target-system baseline only after this host baseline is captured:

```bash
docker compose -f docker-compose.perf.yml up --build -d ssp prometheus
RPS=100 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-capacity
```

For capacity measurement, prefer a separate load-generator EC2 in the same region and call the target EC2 private IP.
