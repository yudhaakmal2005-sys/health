import { z } from 'zod';
import { Errors, zodMessage } from './errors.js';

/** Validasi masukan dengan zod; galat → 400 VALIDATION_ERROR. */
export function parse<T extends z.ZodType>(schema: T, data: unknown): z.infer<T> {
  const r = schema.safeParse(data);
  if (!r.success) throw Errors.validation(zodMessage(r.error));
  return r.data;
}

export const zDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine((s) => {
  const d = new Date(`${s}T00:00:00Z`);
  return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === s;
}, 'Tanggal tidak valid');
export const zTime = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/);
export const zDeviceId = z.string().trim().min(1).max(100);
export const zRw = z.string().trim().regex(/^\d{1,3}$/);
