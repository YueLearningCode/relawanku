# Android Relawanku

Aplikasi native Kotlin/XML dengan Nunito dan palet teal-sage, terhubung ke Laravel melalui Retrofit 3.0.0 dan Gson. Branch integrasi: `andro-backend`. Welcome tetap berupa XML; halaman lain tetap dirender dari Kotlin sesuai kesepakatan sebelumnya.

## Menjalankan dari Android Studio

1. Nyalakan MySQL Laragon. Pastikan `backend/.env` mengarah ke database lokal yang benar.
2. Dari folder `backend`, jalankan:

```powershell
php artisan migrate
php artisan storage:link
php artisan serve --host=0.0.0.0 --port=8000
```

3. Buka folder `android` melalui Android Studio, siapkan Android SDK yang diminta, JDK lengkap 21 untuk Java toolchain, lalu lakukan Gradle Sync.
4. Jalankan `app` pada emulator atau HP.
5. Welcome > Mulai langkah baikmu > **Atur koneksi server**, lalu gunakan salah satu alamat berikut:

| Perangkat | Base URL |
| --- | --- |
| Emulator Android Studio | `http://10.0.2.2:8000/api/` |
| HP melalui Wi-Fi yang sama | `http://IP-LAPTOP:8000/api/` |
| Emulator/HP USB melalui ADB reverse | `http://127.0.0.1:8000/api/` |

Untuk ADB reverse, jalankan dari terminal dengan Android platform-tools di PATH:

```powershell
adb reverse tcp:8000 tcp:8000
```

Jika ada beberapa perangkat, gunakan `adb -s SERIAL reverse tcp:8000 tcp:8000`. `127.0.0.1` pada Android hanya menuju laptop jika reverse sudah aktif. Aturan firewall dan jaringan tetap memengaruhi koneksi Wi-Fi/10.0.2.2. Pada komputer pengujian, port uji melalui 10.0.2.2 mengalami timeout; ADB reverse berhasil.

6. Masuk menggunakan email dan password akun server. Role dibaca dari respons login, tanpa pemilih role demo.

Opsional untuk database lokal: `php artisan db:seed --class=DemoSeeder`. Akun baru seeder adalah `admin@gmail.com`, `koordinator@gmail.com`, dan `relawan@gmail.com`, dengan password dari `DEMO_PASSWORD` atau fallback `RelawanDemo123!`. Seeder menjaga akun yang telah ada, sehingga password akun lama bisa berbeda. Pendaftaran di aplikasi membuat akun Relawan; Admin membuat akun Koordinator.

URL default debug adalah `http://10.0.2.2:8000/api/`. Bisa diganti melalui pengaturan aplikasi atau saat build:

```powershell
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://192.168.1.10:8000/api/
```

Pengaturan server tersimpan di perangkat dan mengungguli default build. Ganti kembali lewat halaman masuk bila perlu. Prefiks API tetap `/api/`, tanpa `/v1`.

## Perilaku aplikasi

Data baca kegiatan/dashboard/Kegiatanku/tugas/dokumentasi/riwayat memakai `ReadCache` dalam ViewModel selama 30 detik, maksimal 64 respons. Kembali ke halaman yang sama selama cache masih berlaku tidak mengirim request ulang. Cache dipisah per server/akun/role/filter/halaman, dan dibersihkan ketika sesi berakhir. `auth/me`, pengguna Admin, dan daftar kehadiran tetap diambil dari server. Tombol **Muat ulang** memaksa pengambilan data halaman aktif. Aksi tulis tidak di-cache dan otomatis membatalkan cache terkait.

Detail kegiatan menggunakan `my_registration` dari respons detail Laravel sehingga cukup satu request untuk mengetahui status pendaftaran dan kehadiran sendiri. Endpoint tugas/dokumentasi/peserta tetap dimuat ketika tabnya dibuka. Perubahan dari perangkat lain diambil saat halaman dimuat lagi setelah TTL 30 detik habis, atau segera lewat **Muat ulang**. Cache tidak menjalankan polling otomatis.


