#!/bin/bash
# Smoke test di emulator: pasang, buka, tunggu data demo, screenshot tiap tahap, cek crash.
mkdir -p smoke-out
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK"
adb logcat -c
adb shell am start -n id.sehati.app.debug/id.sehati.app.MainActivity
shot() { adb exec-out screencap -p > "smoke-out/$1.png"; }
sleep 25; shot 01_setelah_buka
# layar sambutan -> tombol masuk (koordinat relatif; screenshot menjadi bukti utama)
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml smoke-out/ui_welcome.xml >/dev/null 2>&1
adb logcat -d > smoke-out/logcat.txt
echo "=== CRASH CHECK ==="
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: id.sehati" smoke-out/logcat.txt; then
  grep -A25 "FATAL EXCEPTION" smoke-out/logcat.txt | head -60
  echo "RESULT: CRASH"; exit 1
fi
echo "RESULT: NO_CRASH"
