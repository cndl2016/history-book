<template>
  <div ref="editorRef" class="monaco-editor" :style="{ height: height, width: '100%' }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  language: {
    type: String,
    default: 'sql'
  },
  theme: {
    type: String,
    default: 'vs'
  },
  height: {
    type: String,
    default: '300px'
  },
  readOnly: {
    type: Boolean,
    default: false
  },
  options: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const editorRef = ref(null)
let editor = null

const defaultOptions = {
  minimap: { enabled: false },
  fontSize: 14,
  wordWrap: 'on',
  automaticLayout: true,
  scrollBeyondLastLine: false,
  tabSize: 2,
  insertSpaces: true,
  lineNumbers: 'on',
  roundedSelection: true,
  scrollbar: { vertical: 'auto', horizontal: 'auto' }
}

onMounted(() => {
  const mergedOptions = { ...defaultOptions, ...props.options }
  mergedOptions.value = props.modelValue
  mergedOptions.language = props.language
  mergedOptions.theme = props.theme
  mergedOptions.readOnly = props.readOnly

  editor = monaco.editor.create(editorRef.value, mergedOptions)

  editor.onDidChangeModelContent(() => {
    const value = editor.getValue()
    emit('update:modelValue', value)
    emit('change', value)
  })
})

watch(() => props.modelValue, (newValue) => {
  if (editor && newValue !== editor.getValue()) {
    editor.setValue(newValue || '')
  }
})

watch(() => props.language, (newLang) => {
  if (editor) {
    const model = editor.getModel()
    if (model) {
      monaco.editor.setModelLanguage(model, newLang)
    }
  }
})

watch(() => props.theme, (newTheme) => {
  if (editor) {
    editor.updateOptions({ theme: newTheme })
  }
})

watch(() => props.readOnly, (newReadOnly) => {
  if (editor) {
    editor.updateOptions({ readOnly: newReadOnly })
  }
})

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
    editor = null
  }
})

defineExpose({
  getEditor: () => editor,
  focus: () => editor && editor.focus(),
  setValue: (value) => editor && editor.setValue(value || ''),
  getValue: () => editor ? editor.getValue() : ''
})
</script>

<style scoped>
.monaco-editor {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
</style>
