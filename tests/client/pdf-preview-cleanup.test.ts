// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({ getDocument: vi.fn() }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('pdfjs-dist', () => ({ getDocument: mocks.getDocument, GlobalWorkerOptions: {} }))
vi.mock('pdfjs-dist/build/pdf.worker.min.mjs?url', () => ({ default: '/pdf-worker.js' }))
vi.mock('naive-ui', () => ({
  NButton: { template: '<button><slot /></button>' },
  NButtonGroup: { template: '<div><slot /></div>' },
  NInputNumber: { template: '<input />' },
  NSpin: { template: '<div><slot /></div>' },
}))
import PdfFilePreview from '@/components/hermes/files/PdfFilePreview.vue'

function task() {
  return {
    destroy: vi.fn().mockResolvedValue(undefined),
    promise: Promise.resolve({
      numPages: 1,
      // PDFDocumentProxy no longer has destroy() in PDF.js 6.
      getPage: vi.fn().mockResolvedValue({
        getViewport: () => ({ width: 100, height: 200 }),
        render: () => ({ promise: Promise.resolve(), cancel: vi.fn() }),
      }),
    }),
  }
}

describe('PDF.js loading-task cleanup', () => {
  beforeEach(() => {
    mocks.getDocument.mockReset()
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({} as CanvasRenderingContext2D)
  })
  afterEach(() => vi.restoreAllMocks())

  it('destroys the loaded task on replacement and on unmount', async () => {
    const first = task()
    const second = task()
    mocks.getDocument.mockReturnValueOnce(first).mockReturnValueOnce(second)
    const wrapper = mount(PdfFilePreview, { props: { data: new ArrayBuffer(1) } })
    await vi.waitFor(() => expect(mocks.getDocument).toHaveBeenCalledTimes(1))
    await flushPromises()
    await wrapper.setProps({ data: new ArrayBuffer(2) })
    await flushPromises()
    expect(first.destroy).toHaveBeenCalledOnce()
    expect(second.destroy).not.toHaveBeenCalled()
    expect(wrapper.emitted('error')).toBeUndefined()
    wrapper.unmount()
    await flushPromises()
    expect(second.destroy).toHaveBeenCalledOnce()
  })

  it('cleans up a load that resolves after the component unmounts', async () => {
    const pending = task()
    let resolveLoad!: (value: Awaited<typeof pending.promise>) => void
    pending.promise = new Promise(resolve => { resolveLoad = resolve })
    mocks.getDocument.mockReturnValue(pending)
    const wrapper = mount(PdfFilePreview, { props: { data: new ArrayBuffer(1) } })
    await vi.waitFor(() => expect(mocks.getDocument).toHaveBeenCalledOnce())
    wrapper.unmount()
    resolveLoad(await task().promise)
    await flushPromises()
    expect(pending.destroy).toHaveBeenCalled()
    expect(wrapper.emitted('error')).toBeUndefined()
  })
})
