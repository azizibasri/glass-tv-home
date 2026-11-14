# Glass TV Home APK Release 📦

Fail pemasangan rasmi (*Official Release APK*) untuk **Glass TV Home** dan aplikasi pembantu mudah alih **Glass TV Installer**.

---

## ⬇️ Muat Turun Fail APK (Official Direct Downloads)

### 1. 📺 Glass TV Home (Untuk Android TV & Google TV)
Pelancar skrin utama (*home screen launcher*) ultra-ringan dengan rekaan *frosted glass* Apple TV, cuaca dinamik APIMS Malaysia, waktu solat automatik, RAM cleaner 2-klik, dan bebas iklan.

* **Fail APK**: [`glass-tv-home.apk`](glass-tv-home.apk)
* **Versi Semasa**: `v3.1.4` (☕ Kad Belanja Kopi DuitNow QR `@imsomangge`, label butang Candy Box OTA terkini, sokongan paparan logo rasmi GXBank)
* **Pautan Muat Turun Terus (Direct Link)**:  
  [https://github.com/aqmangge/glass-tv-home/raw/main/release/glass-tv-home.apk](https://github.com/aqmangge/glass-tv-home/raw/main/release/glass-tv-home.apk)
* **Saiz Fail**: ~730 KB (Ultra-ringan, muat turun dalam 1 saat & jimat memori TV)
* **Keserasian**: Android TV & Google TV (Android 8.0 Oreo ke atas, API 26+)

---

### 🚗 Glass Car (Untuk pemain kereta Android / Android car head units)
Skrin utama sentuh untuk pemain kereta Android: jam, muzik, meter laju digital besar dengan tekanan turbo, status pintu, trip, suhu penyejuk dan jarak minyak, pembersih RAM dan cache. Butiran: [`car/README.md`](../car/README.md).

* **Fail APK**: [`glass-car.apk`](glass-car.apk)
* **Versi Semasa**: `v1.0.0` (binaan pertama; belum diuji pada unit sebenar / first build, not yet tested on a real unit)
* **Pasang / Install**: `CAR_HOST=<ip> car/install.sh` (guna fail ini jika tiada binaan baharu / uses this file when there is no fresh build)
* **Saiz Fail**: ~270 KB
* **Keserasian**: Android 8.0 ke atas (API 26+), skrin landskap sentuh

---

### 2. 📱 Glass TV Installer (Untuk Telefon & Tablet Android)
Aplikasi pembantu telefon pintar untuk memasang Glass TV Home terus ke TV rumah anda secara tanpa wayar (wireless Wi-Fi) **tanpa perlukan komputer (PC), kabel USB, atau terminal**.

* **Fail APK**: [`glass-tv-installer.apk`](glass-tv-installer.apk)
* **Versi Semasa**: `v1.0.0`
* **Pautan Muat Turun Terus (Direct Link)**:  
  [https://github.com/aqmangge/glass-tv-home/raw/main/release/glass-tv-installer.apk](https://github.com/aqmangge/glass-tv-home/raw/main/release/glass-tv-installer.apk)
* **Saiz Fail**: ~5.2 MB (Dah siap ada pakej APK Glass TV di dalam aplikasi)
* **Keserasian**: Android 8.0 ke atas (Telefon pintar & tablet Android)

---

## 📱 Panduan Pemasangan Mengikut Keutamaan Peranti

Ikuti susunan kaedah berikut mengikut peranti yang anda miliki:

---

### ⭐ Keutamaan 1: Pengguna Telefon Pintar Android (Fokus Utama — Paling Mudah & Disyorkan)

Tidak perlu komputer atau pendrive USB! Pasang APK pembantu terus pada peranti Android anda:

#### ⚙️ Persediaan Awal di TV (Sekali Sahaja)
1. Pada remote TV anda, buka **Tetapan (Settings ⚙️)** $\rightarrow$ **System / Device Preferences** $\rightarrow$ **About**.
2. Tekan **Android TV OS Build** sebanyak **7 kali berturut-turut** sehingga keluar notis *"You are now a developer!"*.
3. Tekan kembali (Back), buka menu baharu **Developer options**, dan aktifkan:
   - ✅ **USB debugging** $\rightarrow$ Hidupkan (*Enable*).
   - *(Jika ada pilihan Network Debugging / Wireless Debugging, hidupkan juga)*.
4. Sambungkan telefon Android dan Android TV anda ke **rangkaian Wi-Fi yang sama**.

#### 🚀 Cara 1: Pasang Automatik 1-Klik (Wireless ADB - Paling Disyorkan)
1. **Pasang & Buka Aplikasi**:  
   Muat turun dan pasang [`glass-tv-installer.apk`](https://github.com/aqmangge/glass-tv-home/raw/main/release/glass-tv-installer.apk) pada telefon Android anda.
2. **Kesan TV Anda**:  
   Tekan butang **`🔍 Imbas Android TV di Rangkaian Wi-Fi`**. Aplikasi akan mencari Android TV anda secara automatik dan mengisi alamat IP (contohnya `192.168.68.110`).  
   *(Jika imbasan automatik tidak menjumpai TV, anda boleh taip alamat IP TV secara manual dari Tetapan Wi-Fi TV anda).*
3. **Pasang ke TV**:  
   Tekan butang biru **`🚀 Pasang Terus ke Android TV`**.
4. **Sahkan di Skrin TV**:  
   Jika pertama kali menyambung, skrin TV anda akan memaparkan notis *"Allow USB debugging?"*. Tandakan kotak **Always allow from this computer** dan tekan **OK / Allow** guna remote TV anda.
5. **Selesai & Lancarkan**:  
   Progress bar pemasangan akan berjalan sehingga 100%. Setelah selesai, tekan butang **`📺 Buka Glass TV Home di Skrin TV`**!

#### 🌐 Cara 2: Guna Pelayan Web Tempatan (Jika TV Tiada Debugging)
1. Di dalam aplikasi **Glass TV Installer** di telefon, tekan butang **`🌐 Pelayan Web TV`**.
2. Telefon akan memaparkan alamat URL tempatan, contohnya: `http://192.168.68.105:8080/`
3. Pada TV anda, buka aplikasi **Downloader** atau pelayar web TV (*Chrome/Browser*), taip alamat URL tersebut dan tekan **Go**.
4. Fail APK Glass TV akan dimuat turun serta-merta ke TV anda untuk dipasang!

---

### 💻 Keutamaan 2: Jika Tiada Telefon Android — Guna Laptop atau PC (Windows / Mac / Linux)

Jika anda tidak mempunyai telefon Android, gunakan laptop atau komputer anda dengan bantuan AI atau terminal:

1. **Bantuan AI Automatik 1-Baris Prompt**:
   Buka **Google Antigravity** [Percuma / Disyorkan], **Claude Code**, atau **Cursor** di komputer anda. Tampal satu baris ini sahaja:
   ```text
   install ni dekat tv rumah saya: https://github.com/aqmangge/glass-tv-home
   ```
   AI akan bersambung ke TV via Wi-Fi (`adb connect <ip>:5555`), menyahaktifkan bloatware/iklan yang melambatkan TV, dan memasang Glass TV Home secara automatik!
2. **Terminal ADB Manual**:
   ```bash
   adb connect <ALAMAT_IP_TV>:5555
   adb install -r release/glass-tv-home.apk
   adb shell am start -n com.gathin.tvhome/.MainActivity
   ```

---

### 🍏 Keutamaan 3: Jika Tiada Android & Tiada Komputer (Hanya Ada iPhone) — Guna iPhone & Bantuan Muse.ai

Jika anda hanya mempunyai iPhone/iPad:

1. **Bantuan AI Melalui Muse.ai**:
   Buka Safari di iPhone anda dan layari [**muse.ai**](https://muse.ai). Berikan soalan ini:
   > *"Saya guna iPhone dan ada Android TV [jenama TV anda, cth: Xiaomi Mi Box / TCL / Sony]. Berikan panduan mudah pasang APK dari https://github.com/aqmangge/glass-tv-home ke TV saya, dan senaraikan aplikasi bawaan kilang yang tidak berguna (bloatware) untuk dimatikan pada TV saya."*
2. **Pasang APK ke TV**:
   - Di TV, buka aplikasi **Downloader by AFTVnews** dan taip pautan terus:  
     `https://github.com/aqmangge/glass-tv-home/raw/main/release/glass-tv-home.apk`  
     TV akan memuat turun dan memasang Glass TV Home serta-merta.
   - Atau gunakan aplikasi percuma **LocalSend** di iPhone & TV untuk hantar APK melalui Wi-Fi.
3. **Nyahaktifkan Aplikasi Tidak Berguna & Iklan**:
   - Di TV: **Settings ⚙️** $\rightarrow$ **Apps** $\rightarrow$ **See all apps**, nyahaktifkan (*Disable*) aplikasi bawaan tidak berguna seperti yang dikenal pasti oleh Muse.ai.
   - Di TV: **Settings ⚙️** $\rightarrow$ **Network & Internet** $\rightarrow$ **Private DNS** $\rightarrow$ masukkan `dns.adguard-dns.com` untuk menyekat semua iklan secara sistemik.

---

### 🛠️ Pilihan Lain: Pasang Manual Guna Pendrive (USB)
1. Salin `glass-tv-home.apk` ke dalam pendrive USB.
2. Cucuk ke Android TV anda.
3. Buka pengurus fail di TV (*File Commander* atau *FX File Explorer*), klik fail APK dan tekan **Install**.
4. Tekan butang **Home** pada remote TV dan pilih **Glass TV Home** sebagai *Always / Sentiasa*.

---

## 🔄 Cara Kemas Kini Patch Terkini (OTA Update)

Pada skrin utama TV:
1. Halakan remote ke butang **Settings (ikon gear ⚙️)**.
2. Pilih **`🔄 Check for Updates`**.
3. TV akan menyemak versi terkini dari pelayan secara automatik. Jika ada versi baharu, tekan butang **Muat Turun & Pasang** untuk mengemas kini tanpa hilang sebarang tetapan atau susunan aplikasi anda!
