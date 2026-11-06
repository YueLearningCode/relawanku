# REST API Relawanku

Base URL lokal: `http://127.0.0.1:8000/api`.

- [Spesifikasi OpenAPI 3.0 JSON](openapi.json)
- [Koleksi Postman](../postman/Relawanku.postman_collection.json)
- [Environment Postman lokal](../postman/Relawanku.local.postman_environment.json)
- [Kolom dan relasi database](DATABASE.md)

## Menjalankan backend

Jalankan perintah Composer, Artisan, dan PHPUnit dari folder `backend`:

```powershell
cd E:\laragon\www\relawanku\relawanku\backend
```

Pada komputer lain, sesuaikan lokasi clone repository. Folder `docs`, `postman`, dan `scripts` tetap berada di akar repository. File konfigurasi lokal Laravel berada di `backend/.env`.

Prasyarat: PHP 8.3+ (lingkungan implementasi: 8.4.5), Composer 2, serta MySQL 8.4+ di Laragon. Ekstensi PHP yang dibutuhkan antara lain PDO MySQL, mbstring, openssl, fileinfo, intl, dan GD untuk pengujian gambar. PHP CLI pada lingkungan ini juga menyediakan SQLite untuk tes.

1. Jalankan MySQL dari Laragon dan buat database kosong bernama `relawanku` dengan charset `utf8mb4`. Contoh SQL melalui phpMyAdmin:

   ```sql
   CREATE DATABASE IF NOT EXISTS relawanku
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. Instal dependensi dan siapkan `.env` jika belum ada:

   ```powershell
   composer install
   if (-not (Test-Path .env)) { Copy-Item .env.example .env }
   ```

3. Sesuaikan `DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME`, dan `DB_PASSWORD` di `.env`. Konfigurasi contoh memakai MySQL `127.0.0.1:3306`, database `relawanku`, user `root`, dan password kosong sesuai instalasi lokal Laragon yang umum. Jalankan `php artisan key:generate` hanya bila `APP_KEY` masih kosong.

4. Jalankan migration, data demo opsional, tautan penyimpanan, dan server:

   ```powershell
   php artisan migrate
   php artisan db:seed --class=DemoSeeder
   php artisan storage:link
   php artisan serve --host=127.0.0.1 --port=8000
   ```

5. Akses `GET http://127.0.0.1:8000/api/health`. Endpoint ini memeriksa aplikasi, bukan koneksi database. Untuk memeriksa migration, gunakan `php artisan migrate:status`.

Backend hanya melayani API; tidak memerlukan Node.js, npm, Vite, atau build frontend. Document root pada virtual host Laragon harus mengarah ke folder `backend/public` dari akar repository.

## Postman

1. Impor kedua file JSON di folder `postman` dan pilih environment **Relawanku - Local**.
2. Sesuaikan `base_url` dan `demo_password` bila konfigurasi lokal berbeda. Akun demo tersedia setelah menjalankan DemoSeeder.
3. Jalankan folder **01 Alur utama - jalankan folder ini** melalui Collection Runner.
4. Folder tersebut login tiga peran, membuat kegiatan baru, mendaftarkan relawan, membagikan tugas, melakukan absensi, membaca riwayat, lalu mengarsipkan kegiatan dan tugas uji. Kegiatan diubah menjadi sedang berlangsung untuk mensimulasikan absensi. Gunakan data lokal untuk alur ini.
5. Token, ID objek, dan kode absensi disimpan otomatis sebagai variabel environment. Tanggal kegiatan dibuat relatif terhadap waktu eksekusi agar contoh dapat dijalankan ulang.
6. Folder **02 Referensi endpoint - jalankan satu per satu** berisi seluruh endpoint. Jalankan request sesuai prasyaratnya; pembatalan pendaftaran memerlukan kegiatan yang belum mulai, sedangkan absensi memerlukan kegiatan yang sedang berlangsung. Jangan menjalankan seluruh koleksi sekaligus.
7. Untuk request foto, pilih file lokal pada **Body → form-data → image**. Unggahan foto dijalankan manual dan tidak termasuk alur otomatis folder 01.

