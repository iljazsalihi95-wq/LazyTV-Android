#!/usr/bin/env bash
set -uo pipefail
mkdir -p device-evidence
adb logcat -c
set +e
./gradlew --no-daemon -PtvSmokeAbis=true connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.lazytv.pro.TvEntryDeviceTest --stacktrace
result=$?
adb logcat -d -v threadtime > device-evidence/logcat.txt
adb shell dumpsys activity activities > device-evidence/activities.txt
adb shell dumpsys meminfo de.lazytv.pro > device-evidence/memory.txt
adb exec-out screencap -p > device-evidence/last-screen.png
exit "$result"
