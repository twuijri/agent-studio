# قاعدة اتجاه النصوص — لكل المطورين والمساعدين

## لماذا؟

لغة الواجهة ليست لغة المحادثة. قد يكتب المستخدم بالعربية داخل واجهة إنجليزية،
أو يختار خيارًا إنجليزيًا في واجهة عربية. القاعدة مشتركة، لا إصلاح CSS لكل شاشة.
نحافظ على البنية الموجودة، ونصنف النص قبل عرضه؛ لا نعدّل النص المخزّن أو المرسل.

## العقد الإلزامي لأي واجهة جديدة أو معدّلة

| النوع | الطريقة | أمثلة |
| --- | --- | --- |
| نص الواجهة المترجم | يرث اتجاه اللغة من `i18n/direction.ts` وNaive UI | أسماء الأزرار والعناوين الثابتة |
| محتوى المستخدم أو الوكيل | `ContentText` | سؤال، خيار، تعليق، اسم مهمة، ملخص |
| حقل نص بشري | `contentInputProps` على العنصر الأصلي | اسم المهمة، الإجابة، النص الطويل |
| شيفرة أو مسار أو صيغة تقنية | `ContentText technical` أو `technicalInputProps` | تعبير cron، مسار، اسم فرع، معرّف نموذج |
| Markdown غني | `MarkdownRenderer` الموجود | رسائل الوكيل ونتائج المهام |

نص مختلط يأخذ الاتجاه من أول حرف ذي اتجاه واضح باستخدام معيار المتصفح
`dir="auto"`؛ ليس خوارزمية لتخمين «اللغة الغالبة». الرقم وعلامة الترقيم ليسا
دليلًا على اتجاه النص. نص يبدأ باسم إنجليزي قد يكون اتجاهه الأساسي يسارًا حتى
لو أكمل صاحبه بالعربية. لا نفرض `rtl` على كل النصوص ولا نضيف أحرف اتجاه خفية.

## أمثلة جاهزة

```vue
<script setup lang="ts">
import ContentText from '@/components/common/ContentText.vue'
import { contentInputProps, technicalInputProps } from '@/utils/content-direction'
</script>

<template>
  <ContentText as="h3">{{ task.title }}</ContentText>
  <ContentText as="div">{{ question }}</ContentText>
  <NButton><ContentText>{{ choice }}</ContentText></NButton>
  <NInput v-model:value="answer" :input-props="contentInputProps" />
  <NInput v-model:value="path" :input-props="technicalInputProps" />
  <ContentText technical>{{ path }}</ContentText>
</template>
```

`ContentText` يحتفظ بالعنصر الدلالي المختار، ولا يضيف غلافًا ثانيًا. ينقل الفئات
والأحداث المعتادة، ويعزل اتجاه النص عن المجاور ويستخدم محاذاة `start`.
استخدمه حول أصغر قطعة محتوى مستقلة، **ليس حول صف كامل أو صفحة**.
لكل خيار اتجاهه، ولا تجعل أول خيار يقرر اتجاه بقية الأزرار.

في `NInput` استخدم `input-props`، لا `dir` على غلاف المكوّن: المطلوب ضبط
`input` أو `textarea` الحقيقي. للحقول الأصلية استخدم `v-bind="contentInputProps"`.
إذا احتجت خصائص إضافية فادمجها دون إسقاط خصائص الاتجاه، وأضف اختبارًا للعقد.
لا تحوّل القيم أو تعكس النص؛ حقول الأسطر المتعددة تستعمل `plaintext` للفقرات.

في `NDrawerContent` و`NModal preset="card"` و`NCollapseItem` استخدم فتحة
`header` مع `ContentText` بدل تمرير عنوان من المستخدم كنص غير معزول.
في دوال العرض استخدم `h(ContentText, props, { default: () => text })`، ومرّر
`inputProps: contentInputProps` إلى `h(NInput, ...)`.

لا تستبدل Markdown بمكوّن النص العادي: معالجه الحالي يدعم اتجاه كل فقرة ويعزل
الشيفرة. لا تغيّر اتجاه الطرفية أو المحرر أو إحداثيات النوافذ لتحقيق اتجاه النص.

## حواجز الاختبارات وواجب المراجع

- `content-direction.test.ts`: المكوّن المشترك وحقول Naive الحقيقية، سلامة القيم
  والأحداث وعدم تفسير المحتوى كـ HTML.
- `content-direction-contract.test.ts`: يفحص قوالب كل ملفات الكانبان والمهام
  المجدولة، بما فيها الملفات الجديدة داخلهما؛ يرفض حقول `NInput` التي لا تختار
  الإعداد المشترك. يفحص الربط بأسماء المحتوى المعروفة والعناوين الديناميكية،
  وأسئلة المحادثة العادية والجماعية. يتضمن أمثلة خاطئة للتأكد أن الحارس يرفضها.
- `global-pending-actions.test.ts`: يفحص أسئلة الإشعارات المنشأة بدوال العرض.
- `content-direction.spec.ts`: متصفح حقيقي، واجهتان عربية وإنجليزية، نص عربي
  وإنجليزي ومختلط، إدخال وإرسال الإجابة، المهام المجدولة وتفاصيل الكانبان.
- تبقى اختبارات اتجاه المستند وNaive وMarkdown وحارس CSS المنطقي الحالية مطلوبة.

**هذا ليس مدققًا دلاليًا لكل محتوى النظام.** اسم متغير جديد غير معروف، أو محتوى
داخل خاصية/دالة عرض جديدة، أو مكوّن خارج نطاق الهجرة قد يحتاج عقدًا إضافيًا.
أي مساهمة تمس نصًا بشريًا يجب أن تحدد تصنيفه وتضيف سطحها أو حالة رجوع إلى هذه
الاختبارات. لا تعطل الحارس ولا تضف استثناءً عامًا؛ وثّق الاستثناء المحدد وسببه
واختباره إن كان العنصر يحتاج سلوكًا آخر (مثل محرر الشيفرة).

تشغيل الفحوص المركزة:

```bash
npx vitest run tests/client/content-direction.test.ts tests/client/content-direction-contract.test.ts tests/client/global-pending-actions.test.ts tests/client/rtl-logical-css.test.ts tests/client/i18n-direction.test.ts tests/client/naive-rtl.test.ts tests/client/markdown-auto-direction.test.ts
npx playwright test tests/e2e/content-direction.spec.ts tests/e2e/i18n-rtl-language-switch.spec.ts
```

تعمل هذه الاختبارات ضمن فحوص البناء والمتصفح المعتادة في CI. لا يكفي وجود
`dir` في المصدر: راجع الحقول الداخلية والنوافذ المنقولة إلى `body`، جرّب القيم
والإرسال، وراجع عدم تغيّر ترتيب الأزرار والأعمدة. هذه المرحلة تطبّق الأساس على
الأسئلة والكانبان والمهام المجدولة، وليست ادعاءً بأن كل شاشة قديمة هُجّرت.