Alur otomatis mencakup login admin, tetapi pengelolaan akun dan unggahan foto diuji terpisah melalui tes fitur. Request referensi login memakai akun koordinator; untuk token admin/relawan, jalankan request login masing-masing di folder 01.

## Autentikasi dan bentuk respons

Kirim header berikut pada request terlindungi:

```http
Accept: application/json
Authorization: Bearer <token>
Content-Type: application/json
```

Untuk unggah foto, gunakan `multipart/form-data` dan biarkan Postman/Retrofit membentuk boundary. Token Sanctum berlaku tujuh hari. Logout mencabut token yang digunakan; perangkat lain tetap memiliki tokennya sendiri. Akun nonaktif ditolak dan penonaktifan melalui endpoint admin mencabut seluruh token akun tersebut.

Contoh login:

```json
{
  "email": "koordinator@relawanku.test",
  "password": "RelawanDemo123!",
  "device_name": "Android"
}
```

Respons login:

```json
{
  "message": "Login berhasil.",
  "data": {
    "user": { "id": 2, "name": "Koordinator Demo", "role": "koordinator" },
    "token": "<token-dari-server>",
    "token_type": "Bearer",
    "expires_at": "2026-11-10T08:00:00.000000Z"
  }
}
```

Contoh di atas mempersingkat objek user. Respons detail/mutasi umumnya berisi `data` dan pesan pada mutasi. Daftar memakai paginator Laravel: `data`, `current_page`, `last_page`, `per_page`, `total`, dan tautan halaman. Gunakan parameter `page` untuk halaman berikutnya. Daftar kosong menghasilkan HTTP 200 dengan `data: []`.

Error validasi:

```json
{
  "message": "The title field is required.",
  "errors": {
    "title": ["The title field is required."]
  }
}
```

Pesan validasi bawaan mengikuti bahasa paket Laravel yang tersedia. Klien sebaiknya menggunakan status HTTP dan nama kolom pada `errors`, bukan membandingkan teks pesan.

| HTTP | Arti |
| --- | --- |
| 200 | Pembacaan, pembaruan, pembatalan, atau pengarsipan berhasil. |
| 201 | Data berhasil dibuat. |
| 401 | Token tidak ada, kedaluwarsa, atau telah dicabut. |
| 403 | Peran, kepemilikan, keikutsertaan, atau status akun tidak mengizinkan akses. |
| 404 | Objek tidak ditemukan, kegiatan telah diarsipkan, atau ID anak tidak sesuai kegiatan. |
| 409 | Konflik bisnis: kuota penuh, pendaftaran/absensi ganda, pendaftaran ditutup, atau waktu kegiatan tidak sesuai. |
| 422 | Input tidak valid, penanggung jawab belum terdaftar, atau kode absensi salah/kedaluwarsa. |
| 429 | Batas request terlampaui. |

## Daftar endpoint

Semua path di bawah relatif terhadap `/api`. **Pengelola** berarti admin atau koordinator pemilik kegiatan. **Peserta** berarti relawan yang memiliki riwayat pendaftaran pada kegiatan tersebut; tugas yang ditampilkan kepada relawan terbatas pada tugasnya sendiri.

### Umum dan akun

| Metode | Path | Akses / fungsi |
| --- | --- | --- |
| GET | `/health` | Publik, status aplikasi. |
| POST | `/auth/register` | Publik; membuat akun relawan. Field `role` dilarang. |
| POST | `/auth/login` | Publik; email, password, device_name. |
| GET | `/auth/me` | Semua akun aktif; profil sendiri. |
| PATCH | `/auth/me` | Semua akun aktif; name, email, phone, bio. |
| POST | `/auth/logout` | Semua akun aktif; cabut token saat ini. |
| GET | `/dashboard` | Ringkasan sesuai peran. |
| GET | `/history` | Riwayat aksi yang dilakukan sendiri. |
| GET | `/my/registrations` | Riwayat pendaftaran sendiri, termasuk kegiatan yang telah diarsipkan. |
| GET | `/users` | Admin; pencarian `search` dan filter `role`. |
| POST | `/users` | Admin; membuat akun dan menetapkan peran. |
| PATCH | `/users/{user}` | Admin; mengubah name, email, phone, role, is_active. |

