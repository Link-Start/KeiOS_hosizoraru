import {strict as assert} from 'node:assert';
import {createModelParts} from '../../app/src/main/assets/ba3d/model-parts.js';
const body = {name:'Body', visible:true, userData:{}};
const shield = {name:'Shield', visible:true, userData:{optional:true, prop0:true}};
const weapon = {name:'Weapon', visible:true, userData:{switch0:true}};
const authoredHidden = {name:'Hidden', visible:false, userData:{name:'Original name'}};
const root = {traverse:visit => [body,shield,weapon,authoredHidden].forEach(visit)};
const parts = createModelParts(root);
parts.apply();
assert.equal(shield.visible,false);
parts.apply({show:['prop0']}); // Hoshino combat: shield must appear.
assert.equal(shield.visible,true);
assert.deepEqual(parts.visibleTagged(),['Shield','Weapon']);
parts.apply({hide:'switch0'}); // Carrier idle: weapon disappears and previous shield is reset.
assert.equal(weapon.visible,false);
assert.equal(shield.visible,false);
parts.apply({hide:['prop0'],show:['prop0','Original name']});
assert.equal(shield.visible,true); // Explicit show wins over hidden/optional baseline.
assert.equal(authoredHidden.visible,true);
parts.apply({show:[42,null,'unknown','constructor']});
assert.equal(authoredHidden.visible,false);
assert.equal(body.visible,true);
assert.equal(weapon.visible,true);
console.log('Model parts: baseline, clip switches, exact names, precedence and malformed rules passed');
