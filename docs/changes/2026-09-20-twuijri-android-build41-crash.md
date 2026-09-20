# انهيار أندرويد عند الإقلاع في نسخة test.41

المسؤول: twuijri
الفرع: mobile-staging
الحالة: review

## المشكلة والهدف
أبلغ المالك أن نسخة `1.0.2-test.41` على أندرويد «لا تفتح أبدًا». على المحاكي
(API 33): التثبيت النظيف يعمل، أما الترقية من نسخة سابقة فتنهار فورًا:
`SecurityException: Neither user nor current process has
android.permission.ACCESS_NETWORK_STATE` من `AppUpdater.isMetered` المستدعاة
من `AppViewModel.checkForUpdates` عند بدء التطبيق (MainActivity.kt:232). فحص
التحديث الذاتي لا يعمل إلا بعد تسجيل الدخول، لذلك لم يظهر في تثبيت نظيف ولا
في الاختبارات.

## القرار والموافقات
إعلان `ACCESS_NETWORK_STATE` في الملف، وتحصين `isMetered` و`isOffline` ضد
`SecurityException` (تُعامل الشبكة المجهولة كغير محدودة). اختبار
`AppUpdateManifestTest` يثبّت الإذن والحماية. المالك طلب إصدار نسخة تست بعد
اكتمال الدفعة؛ هذا الإصلاح شرط لأي نسخة تُثبَّت فوق سابقة.

## الملفات والتأثير
`clients/android/app/src/main/AndroidManifest.xml`،
`clients/android/app/src/main/java/us/i3u/hermesstudio/AppUpdater.kt`،
`clients/android/app/src/test/java/us/i3u/hermesstudio/AppUpdateManifestTest.kt`.

## الفحوص
- محليًا: `gradle --offline testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL،
  458 اختبارًا بلا إخفاق.
- على المحاكي: تثبيت نسخة الالتزام `5420a7ec` (البناء 22) ثم الترقية إلى
  `29f0b9e0` (البناء 41) أعاد الانهيار أعلاه؛ بعد الإصلاح: العملية حيّة،
  `FATAL EXCEPTION` = 0، والنشاط في المقدمة.

## المخاطر والرجوع
إذن عادي لا يطلب موافقة المستخدم. الرجوع: إلغاء الالتزام.

## التسليم والخطوة التالية
دُفع إلى `mobile-staging`؛ بعد اخضرار CI يُدمج في `mobile` لإصدار النسخة
التالية التي يثبّتها المالك يدويًا للمرة الأخيرة.
