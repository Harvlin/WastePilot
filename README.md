# WastePilot

WastePilot adalah aplikasi manajemen operasional limbah produksi yang membantu tim pabrik menjalankan alur kerja harian secara terstruktur: mulai dari `start batch`, pencatatan `inventory`, klasifikasi `waste`, `batch close`, sampai pemeriksaan `integrity`.

Fokus utama proyek ini adalah membuat sistem yang **langsung bisa dipakai operasional**, bukan sekadar dashboard. Karena itu, arsitektur dibangun dengan pendekatan **backend-first** (Spring Boot API) dan **selective frontend fallback** (mock) agar UX tetap berjalan saat layanan tertentu belum tersedia.

---

## Demo & Akses

- **Frontend Demo:** https://waste-pilot.vercel.app/
- **Repository:** https://github.com/Harvlin/WastePilot

> Catatan penting untuk juri/pengguna:  
> Jika backend belum dideploy publik, maka saat membuka link frontend publik aplikasi akan berjalan pada mode hybrid/fallback sesuai konfigurasi environment.  
> Untuk melihat integrasi backend penuh, jalankan fullstack secara lokal mengikuti panduan di bawah.

---

## Kenapa WastePilot Menarik

- **Backend-first, bukan mock-first**: frontend selalu mencoba API Spring terlebih dulu, fallback ke mock hanya untuk skenario tertentu agar aplikasi tetap usable.
- **Clean architecture yang scalable**: pemisahan `controller -> service -> repository`, DTO contracts yang jelas, dan mapper untuk menjaga boundary antar layer.
- **Security-by-design**: JWT authentication, password hashing (`BCrypt`), rate limiting endpoint auth, login lockout, serta CORS terkontrol.
- **Operational integrity sebagai fitur inti**: ada `Activity Logs`, `Audit Trail`, dan `Integrity Overview` untuk menjaga jejak perubahan data.
- **Resilience untuk demo dan growth**: fitur berjalan walau komponen AI belum live, sehingga tim tetap bisa menunjukkan proses bisnis end-to-end.
- **Quality gate nyata**: integration tests backend untuk flow kritikal (`auth`, `settings`, `materials`, `templates`, `operations`), plus build validation frontend.

---

## Fitur Utama

### 1) Authentication
- Signup/Login berbasis JWT.
- Endpoint `me` untuk validasi sesi.
- Error handling konsisten untuk invalid credentials, validation error, dll.

### 2) Materials
- CRUD material produksi.
- Validasi nama unik, kategori, circular grade.

### 3) Templates
- CRUD template komposisi produksi.
- Sinkron line materials + validasi SKU unik.

### 4) Operations (Core Workflow)
- Start batch.
- Log inventory IN/OUT.
- Log waste per batch + recovery ke inventory.
- Auto summary untuk close batch (variance, landfill share, confidence).
- Close batch dengan validasi reason ketika variance melewati threshold.

### 5) Integrity
- Activity logs.
- Audit trail perubahan data.
- Integrity overview (confidence avg, open red flags, overdue closures).

### 6) Settings
- User settings profile/workspace.
- Kontrak backend disesuaikan dengan kebutuhan frontend.

### 7) Insights / Analytics / Reports
- Sudah ada experience di frontend.
- Untuk bagian AI-heavy, saat ini didesain agar tetap bisa berjalan dengan mock/fallback.

---

## Panduan Penggunaan (Diambil dari halaman `/how-to-use`)

Bagian ini mencerminkan workflow aktual yang ada di aplikasi.

## Core Shift Flow (Operations)

1. **Start Batch**
   - Isi `Template Name`, `Output Units`, `Estimated Waste`.
   - Klik `Start Batch`.
   - Selesai jika batch baru muncul dengan status `running`.

2. **Log Material**
   - Masuk `Inventory Input`.
   - Pilih tipe movement `IN` atau `OUT`.
   - Untuk `OUT`, wajib pilih running batch.
   - Selesai jika log muncul di tabel `Inventory Logs`.

3. **Log Waste**
   - Pilih running batch, material, destination, quantity.
   - Gunakan auto-convert untuk `reuse`/`repair` jika diperlukan.
   - Selesai jika `Waste Logs` menampilkan destination + recovery status.

4. **Close Batch**
   - Buka `Batch Close Assistant`.
   - Review auto summary.
   - Jika variance > threshold, isi `Close Reason`.
   - Selesai jika status batch berubah jadi `completed`.

5. **Integrity Check**
   - Buka tab integrity.
   - Review activity logs + audit trail.
   - Pastikan tidak ada red flag kritikal tertinggal.

## Supporting Modules

