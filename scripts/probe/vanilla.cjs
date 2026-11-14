// Protocol-level absence-of-client probe. No Forge, NeoForge or Scrip client mod.
const mc = require('minecraft-protocol');

async function connect() {
  return new Promise((resolve, reject) => {
    const client = mc.createClient({
      host: '127.0.0.1', port: 25575, username: 'P00Probe',
      auth: 'offline', version: '1.21.1', hideErrors: true
    });
    // Vanilla responds to configuration pings; NeoForge uses this round trip
    // to distinguish an ordinary client from a modded negotiation response.
    client.on('ping', ({ id }) => client.write('pong', { id }));
    client.on('state', state => {
      if (state === 'configuration') {
        client.write('custom_payload', {
          channel: 'minecraft:brand', data: Buffer.from([7, ...Buffer.from('vanilla')])
        });
      }
    });
    let stablePlay = false;
    const timer = setTimeout(() => {
      client.end();
      reject(new Error('connection timeout'));
    }, 30000);
    client.once('login', () => {
      // Remain connected briefly to detect immediate post-handshake rejection.
      setTimeout(() => { stablePlay = true; client.end(); }, 1500);
    });
    client.once('error', () => {
      clearTimeout(timer);
      reject(new Error('protocol or connection error'));
    });
    client.once('end', () => {
      clearTimeout(timer);
      if (stablePlay) resolve();
      else reject(new Error('disconnected before play login'));
    });
  });
}

(async () => {
  await connect();
  await new Promise(resolve => setTimeout(resolve, 500));
  await connect();
  console.log('PASS: vanilla 1.21.1 protocol reached play login twice without client mods');
})().catch(error => {
  console.error(`FAIL: ${error.message}`);
  process.exitCode = 1;
});
