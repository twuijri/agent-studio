// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { NInput } from 'naive-ui'
import ContentText from '@/components/common/ContentText.vue'
import { contentInputProps, technicalInputProps } from '@/utils/content-direction'

afterEach(() => { document.documentElement.dir = '' })

describe('content direction independent of interface direction', () => {
  it.each(['ltr', 'rtl'])('preserves semantic element, text and isolation under %s UI', async direction => {
    document.documentElement.dir = direction
    const wrapper = mount(ContentText, { props: { as: 'h3' }, attrs: { class: 'job-name' }, slots: { default: 'راجع API الإصدار 2؟' } })
    expect(wrapper.element.tagName).toBe('H3')
    expect(wrapper.classes()).toContain('job-name')
    expect(wrapper.attributes('dir')).toBe('auto')
    expect(wrapper.text()).toBe('راجع API الإصدار 2؟')
    await wrapper.setProps({ technical: true })
    expect(wrapper.attributes('dir')).toBe('ltr')
    expect(document.documentElement.dir).toBe(direction)
    wrapper.unmount()
  })

  it('escapes text instead of treating user content as HTML', () => {
    const wrapper = mount(defineComponent({ components: { ContentText }, setup: () => ({ text: '<script>alert(1)</script>' }), template: '<ContentText>{{ text }}</ContentText>' }))
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toBe('<script>alert(1)</script>')
    wrapper.unmount()
  })

  it.each(['text', 'textarea'] as const)('sets attributes on real Naive %s fields, preserves edits and technical values', async type => {
    const onUpdateValue = vi.fn()
    const wrapper = mount(NInput, { props: { type, value: 'مرحبا API', inputProps: contentInputProps, onUpdateValue } })
    const input = wrapper.get(type === 'textarea' ? 'textarea' : 'input')
    expect(input.attributes('dir')).toBe('auto')
    expect((input.element as HTMLElement).style.textAlign).toBe('start')
    await input.setValue('English ثم عربي')
    expect(onUpdateValue).toHaveBeenCalledWith('English ثم عربي', expect.anything())
    await wrapper.setProps({ inputProps: technicalInputProps, value: '/home/agent/.hermes' })
    expect(input.attributes('dir')).toBe('ltr')
    expect((input.element as HTMLInputElement).value).toBe('/home/agent/.hermes')
    wrapper.unmount()
  })
})