- Login, pendaftaran, pemulihan sesi melalui `/auth/me`, edit profil, dan logout yang mencabut token perangkat.
- Token dienkripsi AES-GCM menggunakan Android Keystore. Password tidak disimpan; backup aplikasi dinonaktifkan. Token terikat ke alamat server, dan perubahan server menghapus sesi lokal.
- Respons 401 mengakhiri sesi. Validasi 422 ditampilkan pada field; 403/409 dan kegagalan koneksi menampilkan pesan tanpa menganggap aksi berhasil.
- Daftar memakai paginasi server, pencarian, filter kategori/role, loading, kosong, serta tombol coba lagi. Request baca lama dibatalkan saat pindah halaman. Aksi tulis diblokir dari ketukan ganda dan tidak otomatis diulang.
- Relawan: dashboard kontribusi, jelajah, daftar/batal sebelum mulai, Kegiatanku dari `/my/registrations`, tugas sendiri, ubah status tugas, absensi, dokumentasi, profil, dan riwayat. Catatan kegiatan yang diarsipkan tetap terlihat dalam Kegiatanku.
- Koordinator: kegiatan milik sendiri, CRUD dan arsip massal, peserta, tugas dan penanggung jawab dari peserta aktif, arsip tugas, buka/tutup sesi absensi, unggah sampul/dokumentasi, edit keterangan, serta arsip foto.
- Admin: dashboard lintas kegiatan, memilih koordinator aktif pada form kegiatan, mencari/filter pengguna, membuat akun dengan password, edit profil/role, serta aktif/nonaktif akun lain. Perubahan profil tidak mengirim ulang role yang sama agar sesi tidak tercabut tanpa perlu.
- Form jadwal memakai DatePicker/TimePicker, dikirim ISO 8601 dengan zona waktu. Waktu dari server ditampilkan dalam zona perangkat.
- Absensi memakai kode delapan karakter dari server. Kode hanya berada di memori; setelah proses aplikasi berakhir, koordinator membuka sesi baru untuk memperoleh kode baru. Pembukaan sesi baru menutup sesi sebelumnya.
- Foto memakai OpenDocument, salinan sementara maksimal 5 MB, dan multipart `image` (JPEG/PNG/WebP). File sementara dibersihkan setelah upload. `storage:link` diperlukan untuk menampilkan media publik Laravel.
- HTTP diizinkan untuk build debug lokal. Build release mewajibkan HTTPS dan menolak cleartext. Contoh: `-PapiBaseUrl=https://api.domainmu.id/api/`. Signing release dan deployment server belum disiapkan.

`Hapus sesi lokal` tersedia ketika server tidak terjangkau. Ini hanya menghapus sesi pada perangkat, tanpa mencabut token di server. Gunakan Keluar untuk pencabutan server.

## Struktur kode

| Lokasi | Tanggung jawab |
| --- | --- |
| `data/ApiService.kt` | Kontrak endpoint Retrofit. |
| `data/ApiClient.kt` | Konfigurasi HTTP, bearer, alamat/media, dan pesan error. |
| `data/ReadCache.kt` | Cache baca sementara, TTL, isolasi akun, dan invalidasi selektif. |
| `data/ApiModels.kt` | DTO, paginasi, status, serta konversi tanggal. |
| `data/SessionStore.kt` | Sesi terenkripsi dan alamat server. |
| `data/UploadPart.kt` | Validasi ukuran/jenis dan multipart foto. |
| `MainActivity.kt` | Navigasi, lifecycle request, loading/retry, dan pemilih foto. |
| `ui/AppState.kt` | State navigasi dan akun dari server; bukan database lokal. |
| `ui/AuthScreens.kt`, `VolunteerHome.kt` | Autentikasi dan beranda. |
| `ui/EventScreens.kt`, `ActivityActions.kt` | Kegiatan, peserta, tugas, absensi, dokumentasi. |
| `ui/ManagementScreens.kt`, `AccountScreens.kt` | Dashboard, pengguna, profil, dan riwayat. |
| `ui/FormTools.kt`, `UiKit.kt` | Komponen formulir dan UI yang dipakai ulang. |
| `res/layout/screen_welcome.xml` | Welcome yang dapat diedit pada Design/Split. |
| `res/values/strings_welcome.xml` | Teks Welcome. |

