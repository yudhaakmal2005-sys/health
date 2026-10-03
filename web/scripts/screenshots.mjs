#!/usr/bin/env node
/**
 * Tangkapan layar mode demo (data sintetis) dengan Playwright + Chromium terpasang.
 *
 *   npm run build:mock && node scripts/screenshots.mjs [folder-keluaran]
 *
 * Variabel lingkungan:
 *   SHOTS_DIR       folder keluaran (bawaan: ./screenshots)
 *   CHROMIUM_PATH   path chromium bila tidak memakai PLAYWRIGHT_BROWSERS_PATH
 *   BASE_URL        pakai server yang sudah berjalan, alih-alih `vite preview`
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const root = resolve(fileURLToPath(import.meta.url), '../..')
const outDir = resolve(process.argv[2] ?? process.env.SHOTS_DIR ?? join(root, 'screenshots'))
mkdirSync(outDir, { recursive: true })

function findChromium() {
  if (process.env.CHROMIUM_PATH) return process.env.CHROMIUM_PATH
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH ?? '/opt/pw-browsers'
  if (!existsSync(base)) return undefined
  for (const dir of readdirSync(base).filter((d) => d.startsWith('chromium-')).sort().reverse()) {
    const p = join(base, dir, 'chrome-linux', 'chrome')
    if (existsSync(p)) return p
  }
  return undefined
}

async function startPreview() {
  if (process.env.BASE_URL) return { url: process.env.BASE_URL, stop: () => {} }
  if (!existsSync(join(root, 'dist-mock', 'index.html'))) {
    console.error('dist-mock belum ada. Jalankan: npm run build:mock')
    process.exit(1)
  }
  const port = 4179
  const proc = spawn(process.execPath, [join(root, 'node_modules', 'vite', 'bin', 'vite.js'), 'preview', '--outDir', 'dist-mock', '--port', String(port), '--strictPort', '--host', '127.0.0.1'], { cwd: root, stdio: 'ignore' })
  const url = `http://127.0.0.1:${port}`
  for (let i = 0; i < 60; i++) {
    try {
      const r = await fetch(url)
      if (r.ok) return { url, stop: () => proc.kill() }
    } catch { /* belum siap */ }
    await new Promise((r) => setTimeout(r, 250))
  }
  proc.kill()
  throw new Error('vite preview tidak merespons')
}

const settle = (page, ms = 900) => page.waitForLoadState('networkidle').then(() => page.waitForTimeout(ms))

async function shot(page, name, opts = {}) {
  const file = join(outDir, `${name}.png`)
  await page.screenshot({ path: file, fullPage: opts.fullPage ?? true })
  console.log('✓', file)
}

async function login(page, url) {
  await page.goto(`${url}/masuk`)
  await page.getByLabel('SEHATI ID').fill('AD-000001')
  await page.locator('#password').fill('demo1234')
  await page.getByRole('button', { name: 'Masuk', exact: true }).click()
  await page.waitForURL('**/dasbor')
  await settle(page)
}

const DASHBOARD = [
  ['dash-ringkasan', '/dasbor'],
  ['dash-peta-rw', '/dasbor/peta-rw'],
  ['dash-tindak-lanjut', '/dasbor/tindak-lanjut'],
  ['dash-kader', '/dasbor/kader'],
  ['dash-jadwal', '/dasbor/jadwal'],
  ['dash-ambang', '/dasbor/ambang'],
  ['dash-laporan', '/dasbor/laporan'],
  ['dash-audit', '/dasbor/audit'],
  ['dash-ai', '/dasbor/ai'],
]

