# Setup API Relawanku
## Perbaikan password pada 6 November 2026

Baseline `main` adalah `f1af6d7`, yang sudah memuat merge integrasi/UI dari `andro-backend`. Perubahan pengguna untuk menyembunyikan URL pada halaman masuk dipertahankan.

- [x] Bug berhasil direproduksi: ketiga tes baru (login, daftar, tambah pengguna Admin) gagal karena password terlihat pada render pertama.
- [x] Akar masalah ditemukan pada `TextView.setSingleLine()`, yang mengganti PasswordTransformationMethod menjadi SingleLineTransformationMethod setelah inputType password ditentukan. Masking sekarang dipasang setelah seluruh konfigurasi field selesai.
- [x] Ikon custom berdasarkan tindakan: mata terbuka untuk menampilkan password yang tersembunyi, mata dicoret untuk menyembunyikan password yang terlihat. Label aksesibilitas mengikuti tindakan berikutnya.
- [x] Isi password, selection/kursor, tipe input, dan pesan validasi tidak berubah saat toggle. Password dan konfirmasi tetap independen. Ikon error tidak menutup tombol mata.
- [x] Build debug, APK tes, dan 19 unit test lulus; lint debug 0 error, 49 warning.
- [x] Setelah perbaikan, seluruh instrumentation lulus: `OK (12 tests)`, 68,346 detik pada emulator API 37. Termasuk tiga regresi password, cache, keyboard/navigasi, Welcome XML, dan alur Laravel dengan database/storage uji terisolasi.
- [x] Emulator sempat mengalami kegagalan layanan SystemServer sebelum instrumentation dimulai; cold boot memulihkan layanan, lalu seluruh tes berhasil. Ini terpisah dari bug aplikasi yang sudah direproduksi dan diperbaiki.
- [x] `git diff --check` lulus. Backend serta alur pendaftaran/hak akses Koordinator tidak diubah.

Saat ini akun Koordinator tetap dibuat Admin melalui Pengguna > Tambah pengguna > Role Koordinator. Pengajuan mandiri instansi merupakan usulan alur, belum diimplementasikan dalam perbaikan password ini. Verifikasi perangkat dilakukan pada emulator, belum diulang pada HP fisik. Server dan emulator uji dihentikan setelah verifikasi. Perubahan baru tetap lokal, belum commit/push.

## Perbaikan UI dan keyboard pada 6 November 2026

Dilanjutkan pada `andro-backend` (baseline commit `49ce76a`), setelah checkout awal ditemukan berada di `main` yang masih berisi UI pratinjau. Working tree awal bersih; source integrasi dan cache yang sudah tersimpan dipertahankan.

- [x] Akar masalah: root menambahkan padding bawah system bars/IME, sementara Material bottom navigation juga menangani inset bawah. Shell sekarang menjadi satu pemilik inset sistem; area isi navigasi 64 dp, safe area diterapkan sekali, dan divider tipis.
- [x] Keyboard hanya memengaruhi area konten. Bottom navigation disembunyikan saat mengetik, lalu kembali setelah keyboard ditutup. Toolbar profil tetap di posisi yang sama; field terpilih bergulir ke area yang terlihat. Back pertama menutup keyboard tanpa meninggalkan halaman.
- [x] Dashboard Relawan/Koordinator/Admin ditata ulang: header peran, avatar profil dengan target sentuh 48 dp, ringkasan angka, kartu metrik, dan menu tindakan. Form profil dikelompokkan menjadi identitas, informasi pribadi, dan pengaturan akun.
- [x] Tombol 48 dp dengan sudut 12 dp, tombol sekunder outline, kartu kegiatan dengan sudut 14 dp dan indikator kuota, serta label statistik yang tidak terbelah dan kartu sejajar pada font besar.
- [x] Nunito, teal-sage, dan Welcome berbasis XML dipertahankan. Backend, model/cache data, dan kontrak API tidak diubah dalam tahap ini.

| Pemeriksaan UI terbaru | Hasil |
| --- | --- |
| Build debug dan APK instrumentation | Lulus. |
| Unit test Android | 19 tes lulus. |
| Seluruh instrumentation pada emulator API 37, mode gesture, 1080 x 2400 / 420 dpi / font 100% | `OK (9 tests)`, 45,698 detik. Termasuk alur Laravel dengan database/storage terisolasi, cache, Welcome XML, dan tes keyboard. |
| Regresi insets/keyboard pada 1080 x 2160 / 480 dpi (lebar 360 dp), font 130%, navigasi tiga tombol | `OK (2 tests)`, 17,896 detik; satu tes mencakup ketiga role, satu tes login. |
| Geometri navigasi | Root tidak menambah padding bawah, tinggi nav = 64 dp + safe area, nav mencapai tepi bawah app; tidak ada inset ganda. |
| Profil saat keyboard tampil | Toolbar tetap, field terlihat di atas keyboard, draft tetap, nav tersembunyi; Back menutup keyboard dan nav kembali tanpa pindah tab. |
| Screenshot | Dashboard/profil/keyboard ketiga role dan login diperiksa; pemeriksaan font besar menemukan label metrik yang terbelah, kemudian diperbaiki dan diuji ulang. |
| Lint debug | 0 error, 49 warning. |
| Git | `git diff --check` lulus; perubahan pada backend, data/cache Android, serta resource Welcome kosong. |

