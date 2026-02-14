/**
 * Mock API server for activation flow demos (Phase H).
 * Mocks: POST /devices/, POST /validate-login/, POST /registerbanknumber
 * Run: npm run mock-api
 * Default: http://localhost:3999
 */

const express = require('express');
const app = express();
const PORT = process.env.MOCK_API_PORT || 3999;

app.use(express.json());

// POST /api/devices/
app.post('/api/devices/', (req, res) => {
  res.status(201).json({ ok: true });
});

// POST /api/validate-login/
app.post('/api/validate-login/', (req, res) => {
  res.json({ valid: true, approved: true });
});

// POST /api/registerbanknumber
app.post('/api/registerbanknumber', (req, res) => {
  res.status(201).json({ ok: true });
});

app.listen(PORT, () => {
  console.log(`Mock API at http://localhost:${PORT}`);
  console.log('  POST /api/devices/');
  console.log('  POST /api/validate-login/');
  console.log('  POST /api/registerbanknumber');
});
