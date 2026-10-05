import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('../../app/src/main/java/os/kei/ui/page/main/student/section/gallery/GuideWebMemoryLobbyScripts.kt', import.meta.url), 'utf8');
const script = source.match(/GameKeeLobbyFocusScript = """([\s\S]*?)"""\.trimIndent\(\)/)[1];

function attach({ missingRenderer = false } = {}) {
    const track = { animation: { name: 'Idle_01' }, trackTime: 12.5 };
    const spine = {
        skeleton: { data: { animations: [{ name: 'Idle_01' }, { name: 'Talk_01_M' }] } },
        state: { tracks: [track], timeScale: 1 },
    };
    let playing = true;
    let selections = 0;
    const renderer = {
        spineLayers: [{ spine }],
        pixiApp: { start() { playing = true; }, stop() { playing = false; } },
        setAnimation(name, loop) {
            selections++;
            spine.state.tracks[0] = { animation: { name }, trackTime: 0, loop };
            return true;
        },
    };
    const player = {
        querySelector: selector => selector === 'canvas' ? { width: 100, height: 200 } : null,
        classList: { contains: () => true },
    };
    const context = {
        window: {},
        document: {
            querySelector: selector => selector === '.ba-live2d-container' ? player :
                selector === '.ba-pixi-live2d-view' ? (missingRenderer ? null : { __vue__: renderer }) : {},
            getElementById: () => ({}),
        },
    };
    const result = runInNewContext(script, context);
    return { result, controller: context.window.keiosLobby, spine, track,
        isPlaying: () => playing, selections: () => selections };
}

test('actions and current selection come from the skeleton without a Wiki dropdown', () => {
    const { result, controller } = attach();
    assert.equal(result, 'ready');
    assert.deepEqual(JSON.parse(JSON.stringify(controller.state())), {
        actions: ['Idle_01', 'Talk_01_M'], action: 'Idle_01',
    });
    assert.equal(controller.setAction('Talk_01_M'), true);
    assert.equal(controller.state().action, 'Talk_01_M');
    assert.equal(controller.setAction('Unknown'), false);
    assert.equal(controller.state().action, 'Talk_01_M');
});

test('pause and resume retain the same animation track and elapsed time', () => {
    const { controller, spine, track, isPlaying, selections } = attach();
    controller.setPlaying(false);
    assert.equal(isPlaying(), false);
    assert.equal(spine.state.timeScale, 0);
    assert.equal(spine.state.tracks[0], track);
    controller.setPlaying(true);
    assert.equal(isPlaying(), true);
    assert.equal(spine.state.timeScale, 1);
    assert.equal(spine.state.tracks[0], track);
    assert.equal(track.trackTime, 12.5);
    assert.equal(selections(), 0);
});

test('a replaced or unavailable Wiki renderer stays in the recoverable loading state', () => {
    const { result, controller } = attach({ missingRenderer: true });
    assert.equal(result, 'waiting');
    assert.equal(controller, undefined);
});
