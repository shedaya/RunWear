// In-memory mock of the Supabase REST/Auth/Storage surface used by the
// revenuecat-webhook and request-identity-adoption functions, for the
// RW-137 local replay harness. NOT a database: merge semantics are
// proven against production throwaway accounts by the S1 SQL test;
// this mock proves the edge functions' control flow (auth,
// idempotency, identity mapping, TRANSFER adoption invocation,
// response shapes) against the real function code.
//
// State is seeded and scripted through /__seed and /__adopt_script,
// and every call is recorded in `calls` for assertions.
import http from 'node:http'

export function createMock() {
  const state = {
    users: new Map(),          // id -> {id}
    profiles: new Map(),       // id -> {entitled}
    webhookEvents: new Map(),  // event_id -> row
    adoptions: [],             // recorded adopt rpc outcomes
    adoptScript: new Map(),    // "S->D" -> behaviour
    objects: new Set(),        // storage object names in personal-heroes
    copies: [], removes: [], fillKicks: [], entitlementEvents: [],
    jwtUsers: new Map(),       // bearer token -> user id (for auth.getUser)
    calls: [],
  }

  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://x')
    const path = url.pathname
    let body = ''
    for await (const chunk of req) body += chunk
    let json = null
    try { json = body ? JSON.parse(body) : null } catch { /* raw */ }
    state.calls.push({ method: req.method, path, json })
    const send = (code, obj, ctype = 'application/json') => {
      res.writeHead(code, { 'Content-Type': ctype })
      res.end(obj === undefined ? '' : (typeof obj === 'string' ? obj : JSON.stringify(obj)))
    }
    const objectAccept = (req.headers.accept || '').includes('vnd.pgrst.object')

    // ---- harness control -------------------------------------------
    if (path === '/__seed' && req.method === 'POST') {
      for (const u of json.users ?? []) state.users.set(u, { id: u })
      for (const [id, p] of Object.entries(json.profiles ?? {})) state.profiles.set(id, p)
      for (const o of json.objects ?? []) state.objects.add(o)
      for (const [pair, b] of Object.entries(json.adoptScript ?? {})) state.adoptScript.set(pair, b)
      for (const [tok, uid] of Object.entries(json.jwtUsers ?? {})) state.jwtUsers.set(tok, uid)
      return send(200, { ok: true })
    }
    if (path === '/__state' && req.method === 'GET') {
      return send(200, {
        profiles: Object.fromEntries(state.profiles),
        webhookEvents: [...state.webhookEvents.values()],
        adoptions: state.adoptions, copies: state.copies, removes: state.removes,
        fillKicks: state.fillKicks, entitlementEvents: state.entitlementEvents,
        objects: [...state.objects].sort(),
        rpcCalls: state.calls.filter(c => c.path.startsWith('/rest/v1/rpc/')).map(c => ({ path: c.path, json: c.json })),
      })
    }
    if (path === '/__reset' && req.method === 'POST') {
      state.users.clear(); state.profiles.clear(); state.webhookEvents.clear()
      state.adoptions = []; state.adoptScript.clear(); state.objects.clear()
      state.copies = []; state.removes = []; state.fillKicks = []
      state.entitlementEvents = []; state.jwtUsers.clear(); state.calls = []
      return send(200, { ok: true })
    }

    // ---- auth -------------------------------------------------------
    const adminUser = path.match(/^\/auth\/v1\/admin\/users\/(.+)$/)
    if (adminUser && req.method === 'GET') {
      const u = state.users.get(adminUser[1])
      if (u) return send(200, { id: u.id, email: 't@example.com' })
      return send(404, { code: 404, msg: 'User not found' })
    }
    if (path === '/auth/v1/user' && req.method === 'GET') {
      const tok = (req.headers.authorization || '').replace(/^Bearer\s+/i, '')
      const uid = state.jwtUsers.get(tok)
      if (uid) return send(200, { id: uid, email: 't@example.com' })
      return send(401, { code: 401, msg: 'invalid JWT' })
    }

    // ---- postgrest --------------------------------------------------
    if (path === '/rest/v1/personal_webhook_events') {
      if (req.method === 'GET') {
        const id = (url.searchParams.get('event_id') || '').replace(/^eq\./, '')
        const row = state.webhookEvents.get(id)
        if (objectAccept) {
          if (row) return send(200, row)
          return send(406, { code: 'PGRST116', message: 'Results contain 0 rows' })
        }
        return send(200, row ? [row] : [])
      }
      if (req.method === 'POST') {
        state.webhookEvents.set(json.event_id, json)
        return send(201, '')
      }
    }
    if (path === '/rest/v1/personal_profiles' && req.method === 'GET') {
      const uid = (url.searchParams.get('user_id') || '').replace(/^eq\./, '')
      const p = state.profiles.get(uid)
      if (objectAccept) {
        if (p) return send(200, { user_id: uid, entitled: p.entitled === true })
        return send(406, { code: 'PGRST116', message: 'Results contain 0 rows' })
      }
      return send(200, p ? [{ user_id: uid, entitled: p.entitled === true }] : [])
    }
    if (path === '/rest/v1/rpc/set_personal_entitlement' && req.method === 'POST') {
      const { p_user_id, p_entitled, p_source } = json
      const p = state.profiles.get(p_user_id) ?? { entitled: false }
      p.entitled = p_entitled
      state.profiles.set(p_user_id, p)
      state.entitlementEvents.push({ user_id: p_user_id, entitled: p_entitled, source: p_source })
      return send(200, '')
    }
    if (path === '/rest/v1/rpc/adopt_personal_identity' && req.method === 'POST') {
      const { p_source, p_destination, p_proof } = json
      const pair = `${p_source}->${p_destination}`
      const behaviour = state.adoptScript.get(pair) ?? 'ok'
      state.adoptions.push({ source: p_source, destination: p_destination, proof: p_proof, phase: 'call' })
      if (behaviour === 'not_entitled') return send(200, { ok: false, reason: 'destination_not_entitled' })
      if (behaviour === 'tombstoned') return send(200, { ok: false, reason: 'source_tombstoned' })
      if (behaviour === 'already') return send(200, { ok: false, reason: 'source_already_adopted' })
      const prior = state.adoptions.filter(a => a.source === p_source && a.phase === 'call').length
      if (prior === 1) {
        // phase 1: one hero copy outstanding
        const from = `${p_source}/heroes/cellX/heroX.jpg`
        const to = `${p_destination}/heroes/cellX/heroX.jpg`
        return send(200, {
          ok: true, state: 'started', adoption_id: 'mock-adoption-1',
          refs_adopted: false, copies: [{ from, to }],
          note: 'perform the storage copies, then call again to complete',
        })
      }
      return send(200, {
        ok: true, state: 'done', adoption_id: 'mock-adoption-1',
        counts: { heroes_imported: 1 },
        source_cleanup_paths: [`${p_source}/heroes/cellX/heroX.jpg`, `${p_source}/tmp/junk.jpg`],
      })
    }

    // ---- storage ------------------------------------------------------
    if (path === '/storage/v1/object/copy' && req.method === 'POST') {
      state.copies.push(json)
      if (state.objects.has(json.destinationKey)) {
        return send(400, { statusCode: '409', error: 'Duplicate', message: 'The resource already exists' })
      }
      if (!state.objects.has(json.sourceKey)) {
        return send(404, { statusCode: '404', error: 'Not Found', message: 'Object not found' })
      }
      state.objects.add(json.destinationKey)
      return send(200, { Key: json.destinationKey })
    }
    if (path === '/storage/v1/object/list/personal-heroes' && req.method === 'POST') {
      const prefix = json.prefix ?? ''
      const names = [...state.objects]
        .filter(n => n.startsWith(prefix + '/'))
        .map(n => n.slice(prefix.length + 1))
        .filter(n => !n.includes('/'))
        .map(name => ({ name, id: 'obj-' + name }))
      return send(200, names)
    }
    if (path === '/storage/v1/object/personal-heroes' && req.method === 'DELETE') {
      state.removes.push(json.prefixes)
      for (const p of json.prefixes) state.objects.delete(p)
      return send(200, json.prefixes.map(name => ({ name })))
    }

    // ---- functions ----------------------------------------------------
    if (path === '/functions/v1/start-personal-fill' && req.method === 'POST') {
      state.fillKicks.push(json)
      return send(200, { ok: true, enqueued: 0 })
    }
    // RC subscriber API mock (used when REVENUECAT_API_BASE points here)
    const sub = path.match(/^\/rc\/v1\/subscribers\/(.+)$/)
    if (sub && req.method === 'GET') {
      const payload = state.adoptScript.get('rc:' + decodeURIComponent(sub[1]))
      if (payload) return send(200, payload)
      return send(404, { code: 404, message: 'Subscriber not found' })
    }
    if (path.startsWith('/__rc/') && req.method === 'POST') {
      const uid = path.slice('/__rc/'.length)
      state.adoptScript.set('rc:' + uid, json)
      return send(200, { ok: true })
    }

    return send(404, { error: 'mock: unhandled ' + req.method + ' ' + path })
  })
  return { server, state }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const { server } = createMock()
  const port = Number(process.env.MOCK_PORT || 54399)
  server.listen(port, '127.0.0.1', () => console.log(`mock supabase on ${port}`))
}
