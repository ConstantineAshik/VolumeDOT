import { readFileSync, existsSync } from 'node:fs';
import assert from 'node:assert/strict';

const listing = 'fastlane/metadata/android/en-US';
const read = path => readFileSync(path, 'utf8').replace(/^\uFEFF/, '').trim();
const gradle = read('app/build.gradle.kts');
const version = gradle.match(/versionName = "([^"]+)"/)[1];
const code = gradle.match(/versionCode = (\d+)/)[1];
const short = read(`${listing}/short_description.txt`);
assert(short.length > 0 && short.length < 80 && !short.endsWith('.'));
assert(read(`${listing}/full_description.txt`).length > 100);
assert(read(`${listing}/changelogs/${code}.txt`).length <= 500);
const metadata = read('fdroid/metadata/com.ashik.floatvol.yml');
assert(metadata.includes(`versionName: ${version}`));
assert(metadata.includes(`versionCode: ${code}`));
assert(metadata.includes('License: Apache-2.0'));
assert(metadata.includes('https://github.com/ConstantineAshik/VolumeDOT.git'));
assert(metadata.includes('fdroid=true'));
assert(existsSync('LICENSE') && existsSync('NOTICE'));
function pngSize(path) {
  const bytes = readFileSync(path);
  assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  return [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
}
assert.deepEqual(pngSize(`${listing}/images/icon.png`), [512, 512]);
for (let index = 1; index <= 4; index++) {
  const [width, height] = pngSize(`${listing}/images/phoneScreenshots/${index}.png`);
  assert(width >= 320 && height >= 320);
}
console.log(`VolumeDot ${version} (${code}): listing lengths, version fields and PNG assets passed.`);
console.log('This portable check does not replace fdroid lint or an F-Droid server build.');