Admin tidak dapat menonaktifkan atau menurunkan peran akun sendiri. Peran pengguna yang sudah memiliki kegiatan/pendaftaran tidak dapat diubah agar kepemilikan dan riwayat tetap konsisten. Penonaktifan tersedia sebagai pengganti penghapusan akun. Password minimal delapan karakter dengan huruf dan angka; pembuatan akun memerlukan `password_confirmation`. Email menggunakan huruf kecil.

### Kegiatan

| Metode | Path | Akses / fungsi |
| --- | --- | --- |
| GET | `/activities` | Akun aktif; draft hanya terlihat oleh pemilik/admin. |
| POST | `/activities` | Koordinator/admin; membuat kegiatan. |
| GET | `/activities/{activity}` | Akun aktif, mengikuti aturan visibilitas draft. |
| PATCH | `/activities/{activity}` | Pengelola; pembaruan sebagian. |
| DELETE | `/activities/{activity}` | Pengelola; soft delete. |
| DELETE | `/activities/bulk` | Pengelola; body `{"ids":[1,2]}`, maksimal 100 ID unik. |
| POST | `/activities/{activity}/cover` | Pengelola; file image. |
| GET | `/activities/{activity}/history` | Pengelola; log kegiatan. |

Parameter daftar: `search` (nama/lokasi), `category`, `status`, `mine=1`, `per_page` (1–100, default 15), dan `page`. Untuk relawan, `mine=1` menyaring kegiatan yang pernah diikuti; koordinator melihat kegiatan miliknya. Riwayat kegiatan yang diarsipkan tersedia melalui `/my/registrations`.

Body pembuatan kegiatan:

```json
{
  "title": "Bersih Pantai",
  "description": "Membersihkan sampah di area pantai bersama relawan.",
  "category": "Lingkungan",
  "location": "Pantai Losari, Makassar",
  "requirements": "Membawa botol minum sendiri.",
  "quota": 30,
  "starts_at": "2026-12-12T08:00:00+07:00",
  "ends_at": "2026-12-12T12:00:00+07:00",
  "registration_deadline": "2026-12-11T18:00:00+07:00",
  "status": "open"
}
```

Gunakan tanggal yang masih berlaku saat menguji pendaftaran. `requirements` opsional dan status default `draft`. Saat admin membuat kegiatan, tambahkan `coordinator_id` milik koordinator aktif. Saat koordinator membuat kegiatan, ID pemilik berasal dari token sehingga field `coordinator_id` dilarang.

### Peserta dan tugas

| Metode | Path | Akses / fungsi |
| --- | --- | --- |
| POST | `/activities/{activity}/registrations` | Relawan; daftar tanpa body. |
| GET | `/activities/{activity}/registrations` | Pengelola; peserta dan kehadiran. |
| DELETE | `/activities/{activity}/registration` | Batalkan pendaftaran sendiri. |
| DELETE | `/activities/{activity}/registrations/{registration}` | Pengelola/pemilik pendaftaran; batalkan peserta terkait. |
| GET | `/activities/{activity}/tasks` | Peserta/pengelola; relawan hanya melihat tugas sendiri. |
| POST | `/activities/{activity}/tasks` | Pengelola; title, description?, assigned_to?, due_at?, status?. |
| GET | `/activities/{activity}/tasks/{task}` | Penerima tugas/pengelola. |
| PATCH | `/activities/{activity}/tasks/{task}` | Pengelola; ubah detail/penanggung jawab/status. |
| PATCH | `/activities/{activity}/tasks/{task}/status` | Relawan penerima tugas yang masih terdaftar aktif. |
| DELETE | `/activities/{activity}/tasks/{task}` | Pengelola; soft delete. |
| DELETE | `/activities/{activity}/tasks/bulk` | Pengelola; body `{"ids":[1,2]}`, maksimal 100 ID unik. |

