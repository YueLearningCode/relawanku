# Database Relawanku

Target lokal: **MySQL 8.4+** pada Laragon. Migration juga kompatibel dengan SQLite untuk pengujian otomatis. Seluruh waktu disimpan dalam **UTC**. ID menggunakan unsigned bigint; setiap tabel domain mempunyai `created_at` dan `updated_at`.

## Tabel domain

### `users`

| Kolom | Tipe / aturan | Keterangan |
| --- | --- | --- |
| `id` | Primary key | ID pengguna. |
| `name` | varchar(255) | Nama; input API dibatasi 100 karakter. |
| `email` | varchar(255), unique | Email login dalam huruf kecil. |
| `password` | varchar(255) | Hash password, tidak dikirim dalam respons. |
| `role` | enum | `relawan`, `koordinator`, `admin`; default `relawan`. |
| `phone` | varchar(25), nullable | Nomor telepon. |
| `bio` | text, nullable | Biodata singkat. |
| `is_active` | boolean | Default true; akun nonaktif tidak dapat memakai API terlindungi. |
| `email_verified_at` | timestamp, nullable | Disiapkan oleh Laravel; verifikasi email belum diterapkan. |
| `remember_token` | varchar(100), nullable | Kolom bawaan Laravel; login API memakai Sanctum. |

### `activities`

| Kolom | Tipe / aturan | Keterangan |
| --- | --- | --- |
| `id` | Primary key | ID kegiatan. |
| `coordinator_id` | FK → users, restrict delete | Harus akun koordinator aktif saat ditetapkan melalui API. |
| `title` | varchar(150) | Nama kegiatan. |
| `description` | text | Deskripsi kegiatan. |
| `category` | varchar(60), index | Kategori bebas, misalnya Lingkungan atau Pendidikan. |
| `location` | varchar(255) | Lokasi kegiatan. |
| `requirements` | text, nullable | Persyaratan peserta. |
| `quota` | unsigned integer | Minimal 1; tidak boleh lebih kecil dari jumlah peserta aktif. |
| `starts_at`, `ends_at` | datetime | Waktu mulai dan selesai; selesai harus setelah mulai. |
| `registration_deadline` | datetime | Batas pendaftaran, paling lambat waktu mulai. |
| `status` | enum | `draft`, `open`, `closed`, `completed`, `cancelled`. |
| `cover_path` | varchar(255), nullable | Path foto sampul pada disk public. |
| `deleted_at` | timestamp, nullable | Soft delete; peserta dan riwayat tetap tersimpan. |

Indeks gabungan `status, starts_at` mendukung daftar kegiatan. API menyertakan `cover_url`; daftar/detail juga memuat `active_registrations_count`.

### `registrations`

| Kolom | Tipe / aturan | Keterangan |
| --- | --- | --- |
| `id` | Primary key | ID pendaftaran. |
| `activity_id` | FK → activities, restrict delete | Kegiatan yang diikuti. |
| `user_id` | FK → users, restrict delete | Akun relawan. |
| `status` | enum | `registered` atau `cancelled`. |
| `registered_at` | datetime | Waktu pendaftaran terakhir. |
| `cancelled_at` | datetime, nullable | Waktu pembatalan terakhir. |

Pasangan `activity_id, user_id` unik. Pendaftaran ulang menggunakan baris yang sama. Pembatalan hanya sebelum kegiatan mulai dan sebelum memiliki absensi; tugas peserta yang dibatalkan dilepas penanggung jawabnya.

### `tasks`

| Kolom | Tipe / aturan | Keterangan |
| --- | --- | --- |
| `id` | Primary key | ID tugas. |
| `activity_id` | FK → activities, restrict delete | Kegiatan pemilik tugas. |
| `assigned_to` | FK → users, nullable, restrict delete | Relawan aktif yang terdaftar pada kegiatan terkait. |
| `title` | varchar(150) | Nama tugas. |
| `description` | text, nullable | Rincian tugas. |
| `due_at` | datetime, nullable | Batas tugas, tidak melewati waktu selesai kegiatan saat ditetapkan. |
| `status` | enum | `pending`, `in_progress`, `completed`. |
| `deleted_at` | timestamp, nullable | Soft delete. |

### `attendance_sessions`

| Kolom | Tipe / aturan | Keterangan |
| --- | --- | --- |
| `id` | Primary key | ID sesi absensi. |
| `activity_id` | FK → activities, restrict delete | Kegiatan terkait. |
| `created_by` | FK → users, restrict delete | Pengelola pembuka sesi. |
| `code_hash` | varchar(64) | SHA-256 dari kode acak delapan karakter heksadesimal. Tidak dikirim ke klien. |
| `expires_at` | datetime | Kedaluwarsa maksimal 120 menit dan tidak melewati akhir kegiatan. |
| `closed_at` | datetime, nullable | Sesi ditutup manual atau digantikan sesi baru. |

