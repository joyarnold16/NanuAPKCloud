import crypto from 'node:crypto';
import http from 'node:http';

const MAX_BODY_BYTES = 32 * 1024;

function send(response, status, payload) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  });
  response.end(JSON.stringify(payload));
}

function tokenMatches(request, token) {
  const header = request.headers.authorization || '';
  const candidate = header.startsWith('Bearer ') ? header.slice(7) : '';
  const expected = Buffer.from(token);
  const received = Buffer.from(candidate);
  return expected.length === received.length && crypto.timingSafeEqual(expected, received);
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new Error('Request body is too large.');
    chunks.push(chunk);
  }
  if (size === 0) return {};
  try {
    const parsed = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('Request body must be a JSON object.');
    return parsed;
  } catch (error) {
    throw new Error(`Invalid JSON body: ${error.message}`);
  }
}

export function createControlServer({ engine, controlToken, logger = console }) {
  return http.createServer(async (request, response) => {
    const url = new URL(request.url || '/', 'http://localhost');
    if (request.method === 'GET' && url.pathname === '/health') {
      send(response, 200, { ok: true, time: Date.now() });
      return;
    }
    if (!url.pathname.startsWith('/v1/')) {
      send(response, 404, { error: 'Not found.' });
      return;
    }
    if (!tokenMatches(request, controlToken)) {
      send(response, 401, { error: 'Unauthorized.' });
      return;
    }

    try {
      let result;
      if (request.method === 'GET' && url.pathname === '/v1/status') {
        result = engine.status();
      } else if (request.method === 'POST' && url.pathname === '/v1/control/start') {
        result = await engine.start();
      } else if (request.method === 'POST' && url.pathname === '/v1/control/stop') {
        result = await engine.stop('Stopped by authenticated controller.');
      } else if (request.method === 'POST' && url.pathname === '/v1/control/panic') {
        const body = await readJson(request);
        result = await engine.panic({ closePositions: body.closePositions === true });
      } else if (request.method === 'POST' && url.pathname === '/v1/control/scan') {
        result = await engine.scan('controller');
      } else if (request.method === 'POST' && url.pathname === '/v1/portfolio/sync') {
        result = await engine.syncPortfolio();
      } else if (request.method === 'POST' && url.pathname === '/v1/config') {
        const body = await readJson(request);
        result = await engine.updateConfig(body);
      } else {
        send(response, 404, { error: 'Unknown control endpoint.' });
        return;
      }
      send(response, 200, { ok: true, ...result });
    } catch (error) {
      logger.error('Control request failed.', error);
      send(response, 400, { error: error instanceof Error ? error.message : 'Request failed.' });
    }
  });
}
