// Regression tests for the Jellyfin 12 transcode timeline (browser app).
// 12+ transcode URLs drop StartTimeTicks and serve a full-file VOD playlist from segment 0:
// the client seeks to the resume point itself, positions are already absolute, and the Auto
// quality step-down must NOT re-add a stream base offset (the "double-count" regression in 0.3.9,
// verified live against Jellyfin 12.4.0: resumed episode positions doubled on every step-down,
// 111.9s -> 222.1s -> 447.9s, ending episodes early). 10.x URLs carry StartTimeTicks: the stream
// starts at the resume point, the client must not seek, and the base offset is kept.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

const src = fs.readFileSync(path.join(__dirname, '..', 'public', 'app.js'), 'utf8');

// Extract the real helper from app.js so the test exercises shipped code, not a copy.
const m = src.match(/function transcodeServerSideStart[\s\S]*?\n}\n/);
if (!m) throw new Error('transcodeServerSideStart not found in app.js');
eval(m[0] + '\nglobalThis.transcodeServerSideStart = transcodeServerSideStart;');

const URL_10X = '/videos/i1/master.m3u8?MediaSourceId=m1&StartTimeTicks=18000000000&PlaySessionId=p1';
const URL_12 = '/videos/i1/master.m3u8?MediaSourceId=m1&PlaySessionId=p1&VideoBitrate=1616000';

test('server-side start is detected from the URL, not the version', () => {
  assert.strictEqual(transcodeServerSideStart(URL_10X), true);
  assert.strictEqual(transcodeServerSideStart(URL_12), false);
  assert.strictEqual(transcodeServerSideStart(''), false);
  assert.strictEqual(transcodeServerSideStart(null), false);
});

test('stream base is the resume offset only when the server starts the stream there', () => {
  const startTicks = 6000000000; // 10 minutes
  for (const [url, expectedBaseSec, expectedSeek] of [[URL_10X, 600, false], [URL_12, 0, true]]) {
    const serverStart = transcodeServerSideStart(url);
    const streamBaseSec = serverStart ? startTicks / 10000000 : 0;
    const shouldSeek = startTicks > 0 && !serverStart;
    assert.strictEqual(streamBaseSec, expectedBaseSec, url);
    assert.strictEqual(shouldSeek, expectedSeek, url);
    // A quality step-down must resume at the absolute position on both server generations:
    const currentTime = expectedSeek ? 620 : 20; // 12+: absolute clock; 10.x: stream clock
    assert.strictEqual(streamBaseSec + currentTime, 620, 'step-down must preserve the absolute position');
  }
});

test('goPlayer wires the URL-keyed start into the transcode branch and the seek guard', () => {
  assert.ok(src.includes('transcodeSeeksServerSide = transcodeServerSideStart(tRaw);'),
    'transcode branch must key the timeline off the TranscodingUrl');
  assert.ok(src.includes('state.streamBaseSec = transcodeSeeksServerSide ? startTicks / 10000000 : 0;'),
    'stream base must be 0 on Jellyfin 12+ (full-file playlist, client seeks)');
  assert.ok(src.includes('if (startTicks > 0 && !transcodeSeeksServerSide) {'),
    'client must not seek when the server already started the stream at the resume point (10.x)');
});
