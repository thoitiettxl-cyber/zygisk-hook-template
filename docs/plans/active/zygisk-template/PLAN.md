# PLAN — zygisk-hook-template

Repo mẫu dựng Zygisk module thuần Java theo kiến trúc HMA-OSS (Boss duyệt 2026-10-04).
Mục đích trước mắt: làm vehicle cho stealth Tammi (hook `HookDetector.isHooked` → false)
mà không để lại frame "xposed" trong stack trace.

## Quyết định đã chốt (Boss)
- Stack: **aerath-stuff** — ZygoteLoader (Gradle plugin) + AndroidVMTools (ART hook toolkit).
- AndroidVMTools pin tại commit `6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe` (submodule `external/AndroidVMTools`).
- ZygoteLoader plugin version `2a2160f` (JitPack, cùng version HMA-OSS dùng).
- HanSoBored/Zygisk-Loader để dành phase 2 (hot-swap), không dùng ở đây.

## Deliverable
```
zygisk-hook-template/
├── README.md                      — tổng quan + quick start + build
├── docs/ARCHITECTURE.md           — kiến trúc (lấy mẫu HMA-OSS, ghi nguồn)
├── docs/TAMMI-EXAMPLE.md          — ví dụ worked: stealth Tammi
├── settings.gradle.kts            — JitPack + version catalog
├── build.gradle.kts               — root
├── gradle/libs.versions.toml
├── external/AndroidVMTools/       — submodule, pin 6fa2d5c
└── zygote/
    ├── build.gradle.kts           — zygisk {} DSL, deps
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/example/zygisktemplate/
            ├── ZygoteEntry.java    — premain/main entrypoint
            ├── HookPolicy.kt       — policy kiểu BulkHooker (fail-open, circuit breaker, kill-switch)
            └── hooks/TammiStealth.kt — ví dụ: hook isHooked → false
```

## Batch
- **B1 — Verify vật liệu:** đối chiếu mọi API/claim trong template với source cache
  (HMA-OSS, AndroidVMTools@6fa2d5c, ZygoteLoader). Không đoán — cái gì không có trong
  source thì ghi rõ "chưa verify".
- **B2 — Scaffold:** dựng toàn bộ file theo deliverable, commit local.
- **B3 — Review fresh-eyes:** reviewer khác soi diff theo rubric (API có thật trong
  source không? policy fail-open đúng không? docs khớp code không?), verdict
  correct/needs attention.

## Nguồn verify (đường dẫn tuyệt đối)
- HMA-OSS: `~/.cache/checkouts/github.com/frknkrc44/HMA-OSS`
- AndroidVMTools@6fa2d5c: `~/.cache/checkouts/github.com/aerath-stuff/AndroidVMTools`
- ZygoteLoader: `~/.cache/checkouts/github.com/aerath-stuff/ZygoteLoader`
- REPORT Tammi: `~/workspace/research/tammi-antitamper/REPORT.md`

## Ràng buộc
- Mọi commit local; KHÔNG push/PR khi chưa có lệnh Boss.
- Không claim API tồn tại nếu chưa grep thấy trong source cache.
- Ghi rõ trong docs điểm chưa verify trên máy thật: thời điểm `main()` chạy so với
  `checkSecurity()` của Tammi (Application init).
