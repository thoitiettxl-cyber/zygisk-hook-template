# Ví dụ worked: stealth Tammi

## Bài toán

Tammi (`com.viettel.appviettel`) **tự sát sau ~1s** khi có LSPosed module trong scope,
không để lại dấu vết. Phân tích đầy đủ:
`~/workspace/research/tammi-antitamper/REPORT.md`.

Root cause đã xác định:

- `vn.com.viettel.vds.vanillalib.app.security.HookDetector.isHooked(Context): boolean`
  (descriptor `(Landroid/content/Context;)Z`) quét stack trace tìm chuỗi
  `"xposed"` / `"XposedBridge"` / `"substrate"` + kiểm tra `ZygoteInit` xuất hiện ×2.
- Native `checkSecurity()` (`libvds-sotp.so`) **không tự quét** — nó chỉ gọi ngược
  3 check Java qua JNI, trong đó có `isHooked`.
- Không có caller Java nào gọi `isHooked` — chỉ native gọi qua JNI.
- **Mọi** module LSPosed trong scope đều bị bắt, kể cả khi không hook gì (bản thân
  framework LSPosed đã đủ dấu vết).

→ Phương án: hook `isHooked` bằng **vehicle không sinh Java frame "xposed"**:
Zygisk module + AndroidVMTools (ART native hook). Trả `false` và **không gọi bản gốc**
(bản gốc quét stack — gọi nó là tự tố cáo).

## Code

`zygote/src/main/java/com/example/zygisktemplate/hooks/TammiStealth.kt`:

1. **Lấy ClassLoader của app target** (`HookPolicy.appClassLoader()` — context
   classloader của thread trong `main()`, tức post-specialize đã ở process Tammi).
2. **Case class đã load**: `Class.forName(...)` thành công → `hookIsHooked()` ngay.
3. **Case class chưa load**: `ClassLoaderHooks.hookFindClass(loader)` — API đã verify
   trong AVT source (`hookFindClass(ClassLoader, FindClassI)`,
   `FindClassI.findClass(loader, name, original)`) — khi `findClass` trả về đúng
   `TARGET_CLASS` thì cài hook rồi mới trả class về (fail-open: lỗi thì vẫn trả class).
4. **Hook**: `policy.hookBefore(..., parameterTypes = [Context])` với callback set
   `value.replace = true; value.result = false` → short-circuit, bản gốc không chạy.

## Checklist test trên máy thật

1. Build `:zygote:assembleRelease`, lấy zip ở `zygote/build/outputs/magisk/`,
   flash qua Magisk/KernelSU, reboot.
2. Mở Tammi, quan sát: không tự sát sau ~1s như khi bật SpoofX.
3. `adb logcat -s ZygiskTemplate` — expect:
   `Injected into com.viettel.appviettel`, `Hooked ...isHooked -> always false`,
   và mỗi lần native gọi check → `isHooked() spoofed -> false`.
4. Test sâu hơn (REPORT Tammi gợi ý): đi tới màn hình eKYC/CustomTab — nếu Tammi
   chết ở đó với SIGABRT/exception thì detector thứ hai (`libsbuhgb.so`) đã nổ,
   cần stealth thêm (ngoài scope template này).

## Điểm chưa verify (TODO verify on device)

- Thời điểm `main()` chạy so với lần `checkSecurity()` đầu tiên của Tammi.
  Nếu native check chạy **trước** post-specialize thì hook tới muộn — cần đo bằng logcat.
- `packages("com.viettel.appviettel")` có match đúng process không.
- Detector thứ hai `libsbuhgb.so` (verify chữ ký APK native + kill bằng abort,
  check chạy rất sớm qua patch `ComponentActivity.<clinit>`) — template này
  không xử lý nó.
