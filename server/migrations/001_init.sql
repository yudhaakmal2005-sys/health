-- SEHATI API v1: skema awal. Waktu disimpan sebagai epoch milidetik (bigint) agar sama dengan aplikasi.

-- Akun & registri subjek. Warga yang didaftarkan kader tanpa kata sandi tetap punya baris (hash NULL)
-- agar RBAC/visibilitas (role, RW, consent) dapat diperiksa.
CREATE TABLE accounts (
  sehati_id            text PRIMARY KEY,
  role                 text NOT NULL CHECK (role IN ('WARGA', 'KADER', 'ADMIN')),
  full_name            text NOT NULL DEFAULT '',
  rw                   text NOT NULL DEFAULT '',
  village              text NOT NULL DEFAULT '',
  salt                 text,
  hash                 text,
  iterations           integer,
  active               boolean NOT NULL DEFAULT true,
  consent_server_sync  boolean NOT NULL DEFAULT false,
  registered_by        text,
  failed_attempts      integer NOT NULL DEFAULT 0,
  locked_until         bigint NOT NULL DEFAULT 0,
  last_seen_at         bigint,
  created_at           bigint NOT NULL,
  updated_at           bigint NOT NULL
);
CREATE INDEX accounts_role_rw ON accounts (role, rw);

-- Sesi: hanya hash SHA-256 (hex) token yang disimpan.
CREATE TABLE sessions (
  token_hash  text PRIMARY KEY,
  account_id  text NOT NULL REFERENCES accounts (sehati_id) ON DELETE CASCADE,
  device_id   text,
  created_at  bigint NOT NULL,
  expires_at  bigint NOT NULL
);
CREATE INDEX sessions_account ON sessions (account_id);
CREATE INDEX sessions_expires ON sessions (expires_at);

-- Reservasi SEHATI ID. Penghitung per prefix tidak pernah mundur, nomor tidak dipakai ulang.
CREATE TABLE id_counters (
  prefix  text PRIMARY KEY,
  next    bigint NOT NULL
);
CREATE TABLE id_reservations (
  sehati_id    text PRIMARY KEY,
  prefix       text NOT NULL,
  device_id    text NOT NULL,
  reserved_by  text,
  created_at   bigint NOT NULL,
  expires_at   bigint NOT NULL,
  used_at      bigint
);
CREATE INDEX id_reservations_device ON id_reservations (device_id);

-- Entitas tersinkron: payload JSON entitas Room apa adanya. seq naik setiap perubahan (kursor pull).
CREATE SEQUENCE entity_seq;
CREATE TABLE entities (
  type        text NOT NULL,
  entity_id   text NOT NULL,
  server_id   uuid NOT NULL DEFAULT gen_random_uuid(),
  subject_id  text,
  subject_rw  text,
  version     integer NOT NULL,
  payload     jsonb,
  deleted     boolean NOT NULL DEFAULT false,
  seq         bigint NOT NULL,
  updated_by  text,
  device_id   text,
  updated_at  bigint NOT NULL,
  PRIMARY KEY (type, entity_id)
);
CREATE UNIQUE INDEX entities_seq ON entities (seq);
CREATE INDEX entities_subject ON entities (subject_id);
CREATE INDEX entities_rw_seq ON entities (subject_rw, seq);
CREATE INDEX entities_type_seq ON entities (type, seq);

-- Kunci idempoten push.
CREATE TABLE sync_receipts (
  idem_key    text PRIMARY KEY,
  account_id  text NOT NULL,
  type        text NOT NULL,
  entity_id   text NOT NULL,
  status      text NOT NULL,
  server_id   uuid,
  created_at  bigint NOT NULL
);
CREATE INDEX sync_receipts_created ON sync_receipts (created_at);

-- Audit: tanpa isi data kesehatan, kata sandi, atau token.
CREATE TABLE audit_log (
  id          bigserial PRIMARY KEY,
  at          bigint NOT NULL,
  actor_id    text NOT NULL,
  actor_role  text NOT NULL,
  action      text NOT NULL,
  subject_id  text,
  detail      text NOT NULL DEFAULT ''
);
CREATE INDEX audit_log_at ON audit_log (at DESC);

-- Pemakaian AI: hanya hitungan per akun per hari (zona waktu server), tanpa isi percakapan.
CREATE TABLE ai_usage (
  account_id   text NOT NULL,
  day          date NOT NULL,
  messages     integer NOT NULL DEFAULT 0,
  emergencies  integer NOT NULL DEFAULT 0,
  PRIMARY KEY (account_id, day)
);
CREATE INDEX ai_usage_day ON ai_usage (day);

-- Pengaturan terpusat (mis. ambang klinis) dengan nomor versi.
CREATE TABLE settings (
  key         text PRIMARY KEY,
  value       jsonb,
  version     integer NOT NULL DEFAULT 0,
  updated_at  bigint NOT NULL,
  updated_by  text
);

CREATE TABLE posyandu_schedule (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  rw          text NOT NULL,
  date        date NOT NULL,
  start_time  text NOT NULL,
  end_time    text NOT NULL,
  location    text NOT NULL,
  notes       text NOT NULL DEFAULT '',
  created_by  text,
  created_at  bigint NOT NULL,
  updated_at  bigint NOT NULL
);
CREATE INDEX posyandu_schedule_date ON posyandu_schedule (date, rw);

-- Konversi aman teks JSON → angka (NULL bila bukan angka) untuk agregat dashboard.
CREATE FUNCTION sehati_num(t text) RETURNS double precision
  LANGUAGE sql IMMUTABLE PARALLEL SAFE
  AS $$ SELECT CASE WHEN t ~ '^-?[0-9]+(\.[0-9]+)?([eE][-+]?[0-9]+)?$' THEN t::double precision END $$;