Kode asli hanya dikirim sekali saat pembukaan sesi. Absensi hanya pada kegiatan berstatus `open`/`closed` yang sedang berlangsung. Membuka sesi baru menutup sesi sebelumnya.

### `attendances`

| Kolom | Tipe / aturan | Keterangan |
| --- | --- | --- |
| `id` | Primary key | ID kehadiran. |
| `registration_id` | FK → registrations, unique, restrict delete | Satu kehadiran per pendaftaran. |
| `attendance_session_id` | FK → attendance_sessions, restrict delete | Sesi yang digunakan. |
| `checked_in_at` | datetime | Waktu absensi menurut server. |

Kegiatan dan peserta dapat ditelusuri melalui registration. Klien tidak dapat mengirim user ID atau waktu absensi sendiri.

### `documentations`

| Kolom | Tipe / aturan | Keterangan |
| --- | --- | --- |
| `id` | Primary key | ID dokumentasi. |
| `activity_id` | FK → activities, restrict delete | Kegiatan terkait. |
| `uploaded_by` | FK → users, restrict delete | Pengunggah. |
| `image_path` | varchar(255) | Path foto pada disk public. |
| `caption` | text, nullable | Keterangan foto. |
| `deleted_at` | timestamp, nullable | Soft delete; berkas tetap disimpan sebagai riwayat. |

API menyertakan `image_url`. JPEG, PNG, dan WebP dibatasi 5 MB. URL berkas foto bersifat publik; akses daftar dokumentasi dibatasi untuk peserta/pengelola.

### `activity_logs`

| Kolom | Tipe / aturan | Keterangan |
| --- | --- | --- |
| `id` | Primary key | ID riwayat. |
| `actor_id` | FK → users, nullable, null on delete | Pelaku aksi. |
| `activity_id` | FK → activities, nullable, null on delete | Kegiatan terkait; nullable untuk pengelolaan akun. |
| `event` | varchar(80), index | Misalnya `activity.created`, `task.updated`, atau `attendance.checked_in`. |
| `properties` | JSON, nullable | ID objek, nama kolom yang diubah, atau status; tidak menyimpan password/token/kode asli. |

Mutasi kegiatan dan riwayatnya berada dalam transaksi yang sama. Tabel ini tidak memiliki endpoint edit atau hapus.

## Relasi dan integritas

- Koordinator memiliki banyak kegiatan.
- Relawan mengikuti banyak kegiatan melalui registrations.
- Kegiatan memiliki banyak tugas, sesi absensi, dokumentasi, dan log.
- Registration memiliki maksimal satu attendance.
- Attendance merujuk sesi absensi dan registration dari kegiatan yang sama; kesesuaian ini diperiksa di API.
- Foreign key menjaga referensi. Aturan peran, jadwal, kuota, dan kesesuaian kegiatan diperiksa oleh API; hindari penulisan langsung ke tabel domain.
- Pendaftaran, perubahan kuota, pembatalan, tugas, dan absensi mengunci baris kegiatan dalam transaksi. Pengujian SQLite tidak membuktikan perilaku konkurensi MySQL.

## Tabel infrastruktur Laravel

`personal_access_tokens` menyimpan hash token Sanctum. Tabel `password_reset_tokens`, `sessions`, `cache`, `cache_locks`, `jobs`, `job_batches`, `failed_jobs`, dan `migrations` berasal dari kerangka Laravel. Endpoint reset password belum disediakan. Konfigurasi awal memakai session array, antrean sync, dan cache database.

## Data contoh lokal

```powershell
php artisan db:seed --class=DemoSeeder
```

Seeder hanya berjalan pada `APP_ENV=local` atau `testing`. Isinya tiga akun, tiga kegiatan, tiga pendaftaran, tiga tugas, dan log awal. Sesi absensi, kehadiran, serta foto dibuat lewat API agar tetap mencerminkan tindakan nyata.

| Peran | Email | Password awal |
| --- | --- | --- |
| Admin | admin@relawanku.test | Nilai `DEMO_PASSWORD` di `.env` |
| Koordinator | koordinator@relawanku.test | Nilai `DEMO_PASSWORD` di `.env` |
| Relawan | relawan@relawanku.test | Nilai `DEMO_PASSWORD` di `.env` |

Nilai contoh `DEMO_PASSWORD` adalah `RelawanDemo123!`. Seeder tidak mengganti password akun yang sudah ada dan tidak mengembalikan kegiatan demo yang sudah diarsipkan. Tanggal kegiatan ditentukan saat pertama dibuat, sehingga seeder ulang tidak menggeser jadwal yang telah tersimpan.