- **AI Vision Scan (`/scan`)**: OCR invoice -> validasi row -> simpan sebagai inventory IN.
- **Materials (`/materials`)**: kelola master material.
- **Production Templates (`/templates`)**: siapkan recipe/baseline waste.
- **Insights (`/insights`)**: action queue rekomendasi/anomali.
- **Analytics (`/analytics`)**: trend dan snapshot performa.

## Golden Rules

- Selalu pastikan ada satu running batch sebelum log `OUT` atau waste.
- Isi quantity + unit dengan benar di setiap form.
- Isi close reason saat variance melebihi threshold.

---

## Arsitektur Sistem

### Frontend
- React 18 + TypeScript + Vite
- Routing: `react-router-dom`
- UI: Tailwind + Radix/shadcn components
- API client terpusat di `internal-api.ts`
- Mode data provider:
  - `spring` (backend aktif)
  - `mock` (frontend mock)
  - `spring + fallback` (hybrid)

### Backend
- Spring Boot 3.3.x (Java 21)
- Spring Web, Security, Data JPA, Validation
- JWT Resource Server
- Flyway migrations
- MySQL untuk runtime, H2 untuk test profile

### Data Flow
- Frontend submit request ke API backend.
- Backend validasi + proses business logic.
- Error response konsisten via global exception handler.
- Untuk beberapa operasi read tertentu, frontend dapat fallback ke mock bila backend/network gagal (sesuai konfigurasi).

---

## API Coverage (yang sudah tersedia)

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`

- `GET /api/v1/settings`
- `PUT /api/v1/settings`

- `GET /api/v1/materials`
- `POST /api/v1/materials`
- `PUT /api/v1/materials/{id}`
- `DELETE /api/v1/materials/{id}`

- `GET /api/v1/templates`
- `POST /api/v1/templates`
- `PUT /api/v1/templates/{id}`
- `DELETE /api/v1/templates/{id}`

- `GET /api/v1/operations`
- `POST /api/v1/operations/batches`
- `POST /api/v1/operations/inventory-logs`
- `POST /api/v1/operations/waste-logs`
- `POST /api/v1/operations/waste-logs/recover`
- `GET /api/v1/operations/batch-close/summary/{batchId}`
- `POST /api/v1/operations/batch-close`

- `GET /api/v1/integrity/activity-logs`
- `GET /api/v1/integrity/audit-trail`
- `GET /api/v1/integrity/overview`

---

## Jalankan Lokal (Fullstack)

## Prasyarat

- Node.js 18+ (disarankan 20+)
- npm
- Java 21
- MySQL (default config tersedia di `application.properties`)

## 1) Jalankan Backend

```bash
cd wastepilot
./mvnw spring-boot:run
```

Backend default di `http://localhost:8080`.

## 2) Siapkan Frontend Env

Buat `frontend/.env` dari `frontend/.env.example`:

```env
VITE_INTERNAL_API_PROVIDER=spring
VITE_SPRING_API_BASE_URL=http://localhost:8080
VITE_SPRING_API_TIMEOUT_MS=10000
VITE_SPRING_FALLBACK_TO_MOCK=true
```

## 3) Jalankan Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend default di `http://localhost:5173`.

---

## Environment Configuration

## Frontend (`frontend/.env`)

- `VITE_INTERNAL_API_PROVIDER` = `spring` atau `mock`
- `VITE_SPRING_API_BASE_URL` = base URL backend
- `VITE_SPRING_API_TIMEOUT_MS` = timeout request ke backend
- `VITE_SPRING_FALLBACK_TO_MOCK` = `true/false`

## Backend (`wastepilot/src/main/resources/application.properties`)

Variabel penting:
- `SERVER_PORT`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_ACCESS_TOKEN_TTL_SECONDS`

---

## Testing & Quality Check

## Backend

```bash
cd wastepilot
./mvnw -q test
```

## Frontend

```bash
cd frontend
npm run build
npm run test
```

---

## Catatan Penjurian / Verifikasi

- **Jika hanya membuka frontend deploy URL** dan backend tidak dipublish, aplikasi akan tampil dengan mode fallback/mock sesuai env.
- **Jika ingin verifikasi backend real**, juri disarankan menjalankan fullstack lokal.
- Pendekatan ini dipilih agar demo tetap stabil, sekaligus menunjukkan implementasi backend yang nyata dan siap dikembangkan lanjut.

---

## Roadmap

- Deploy backend publik untuk full online E2E demo.
- Hardening relasi audit/recovery agar seluruh metadata persisten penuh.
- Integrasi AI service production (OCR/insight engine) menggantikan placeholder/mock.
- Penambahan observability (structured logs, metrics dashboards).

---

## Tim

Tim WastePilot

---