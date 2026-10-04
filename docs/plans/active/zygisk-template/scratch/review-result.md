# REVIEW RESULT — B3 (reviewer fresh-eyes, chiến dịch zygisk-hook-template)

Ngày: 2026-10-04. Phạm vi: toàn bộ working-tree/staged của
`/home/hatch/workspace/zygisk-hook-template/` (11 file + submodule), đối chiếu
nguồn gốc: `~/.cache/checkouts/github.com/{aerath-stuff/AndroidVMTools,aerath-stuff/ZygoteLoader,frknkrc44/HMA-OSS}`
và `~/workspace/research/tammi-antitamper/REPORT.md`. Không sửa file, không commit.

## Verdict: NEEDS ATTENTION

1 lỗi **blocking** (submodule pin sai trong index — vi phạm invariant pin của chiến dịch),
7 lỗi **non-blocking** + 2 ghi chú informational. Mọi API trong code đều có evidence
trong source (không có API bịa), fail-open cơ bản đúng, docs khớp code.

---

## Bảng rubric

| # | Mục | Kết quả | Evidence |
|---|---|---|---|
| 1 | API có thật trong source? | ✅ **PASS** | `Hooks.EntryPointType.DIRECT` — enum nested `Hooks.java:173` (AVT). `Hooks.hook(Executable, EntryPointType, HookTransformer, EntryPointType)` — `Hooks.java:298-299` (trả `Pair<Long,Long>`). `ClassLoaderHooks.hookFindClass(ClassLoader, FindClassI)` — `ClassLoaderHooks.java:89`; `FindClassI`/`FindClassBackup` đều `@FunctionalInterface` → SAM conversion Kotlin hợp lệ; `original.findClass(loader, name)` khớp signature `FindClassBackup`. `com.v7878.zygisk.ZygoteLoader.getPackageName()` — tồn tại ở ZL runtime `ZygoteLoader.java:23-24` (B1 không liệt kê nhưng có thật — ZygoteEntry dùng đúng). `Transformers.invokeExactNoChecks` — usage HMA `BulkHooker.kt:225`. `RETURN_VALUE_IDX` import từ `com.v7878.unsafe.invoke.EmulatedStackFrame` — khớp HMA `ZLUtils.kt:6`. DSL fields + `packages(String...)` + annotations `com.v7878.r8.annotations.*` — khớp B1. Không phát hiện API bịa. |
| 2 | `setReturnValue` port đúng? | ✅ **PASS** — premise của rubric sai, builder làm đúng | HMA `ZLUtils.kt:104-108` **CÓ** guard `if (type().returnType() != Void::class.java)` — builder port nguyên văn, không phải "thêm guard thừa". Guard này đúng và cần thiết. (Guard void phụ thêm của builder ở `hookAfter` read và `invokeOriginal` — HMA đọc unconditional trên Tiramisu+ — là deviation an toàn, chấp nhận được.) |
| 3 | Fail-open đúng? | ⚠️ **PASS với caveat** (2 lỗ hổng xem Fix Queue #2, #3) | `ZygoteEntry.main` bọc toàn bộ init trong try/catch ✅. `hookBefore`/`hookAfter`: callback và `invokeOriginal` đều try/catch riêng; rethrow `value.throwable` là đúng semantics (giữ exception gốc của app, không phải crash do hook) ✅. `applyHook`: `Class.forName` + `Hooks.hook` bọc try/catch, circuit breaker `hooksWasCrashed` ✅, kill-switch `disabledHooks` ✅. `TammiStealth.init`: `hookFindClass` bọc try/catch; deferred callback vẫn trả `clazz` khi hook lỗi ✅. |
| 4 | Entrypoint contract | ✅ **PASS** | `public static void premain()` + `public static void main()` ✅; class annotate `@DoNotObfuscateType`/`@DoNotShrinkType`, methods annotate `@DoNotObfuscate`/`@DoNotShrink` ✅ (khớp HMA). Runtime dùng `getMethod()` → public là bắt buộc — code đúng. `throws Throwable` không ảnh hưởng lookup. |
| 5 | `zygisk {}` DSL | ✅ **PASS** | `packages("com.viettel.appviettel")` hợp lệ (`packages(String...)`, ZL `ZygoteLoaderExtension.java:~24`). `id/name/author/description/entrypoint/archiveName/updateJson/isAddVariantToArchiveName` đều là field đã verify; `isAddVariantToArchiveName = true` hợp lệ qua Kotlin boolean-accessor. `updateJson = ""` là placeholder đã gắn TODO trong code (chưa biết plugin có chấp nhận chuỗi rỗng — ghi ở Fix Queue #8). |
| 6 | Submodule pin | ❌ **FAIL — BLOCKING** | Xem Fix Queue #1. |
| 7 | Docs khớp code? | ✅ **PASS** | README: cấu trúc file, bảng pin, quick start, 4 quy tắc an toàn đều khớp code ✅; mục "Chưa verify" liệt kê đúng 3 điểm ✅. ARCHITECTURE: luồng, mô tả từng lớp, guard void, thiếu path SDK<33, lý do "không frame xposed" đều chính xác ✅. TAMMI-EXAMPLE: root cause, code path 3 bước, checklist logcat, điểm chưa verify đều khớp REPORT Tammi + code ✅. Một câu trong ARCHITECTURE ("template fail-open bằng cách skip" ở SDK<33) mô tả sai hành vi thật (xem Fix Queue #2). |
| 8 | `TammiStealth` target/logic | ✅ **PASS** | Class `vn.com.viettel.vds.vanillalib.app.security.HookDetector`, method `isHooked`, `parameterTypes = [Context]` → khớp descriptor `(Landroid/content/Context;)Z` trong REPORT ✅. `hookBefore` + `replace=true` + `result=false`, không gọi bản gốc ✅ — đúng phương án B của REPORT (bản gốc quét stack, gọi là tự tố cáo). |
| 9 | `describeTarget()` unused | 💡 **Ý kiến** (non-blocking) | Xem Fix Queue #6: nên DÙNG nó (log signature method đã resolve ở path success — thành công cụ verify trên máy thật) thay vì để dead code hoặc xóa. |
| 10 | `hookFindClass` deferral | ✅ **PASS với khuyến nghị** (non-blocking) | Deadlock: callback chạy trong `findClass` (giữ class-loading lock) nhưng chỉ gọi `Class.forName` (no-op, class vừa define) + `Hooks.hook` (native ART ops, không load class mới) → nguy cơ deadlock THẤP. Reentrancy: hook bắn cho MỌI class qua loader (overhead 1 string-compare, chấp nhận được); có nguy cơ double-hook nếu `findClass(TARGET_CLASS)` chạy 2 lần → nên có once-guard (Fix Queue #5). |

---

## Fix Queue

### BLOCKING

**#1 — `external/AndroidVMTools`: gitlink trong index ≠ pin yêu cầu**
`git ls-files --stage external/AndroidVMTools` → `160000 56dff5b6f94a64f2599726331332bf37a2ef00b4`
("Merge QPR3 Beta 1 fix", commit mới hơn), trong khi working-tree đã checkout đúng
`6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe` và `git submodule status` báo prefix `+`
(mismatch). Khi coordinator commit, pin được ghi vào lịch sử sẽ là **56dff5b**,
vi phạm invariant pin `6fa2d5c` của chiến dịch. (build-result.md còn báo sai: ghi
"dấu space đầu" trong khi thực tế là `+` — fable-check của builder không đạt ở điểm này.)
Đề xuất sửa (coordinator, không phải worker): sau khi confirm working-tree ở
`6fa2d5c`, chạy `git add external/AndroidVMTools` từ repo root để stage đúng gitlink,
rồi verify lại `git ls-files --stage` ra `6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe`
trước khi commit.

### NON-BLOCKING

**#2 — `HookPolicy.kt` (~dòng 155-166, `invokeOriginal` nhánh SDK<33): path "fail-open" bịa kết quả null**
Khi `SDK_INT < 33`, code set `value.replace = true` mà không set `value.result`
(null), rồi `hookBefore`/`hookAfter` gọi `frame.setReturnValue(null)` cho method
non-void → bịa return null (với primitive như boolean có thể NPE/unboxing sai trong
transformer). Đây là fail-WRONG, không phải fail-open, và câu "fail-open bằng cách
skip" trong `docs/ARCHITECTURE.md` mô tả sai hành vi này.
Đề xuất: vì template đã tuyên bố chỉ target Tiramisu+, từ chối cài hook ngay ở
`applyHook()` khi `SDK_INT < 33` (log + `return false`) — sạch hơn là bịa null.
(Máy target Tammi là SDK 36 nên không ảnh hưởng mục tiêu trước mắt.)

**#3 — `HookPolicy.kt` (`hookBefore` ~dòng 118, `hookAfter` ~dòng 148): thiếu scrub stack trace trước khi rethrow**
HMA `BulkHooker.kt:96,134` gọi `ServiceUtils.clearStackTraces(it)` trước `throw it`
để xóa frame `BulkHooker`/`com.v7878`/package app khỏi stack trace — biện pháp
stealth của HMA. Builder drop tính năng này mà không ghi lý do. Với template lấy
stealth làm mục tiêu thì đây là fidelity gap đáng kể (frame
`com.example.zygisktemplate.*` sẽ lộ trong mọi exception đi qua hook).
Đề xuất: port `clearStackTraces` (lọc `com.v7878`, `HookPolicy`, package template)
hoặc ghi rõ trong ARCHITECTURE vì sao cố ý bỏ.

**#4 — `HookPolicy.kt` (~dòng 40): `disabledHooks` không thread-safe**
`mutableSetOf<String>()` — hook có thể được cài từ nhiều thread (vd deferred
`hookFindClass` + init trực tiếp). Đề xuất: `Collections.synchronizedSet(mutableSetOf())`
hoặc `ConcurrentHashMap.newKeySet()`.

**#5 — `hooks/TammiStealth.kt` (`init`, case 2): thiếu once-guard cho deferred hook**
Nếu `findClass(TARGET_CLASS)` được gọi lần 2 (loader khác, hoặc reload), `hookIsHooked`
chạy lại → `Hooks.hook` trên method đã hook (chain transformer, phí + khó debug).
Đề xuất: `private val hooked = AtomicBoolean(false)`; `hookIsHooked` chỉ chạy khi
`hooked.compareAndSet(false, true)`.

**#6 — `hooks/TammiStealth.kt` (~dòng 92-94): `describeTarget()` dead code**
Đã `@Suppress("unused")` nhưng không ai gọi. Đề xuất: DÙNG nó — trong `hookIsHooked`,
sau khi resolve được `Method`, log `describeTarget(method)` ở path success để có
bằng chứng on-device rằng đúng overload `(Context):boolean` đã bị hook (phục vụ
checklist trong TAMMI-EXAMPLE.md). Xóa cũng được, nhưng dùng thì có giá trị hơn.

**#7 — `HookPolicy.kt` (~dòng 10): import thừa**
`import java.lang.reflect.Method` không được dùng (code chỉ dùng `Executable`).
Đề xuất: xóa để sạch lint.

**#8 — `zygote/build.gradle.kts` (`updateJson = ""`)**
Đã có TODO trong code. Giữ nguyên, nhưng khi có release channel thì điền URL thật —
chưa rõ plugin có chấp nhận chuỗi rỗng hay sinh file update JSON rỗng (verify khi
build thật).

### INFORMATIONAL (không cần sửa)

- **(I1)** Premise rubric mục 2 sai: HMA `ZLUtils.kt:105` đã có guard void —
  builder port trung thực. Không có gì để sửa.
- **(I2)** Mọi usage `EmulatedStackFrame`/`Transformers` chỉ được verify qua usage
  của HMA/AVT (định nghĩa gốc ở PanamaPort:Unsafe `c0d8fa4` không có trong cache) —
  builder đã pin đúng commit và không suy thêm method; cửa ải thật sự là một lần
  compile trên CI/máy build (Gradle không chạy được trong sandbox).

---

## Ghi chú cho coordinator

- Đừng commit khi chưa fix #1 — nếu không pin `6fa2d5c` sẽ đi vào lịch sử git sai.
- Sau fix #1, nên chạy `git diff --cached --stat` + `git ls-files --stage external/AndroidVMTools`
  làm bằng chứng trước commit (fable-check).
- File review này: `docs/plans/active/zygisk-template/scratch/review-result.md`.
