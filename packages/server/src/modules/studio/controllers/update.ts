import type { Context } from 'koa'
import { PERSONAL_FORK } from '../public/personal-fork'
import {
  handleUpdate as runStudioUpdate,
  installPreview as installVersionPreview,
  preparePreview as prepareVersionPreview,
  previewStatus as getVersionPreviewStatus,
  previewTags as listVersionPreviewTags,
  startPreview as startVersionPreview,
  stopPreview as stopVersionPreview,
} from '../services/update/version-preview-manager'

function requiresManualUpdate(ctx: Context): boolean {
  if (PERSONAL_FORK) {
    ctx.status = 409
    ctx.body = { error: 'manual_fork_update_required', message: 'Update Core Hub using docs/PERSONAL-FORK.md.' }
    return true
  }
  return false
}

export async function handleUpdate(ctx: Context): Promise<void> {
  if (requiresManualUpdate(ctx)) return
  await runStudioUpdate(ctx)
}

export async function previewStatus(ctx: Context): Promise<void> {
  await getVersionPreviewStatus(ctx)
}

export async function previewTags(ctx: Context): Promise<void> {
  if (requiresManualUpdate(ctx)) return
  await listVersionPreviewTags(ctx)
}

export async function preparePreview(ctx: Context): Promise<void> {
  if (requiresManualUpdate(ctx)) return
  await prepareVersionPreview(ctx)
}

export async function installPreview(ctx: Context): Promise<void> {
  if (requiresManualUpdate(ctx)) return
  await installVersionPreview(ctx)
}

export async function startPreview(ctx: Context): Promise<void> {
  if (requiresManualUpdate(ctx)) return
  await startVersionPreview(ctx)
}

export async function stopPreview(ctx: Context): Promise<void> {
  await stopVersionPreview(ctx)
}
