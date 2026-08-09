# Build instructions

## Debug

With gradle installed or using gradlew, run `gradle assembleDebug`. Debug will be at app/build/outputs/apk/debug/app-debug.apk.

## Release

With gradle installed or using gradlew, first set environment variables for signing:

export SONEME_KEYSTORE="$HOME/path to soneme-release.jks"
export SONEME_STORE_PASSWORD='keystore password'
export SONEME_KEY_PASSWORD='key password'

Then run `gradle assembleRelease`. Release will be at app/build/outputs/apk/release/app-release.apk. Copy this to the repo dir and rename it soneme-audiobooks-(ver).apk and you're set.
