# Demo API Relawanku di Postman

Koleksi dan environment ini diperbarui berdasarkan route Laravel serta database MySQL lokal yang diperiksa pada **22 November 2026**. Base URL: `http://127.0.0.1:8000/api`.

## Persiapan

1. Nyalakan MySQL di Laragon. Dari folder `backend`, jalankan:

   ```powershell
   php artisan migrate:status
   php artisan serve --host=127.0.0.1 --port=8000
   ```

2. Import `Relawanku.postman_collection.json` dan `Relawanku.local.postman_environment.json` ke Postman. Jika koleksi lama sudah diimpor, ganti dengan versi ini agar request tidak tercampur.
3. Pilih environment **Relawanku - Local**. Sesuaikan `base_url` bila server memakai port lain. Uji `GET {{base_url}}/health` terlebih dahulu.

### Akun lokal yang telah diperiksa

| Peran | Email | ID saat diperiksa |
| --- | --- | --- |
| Admin | `yue@gmail.com` | 1 |
| Koordinator | `koordinator@gmail.com` | 2 |
| Relawan | `relawan@gmail.com` | 3 |

Ketiganya cocok dengan `demo_password` pada environment, yaitu `12345678`. Token dan ID akun diisi ulang dari respons login; tidak perlu menyalin token secara manual. File yang disimpan di repository tidak berisi token aktif.

Akun lokal sudah tersedia, sehingga tidak perlu menjalankan seeder untuk demo ini. `DemoSeeder` saat ini menggunakan `admin@gmail.com` untuk admin baru dan nilai `DEMO_PASSWORD` untuk password awal. Seeder mempertahankan password akun yang sudah ada. Jika memakai database baru, sesuaikan email/password environment dengan akun yang benar-benar dibuat.

## Pilih alur demo

Jalankan **satu folder** melalui Collection Runner, satu iterasi, dengan jeda sekitar 300 ms antarrequest. Aktifkan penyimpanan perubahan variabel environment jika ingin memakai token/ID hasil run pada request manual. Jangan menjalankan seluruh koleksi sekaligus.

### 00 Data saat ini - baca saja

Untuk menunjukkan data yang sudah ada: login tiga peran, profil, dashboard, daftar/detail kegiatan, peserta, tugas, dokumentasi, kehadiran, pendaftaran sendiri, dan riwayat. Login menerbitkan token; request lainnya membaca data.

| Variabel | Nilai | Kegiatan | Jadwal WIB |
| --- | --- | --- | --- |
| `existing_activity_id` | 9 | demo lagi | 14 November 2026 19.54 - 28 November 2026 19.54 |
| `existing_upcoming_activity_id` | 4 | Bersih Selokan | 12 Desember 2026 08.00 - 12.00 |

Ini adalah pilihan ID dari database lokal, bukan ID yang berlaku di semua instalasi. Ubah variabel tersebut jika kegiatan diarsipkan atau database diganti. Daftar peserta/tugas/foto/kehadiran boleh kosong dan tetap menghasilkan HTTP 200.

Kegiatan ID 9 sudah melewati batas pendaftaran. Kegiatan demo lama ID 1 dan 3 juga telah selesai pada saat pemeriksaan. Gunakan folder 01 untuk mendemonstrasikan pendaftaran dan absensi dengan jadwal yang sesuai.

### 01 Alur utama - jalankan folder ini

Alur otomatis yang bisa dijalankan ulang:

1. Memeriksa API dan login admin, koordinator, serta relawan.
2. Membuat kegiatan baru berstatus `open`, mulai dua hari setelah waktu run. `activity_id` disimpan dari respons.
3. Membaca kegiatan, mendaftarkan relawan, dan memeriksa bahwa pendaftaran ganda ditolak dengan HTTP 409.
4. Membuat, membaca, memperbarui tugas, lalu menyelesaikannya sebagai relawan yang ditugaskan.
5. Memeriksa bahwa relawan ditolak saat mengakses pengelolaan akun (HTTP 403).
6. Mengubah jadwal kegiatan uji menjadi sedang berlangsung, membuka sesi absensi, dan menyimpan kode yang dikirim server.
7. Melakukan absensi, memeriksa bahwa absensi ganda ditolak (HTTP 409), serta memastikan detail kegiatan memuat `my_registration.attendance` milik relawan.
8. Membaca dashboard, riwayat, dan kehadiran; menutup sesi; mengarsipkan tugas dan kegiatan uji; memastikan detail kegiatan yang diarsipkan menghasilkan HTTP 404.

