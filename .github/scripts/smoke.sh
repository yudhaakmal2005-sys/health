#!/bin/bash
# Menjalankan tes instrumented E2E di emulator lalu menarik screenshot + log.
mkdir -p smoke-out
adb logcat -c
adb shell settings put global hide_error_dialogs 1
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
./gradlew --no-daemon --console=plain -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.notClass=id.sehati.app.ShowcaseTest connectedDebugAndroidTest > smoke-out/connected.log 2>&1
code=$?

# Rekaman video pameran dengan animasi MENYALA (tidak memengaruhi status lulus/gagal).
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb shell screenrecord --time-limit 175 --bit-rate 6000000 /sdcard/sehati-demo.mp4 &
REC=$!
sleep 1
# APK app + tes sudah terpasang dari langkah sebelumnya: jalankan instrumentasi langsung agar rekaman mulai tepat waktu.
adb shell am instrument -w -e class id.sehati.app.ShowcaseTest id.sehati.app.debug.test/id.sehati.app.HiltTestRunner > smoke-out/showcase.log 2>&1 || echo "showcase gagal (tidak memblokir)"
adb shell pkill -INT screenrecord || true
wait $REC 2>/dev/null; sleep 3
adb pull /sdcard/sehati-demo.mp4 smoke-out/sehati-demo.mp4 > /dev/null 2>&1 && ls -la smoke-out/sehati-demo.mp4
adb pull /sdcard/Android/data/id.sehati.app.debug/files/shots smoke-out/shots > /dev/null 2>&1
adb logcat -d > smoke-out/logcat.txt
grep -E "FATAL EXCEPTION" -A20 smoke-out/logcat.txt | head -50
grep -E "^e: |FAILED|Tests? .*(failed|passed)|There were failing" -A3 smoke-out/connected.log | head -40
ls smoke-out/shots 2>/dev/null | wc -l
echo "GRADLE_CODE=$code" > smoke-out/code.txt
exit $code
