/**
 * Data DEMO sintetis (bukan data nyata): 3 kader, 60 warga RW 01–05, pemeriksaan, profil, tindak lanjut, jadwal Posyandu.
 * Semua dibuat lewat jalur kode yang sama dengan API (createCadre, reserveIds, pushBatch, createSchedule).
 *
 *   npm run seed:demo               (development)
 *   SEED_DEMO_CONFIRM=yes npm run seed:demo   (wajib bila NODE_ENV=production)
 *
 * Kata sandi semua akun demo: DEMO_PASSWORD (bawaan "demo1234").
 */
import { randomUUID } from 'node:crypto';
import { loadConfig } from '../config.js';
import { createPool, withTx } from '../db/pool.js';
import { runMigrations } from '../db/migrate.js';
import { ensureAdmin } from '../core/bootstrap.js';
import type { Actor, Deps } from '../core/types.js';
import { getAccount } from '../auth/accounts.js';
import { hashPassword } from '../auth/password.js';
import { createCadre } from '../admin/routes.js';
import { createSchedule } from '../admin/posyandu.js';
import { reserveIds } from '../sync/ids.js';
import { pushBatch, type PushItem } from '../sync/service.js';
import { addDays, DAY_MS, localDate } from '../core/time.js';
import { audit } from '../core/audit.js';

const config = loadConfig();
if (config.env === 'production' && process.env.SEED_DEMO_CONFIRM !== 'yes') {
  console.error('Menolak mengisi data demo di production. Atur SEED_DEMO_CONFIRM=yes bila memang disengaja (mis. server uji).');
  process.exit(1);
}
const DEMO_PASSWORD = process.env.DEMO_PASSWORD ?? 'demo1234';
const pool = createPool(config.databaseUrl);
const deps: Deps = { pool, config, ai: null, now: () => Date.now() };
const log = { error: (...a: unknown[]) => console.error(...a) } as unknown as Parameters<typeof pushBatch>[4];

/** PRNG deterministik agar data demo sama setiap kali. */
function rng(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 2 ** 32;
  };
}
const rand = rng(20261003);
const pick = <T>(xs: readonly T[]): T => xs[Math.floor(rand() * xs.length)]!;
const between = (a: number, b: number) => Math.round(a + rand() * (b - a));

function actorFrom(a: NonNullable<Awaited<ReturnType<typeof getAccount>>>): Actor {
  return { id: a.sehati_id, role: a.role, fullName: a.full_name, rw: a.rw, village: a.village, sessionHash: 'seed' };
}

const it = (type: string, entityId: string, subjectId: string | null, payload: object, version = 1): PushItem => ({
  id: randomUUID(), type, entityId, subjectId, operation: 'UPSERT', version, payload: JSON.stringify(payload),
});

