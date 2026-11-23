const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const source = fs.readFileSync('app/src/main/java/com/xmu/course/ui/import/NauDomCapture.kt', 'utf8');
const script = source.match(/val examScript = """([\s\S]*?)"""\.trimIndent\(\)/)[1];
const fixture = fs.readFileSync('app/src/test/resources/sample_nau_exam.html', 'utf8');
const shell = fs.readFileSync('app/src/test/resources/sample_nau_exam_frame.html', 'utf8');
const frameId = shell.match(/<iframe[^>]+id="([^"]+)"/)[1];
const title = fixture.match(/<td class="tdTitle"[^>]*>([^<]+)</)[1];
const table = {
    outerHTML: fixture,
    querySelector: selector => selector === '.tdTitle' ? { textContent: title } : null,
};
const examPage = { querySelector: selector => selector === 'table#content' ? table : null };

function capture(frame, hostname = 'jwc.nau.edu.cn') {
    const document = { getElementById: id => id === frameId ? frame : null, querySelector: () => null };
    return vm.runInNewContext(script, { document, location: { hostname } });
}
assert.equal(capture({ contentDocument: examPage }), fixture);
assert.equal(capture({ contentDocument: examPage }, 'sso.nau.edu.cn'), null);
assert.equal(capture({ contentDocument: null }), null);
assert.equal(capture(null), null);
assert.equal(capture({ get contentDocument() { throw Error('cross-origin'); } }), null);
assert.equal(capture({ contentDocument: { querySelector: () => ({ querySelector: () => ({ textContent: '我的课表' }) }) } }), null);
console.log('Exam DOM capture passed for sample frame, login, wrong page, loading and inaccessible frame.');
