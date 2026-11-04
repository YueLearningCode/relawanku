# Android Relawanku

Aplikasi native Kotlin dan XML dengan Nunito serta sistem warna teal-sage dari README utama. Proyek Android Studio berada dalam repository GitHub yang sama dengan backend Laravel.

## Status implementasi

UI interaktif memakai data contoh lokal dalam PreviewState. Belum ada pemanggilan API, autentikasi server, atau penyimpanan ke MySQL. Pemilihan peran pada halaman masuk hanya untuk pratinjau; ini bukan mekanisme otorisasi produksi. Password tidak disimpan. Data demo bertahan saat rotasi layar, tetapi kembali ke awal setelah proses aplikasi berakhir.

Layar tersedia:

- Dua splashscreen animasi, masuk, dan daftar dengan validasi form.
- Relawan: beranda, jelajah dengan pencarian/filter, detail kegiatan, daftar/batal, Kegiatanku, tugas, absensi, dokumentasi, profil, dan riwayat.
- Koordinator: dashboard, daftar kegiatan, tambah/edit, arsip individu/massal dengan konfirmasi, peserta, tugas, sesi absensi, dan dokumentasi.
- Admin: dashboard, seluruh kegiatan, cari/filter pengguna, tambah/edit pengguna, penggantian peran, dan aktif/nonaktif akun contoh.
- Kondisi daftar data tersedia, loading, kosong, dan gagal dapat dilihat melalui ikon filter di daftar kegiatan.

## Menjalankan

1. Android Studio > Open > pilih folder android ini. Tunggu Gradle Sync selesai.
2. Pilih emulator atau HP yang sudah mengizinkan USB debugging.
3. Jalankan konfigurasi app.
4. Tunggu dua layar pembuka. Pada halaman masuk, gulir ke bagian Coba tampilan aplikasi.
5. Pilih Relawan, Koordinator, atau Admin, lalu ketuk Coba dengan data contoh. Server Laravel tidak diperlukan untuk tahap UI.
6. Peran dapat diganti dari Profil > Ganti peran pratinjau.

Kode absensi untuk simulasi UI adalah RW2026. Kode ini tidak terkait sesi absensi Laravel. Tanggal kegiatan dan nama peserta merupakan data ilustrasi. Validasi waktu, kepemilikan data, kuota bersamaan, dan hak akses produksi tetap harus ditegakkan oleh backend saat integrasi.

Form kegiatan saat ini menggunakan tanggal/waktu berupa teks untuk penyusunan UI; pemilihan tanggal/waktu terstruktur serta konversi ISO 8601 menjadi pekerjaan integrasi berikutnya. Form admin membuat profil contoh tanpa kredensial server. Pemilih foto menampilkan dokumentasi lokal, belum mengunggah file.

## Struktur kode

- app/src/main/java/com/example/relawanku/MainActivity.kt: shell navigasi, lifecycle, splash, profil, riwayat, dan pemilih dokumentasi.
- ui/PreviewState.kt: ViewModel dan contoh data untuk pratinjau.
- ui/UiKit.kt: komponen UI native yang dipakai ulang.
- ui/AuthScreens.kt: form masuk dan daftar.
- ui/VolunteerHome.kt: beranda relawan.
- ui/EventScreens.kt: daftar, detail, form, tugas, absensi, dan dokumentasi.
- ui/ManagementScreens.kt: dashboard dan pengelolaan pengguna.
- app/src/main/res/layout: XML shell, halaman scroll, dan kartu kegiatan.
- app/src/main/res/values: token warna dan tema Material 3.
- app/src/main/res/font: Nunito Regular 400, SemiBold 600, Bold 700 yang dibundel agar bisa dipakai offline.

## Pemeriksaan

Dari folder android:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

Pengujian perangkat membutuhkan emulator/HP aktif. Jika ada beberapa perangkat, pilih emulator dengan environment ANDROID_SERIAL. Hasil verifikasi terakhir dicatat di ../todo.md.

## Integrasi API berikutnya

Jalankan MySQL di Laragon. Dari backend jalankan php artisan serve --host=0.0.0.0 --port=8000.

- Emulator Android Studio: http://10.0.2.2:8000/api/
- HP pada jaringan yang sama: http://IP-LAPTOP:8000/api/

Tambahkan Retrofit, izin INTERNET, konfigurasi HTTP khusus pengembangan, dan penyimpanan token sesuai rancangan autentikasi. Ganti sumber data PreviewState dengan repository API, lalu validasi akses seluruh role melalui Laravel. Panduan endpoint: ../docs/API.md.

## Aset dan lisensi

Nunito diunduh dari https://github.com/google/fonts/tree/main/ofl/nunito dengan lisensi SIL Open Font License, disertakan dalam NUNITO-OFL.txt. Font variabel diturunkan menjadi tiga bobot statis untuk kompatibilitas perangkat.

Foto ilustrasi dari Unsplash disertakan lokal agar UI tetap tampil tanpa jaringan:

- Pantai: https://images.unsplash.com/photo-1507525428034-b723cf961d3e
- Hutan: https://images.unsplash.com/photo-1441974231531-c6227db76b6e
- Pendidikan: https://images.unsplash.com/photo-1503676260728-1c00da094a0b

Foto merupakan ilustrasi kategori, bukan dokumentasi kegiatan sebenarnya. Ikon garis berbasis VectorDrawable dibuat untuk UI ini.

Jangan commit local.properties, token, password, signing key, atau hasil build. Commit source Kotlin/XML, konfigurasi Gradle, dan Gradle wrapper.
