const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const Jasmine = require('jasmine');
const root = path.resolve(__dirname, '..');
const configFile = ts.readConfigFile(path.join(root, 'tsconfig.spec.json'), ts.sys.readFile);
if (configFile.error) throw new Error(ts.flattenDiagnosticMessageText(configFile.error.messageText, '\n'));
const config = ts.parseJsonConfigFileContent(configFile.config, ts.sys, root);
const program = ts.createProgram(config.fileNames, config.options);
const diagnostics = [...config.errors, ...ts.getPreEmitDiagnostics(program)];
if (diagnostics.length) {
  console.error(ts.formatDiagnosticsWithColorAndContext(diagnostics, {
    getCurrentDirectory: () => root, getCanonicalFileName: x => x, getNewLine: () => '\n'
  }));
  process.exit(1);
}
const emitted = program.emit();
if (emitted.emitSkipped) process.exit(1);
fs.writeFileSync(path.join(root, '.verify/jasmine/package.json'), '{"type":"commonjs"}');
// Angular's partially compiled packages need the compiler when tested without the CLI linker.
require('@angular/compiler');
const runner = new Jasmine({projectBaseDir: root});
const specs = config.fileNames.filter(name => name.endsWith('.spec.ts'))
  .map(name => path.relative(path.join(root, 'tests/jasmine'), name).replace(/\.ts$/, '.js').replaceAll('\\', '/'));
runner.loadConfig({spec_dir: '.verify/jasmine/tests/jasmine', spec_files: specs,
  env: {random: true, seed: process.env.JASMINE_SEED, stopSpecOnExpectationFailure: false}});
runner.execute();