Lokasi Kotlin di atas berada di `app/src/main/java/com/example/relawanku/`. API referensi: [../docs/API.md](../docs/API.md).

## Pengujian

Dari folder `android`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Unit test menggunakan MockWebServer untuk JSON login, bearer dan batas origin, paginasi, DELETE ber-body, multipart, validasi/error, URL media, waktu, dan PATCH dengan null eksplisit. Tes perangkat memeriksa Welcome XML, validasi login, enkripsi sesi, dan kegagalan koneksi.

Satu tes integrasi dilewati kecuali argumen `integrationBaseUrl` dan `integrationPassword` diberikan. **Gunakan server dengan database dan storage uji yang terpisah**, karena tes membuat akun/kegiatan dan melakukan CRUD. Server uji harus menyediakan akun DemoSeeder dengan password yang sama. Contoh setelah server terisolasi berjalan pada port 8765:

```powershell
adb reverse tcp:8765 tcp:8765
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.integrationBaseUrl=http://127.0.0.1:8765/api/ `
  -Pandroid.testInstrumentationRunnerArguments.integrationPassword=PASSWORD_DATABASE_UJI
```

Alur integrasi mencakup pendaftaran, login tiga role, validasi kredensial salah, pendaftaran kegiatan, tugas dan statusnya, absensi berkode, upload/edit/arsip dokumentasi, edit kegiatan, tambah pengguna, penolakan akses admin bagi relawan, riwayat, serta token yang dicabut. Sebagian persiapan dan pemeriksaan memakai Retrofit langsung; tindakan pengguna utama dijalankan melalui UI. Hasil verifikasi dicatat di [../todo.md](../todo.md).

Batas pengujian: belum diverifikasi pada HP fisik/API 24, jaringan Wi-Fi pengguna, signing release, atau deployment publik. Draft formulir belum dipulihkan saat rotasi; data server dimuat ulang dan aksi tidak diulang otomatis.

## Aset dan lisensi

Nunito diunduh dari https://github.com/google/fonts/tree/main/ofl/nunito dengan lisensi SIL Open Font License, disertakan dalam NUNITO-OFL.txt. Font variabel diturunkan menjadi tiga bobot statis untuk kompatibilitas perangkat.

Foto ilustrasi dari Unsplash disertakan lokal agar UI tetap tampil tanpa jaringan:

- Pantai: https://images.unsplash.com/photo-1507525428034-b723cf961d3e
- Hutan: https://images.unsplash.com/photo-1441974231531-c6227db76b6e
- Pendidikan: https://images.unsplash.com/photo-1503676260728-1c00da094a0b

Foto merupakan ilustrasi kategori, bukan dokumentasi kegiatan sebenarnya. Ikon garis berbasis VectorDrawable dibuat untuk UI ini.

Jangan commit local.properties, token, password, signing key, atau hasil build. Commit source Kotlin/XML, konfigurasi Gradle, dan Gradle wrapper.

## Halaman Welcome dengan XML

Halaman Welcome tersusun sepenuhnya di app/src/main/res/layout/screen_welcome.xml. Buka file tersebut lalu pilih Split atau Design di Android Studio untuk melihat dan mengubah tata letaknya. Teks berada di values/strings_welcome.xml; warna, Nunito, ikon dan foto memakai resource yang sudah tersedia. Preview menampilkan konten asli karena teks dan gambar ditentukan langsung di XML.

Alur awal: splash pertama → splash kedua → Welcome → Masuk atau Daftar. Kotlin pada fungsi welcome di MainActivity hanya memuat layout dan memasang aksi tombol. Layar lainnya tetap memakai pendekatan Kotlin sebelumnya.