Request bernama **Uji 403/404/409** memang mengharapkan respons tersebut. Lihat hasil **Tests**, bukan hanya warna status HTTP. Semua request memeriksa status dan respons JSON; respons daftar juga diperiksa pagination-nya. Status yang tidak sesuai menghentikan alur Collection Runner agar request berikutnya tidak memakai hasil yang gagal.

Alur ini membuat data uji baru dengan judul `Kegiatan Postman ...`. Pendaftaran, absensi, dan log data uji tetap tersimpan sebagai riwayat setelah pengarsipan. Jika run terhenti di tengah, periksa request yang gagal; menjalankan ulang folder membuat kegiatan baru.

### 02 Referensi endpoint - jalankan satu per satu

Katalog endpoint untuk demo manual. Tersedia login masing-masing peran di subfolder **Autentikasi**. Token dipilih sesuai akses request.

- Setelah folder 01 selesai, `activity_id` dan `task_id` menunjuk data uji yang sudah diarsipkan. Jalankan **Buat kegiatan**, **Daftar kegiatan**, lalu **Buat tugas** untuk memulai demo manual baru, atau isi ID data yang ingin diuji.
- `existing_activity_id` dan `existing_upcoming_activity_id` dipakai khusus folder 00. Keduanya tidak dijadikan target otomatis untuk perubahan/pengarsipan folder 01.
- Tugas yang ditugaskan memerlukan `volunteer_id` yang sudah terdaftar pada kegiatan tersebut.
- Pembatalan pendaftaran dilakukan sebelum kegiatan dimulai dan sebelum ada absensi. Pembukaan sesi dan absensi dilakukan saat kegiatan berlangsung.
- Untuk unggah sampul/dokumentasi, pilih file JPEG/PNG/WebP maksimal 5 MB pada **Body > form-data > image**. Jalankan `php artisan storage:link` dari `backend` jika tautan penyimpanan belum tersedia. Foto dipilih manual dan tidak termasuk folder otomatis.
- Request pembuatan/perubahan akun admin menggunakan `user_id` hasil **Buat akun pengguna**. Periksa body sebelum menekan **Send**.

## Jika request gagal

| Hasil | Yang diperiksa |
| --- | --- |
| Tidak tersambung | MySQL dan server Laravel menyala; host/port `base_url` benar. |
| 401 | Jalankan login peran terkait; pilih environment yang benar. |
| 403 di luar request Uji | Peran token, kepemilikan kegiatan, atau status kepesertaan. |
| 404 di luar request Uji | ID berubah, data diarsipkan, atau ID anak tidak sesuai kegiatan. |
| 409 di luar request Uji | Jadwal, kuota, batas pendaftaran, atau data sudah terdaftar/hadir. |
| 422 | Baca `errors`; cek email/password, body, ID penanggung jawab, atau kode absensi. |
| 429 | Tunggu sesuai `Retry-After`; hindari mengulang login berkali-kali dalam satu menit. |

## Membuat ulang file

Dari akar repository:

```powershell
python scripts/generate-api-artifacts.py
```

Generator mempertahankan `base_url`, tiga email login, `demo_password`, dan dua ID pilihan folder 00 dari environment lokal yang sudah ada. Token, ID hasil mutasi, tanggal, dan kode absensi dikosongkan saat file dibuat ulang. Katalog endpoint juga menghasilkan [OpenAPI](../docs/openapi.json).

Kontrak dan aturan bisnis lengkap tersedia di [docs/API.md](../docs/API.md). Rujukan perilaku skrip: [Postman variables](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-variables/) dan [urutan request Collection Runner](https://learning.postman.com/docs/tests-and-scripts/running-collections/building-workflows/).

## Hasil validasi 22 November 2026

Pengujian koleksi dijalankan menggunakan Newman 6.2.3:

| Pemeriksaan | Hasil |
| --- | --- |
| Folder 00 pada MySQL lokal saat ini | 19 request, 59 assertion, 0 gagal. |
| Folder 01 pada SQLite terpisah | 30 request, 86 assertion, 0 gagal; dijalankan dua kali dan keduanya lulus. |
| Password salah | Alur berhenti setelah health dan login admin; token admin lama dikosongkan, request mutasi tidak dikirim. |
| Katalog referensi | 39 pasangan metode/path sesuai route Laravel saat ini, dengan tiga pilihan login. Alias PUT tetap diterima server. |
| File dan generator | JSON valid, 110 skrip JavaScript lolos pemeriksaan sintaks, variabel lengkap, hasil generator konsisten saat diulang. |

Token yang diterbitkan saat validasi sudah dicabut. Pengujian CRUD/absensi memakai database terpisah; hasil tersebut tidak membuktikan perilaku konkurensi MySQL. Impor melalui antarmuka Postman dan unggahan foto manual belum dijalankan pada pemeriksaan ini.
