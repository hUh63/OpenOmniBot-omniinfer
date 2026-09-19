// Real upstream serializer, local HTTP fixture only. No Provider calls.
// Usage: node scripts/verify-dsh-reasoning-wire.mjs INSTALLED_DEPENDENCY_DIRECTORY
import http from 'node:http';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';

const entry = resolve(process.argv[2] || '.',
  'node_modules/@earendil-works/pi-ai/dist/api/openai-completions.js');
const {streamSimple} = await import(pathToFileURL(entry));
const requests = [];
const server = http.createServer(async (req, res) => {
  let raw = '';
  for await (const chunk of req) raw += chunk;
  requests.push(JSON.parse(raw));
  res.writeHead(200, {'Content-Type': 'text/event-stream'});
  res.end('data: ' + JSON.stringify({
    id: 'fixture',
    choices: [{index: 0, delta: {content: 'ok'}, finish_reason: 'stop'}],
  }) + '\n\ndata: [DONE]\n\n');
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
try {
  const model = {
    id: 'gateway-reasoner', name: 'fixture', provider: 'omnibot-dispatch',
    api: 'openai-completions',
    baseUrl: `http://127.0.0.1:${server.address().port}/v1`,
    reasoning: true, input: ['text'], contextWindow: 128000, maxTokens: 4096,
    cost: {input: 0, output: 0, cacheRead: 0, cacheWrite: 0},
    // Mirrors the generated mapping asserted by AgentWebRuntimeTest.
    // The opt-in legacy value reproduces the pre-fix failure.
    thinkingLevelMap: {
      off: process.env.OOB_TEST_LEGACY_OFF === '1' ? null : 'none', high: 'high',
    },
  };
  for (const effort of ['high', 'off', 'high', 'off']) {
    // DSH's profileOptions omits Pi's reasoning argument for ACP `off`.
    const stream = streamSimple(model, {
      messages: [{role: 'user', content: 'OOB_DSH_WIRE', timestamp: Date.now()}],
    }, {
      apiKey: 'fixture', reasoning: effort === 'off' ? undefined : effort,
      maxRetries: 0, signal: AbortSignal.timeout(10000),
    });
    const result = await stream.result();
    assert.notEqual(result.stopReason, 'error', result.errorMessage);
    const request = requests.at(-1);
    assert(request, 'Upstream must send a request');
    console.log(JSON.stringify({selected: effort, sent: request.reasoning_effort ?? 'OMITTED'}));
    assert.equal(request.reasoning_effort, effort === 'off' ? 'none' : 'high',
      'Explicit Off must not become provider default');
  }
  assert.equal(requests.length, 4, 'One request per selection; no retry');
} finally {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}
