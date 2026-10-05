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
    let viewport;
    let renders = 0;
    const base = { x: 0, y: -100, width: 200, height: 400 };
    const renderer = {
        spineLayers: [{ spine }],
        layerList: [{ position: base }],
        parsePad: value => Number(value) || 0,
        applyViewportToAll(value) { viewport = value; },
        resizeToContainer() { viewport = base; },
        pixiApp: { screen: { width: 100, height: 200 }, render() { renders++; },
            start() { playing = true; }, stop() { playing = false; } },
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
        renderer, viewport: () => viewport, renders: () => renders,
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

test('camera changes and reset redraw a paused pose without replacing its animation', () => {
    const { controller, viewport, renders, spine, track, selections, isPlaying } = attach();
    controller.setPlaying(false);
    assert.equal(controller.setCamera(2, 0.2, -0.1), true);
    assert.deepEqual(JSON.parse(JSON.stringify(viewport())), { x: 30, y: -20, width: 100, height: 200 });
    assert.equal(renders(), 1);
    assert.equal(controller.setCamera(1, 0, 0), true);
    assert.deepEqual(JSON.parse(JSON.stringify(viewport())), { x: 0, y: -100, width: 200, height: 400 });
    assert.equal(spine.state.tracks[0], track);
    assert.equal(track.trackTime, 12.5);
    assert.equal(selections(), 0);
    assert.equal(isPlaying(), false);
});

test('resize preserves normalized zoom and pan, and invalid camera values are rejected', () => {
    const { controller, renderer, viewport } = attach();
    controller.setCamera(2, 0.2, 0);
    renderer.pixiApp.screen = { width: 200, height: 100 };
    renderer.resizeToContainer();
    assert.deepEqual(JSON.parse(JSON.stringify(viewport())), { x: 30, y: 0, width: 100, height: 200 });
    for (const scale of [NaN, Infinity, 0, 5]) assert.equal(controller.setCamera(scale, 0, 0), false);
    assert.equal(controller.cameraState().scale, 2);
});
