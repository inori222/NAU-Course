const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const source = fs.readFileSync('app/src/debug/java/com/xmu/course/ui/import/DebugPageInspection.kt', 'utf8');
const script = source.match(/internal val script = """([\s\S]*?)"""\.trimIndent\(\)/)[1];
const body = { children: [1, 2], classList: { contains: () => true }, getBoundingClientRect: () => ({ width: 300, height: 600 }) };
const inner = { readyState: 'complete', body, getElementsByTagName: () => Array(20) };
const frame = { contentDocument: inner, getBoundingClientRect: () => ({ width: 200, height: 400, left: 10, right: 210, top: 20, bottom: 420 }) };
function run(selectedFrame) {
  const document = { body, documentElement: { clientHeight: 700, classList: { contains: () => true }, getBoundingClientRect: () => ({ height: 700 }) }, styleSheets: [], readyState: 'complete', elementFromPoint: () => ({ tagName: 'DIV' }), getElementById: () => selectedFrame, getElementsByTagName: tag => Array(tag === 'iframe' ? 1 : tag === 'link' ? 0 : 50) };
  return vm.runInNewContext(script, { document, window: { innerWidth: 390, innerHeight: 700 }, getComputedStyle: () => ({ display: 'block', visibility: 'visible', opacity: '1' }) });
}
const result = run(frame);
assert.equal(result.frameReadable, true);
assert.equal(result.frameElements, 20);
assert.equal(result.frameWidth, 200);
assert.equal(result.frameInViewport, true);
assert.equal(result.centerTag, 'DIV');
assert.equal(run(null).framePresent, false);
const crossOrigin = { get contentDocument() { throw new Error('secret URL'); }, getBoundingClientRect: frame.getBoundingClientRect };
assert.equal(run(crossOrigin).frameReadable, false);
assert(!JSON.stringify(run(crossOrigin)).includes('secret'));
const repairSource = fs.readFileSync('app/src/main/java/com/xmu/course/ui/import/NauLayoutRepair.kt', 'utf8');
const resizeScript = repairSource.match(/val script = """([\s\S]*?)"""\.trimIndent\(\)/)[1];
let events = 0, layout = 0;
function style() {
  let value = '', priority = '';
  return { getPropertyValue: () => value, getPropertyPriority: () => priority, setProperty: (_, v, p) => { value = v; priority = p; }, removeProperty: () => { value = ''; priority = ''; } };
}
const testBody = { style: style(), classList: { contains: x => x === 'easyui-layout' }, getBoundingClientRect: () => ({ height: 0 }) };
const root = { style: style(), getBoundingClientRect: () => ({ height: 0 }) };
const document = { body: testBody, documentElement: root, readyState: 'complete', getElementById: () => frame };
let expectedHeight = 700;
function jq() { return { layout: (x, size) => { assert.equal(x, 'resize'); assert.equal(size.height, expectedHeight); layout++; } }; }
jq.fn = { layout() {} };
jq.data = () => ({});
function Event(type) { this.type = type; }
let listener = null;
const window = { innerWidth: 390, innerHeight: 700, location: { protocol: 'https:', hostname: 'jwc.nau.edu.cn' }, dispatchEvent: event => { assert.equal(event.type, 'resize'); events++; }, addEventListener: (type, handler) => { assert.equal(type, 'resize'); listener = handler; }, removeEventListener: () => {}, jQuery: jq };
assert.equal(vm.runInNewContext(resizeScript, { document, window, Event }), 'height-adjusted');
assert.equal(layout, 1);
assert.equal(testBody.style.getPropertyValue('height'), '700px');
assert.equal(root.style.getPropertyValue('height'), '700px');
expectedHeight = 500;
window.innerHeight = 500;
listener();
assert.equal(testBody.style.getPropertyValue('height'), '500px');
assert.equal(root.style.getPropertyValue('height'), '500px');
assert.equal(layout, 2);
expectedHeight = 700;
window.innerHeight = 700;
testBody.getBoundingClientRect = () => ({ height: 700 });
assert.equal(vm.runInNewContext(resizeScript, { document, window, Event }), 'body-not-zero');
testBody.getBoundingClientRect = () => ({ height: 0 });
window.location.hostname = 'other.example';
assert.equal(vm.runInNewContext(resizeScript, { document, window, Event }), 'not-matched');
window.location.hostname = 'jwc.nau.edu.cn';
delete window.jQuery;
assert.equal(vm.runInNewContext(resizeScript, { document, window, Event }), 'not-matched');
assert.equal(events, 1);
window.jQuery = jq;
testBody.style.setProperty('height', '2px', '');
root.style.setProperty('height', '3px', '');
function throwingJq() { return { layout: () => { throw new Error('secret'); } }; }
throwingJq.fn = { layout() {} };
throwingJq.data = () => ({});
window.jQuery = throwingJq;
assert.equal(vm.runInNewContext(resizeScript, { document, window, Event }), 'failed');
assert.equal(testBody.style.getPropertyValue('height'), '2px');
assert.equal(root.style.getPropertyValue('height'), '3px');
console.log('Inspection and zero-height repair scripts: frame, origin, zero-height and EasyUI guards passed');
