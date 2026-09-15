import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { parse } from '@vue/compiler-sfc'
import { parse as parseTemplate, NodeTypes, type ElementNode, type TemplateChildNode } from '@vue/compiler-dom'

// New files/inputs in these feature folders are covered automatically. Add new
// surfaces here when adopting the contract; do not claim to classify all prose.
const root = 'packages/client/src/components'
const featureFolders = ['hermes/kanban', 'hermes/jobs']
const clarificationSurfaces = ['hermes/chat/MessageList.vue', 'hermes/group-chat/GroupChatPanel.vue']
const prose = /\b(?:question|prompt|body|summary|error|description|title|name|choice|completionSummary|commentBody|clarifyResponse)\b|\bgetJobName\(/

function binding(node: ElementNode, name: string) {
  return node.props.find(prop => prop.type === NodeTypes.DIRECTIVE && prop.name === 'bind'
    && prop.arg?.type === NodeTypes.SIMPLE_EXPRESSION && prop.arg.content === name)
}

function checkTemplate(template: string, allInputs: boolean): string[] {
  const errors: string[] = []
  const content = (expression: string) => !/^t\(/.test(expression.trim()) && prose.test(expression)
  function visit(node: TemplateChildNode, isolated = false) {
    if (node.type === NodeTypes.INTERPOLATION) {
      if (!isolated && content(node.content.loc.source)) errors.push(`ContentText missing: ${node.content.loc.source}`)
      return
    }
    if (node.type !== NodeTypes.ELEMENT) return
    const protectedText = isolated || node.tag === 'ContentText'
    // Naive title props render plain strings. Use the header slot for content.
    for (const name of ['title', 'description']) {
      const prop = binding(node, name)
      if (prop?.type === NodeTypes.DIRECTIVE && prop.exp && content(prop.exp.loc.source)) {
        errors.push(`Use a ContentText slot instead of ${node.tag}:${name}`)
      }
    }
    const model = node.props.find(prop => prop.type === NodeTypes.DIRECTIVE && prop.name === 'model')
    if (node.tag === 'NInput' && (allInputs || (model?.type === NodeTypes.DIRECTIVE && model.exp?.loc.source === 'clarifyResponse'))) {
      const prop = binding(node, 'input-props')
      if (prop?.type !== NodeTypes.DIRECTIVE || !['contentInputProps', 'technicalInputProps'].includes(prop.exp?.loc.source || '')) {
        errors.push('NInput must classify its native input with shared input-props')
      }
    }
    if (allInputs && ['input', 'textarea'].includes(node.tag)) {
      const type = node.props.find(prop => prop.type === NodeTypes.ATTRIBUTE && prop.name === 'type')
      const nonText = type?.type === NodeTypes.ATTRIBUTE && ['checkbox', 'radio', 'range', 'file', 'button', 'submit', 'hidden'].includes(type.value?.content || '')
      const sharedProps = node.props.some(prop => prop.type === NodeTypes.DIRECTIVE && prop.name === 'bind' && !prop.arg
        && ['contentInputProps', 'technicalInputProps'].includes(prop.exp?.loc.source || ''))
      if (!nonText && !sharedProps) errors.push('Native text input must bind shared direction props')
    }
    for (const child of node.children) visit(child, protectedText)
  }
  for (const child of parseTemplate(template).children) visit(child)
  return errors
}

function files(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const path = join(dir, entry.name)
    return entry.isDirectory() ? files(path) : path.endsWith('.vue') ? [path] : []
  })
}

describe('shared content direction adoption contract', () => {
  it('guards current and future feature inputs and conventional prose bindings', () => {
    const paths = featureFolders.flatMap(folder => files(join(root, folder)))
    expect(paths.length).toBeGreaterThanOrEqual(8)
    const violations = paths.flatMap(file => checkTemplate(parse(readFileSync(file, 'utf8')).descriptor.template!.content, true).map(error => `${file}: ${error}`))
    expect(violations).toEqual([])
  })

  it.each(clarificationSurfaces)('guards question, choices and answer field in %s', file => {
    const template = parse(readFileSync(join(root, file), 'utf8')).descriptor.template!.content
    // Limit this migration to clarification cards; legacy chat surfaces retain
    // their tested Markdown/dir=auto implementation, not an exemption for new UI.
    const ast = parseTemplate(template)
    const cards: ElementNode[] = []
    function find(node: TemplateChildNode) {
      if (node.type !== NodeTypes.ELEMENT) return
      if (node.props.some(prop => prop.type === NodeTypes.DIRECTIVE && prop.name === 'if' && prop.exp?.loc.source === '!visibleApproval && visibleClarify')) {
        if (node.loc.source.includes('clarifyResponse')) cards.push(node)
      }
      node.children.forEach(find)
    }
    ast.children.forEach(find)
    expect(cards.length).toBeGreaterThan(0)
    expect(cards.flatMap(card => checkTemplate(card.loc.source, false))).toEqual([])
  })

  it('rejects omissions, wrapper-only direction, copied rules and untranslated dynamic titles', () => {
    for (const template of [
      '<div>{{ task.title }}</div>',
      '<div dir="auto">{{ task.title }}</div>',
      '<NInput dir="auto" v-model:value="prompt" />',
      '<NInput :input-props="{dir: \'auto\'}" />',
      '<NModal :title="task.title" />',
      '<NButton>{{ choice }}</NButton>',
      '<textarea dir="auto" />',
      '<input v-model="answer" />',
    ]) expect(checkTemplate(template, true).length, template).toBeGreaterThan(0)
    expect(checkTemplate('<ContentText>{{ task.title }}</ContentText><NInput :input-props="contentInputProps" /><NInput :input-props="technicalInputProps" /><textarea v-bind="contentInputProps" /><input type="checkbox" /><span>{{ t("jobs.title") }}</span>', true)).toEqual([])
  })
})
