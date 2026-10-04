# Kiến trúc zygisk-hook-template

Mẫu theo **HMA-OSS** (frknkrc44/HMA-OSS, module `:zygote`), đã đối chiếu source
thật — xem `docs/plans/active/zygisk-template/scratch/verify-result.md`.

## Luồng tổng thể

```
Zygisk (Magisk) ──fork+specialize──▶ process target (vd com.viettel.appviettel)
        │
        ▼  ZygoteLoader runtime (com.v7878.zygisk.EntryPoint)
   preSpecialize() ──▶ ZygoteEntry.premain()     (trong zygote, TRƯỚC specialize)
  postSpecialize() ──▶ ZygoteEntry.main()        (trong process target, SAU specialize)
        │
        ▼
  TammiStealth.init() ──▶ HookPolicy.hookBefore()
        │
        ▼  AndroidVMTools: Hooks.hook(executable, DIRECT, transformer, DIRECT)
  ART method entry point bị trỏ sang transformer —
  mọi call isHooked() chạy qua HookTransformer.transform(original, frame)
        │
        ▼
  transformer set frame return value = false, KHÔNG gọi bản gốc
```

## Các lớp

**ZygoteEntry.java** — entrypoint duy nhất. `premain()` giữ rỗng (ghi chú nếu cần),
`main()` gọi `TammiStealth.init()` trong try/catch (fail-open).
Cả class + 2 method annotate `@DoNotObfuscate(Type)`/`@DoNotShrink(Type)`
(`com.v7878.r8.annotations`) để R8 không strip/đổi tên — runtime load class bằng
`Class.forName(entrypoint)` và tìm method bằng `getMethod()` (chỉ thấy public).

**HookPolicy.kt** — port giản lược `BulkHooker.kt` + `ZLUtils.kt` của HMA:
- `hookBefore` / `hookAfter`: transformer builder. Callback nhận
  `(methodName, frame, ReturnValue)`; set `value.replace = true` + `value.result`
  để short-circuit (không gọi bản gốc).
- `ReturnValue`: holder `replace` / `result` / `throwable`.
- `EmulatedStackFrame.setReturnValue()`: **extension port từ HMA, không phải method
  gốc** — impl `accessor().setValue(RETURN_VALUE_IDX, value)`, skip khi return void.
  (`RETURN_VALUE_IDX` import từ `com.v7878.unsafe.invoke.EmulatedStackFrame`,
  artifact PanamaPort:Unsafe.)
- `invokeOriginal()`: port nhánh Tiramisu+ của HMA `invokeExactCompat` —
  `Transformers.invokeExactNoChecks(original, frame)`. Path Android 12- của HMA
  (ArtMethodUtils) chưa port: template KHÔNG fail-open bằng skip — `applyHook()`
  từ chối cài hook ngay trên SDK < 33 (log lý do + `return false`), vì template
  chỉ target Tiramisu+.
- `hookBefore`/`hookAfter` rethrow `value.throwable` sau khi scrub stack trace
  (port HMA `ServiceUtils.clearStackTraces`: lọc frame `HookPolicy` /
  `com.v7878` / package template, trừ `r8-map-id-`/`dex-id-`).
- `disabledHooks` là `Collections.synchronizedSet` (thread-safe — hook có thể cài
  từ nhiều thread: init trực tiếp + deferred `hookFindClass`).
- `hooksWasCrashed` (circuit breaker), `disabledHooks` (kill-switch
  `class->method(argCount)`), `appClassLoader()` (context classloader của thread).

**AndroidVMTools** (`com.v7878.vmtools.*`, compile trực tiếp từ submodule):
- `Hooks.hook(Executable, EntryPointType, HookTransformer, EntryPointType)` → `Pair<Long,Long>`;
  `Hooks.deoptimize(Executable)`.
- `HookTransformer.transform(MethodHandle original, EmulatedStackFrame frame)`.
- `ClassLoaderHooks.hookFindClass(ClassLoader, FindClassI)` — cài hook khi class
  load lần đầu (xử lý "class chưa load khi main() chạy").

**PanamaPort:Unsafe** (`com.v7878.unsafe.invoke`, external qua JitPack):
- `EmulatedStackFrame` (`type()`, `accessor()`), `Transformers.invokeExact` /
  `invokeExactNoChecks`. Định nghĩa gốc chưa có trong source cache — template pin
  đúng `c0d8fa4` và chỉ dùng API đã thấy qua usage.

## Vì sao không để lại frame "xposed"

LSPosed/Xposed hook bằng Java: stack trace của thread bị hook chứa frame
`de.robv.android.xposed.*` / `XposedBridge` → `HookDetector` quét chuỗi là bắt được.
Zygisk + AndroidVMTools hook ở tầng **ART native** (đổi entry point của ArtMethod):
Java stack trace của target không hề có frame nào của framework hook — đó là lý do
vehicle này qua được detector quét stack trace của Tammi.