Pendaftaran memerlukan status `open`, waktu sebelum mulai, batas pendaftaran belum lewat, dan kuota tersedia. Pemeriksaan dilakukan dalam transaksi dengan lock kegiatan. Pembatalan hanya sebelum kegiatan dimulai dan sebelum absensi. Penanggung jawab tugas harus relawan aktif yang terdaftar pada kegiatan yang sama. Tugas boleh belum memiliki penanggung jawab (`assigned_to: null`). Status tugas: `pending`, `in_progress`, `completed`.

Hapus massal bersifat seluruhnya berhasil atau seluruhnya batal. Satu ID yang tidak ditemukan atau tidak boleh dikelola membatalkan semua perubahan.

### Absensi dan dokumentasi

| Metode | Path | Akses / fungsi |
| --- | --- | --- |
| POST | `/activities/{activity}/attendance-sessions` | Pengelola; body `{"duration_minutes":15}`. |
| DELETE | `/activities/{activity}/attendance-sessions/{attendanceSession}` | Pengelola; tutup sesi. |
| POST | `/activities/{activity}/attendances` | Relawan aktif; body `{"code":"A1B2C3D4"}`. |
| GET | `/activities/{activity}/attendances` | Pengelola; peserta yang sudah hadir. |
| GET | `/activities/{activity}/documentations` | Peserta/pengelola; daftar foto. |
| POST | `/activities/{activity}/documentations` | Pengelola; form-data image dan caption opsional. |
| PATCH | `/activities/{activity}/documentations/{documentation}` | Pengelola; body caption, boleh null. |
| DELETE | `/activities/{activity}/documentations/{documentation}` | Pengelola; soft delete. |

Sesi absensi hanya dibuka saat `starts_at <= sekarang < ends_at` dan status kegiatan `open`/`closed`. Durasi 1–120 menit dan tidak melewati akhir kegiatan. Membuka sesi baru menutup sesi sebelumnya. Kode hanya ditampilkan pada respons pembukaan; database menyimpan hash-nya. Satu peserta hanya dapat melakukan absensi sekali per kegiatan.

Foto JPEG/PNG/WebP maksimal 5 MB disimpan pada disk `public`; jalankan `storage:link`. URL foto dapat diakses publik, sehingga dokumentasi ditujukan untuk foto kegiatan yang boleh dibagikan. Pengarsipan tidak menghapus berkas fisik agar riwayat tetap tersedia.

Route pembaruan resource kegiatan, tugas, dokumentasi, dan pengguna juga menerima PUT. PATCH disarankan untuk perubahan sebagian.

## Integrasi Android

`GET /api/activities/{activity}` juga mengembalikan `data.my_registration`: pendaftaran milik pengguna yang sedang login beserta `attendance`, atau `null` jika belum mendaftar/bukan Relawan. Status `cancelled` tetap dikirim untuk riwayat. Field ini hanya ada pada respons detail, tidak memuat peserta lain, data akun, kode, atau identitas sesi absensi. Android tidak perlu memindai `/my/registrations` untuk memeriksa satu kegiatan.

Android menyimpan hasil baca daftar/detail kegiatan, dashboard, tugas, dokumentasi, Kegiatanku, dan riwayat dalam cache memori 30 detik (maksimal 64 respons). Cache berada dalam ViewModel, dipisahkan berdasarkan server, akun, role, URL/filter/halaman, dan hilang saat proses berakhir atau sesi dihapus. Profil autentikasi (`auth/me`), pengelolaan pengguna, dan daftar kehadiran tidak di-cache.

Tombol **Muat ulang** menghapus cache halaman yang sedang dibuka. Aksi tulis selalu menuju server dan menghapus cache yang terdampak, termasuk saat hasil koneksi belum pasti. Respons baca lama yang dimulai sebelum mutasi tidak boleh mengisi ulang cache. Perubahan dari perangkat lain terlihat setelah TTL habis atau refresh manual; validasi kuota, hak akses, dan absensi tetap dilakukan server pada setiap aksi. Ini merupakan cache aplikasi per sesi, bukan cache respons bersama di Laravel.


