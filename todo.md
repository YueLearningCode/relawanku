# Setup API Relawanku

- [x] Laravel khusus API dan Sanctum terpasang.
- [x] Migration dan model sesuai rancangan database.
- [x] Endpoint autentikasi, kegiatan, peserta, tugas, absensi, dokumentasi, dan admin.
- [x] Data contoh lokal yang dapat dijalankan ulang.
- [x] Koleksi dan environment Postman serta dokumentasi REST API.
- [x] Pengujian fitur, akses antarperan, validasi, dan migration.
- [ ] Menjalankan migration/seeder pada MySQL Laragon utama `127.0.0.1:3306` setelah server dinyalakan.

## Keputusan implementasi

- API menggunakan prefiks `/api` dan token Bearer Sanctum.
- Waktu disimpan dalam UTC; klien mengirim ISO 8601 dengan zona waktu.
- Pendaftaran mengunci baris kegiatan dalam transaksi sebelum memeriksa kuota.
- Kegiatan, tugas, dan dokumentasi memakai soft delete untuk menjaga riwayat.
- Data contoh dipisahkan dari migration dan hanya boleh digunakan di lingkungan lokal/pengujian.

## Bukti verifikasi

Verifikasi pada **3 November 2026**, lingkungan Windows, PHP 8.4.5, Laravel 13.39.0, Sanctum 4.3.3.

| Pemeriksaan | Hasil |
| --- | --- |
| `composer validate --strict` | Valid. |
| `php vendor/bin/pint --dirty --format agent` | Lolos setelah format diterapkan. |
| `php artisan test --compact` pada SQLite memory | 26 tes lolos, 124 assertion; satu tes konkurensi opt-in dilewati. |
| Suite yang sama pada MySQL 8.4.3 terpisah | 26 tes lolos, 124 assertion; satu tes konkurensi opt-in dilewati. |
| `ConcurrentRegistrationTest` dengan dua server HTTP dan MySQL | 1 tes lolos, 11 assertion. Dua request terbukti menunggu lock baris yang sama; hasil 201/409 dan satu peserta tersimpan. |
| Migration dan DemoSeeder pada MySQL pengujian | Enam migration berstatus Ran; akun dan kegiatan demo berhasil dibuat. |
| Runner Newman, folder 01 koleksi Postman | 23 request dan 23 assertion lolos melalui HTTP ke API dengan MySQL. |
| Validasi OpenAPI dengan Swagger CLI | `docs/openapi.json is valid`. |
| `php artisan route:list --path=api --except-vendor` | 39 route API terdaftar; pembaruan resource juga menerima PUT. |
| `git diff --check` | Tidak ada kesalahan whitespace. |

Tes mencakup autentikasi token dan kedaluwarsa/logout, akun nonaktif, larangan eskalasi peran, kepemilikan kegiatan, visibilitas draft, jadwal, kuota, pendaftaran ganda/batal/ulang, pembatasan tugas, pengarsipan massal atomik, kode absensi kedaluwarsa/ditutup/diganti, unggahan gambar, pengelolaan admin, riwayat, rollback saat log gagal, rate limit login, dan seeder idempoten.

MySQL Laragon utama belum menerima koneksi saat pemeriksaan. Verifikasi memakai instance MySQL sementara pada port **33307** dengan data di dalam workspace, terpisah dari database Laragon yang sudah ada. Database pengujian dan proses server sementara dihentikan setelah verifikasi. `.env` utama tetap mengarah ke database `relawanku` pada port 3306.

## Langkah lokal berikutnya

1. Nyalakan MySQL dari Laragon dan buat database `relawanku` jika belum ada.
2. Pastikan koneksi pada `.env` sesuai, lalu jalankan `php artisan migrate`.
3. Opsional: jalankan `php artisan db:seed --class=DemoSeeder`.
4. Jalankan `php artisan serve`, impor kedua JSON Postman, dan jalankan folder 01.

Petunjuk lengkap dan cara mengulang tes konkurensi ada di [docs/API.md](docs/API.md). Pengujian Android/Retrofit belum dijalankan karena aplikasi Android belum tersedia pada repositori ini.

## Struktur monorepo

- Backend Laravel berada di `backend`; jalankan Composer, Artisan, dan PHPUnit dari folder tersebut.
- Dokumentasi, Postman, dan generator kontrak tetap di akar repository.
- Folder `android` disiapkan untuk proyek Kotlin/XML; aplikasi Android belum dibuat.
- Data MySQL dan akun tidak diubah oleh penataan folder.
- Verifikasi setelah penataan: 39 route `/api` terdaftar; Composer valid; endpoint `GET http://127.0.0.1:8000/api/health` menghasilkan HTTP 200.
- Tes setelah pemindahan: 25 lulus dari 26; satu error lama pada tes DemoSeeder karena mencari `relawan@relawanku.test` sementara seeder membuat `relawan@gmail.com`. Tes memakai SQLite `:memory:`; bukan verifikasi konkurensi MySQL.
- Pemeriksaan integritas source yang dipindahkan dan `.env`: seluruh hash sesuai; `.git` tetap di akar dan tidak ada repository Git bersarang.

## UI Android tahap pertama pada 4 November 2026

- [x] Nunito 400/600/700 dibundel offline, palet teal-sage dan tema Material 3 diterapkan.
- [x] Dua splashscreen animasi, masuk/daftar dengan validasi, serta pilihan tiga peran pratinjau.
- [x] Relawan: beranda, jelajah, pencarian/filter, detail, daftar/batal, tugas, absensi contoh RW2026, dokumentasi, profil, dan riwayat.
- [x] Koordinator: dashboard, tambah/edit kegiatan, arsip individu/massal dengan konfirmasi, peserta, pembagian tugas, sesi absensi, dan pemilih dokumentasi lokal.
- [x] Admin: dashboard, daftar/cari/filter pengguna, tambah/edit profil contoh, pengaturan peran dan aktif/nonaktif.
- [x] APK debug berhasil dibangun. Satu unit test bawaan dan empat tes perangkat (termasuk tiga skenario alur UI) lulus pada emulator API 37.
- [x] Tes alur mencakup validasi form, pencarian kosong, pendaftaran, kode absensi salah/benar, state saat recreate Activity, tambah/arsip kegiatan, tambah pengguna, ganti peran, dan indikator navigasi aktif.
- [x] lintDebug selesai tanpa error. Peringatan mencakup versi dependensi bawaan dan resource desain yang belum dipakai; laporan tersedia di android/app/build/reports/lint-results-debug.html.
- [x] Screenshot asli emulator diperiksa untuk login, beranda, jelajah, detail, dashboard koordinator/admin; dashboard juga diperiksa pada lebar 360 dp dan font scale 130 persen. Pengaturan emulator dipulihkan.
- [x] git diff --check lulus. Tidak ada perubahan backend maupun database dalam tahap UI ini.
- [ ] Integrasi Retrofit, token Sanctum, API tiga role, dan unggah dokumentasi ke Laravel.
- [ ] Ganti formulir tanggal/waktu contoh dengan pemilih terstruktur serta ISO 8601.
- [ ] Verifikasi pada HP fisik, Android API 24, dan skenario jaringan nyata.

UI ini menggunakan PreviewState lokal, bukan autentikasi atau otorisasi produksi. Pemilih peran hanya untuk pratinjau. Data demo berada dalam ViewModel dan bertahan selama proses aplikasi, termasuk recreate Activity. Draft formulir belum dipertahankan saat rotasi. Akses foto lokal belum berarti unggah ke server.
