// Builds an installable APK of one app: node scripts/build-apk.js buyer|seller
// Output: dist/Shoppi-NguoiMua.apk or dist/Shoppi-NguoiBan.apk
//
// Needs the Android SDK (ANDROID_HOME, default D:\Android\SDK) and JDK 17+ (JAVA_HOME).
// "expo prebuild" regenerates the android/ folder for the chosen variant every time.
const { execSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const variant = process.argv[2] === 'seller' ? 'seller' : 'buyer'
const root = path.resolve(__dirname, '..')
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || 'D:\\Android\\SDK'
// emulator (x86_64) + almost every real phone (arm64-v8a); add armeabi-v7a for very old phones
const abis = process.env.ABIS || 'arm64-v8a,x86_64'
const env = { ...process.env, APP_VARIANT: variant, ANDROID_HOME: sdk, NODE_ENV: 'production' }
const run = (cmd, cwd = root) => execSync(cmd, { cwd, env, stdio: 'inherit' })

console.log(`\n=== Shoppi ${variant}: expo prebuild`)
run('npx expo prebuild --platform android --clean --no-install')
fs.writeFileSync(path.join(root, 'android', 'local.properties'), `sdk.dir=${sdk.replace(/\\/g, '\\\\')}\n`)

console.log(`\n=== Shoppi ${variant}: gradle assembleRelease (${abis})`)
const gradlew = path.join(root, 'android', process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')
run(`"${gradlew}" assembleRelease -PreactNativeArchitectures=${abis} --no-daemon`, path.join(root, 'android'))

const out = path.join(root, 'dist')
fs.mkdirSync(out, { recursive: true })
const apk = path.join(out, variant === 'seller' ? 'Shoppi-NguoiBan.apk' : 'Shoppi-NguoiMua.apk')
fs.copyFileSync(path.join(root, 'android', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk'), apk)
console.log(`\n✓ ${apk} (${(fs.statSync(apk).size / 1048576).toFixed(1)} MB)`)
