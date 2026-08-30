---
title: 'مقصد تلگرام و پاسخ پیامکی از Reply'
type: 'feature'
created: '2026-08-30'
status: 'in-review'
review_loop_iteration: 0
baseline_commit: '28594fe61a502daea3f0ccfbd4a2e525ae21e99c'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** برنامه باید علاوه بر مقصدهای فعلی، پیامکهای ورودی را به یک گروه یا کانال تلگرام بفرستد و Reply واقعی اعضا به پیام بات را به شمارهٔ همان پیامک برگرداند. تنظیمات لازم توکن بات، شناسهٔ گفتگو و پراکسی است.

**Approach:** یک مقصد مستقل مبتنی بر Telegram Bot API اضافه میشود؛ هر پیام خروجی با شناسهٔ پیام تلگرام به شمارهٔ فرستنده نگاشت میشود و دریافت پاسخ با long polling پایدار انجام میشود. SOCKS5 مستقیماً پشتیبانی میشود؛ لینک MTProto فقط از مسیر مترجم محلی MTProto→SOCKS استفاده میشود و هرگز مستقیماً به Bot API داده نمیشود.

## Boundaries & Constraints

**Always:** تلگرام opt-in و مستقل از ntfy و دلتاچت است؛ شکست آن مسیرهای دیگر یا سرویس foreground را متوقف نمیکند. توکن بات و مشخصات حساس پراکسی در log، اعلان، backup یا repo چاپ نمیشوند. فقط Reply واقعی به پیام ارسالشدهٔ همین بات مجاز است؛ نگاشت پیام به شماره و offset دریافت بهصورت پایدار و deduplicated ذخیره میشود. پاسخ خالی، پیام ویرایششده، پیام تکراری، گفتگوی دیگر و Reply به پیام ناشناس رد میشوند. پیش از ارسال پیامک، شماره و متن اعتبارسنجی میشوند. کانال تنها وقتی برای پاسخ مناسب است که Reply در discussion/group قابل مشاهدهٔ بات باشد.

**Never:** پاسخ دلتاچت در این نسخه پیادهسازی نمیشود. شماره از متن پیام استخراج نمیشود و دستور دستی جای Reply واقعی را نمیگیرد. MTProto بهعنوان transport مستقیم Bot API یا TDLib پیادهسازی نمیشود؛ مقدار MTProto باید به endpoint محلی SOCKS مترجم نگاشت شود. توکن بات وارد پایگاه دادهٔ نگاشت یا متن پیام نمیشود.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| ارسال | توکن، chat ID و مقصد معتبر | پیام قالببندی و message_id به شماره نگاشت میشود | شکست فقط ثبت redacted میشود |
| پاسخ معتبر | Reply واقعی در chat تنظیمشده به پیام بات | متن Reply یک بار به شمارهٔ نگاشتشده SMS میشود | نتیجهٔ ارسال ثبت و update نهایی میشود |
| پاسخ نامعتبر | chat دیگر، پیام ناشناس، بدون Reply یا تکراری | هیچ SMS ارسال نمیشود | update بدون retry نامحدود نهایی میشود |
| SOCKS | لینک t.me/socks معتبر | Bot API و polling از SOCKS5 عبور میکنند | ورودی نامعتبر رد میشود |
| MTProto | لینک t.me/proxy و مترجم محلی در دسترس | secret افشا نمیشود و endpoint SOCKS مترجم استفاده میشود | نبود مترجم خطای روشن میدهد و direct fallback ندارد |
| راهاندازی مجدد | mapping و offset ذخیره شده | Replyهای قدیمی دوباره SMS نمیشوند | پردازش fail-closed ادامه مییابد |

</frozen-after-approval>

## Code Map

- `android-native/app/src/main/java/com/smsntfy/data/Preferences.kt` -- تنظیمات موجود؛ افزودن enable، token، chat ID و proxy با ممانعت backup.
- `android-native/app/src/main/res/layout/activity_settings.xml` و `SettingsActivity.kt` -- سه کادر و کنترل فعالسازی/آزمایش اتصال.
- `android-native/app/src/main/java/com/smsntfy/service/SmsForwardingService.kt:259-305` -- fan-out مستقل پیامک و lifecycle سرویس دریافت پاسخ.
- `android-native/app/src/main/java/com/smsntfy/sms/SmsReplyHelper.kt:23-52` -- مسیر موجود ارسال SMS چندبخشی.
- `android-native/app/src/main/java/com/smsntfy/data/AppDatabase.kt` و DAOهای reply -- الگوی Room برای mapping، dedupe و بازیابی پس از restart.
- `android-native/app/src/main/java/com/smsntfy/network/NtfyClient.kt` -- الگوی OkHttp و جداسازی خطای مقصد.
- `android-native/app/src/main/AndroidManifest.xml` و قواعد backup -- مجوزها و حذف secrets/state حساس از backup.

## Tasks & Acceptance

**Execution:**
- [x] `Preferences.kt`, layout و settings UI -- ذخیرهٔ امن سه ورودی، فعالسازی opt-in، parsing لینک SOCKS/MTProto و تست اتصال.
- [x] `telegram/TelegramProxy.kt` و `TelegramBotClient.kt` -- Bot API sendMessage/getUpdates با timeout، redaction و پراکسی مشخص.
- [x] Room entities/DAO/migration -- نگاشت اتمیک message_id→phone و offset/update dedupe crash-safe.
- [x] `SmsForwardingService.kt` -- fan-out مستقل و polling فقط هنگام فعال بودن سرویس؛ Reply معتبر را به `SmsReplyHelper` بسپارد.
- [ ] تستهای واحد و migration -- تمام سطرهای ماتریس، parsing نمونه لینکها، restart و duplicate را پوشش دهند.
- [ ] آزمون دستگاه -- پیامک واقعی→تلگرام→Reply واقعی→SMS را با marker غیرحساس اثبات کند.

**Acceptance Criteria:**
- Given تلگرام فعال و تنظیمات معتبر است، when پیامک واقعی میرسد، then همان پیام بدون اختلال در ntfy/Delta Chat به chat تنظیمشده ارسال و نگاشت پایدار میشود.
- Given کاربر در همان chat به پیام بات Reply میزند، when update فقط یک بار دریافت میشود، then متن دقیق یک بار به شمارهٔ پیام اصلی SMS میشود.
- Given پراکسی یا Telegram API شکست میخورد، when سرویس پردازش میکند، then سرویس و سایر مقصدها فعال میمانند و secret افشا نمیشود.

## Spec Change Log

## Review Triage Log

## Design Notes

Bot API فقط HTTPS است. SOCKS5 با OkHttp مستقیم استفاده میشود. لینک MTProto صرفاً ورودی پیکربندی مترجم محلی است؛ آداپتر، endpoint محلی SOCKS متناظر را میگیرد و در نبود آن fail-closed است. این مرز از افزودن TDLib و پیچیدگی حساب کاربری تلگرام جلوگیری میکند.

## Verification

**Commands:**
- `JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto ./gradlew testDebugUnitTest --no-daemon --console=plain` -- همهٔ تستها موفق.
- `JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto ./gradlew assembleDebug --no-daemon --console=plain` -- APK سالم برای ARM ساخته شود.
- `git diff --check` -- بدون خطای whitespace.

**Manual checks (if no CLI):**
- روی گوشی واقعی هر دو نمونهٔ SOCKS و MTProto-bridge آزمایش شوند؛ سپس Reply واقعی در گروه باید دقیقاً یک SMS ایجاد کند.