const { url, stop } = await startPreview()
const browser = await chromium.launch({ executablePath: findChromium() })
try {
  const desktop = { viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, reducedMotion: 'reduce', colorScheme: 'light', locale: 'id-ID' }
  const mobile = { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, reducedMotion: 'reduce', colorScheme: 'light', locale: 'id-ID' }

  // Publik
  {
    const ctx = await browser.newContext(desktop)
    const page = await ctx.newPage()
    await page.goto(url); await settle(page); await shot(page, 'landing-desktop')
    await page.goto(`${url}/privasi`); await settle(page); await shot(page, 'privasi-desktop')
    await page.goto(`${url}/masuk`); await settle(page); await shot(page, 'login-desktop', { fullPage: false })
    // Kader → pesan ramah
    await page.locator('#sehatiId').fill('KD-000001')
    await page.locator('#password').fill('apa-saja')
    await page.getByRole('button', { name: 'Masuk', exact: true }).click()
    await page.getByText('Halo,').waitFor(); await settle(page, 400)
    await shot(page, 'login-kader', { fullPage: false })
    await ctx.close()
  }
  {
    const ctx = await browser.newContext(mobile)
    const page = await ctx.newPage()
    await page.goto(url); await settle(page); await shot(page, 'landing-mobile')
    await page.goto(`${url}/masuk`); await settle(page); await shot(page, 'login-mobile', { fullPage: false })
    await ctx.close()
  }

  // Dashboard desktop (terang)
  {
    const ctx = await browser.newContext(desktop)
    const page = await ctx.newPage()
    await login(page, url)
    for (const [name, path] of DASHBOARD) {
      await page.goto(`${url}${path}`)
      await settle(page, 1100)
      await shot(page, name)
    }
    // Dialog
    await page.goto(`${url}/dasbor/kader`); await settle(page)
    await page.getByRole('button', { name: 'Tambah kader' }).first().click(); await page.waitForTimeout(400)
    await shot(page, 'dash-kader-dialog', { fullPage: false })
    await page.keyboard.press('Escape')
    await page.goto(`${url}/dasbor/jadwal`); await settle(page)
    await page.getByRole('button', { name: 'Tambah jadwal' }).first().click(); await page.waitForTimeout(400)
    await shot(page, 'dash-jadwal-dialog', { fullPage: false })
    await page.keyboard.press('Escape')
    await page.goto(`${url}/dasbor/ambang`); await settle(page)
    await page.locator('#t-bpHighSys').fill('135'); await page.waitForTimeout(200)
    await page.locator('#t-bpNormalSys').fill('150'); await page.waitForTimeout(300)
    await shot(page, 'dash-ambang-invalid', { fullPage: false })
    await ctx.close()
  }

  // Dashboard gelap + seluler
  {
    const ctx = await browser.newContext({ ...desktop, colorScheme: 'dark' })
    const page = await ctx.newPage()
    await login(page, url)
    await shot(page, 'dash-ringkasan-dark')
    await page.goto(`${url}/dasbor/peta-rw`); await settle(page); await shot(page, 'dash-peta-rw-dark')
    await page.goto(`${url}/dasbor/tindak-lanjut`); await settle(page); await shot(page, 'dash-tindak-lanjut-dark', { fullPage: false })
    await page.goto(url); await settle(page); await shot(page, 'landing-desktop-dark', { fullPage: false })
    await ctx.close()
  }
  {
    const ctx = await browser.newContext(mobile)
    const page = await ctx.newPage()
    await login(page, url)
    await shot(page, 'dash-ringkasan-mobile')
    await page.getByRole('button', { name: 'Buka menu' }).click(); await page.waitForTimeout(400)
    await shot(page, 'dash-menu-mobile', { fullPage: false })
    await page.keyboard.press('Escape')
    await page.goto(`${url}/dasbor/tindak-lanjut`); await settle(page); await shot(page, 'dash-tindak-lanjut-mobile')
    await ctx.close()
  }
  {
    const ctx = await browser.newContext({ ...mobile, viewport: { width: 360, height: 760 } })
    const page = await ctx.newPage()
    await page.goto(url); await settle(page); await shot(page, 'landing-360', { fullPage: false })
    await ctx.close()
  }
} finally {
  await browser.close()
  stop()
}
