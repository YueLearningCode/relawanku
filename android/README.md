# Android Relawanku

Folder ini adalah lokasi proyek Android dalam repository yang sama dengan backend Laravel. Aplikasi Android belum dibuat. Buat proyek melalui Android Studio agar versi Android Gradle Plugin, Kotlin, Gradle wrapper, dan SDK sesuai instalasi tim.

## Membuat proyek

1. Buka Android Studio dan pilih New Project.
2. Pilih Empty Views Activity untuk Kotlin dan layout XML. Jangan memilih template Compose.
3. Isi nama Relawanku dan package sesuai kesepakatan tim, misalnya id.ac.relawanku.
4. Atur Save location ke folder android ini. Jika wizard menolak folder yang sudah berisi README, buat di folder sementara di luar repository, lalu salin seluruh file proyek termasuk gradlew, gradlew.bat, folder gradle, dan file tersembunyi ke sini. Jangan salin folder .git.
5. Jangan membuat repository Git baru. Repository induk sudah terhubung ke GitHub.
6. Tunggu Gradle Sync selesai, lalu jalankan aplikasi bawaan pada emulator atau HP.

Buka folder android, bukan backend, ketika Android Studio meminta lokasi proyek.

## Koneksi backend

Jalankan MySQL di Laragon. Dari folder backend, jalankan:

```powershell
php artisan serve --host=0.0.0.0 --port=8000
```

Base URL Retrofit harus berakhiran garis miring:

- Emulator Android Studio: http://10.0.2.2:8000/api/
- HP pada jaringan yang sama: http://IP-LAPTOP:8000/api/

Tambahkan izin INTERNET serta pengaturan HTTP khusus pengembangan saat Retrofit diimplementasikan. Gunakan HTTPS untuk server publik. Panduan endpoint dan autentikasi ada di ../docs/API.md.

Jangan commit local.properties, token, password, signing key, atau hasil build. Commit source Kotlin/XML, konfigurasi Gradle, dan Gradle wrapper.
