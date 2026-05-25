#!/bin/sh

~/Downloads/dumberos_build/out/host/linux-x86/bin/apksigner sign --key ~/.android-certs/platform.pk8 --cert ~/.android-certs/platform.x509.pem -out signed.apk ./app/build/outputs/apk/release/app-release-unsigned.apk
