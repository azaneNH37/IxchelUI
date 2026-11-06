// Runs the repository's Gradle wrapper with the given arguments, from the repository root.
import { spawnSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const windows = process.platform === 'win32'
const result = spawnSync(windows ? 'gradlew.bat' : './gradlew', process.argv.slice(2), {
  cwd: root,
  stdio: 'inherit',
  shell: windows,
})
process.exit(result.status ?? 1)