Perubahan baru masih lokal, belum commit/push. Belum diuji ulang pada HP fisik/OEM Infinix atau API 24. Pengujian tidak menggunakan database utama. Server dan emulator sementara dihentikan; ukuran layar, density, font scale, mode navigasi, serta pengaturan software keyboard emulator dipulihkan. Draft formulir saat rotasi tetap merupakan batasan yang terpisah dari pembukaan/penutupan keyboard.

## Efisiensi request Android/API pada 6 November 2026

- [x] Detail kegiatan mengirim `my_registration` milik pengguna yang login, termasuk kehadiran sendiri; data akun/peserta lain dan identitas sesi absensi tidak ikut dikirim. Tidak menambah endpoint atau migration.
- [x] Android tidak lagi mengambil seluruh halaman `/my/registrations` untuk satu detail. Pembukaan detail yang belum di-cache cukup satu request, sebelumnya minimal dua dan bertambah sesuai jumlah halaman pendaftaran.
- [x] `ReadCache` berada dalam ViewModel: TTL 30 detik, maksimal 64 respons, dipisah server/akun/role/URL/filter/halaman. Hasil autentikasi, pengguna Admin, dan daftar kehadiran tidak di-cache.
- [x] Tombol Muat ulang memaksa pembacaan ulang halaman aktif. Mutasi menghapus cache terkait sebelum dan sesudah request, termasuk ketika koneksi gagal; respons baca yang dimulai sebelum invalidasi tidak mengisi ulang cache lama.
- [x] Cache dibersihkan saat keluar/ganti server/akun. Request tulis dan validasi kuota/akses/kode absensi tetap menuju Laravel.
- [x] OpenAPI, generator kontrak, API.md dan README Android disesuaikan. Environment Postman tidak ditimpa.
- [x] Fixture tes DemoSeeder disesuaikan ke email `relawan@gmail.com` sesuai seeder; akun utama tidak disentuh.

| Verifikasi optimasi | Hasil |
| --- | --- |
| Laravel PHPUnit, SQLite memory eksplisit | 29 tes lolos, 149 assertion; 1 tes konkurensi opt-in dilewati. |
| Pint pada PHP yang diubah | Lulus. |
| Android build debug dan APK instrumentation | Lulus. |
| Unit test Android | 19 lulus: 9 kontrak HTTP, 9 kebijakan cache, 1 tes bawaan. |
| Instrumentation emulator API 37 | `OK (7 tests)`, 58,271 detik, termasuk alur Laravel terisolasi. |
| Hitungan HTTP melalui MockWebServer pada emulator | Dua pembacaan URL yang sama menjadi 1 request; refresh menambah request; setelah mutasi dan saat ganti akun, pembacaan kembali menghubungi server. Cache bertahan saat Activity recreate. |
| Lint debug | 0 error, 53 warning. |
| Kontrak OpenAPI | Model sesuai generator; semua referensi schema lokal terhubung. |
| Whitespace Git | `git diff --check` lulus. |

Cache ini tidak menjalankan polling otomatis. Perubahan dari perangkat lain diambil saat halaman dimuat lagi setelah TTL habis, atau ketika pengguna menekan Muat ulang. Cache hanya di memori, tidak menyediakan mode offline permanen. Pengujian perangkat memakai emulator yang dipilih secara eksplisit; HP fisik yang sedang terhubung tidak dipakai untuk tes. Tidak ada migrasi/seeder pada database utama. Server dan emulator uji dihentikan setelah verifikasi. Perubahan tetap lokal pada `andro-backend`, belum commit/push.

## Integrasi Android pada 6 November 2026 — branch andro-backend

Status terkini: Android memakai data API Laravel melalui Retrofit 3.0.0. Catatan bertanggal di bawah bagian ini merekam tahap sebelumnya, termasuk saat UI masih memakai data contoh.

