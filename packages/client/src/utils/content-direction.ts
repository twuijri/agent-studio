/** Content language is independent of the UI locale. Never rewrite the value. */
export const contentInputProps = {
  dir: 'auto',
  style: { textAlign: 'start', unicodeBidi: 'plaintext' },
} as const

/** Protocol syntax, paths and code must stay readable inside RTL interfaces. */
export const technicalInputProps = {
  dir: 'ltr',
  style: { textAlign: 'start', unicodeBidi: 'isolate' },
} as const