async function main() {
  await runMigrations(pool);
  await ensureAdmin(pool, config, { info: console.log, warn: console.warn });
  const seeded = await pool.query(`SELECT 1 FROM settings WHERE key = 'demo_seeded'`);
  if ((seeded.rowCount ?? 0) > 0) {
    console.log('Data demo sudah ada; tidak ada yang diubah.');
    return;
  }
  const adminAcc = await getAccount(pool, config.adminId);
  if (!adminAcc) throw new Error('Admin tidak ditemukan');
  const admin = actorFrom({ ...adminAcc, village: 'Desa Mirigambar' });
  const village = 'Desa Mirigambar';
  const now = Date.now();

  // 1) Kader (lewat jalur admin)
  const cadreSpecs = [
    { fullName: 'Kader Demo Melati', rw: '01-02', facilityId: 'fac-melati' },
    { fullName: 'Kader Demo Mawar', rw: '03', facilityId: 'fac-mawar' },
    { fullName: 'Kader Demo Kenanga', rw: '04-05', facilityId: 'fac-kenanga' },
  ];
  const cadres: Actor[] = [];
  for (const c of cadreSpecs) {
    const id = await createCadre(deps, admin, { ...c, password: DEMO_PASSWORD });
    cadres.push(actorFrom((await getAccount(pool, id))!));
  }
  const cadreFor = (rw: string) => (['01', '02'].includes(rw) ? cadres[0]! : rw === '03' ? cadres[1]! : cadres[2]!);

  // 2) Warga: ID dicadangkan per kader, didaftarkan lewat push sinkronisasi (seperti aplikasi kader)
  const rws = ['01', '02', '03', '04', '05'];
  const pw = await hashPassword(DEMO_PASSWORD);
  let total = 0;
  for (const rw of rws) {
    const kader = cadreFor(rw);
    const deviceId = `seed-demo-${kader.id}`;
    const { ids } = await withTx(pool, (c) => reserveIds(c, { prefix: 'HM', count: 12, deviceId, reservedBy: kader.id, now }));
    for (const [n, id] of ids.entries()) {
      total++;
      const sex = rand() < 0.55 ? 'FEMALE' : 'MALE';
      const birth = `${between(1950, 1995)}-${String(between(1, 12)).padStart(2, '0')}-${String(between(1, 28)).padStart(2, '0')}`;
      const createdAt = now - between(120, 200) * DAY_MS;
      const hhId = `hh-demo-${id}`;
      const smoker = sex === 'MALE' && rand() < 0.45;
      const baseSys = between(105, 165);
      const items: PushItem[] = [
        it('user', id, id, {
          sehatiId: id, fullName: `Warga Demo ${String(total).padStart(3, '0')}`, birthDate: birth, sex, village, rw, rt: `0${between(1, 4)}`,
          phone: null, role: 'WARGA', qrToken: randomUUID().replace(/-/g, ''), goals: '', consentLocal: true, consentServerSync: true,
          consentHealthConnect: false, consentAt: createdAt, onboardingDone: true, assessmentDone: true, hasAccount: n % 3 === 0,
          registeredBy: kader.id, householdId: hhId, isDemo: true, createdAt, updatedAt: createdAt, syncStatus: 'SYNCED', serverId: null, version: 1,
        }),
        it('household', hhId, id, { id: hhId, headName: `KK Demo ${String(total).padStart(3, '0')}`, rw, rt: `0${between(1, 4)}`, createdAt, updatedAt: createdAt, syncStatus: 'SYNCED', serverId: null, version: 1 }),
      ];
      // sepertiga warga punya akun login (kredensial format aplikasi); sisanya dapat diaktifkan lewat /auth/activate
      if (n % 3 === 0) items.push(it('credential', id, id, { sehatiId: id, salt: pw.salt, hash: pw.hash, iterations: pw.iterations }));
      const takenAt = createdAt + DAY_MS;
      items.push(it('assessment', `as-demo-${id}`, id, {
        id: `as-demo-${id}`, userId: id, takenAt, heightCm: between(148, 175), weightKg: between(45, 85), waistCm: between(68, 100),
        knownHypertension: baseSys >= 140, knownDiabetes: rand() < 0.1, knownDyslipidemia: rand() < 0.15, knownHeartDisease: false,
        knownKidneyDisease: false, otherConditions: '', familyHypertension: rand() < 0.4, familyDiabetes: rand() < 0.2, familyCardio: rand() < 0.2,
        smokingStatus: smoker ? 'CURRENT' : pick(['NEVER', 'NEVER', 'FORMER']), smokingProduct: smoker ? 'KRETEK' : '', cigarettesPerDay: smoker ? between(4, 16) : 0,
        vegetableDays: between(1, 7), fruitDays: between(0, 6), saltyFrequent: rand() < 0.5, sugaryFrequent: rand() < 0.4, fattyFrequent: rand() < 0.4,
        activeDays: between(0, 6), activeMinutes: between(10, 45), activityIntensity: 'MODERATE', sedentaryHours: between(3, 10), sleepHours: between(5, 8),
        sleepQuality: 'FAIR', stressLevel: between(1, 4), redFlagSymptom: false, createdAt: takenAt, updatedAt: takenAt, syncStatus: 'SYNCED', serverId: null, version: 1,
      }));
      // 1–4 pemeriksaan Posyandu dalam 5 bulan terakhir
      let lastSys = baseSys;
      let lastDia = 70;
      const visits = between(1, 4);
      for (let v = 0; v < visits; v++) {
        // kunjungan pertama umumnya dalam 30 hari terakhir agar dashboard berisi
        const at = v === 0 && rand() < 0.75 ? now - between(1, 28) * DAY_MS : now - (between(1, 4) * 30 + between(0, 25)) * DAY_MS;
        const mid = `m-demo-${id}-${v}`;
        lastSys = Math.max(95, baseSys + between(-8, 8));
        lastDia = Math.round(lastSys * 0.62) + between(-4, 4);
        items.push(it('measurement', mid, id, {
          id: mid, userId: id, measuredAt: at, source: 'POSYANDU', operatorId: kader.id, facilityId: null, visitId: null, notes: '',
          verification: 'VERIFIED', createdAt: at, updatedAt: at, syncStatus: 'SYNCED', serverId: null, version: 1,
        }));
        const w = between(45, 85);
        const h = between(148, 175);
        items.push(it('measurement_detail', mid, id, {
          measurementId: mid,
          anthropometry: { id: `an-${mid}`, measurementId: mid, userId: id, weightKg: w, heightCm: h, waistCm: between(68, 100), bmi: Math.round((w / (h / 100) ** 2) * 10) / 10 },
          bloodPressure: { id: `bp-${mid}`, measurementId: mid, userId: id, systolic: lastSys, diastolic: lastDia, heartRate: between(62, 95) },
          glucose: rand() < 0.5 ? { id: `gl-${mid}`, measurementId: mid, userId: id, mgDl: between(85, 210), fasting: false } : null,
          lipid: rand() < 0.3 ? { id: `li-${mid}`, measurementId: mid, userId: id, totalCholesterol: between(160, 260) } : null,
        }));
      }
      const high = lastSys >= 140 || lastDia >= 90;
      const level = lastSys >= 160 ? 'MEDICAL_FOLLOW_UP' : high ? 'HIGHER_MONITORING' : smoker ? 'RISK_AWARENESS' : 'HEALTHY_HABIT';
      items.push(it('profile', id, id, { userId: id, level, findingsJson: '[]', planJson: '[]', rulesetVersion: 'sehati-rules-1.0', computedAt: now, syncStatus: 'SYNCED', serverId: null, version: 1 }));
      if (high) {
        const fid = `fu-demo-${id}`;
        const urgent = lastSys >= 180 || lastDia >= 110;
        items.push(it('followup', fid, id, {
          id: fid, userId: id, visitId: null, type: urgent ? 'PUSKESMAS_EVALUATION' : 'REPEAT_MEASUREMENT', reasonCode: urgent ? 'bp_urgent' : 'bp_elevated',
          reason: urgent ? `Tekanan darah ${lastSys}/${lastDia} sangat tinggi` : `Tekanan darah ${lastSys}/${lastDia} perlu diukur ulang`,
          priority: urgent ? 3 : 2, status: pick(['OPEN', 'OPEN', 'SCHEDULED', 'DONE']), assignedCadreId: rand() < 0.5 ? kader.id : null,
          dueAt: now + between(-10, 14) * DAY_MS, createdBy: kader.id, closedAt: null, notes: '', createdAt: now - 20 * DAY_MS, updatedAt: now - 20 * DAY_MS,
          syncStatus: 'SYNCED', serverId: null, version: 1,
        }));
      }
      const results = await pushBatch(deps, kader, deviceId, items, log);
      const bad = results.filter((r) => r.status !== 'OK');
      if (bad.length) throw new Error(`Push demo gagal untuk ${id}: ${JSON.stringify(bad[0])}`);
    }
  }

  // 3) Jadwal Posyandu 4 minggu ke depan
  const today = localDate(now, config.timeZone);
  for (const [i, rw] of rws.entries()) {
    for (let week = 0; week < 4; week++) {
      await createSchedule(pool, {
        rw, date: addDays(today, week * 7 + i + 1), startTime: '08:00', endTime: '11:00', location: `Balai RW ${rw}`,
        notes: 'Data demo — bawa QR SEHATI', }, admin.id, now);
    }
  }

  await pool.query(`INSERT INTO settings (key, value, version, updated_at, updated_by) VALUES ('demo_seeded', 'true', 1, $1, 'seed')`, [now]);
  await audit(pool, { actorId: admin.id, actorRole: 'ADMIN', action: 'seed_demo', detail: `warga=${total} kader=${cadres.length}` }, now);
  console.log(`Data demo dibuat: ${cadres.length} kader (${cadres.map((c) => c.id).join(', ')}), ${total} warga, kata sandi "${DEMO_PASSWORD}".`);
}

main()
  .catch((e: unknown) => {
    console.error('Seed demo gagal:', e instanceof Error ? e.message : e);
    process.exitCode = 1;
  })
  .finally(() => pool.end());
