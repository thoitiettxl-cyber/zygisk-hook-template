# FIX RESULT — pass 2 (worker FIX, chiến dịch zygisk-hook-template)

Ngày: 2026-10-04. Repo: `/home/hatch/workspace/zygisk-hook-template/`.
Áp dụng toàn bộ Fix Queue #1–#7 từ review-result.md (pass 2). Không commit.

## #1 — submodule pin (BLOCKING) ✅
- `external/AndroidVMTools`: `git rev-parse HEAD` trong submodule → `6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe` (đúng pin, không cần checkout lại).
- Chạy `git add external/AndroidVMTools` từ repo root; verify `git ls-files --stage external/AndroidVMTools` → `160000 6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe 0	external/AndroidVMTools` ✅ (pin đúng sẽ đi vào commit).

## #2 — SDK<33 bịa null → từ chối cài hook ✅
- `zygote/src/main/java/com/example/zygisktemplate/HookPolicy.kt:212-219` (applyHook): thêm gate `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)` ở đầu — log lý do + `return false`, không cài hook.
- `HookPolicy.kt:150-164` (invokeOriginal): xóa toàn bộ nhánh `else` bịa `replace=true`/result-null; hàm chỉ còn path Tiramisu+ (applyHook đã gate).
- `docs/ARCHITECTURE.md:42-47`: câu "fail-open bằng cách skip" ở SDK<33 đã sửa → mô tả đúng "từ chối cài hook ngay (log + return false)"; thêm dòng scrub stack trace + synchronizedSet cho khớp code.

## #3 — scrub stack trace trước rethrow ✅
- `HookPolicy.kt:167-195`: port trung thực HMA `ServiceUtils.clearStackTraces` (`containsMultiple` → helper stdlib `containsAny` local); lọc frame chứa `HookPolicy` / `com.v7878` / `com.example.zygisktemplate` và fileName `r8-map-id-` / `dex-id-`; lặp qua `cause` chain. Chỉ dùng stdlib (`Throwable.stackTrace` getter/setter, `String.contains`).
- `HookPolicy.kt:101-105` (hookBefore) và `HookPolicy.kt:136-140` (hookAfter): `value.throwable?.let { clearStackTraces(it); throw it }`.

## #4 — disabledHooks thread-safe ✅
- `HookPolicy.kt:46`: `mutableSetOf<String>()` → `Collections.synchronizedSet(mutableSetOf<String>())`; thêm `import java.util.Collections` (dòng 12).

## #5 — once-guard deferred hook ✅
- `zygote/src/main/java/com/example/zygisktemplate/hooks/TammiStealth.kt:54`: `private val hooked = AtomicBoolean(false)` + `import java.util.concurrent.atomic.AtomicBoolean`.
- `TammiStealth.kt:88-91` (hookIsHooked): `if (!hooked.compareAndSet(false, true))` → log + return, chống double-hook khi `findClass(TARGET_CLASS)` chạy lần 2.

## #6 — dùng describeTarget() ✅
- `TammiStealth.kt:93` (hookIsHooked): resolve method trước hook qua `resolveTargetMethod()` mới (`TammiStealth.kt:112-121`, mirror hierarchy-walk của applyHook).
- `TammiStealth.kt:110-112` path success: log `describeTarget(method)` (vd `...isHooked... -> [Context] : boolean -> always false`) làm bằng chứng on-device đúng overload; bỏ `@Suppress("unused")`.

## #7 — import thừa ✅
- `HookPolicy.kt`: xóa `import java.lang.reflect.Method` (chỉ còn `Executable`; verify grep không còn usage).

## #8 — giữ nguyên
- `zygote/build.gradle.kts` `updateJson = ""` vẫn là TODO như review chỉ định — không động.

## Ghi chú cho coordinator
- `.gitmodules` + gitlink `external/AndroidVMTools` đã staged đúng pin `6fa2d5c`; các file Kotlin/docs còn untracked (repo mới init) — commit sẽ gom hết.
- Không chạy được compile (Gradle daemon không chạy trong sandbox — xem ~/TOOLS.md); cửa ải thật vẫn là compile trên CI/máy build.
- Không còn mục nào trong fix queue chưa xử lý — #1–#7 done, #8 giữ nguyên theo chỉ định.
