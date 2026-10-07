import {strict as assert} from 'node:assert';
import {modelBackgroundColor} from '../../app/src/main/assets/ba3d/model-background.js';
assert.equal(modelBackgroundColor('#F2F4F8'), '#f2f4f8');
assert.equal(modelBackgroundColor('#000000'), '#000000');
for (const invalid of [null, 123, '#FFF', '#80FFFFFF', 'red', '#ffffff;alert(1)', '#gggggg']) {
  assert.equal(modelBackgroundColor(invalid), null);
}
console.log('Renderer background colors: opaque RGB accepted, malformed/style/script inputs rejected');
