# zygisk-hook-template

Template Zygisk module **thuần Java** dựng ART hook theo kiến trúc **HMA-OSS**
(frknkrc44/HMA-OSS), dùng stack **aerath-stuff**:
[ZygoteLoader](https://github.com/aerath-stuff/ZygoteLoader) (Gradle plugin) +
[AndroidVMTools](https://github.com/aerath-stuff/AndroidVMTools) (ART hook toolkit).

Mục đích trước mắt: làm **vehicle cho stealth Tammi** — hook
`HookDetector.isHooked()` → `false` mà **không để lại frame "xposed"** nào trong
stack trace (mọi module LSPosed trong scope đều bị Tammi bắt — xem
`docs/TAMMI-EXAMPLE.md`).

## Pin đã chốt

| Thành phần | Pin | Nguồn |
|---|---|---|
| ZygoteLoader plugin | `2a2160f` (JitPack) | cùng version HMA-OSS dùng |
| AndroidVMTools | submodule `external/AndroidVMTools` @ `6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe` | HMA `.gitmodules` gitlink |
| PanamaPort (Core/Unsafe/LLVM) | `c0d8fa4` (JitPack) | `AndroidVMTools/gradle/libs.versions.toml` |
| R8Annotations / SunCleanerStub | `v1.0.1` | cùng file trên |

## Cấu trúc

```
├── settings.gradle.kts        — JitPack + version catalog androidvmtools (từ submodule)
├── build.gradle.kts           — root, chỉ khai báo plugin cho module con
├── gradle/libs.versions.toml
├── external/AndroidVMTools/   — submodule (pin 6fa2d5c)
├── docs/
│   ├── ARCHITECTURE.md        — kiến trúc (nguồn HMA-OSS)
│   └── TAMMI-EXAMPLE.md       — ví dụ worked: stealth Tammi
└── zygote/
    ├── build.gradle.kts       — zygisk {} DSL, deps, sourceSets AVT
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/example/zygisktemplate/
            ├── ZygoteEntry.java    — entrypoint: public static premain()/main()
            ├── HookPolicy.kt       — policy kiểu BulkHooker (fail-open, circuit breaker, kill-switch)
            └── hooks/TammiStealth.kt — hook isHooked → false
```

## Quick start

```bash
git clone --recurse-submodules <repo-url>
cd zygisk-hook-template
./gradlew :zygote:assembleRelease
# zip Magisk module ở zygote/build/outputs/magisk/
```

## Quy tắc an toàn của template

1. **Fail-open**: mọi callback hook và init đều try/catch `Throwable` — hook lỗi thì
   app target vẫn chạy bình thường, không bao giờ crash process người ta.
2. **Circuit breaker** (`HookPolicy.hooksWasCrashed`): một lần `Hooks.hook()` crash
   thì từ chối mọi hook tiếp theo.
3. **Kill-switch** (`HookPolicy.disabledHooks`): liệt kê hook theo
   `class->method(argCount)` để skip khi cần.
4. **Entrypoint bắt buộc public**: ZygoteLoader runtime tìm `premain`/`main` bằng
   `Class.getMethod()` — method private/package-private sẽ không thấy.

## Chưa verify (cần máy thật)

- Thời điểm `main()` (post-specialize) so với lần đầu Tammi gọi `checkSecurity()`.
- `packages("com.viettel.appviettel")` có match đúng process Tammi không.
- Định nghĩa gốc `EmulatedStackFrame`/`Transformers` (PanamaPort:Unsafe) — template
  chỉ dùng đúng API đã thấy qua usage của HMA/AVT, pin đúng `c0d8fa4`.

Chi tiết verify: `docs/plans/active/zygisk-template/scratch/verify-result.md`.