- Emulator Android umumnya mengakses host melalui `http://10.0.2.2:8000/api/` (akhiri base URL Retrofit dengan `/`).
- Untuk perangkat fisik, gunakan IP LAN komputer dan jalankan server dengan `--host=0.0.0.0`; perangkat dan komputer harus terhubung ke jaringan yang sama.
- Tambahkan header Bearer melalui interceptor setelah login. Tangani HTTP 401 dengan meminta pengguna masuk kembali.
- Kirim waktu ISO 8601 lengkap dengan zona, misalnya `+07:00` atau `Z`. Respons datetime menggunakan UTC; ubah ke zona perangkat untuk tampilan.
- HTTP lokal membutuhkan konfigurasi cleartext khusus pengembangan Android; gunakan HTTPS pada server publik.

## Pemeriksaan

```powershell
php artisan test --compact
php artisan route:list --path=api --except-vendor
composer validate --strict
```

Tes PHPUnit default memakai SQLite `:memory:` sesuai `phpunit.xml`; tidak mengubah database utama. Untuk pengujian MySQL, gunakan database kosong khusus tes dan override `DB_CONNECTION`, `DB_PORT`, serta `DB_DATABASE` pada environment proses. `RefreshDatabase` hanya boleh diarahkan ke database pengujian.

### Pengujian konkurensi MySQL

`ConcurrentRegistrationTest` bersifat opt-in dan dilewati pada suite biasa. Tes ini menjalankan dua request HTTP, memastikan keduanya benar-benar menunggu lock baris kegiatan yang sama melalui `performance_schema.data_lock_waits`, lalu memeriksa hasil HTTP 201 dan 409 serta hanya satu peserta tersimpan.

Untuk mengulangnya, buat database MySQL khusus seperti `relawanku_concurrency_test`, jalankan migration dan DemoSeeder ke database tersebut, lalu jalankan dua server pada terminal terpisah dengan koneksi database pengujian yang sama:

```powershell
# Jalankan pada kedua terminal server sebelum perintah serve.
$env:DB_CONNECTION = 'mysql'
$env:DB_DATABASE = 'relawanku_concurrency_test'
$env:DB_PORT = '3306'

# Terminal pertama:
php artisan serve --host=127.0.0.1 --port=8765 --no-reload

# Terminal kedua, setelah mengatur environment yang sama:
php artisan serve --host=127.0.0.1 --port=8766 --no-reload
```

Pada terminal ketiga:

```powershell
$env:TEST_API_URL = 'http://127.0.0.1:8765/api'
$env:TEST_API_URL_SECOND = 'http://127.0.0.1:8766/api'
$env:TEST_MYSQL_DATABASE = 'relawanku_concurrency_test'
$env:TEST_MYSQL_PORT = '3306'
php vendor/bin/phpunit tests/Feature/ConcurrentRegistrationTest.php
```

Sesuaikan `TEST_MYSQL_HOST`, `TEST_MYSQL_USERNAME`, `TEST_MYSQL_PASSWORD`, dan `TEST_DEMO_PASSWORD` bila berbeda dari nilai lokal bawaan. Akun pemeriksa memerlukan akses baca `performance_schema`. Tes membuat akun relawan khusus dan mengarsipkan kegiatan uji; gunakan database pengujian saja. Di Windows, dua proses server diperlukan karena server pengembangan PHP melayani request secara berurutan pada satu proses.

Spesifikasi dan koleksi dapat dibuat ulang setelah memperbarui katalog endpoint di `scripts/generate-api-artifacts.py`:

```powershell
# Jalankan dari akar repository, bukan backend.
python scripts/generate-api-artifacts.py
```

Rujukan implementasi: [Laravel 13 Routing](https://laravel.com/docs/13.x/routing), [Sanctum](https://laravel.com/docs/13.x/sanctum), dan [Migration](https://laravel.com/docs/13.x/migrations).
