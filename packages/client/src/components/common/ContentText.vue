<script setup lang="ts">
// Keep the original semantic element/layout; do not mirror its surrounding UI.
withDefaults(defineProps<{
  as?: 'span' | 'div' | 'p' | 'h1' | 'h2' | 'h3' | 'h4' | 'label' | 'code' | 'pre'
  technical?: boolean
}>(), { as: 'span', technical: false })
</script>

<template>
  <component :is="as" class="content-text" :dir="technical ? 'ltr' : 'auto'"><slot /></component>
</template>

<style scoped>
.content-text {
  unicode-bidi: isolate;
  text-align: start;
}
</style>