- [x] Branch `andro-backend` dibuat dari `main`; perubahan masih lokal, belum di-commit/push.
- [x] Auth server (login/daftar/me/logout), role server, token AES-GCM Android Keystore, pemulihan sesi, dan respons 401.
- [x] Dashboard, kegiatan, pencarian/filter/paginasi, daftar/batal, Kegiatanku dengan catatan arsip, peserta, CRUD tugas/status, dan arsip massal.
- [x] Pemilih tanggal/waktu dan ISO 8601 dengan zona waktu.
- [x] Sesi absensi dan kode nyata dari server, absensi relawan, serta daftar kehadiran.
- [x] Sampul/dokumentasi multipart, batas foto 5 MB, keterangan dan arsip foto, profil, riwayat, serta pengelolaan pengguna Admin.
- [x] Welcome XML dan sumber teksnya dipertahankan. Halaman lain tetap Kotlin, Nunito dan palet teal-sage tetap digunakan.
- [x] Koneksi dapat diatur di halaman masuk. HTTP debug lokal diizinkan; release memakai HTTPS. API tetap `/api/`.
- [x] Pesan validasi pada field, loading/kosong/error/retry, pembatalan request lama, dan pencegahan aksi tulis ganda. Pesan lama dibersihkan saat berpindah halaman.

| Pemeriksaan terbaru | Hasil |
| --- | --- |
| `:app:assembleDebug :app:assembleDebugAndroidTest` | Lulus; APK debug dibangun. |
| `:app:testDebugUnitTest` | 10 tes lulus: 9 kontrak HTTP/DTO dan 1 tes bawaan. |
| `:app:lintDebug` | 0 error, 53 warning, terutama resource/string/dependensi dan HTTP khusus debug. |
| `:app:assembleRelease` | Lulus; APK unsigned untuk pemeriksaan build, belum signing/distribusi produksi. |
| `:app:connectedDebugAndroidTest` dengan argumen server uji | 6 tes lulus pada emulator API 37. |
| Instrumentation ulang setelah perbaikan tampilan | `OK (6 tests)`, 44,78 detik; screenshot setelah data dimuat diperiksa. |
| API yang diuji | Laravel dari checkout ini, SQLite dan storage terisolasi di workspace, port 8765. `.env`, database MySQL, dan akun utama tidak diubah. |
| Koneksi perangkat | `10.0.2.2:8765` timeout pada host ini; ADB reverse dan `127.0.0.1:8765/api/` berhasil. |
| Pemeriksaan source | `git diff --check` lulus; backend dan resource Welcome tidak berubah. |

Tes perangkat mencakup Welcome XML, validasi login tanpa mode demo, token terenkripsi dan terikat server, retry saat server tidak terjangkau, serta alur integrasi Laravel. Alur tersebut menjalankan pendaftaran akun, kredensial salah/benar, tiga role, pendaftaran kegiatan, tugas dan perubahan status, absensi berkode, upload/edit/arsip dokumentasi, edit kegiatan melalui UI, pembuatan pengguna Admin, larangan akses daftar pengguna bagi relawan, riwayat dan pencabutan token. Sebagian fixture/pemeriksaan memakai Retrofit langsung; bukan seluruh endpoint diuji melalui ketukan UI.

Batas verifikasi: pemilih file/sampul pada perangkat fisik, API 24, koneksi Wi-Fi pengguna, MySQL utama, dan signing/deployment publik belum diuji. Draft formulir belum dipulihkan saat rotasi. Panduan koneksi dan perintah pengujian ada di `android/README.md`. Server uji dan emulator sementara dihentikan setelah verifikasi.


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
- [x] Integrasi Retrofit, token Sanctum, API tiga role, dan unggah dokumentasi ke Laravel (selesai 6 November 2026).
- [x] Ganti formulir tanggal/waktu contoh dengan pemilih terstruktur serta ISO 8601 (selesai 6 November 2026).
- [ ] Verifikasi pada HP fisik, Android API 24, dan skenario jaringan nyata.

UI ini menggunakan PreviewState lokal, bukan autentikasi atau otorisasi produksi. Pemilih peran hanya untuk pratinjau. Data demo berada dalam ViewModel dan bertahan selama proses aplikasi, termasuk recreate Activity. Draft formulir belum dipertahankan saat rotasi. Akses foto lokal belum berarti unggah ke server.

## Welcome XML pada 4 November 2026

- [x] Halaman Welcome dibuat sepenuhnya di android/app/src/main/res/layout/screen_welcome.xml; teks pada values/strings_welcome.xml. Warna, Nunito, foto, dan ikon memakai resource yang sudah tersedia.
- [x] MainActivity hanya memuat layout Welcome dan memasang listener tombol. Layar lain tetap memakai renderer Kotlin sebelumnya.
- [x] Alur dua splashscreen -> Welcome -> Masuk/Daftar terhubung. Tombol kembali dari Masuk menuju Welcome.
- [x] assembleDebug dan lintDebug lulus; tes perangkat khusus welcome_xml_routes_to_login_and_registration lulus pada emulator API 37, termasuk pemeriksaan konten XML dan dua tujuan tombol.
- [x] Screenshot Welcome pada emulator diperiksa; APK pratinjau diperbarui. Integrasi API tetap pekerjaan berikutnya.
