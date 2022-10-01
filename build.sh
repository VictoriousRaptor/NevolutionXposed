#!/bin/bash
./gradlew assembleRelease && adb install build/outputs/apk/release/*.apk