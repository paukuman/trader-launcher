# Trader Launcher

Home screen khusus trader:
- Jam WIB + status sesi Sydney / Tokyo / London / New York (jam buka-tutup dalam WIB, DST otomatis)
- Pantauan pair: crypto live dari Binance (refresh 5 dtk), forex kurs harian indikatif
- Tap pair -> buka chart di TradingView, tekan lama -> hapus
- Jurnal trading: entry/SL/TP, RR, hasil win/loss, setup, catatan; statistik win rate, net PnL, profit factor; ekspor CSV
- Alat: kalkulator ukuran posisi, checklist pra-trade, tautan cepat
- Dock aplikasi trading (auto-deteksi TradingView, MT4/5, Binance, Bybit, OKX) + pin manual
- App drawer dengan pencarian

## Build dari Termux

    pkg update && pkg install git gh unzip
    termux-setup-storage
    unzip ~/storage/downloads/TraderLauncher.zip -d ~
    cd ~/TraderLauncher
    gh auth login
    git init -b main
    git add . && git commit -m "init"
    gh repo create trader-launcher --private --source=. --push

    gh run watch
    gh run download -n TraderLauncher-debug
    cp TraderLauncher-debug/TraderLauncher.apk ~/storage/downloads/
