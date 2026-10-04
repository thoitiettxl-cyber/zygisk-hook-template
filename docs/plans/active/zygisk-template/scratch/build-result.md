# BUILD RESULT — B2 (worker builder, chiến dịch zygisk-hook-template)

Ngày: 2026-10-04. Không chạy Gradle (sandbox không chạy được daemon);
không commit (coordinator gom batch review sau).

## Submodule
- `git submodule add https://github.com/aerath-stuff/AndroidVMTools external/AndroidVMTools`
  → checkout pin `6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe` (detached HEAD, khớp gitlink HMA).
- `git submodule status`: ` 6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe external/AndroidVMTools (6fa2d5c)`
  (dấu space đầu = chưa commit — đúng, chờ coordinator commit batch).

## File đã tạo (11 file + submodule)

| File | Nội dung |
|---|---|
| `settings.gradle.kts` | pluginManagement + dependencyResolutionManagement (JitPack + google/mavenCentral); versionCatalog `androidvmtools` từ `external/AndroidVMTools/gradle/libs.versions.toml` qua `files(...)` như HMA |
| `build.gradle.kts` | root tối thiểu: khai báo plugin agp/kotlin/zygoteloader `apply false` |
| `gradle/libs.versions.toml` | agp 8.13.2, kotlin 2.3.21, zygoteloader `2a2160f`, r8_annotations `v1.0.1`; plugin id `com.github.aerath-stuff.ZygoteLoader` |
| `zygote/build.gradle.kts` | plugins agp app + kotlin + zygoteloader; sourceSets java srcDirs → AVT; zygisk DSL (`packages("com.viettel.appviettel")`, id/name/author/description, entrypoint `com.example.zygisktemplate.ZygoteEntry`, archiveName, updateJson `""` placeholder, isAddVariantToArchiveName = true); deps R8Annotations + panama core/unsafe/llvm (`androidvmtools.*`) + sun-cleaner |
| `zygote/src/main/AndroidManifest.xml` | `<manifest />` rỗng như HMA (plugin tự sinh phần cần thiết) |
| `zygote/.../ZygoteEntry.java` | port HMA: class + premain()/main() public, @DoNotObfuscate(Type)/@DoNotShrink(Type); main() log package + gọi TammiStealth.init() trong try/catch fail-open |
| `zygote/.../HookPolicy.kt` | port BulkHooker+ZLUtils: hookBefore/hookAfter (fail-open try/catch từng callback), circuit breaker hooksWasCrashed, kill-switch disabledHooks, ReturnValue, setReturnValue extension (accessor().setValue(RETURN_VALUE_IDX,…), skip void), invokeOriginal qua Transformers.invokeExactNoChecks (Tiramisu+; SDK<33 fail-open skip + log) |
| `zygote/.../hooks/TammiStealth.kt` | hook HookDetector.isHooked(Context):boolean → luôn false, không gọi bản gốc; xử lý class chưa load bằng Class.forName trước, fallback ClassLoaderHooks.hookFindClass(loader) đúng signature đã verify |
| `README.md` | tổng quan + pin + cấu trúc + quick start + 4 quy tắc an toàn + điểm chưa verify (tiếng Việt) |
| `docs/ARCHITECTURE.md` | luồng zygisk→entrypoint→ART hook, mô tả từng lớp, nguồn HMA-OSS, giải thích vì sao không có frame "xposed" |
| `docs/TAMMI-EXAMPLE.md` | worked example: root cause Tammi, code path, checklist test máy thật, điểm chưa verify |

## Code defensively ở đâu (cho reviewer)
1. `HookPolicy.invokeOriginal`: chỉ đọc `accessor().getValue(RETURN_VALUE_IDX)` khi
   `returnType != void` (HMA đọc unconditional — hành vi trên void chưa verify).
2. `hookAfter`: guard void tương tự trước khi đọc result.
3. `TammiStealth.init`: `hookFindClass` callback bọc try/catch — lỗi vẫn trả class về bình thường.
4. `ZygoteEntry.main`: toàn bộ init trong try/catch — fail-open, không crash process target.
5. `updateJson = ""` + 3 `// TODO verify on device` (packages match, main() timing vs checkSecurity, SDK<33 original-call path).
6. Chỉ dùng API có trong bảng VERIFIED của verify-result.md; `EmulatedStackFrame`/
   `Transformers` pin đúng PanamaPort `c0d8fa4`, không suy thêm method.
