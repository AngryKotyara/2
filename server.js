import express from 'express';
import session from 'express-session';
import cookieParser from 'cookie-parser';
import { nanoid } from 'nanoid';
import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const app = express();
const PORT = process.env.PORT || 3000;
const DATA_FILE = path.join(__dirname, 'data', 'invites.json');

const ADMIN_LOGIN = process.env.ADMIN_LOGIN || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'change-me-now';
const SESSION_SECRET = process.env.SESSION_SECRET || 'replace-this-session-secret';
const PUBLIC_URL = (process.env.PUBLIC_URL || `http://localhost:${PORT}`).replace(/\/$/, '');

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(cookieParser());
app.use(session({
  secret: SESSION_SECRET,
  resave: false,
  saveUninitialized: false,
  cookie: { httpOnly: true, sameSite: 'lax', secure: false, maxAge: 1000 * 60 * 60 * 12 }
}));
app.use(express.static(path.join(__dirname, 'public')));

async function readInvites() {
  try {
    return JSON.parse(await fs.readFile(DATA_FILE, 'utf8'));
  } catch {
    return [];
  }
}

async function writeInvites(invites) {
  await fs.writeFile(DATA_FILE, JSON.stringify(invites, null, 2));
}

function requireAdmin(req, res, next) {
  if (!req.session?.admin) return res.status(401).json({ error: 'Требуется авторизация' });
  next();
}

app.get('/api/invite/:slug', async (req, res) => {
  const invites = await readInvites();
  const invite = invites.find(item => item.slug === req.params.slug && item.active !== false);
  if (!invite) return res.status(404).json({ error: 'Приглашение не найдено' });
  res.json({
    id: invite.id,
    slug: invite.slug,
    greeting: invite.greeting,
    guestNames: invite.guestNames,
    response: invite.response || null
  });
});

app.post('/api/invite/:slug/rsvp', async (req, res) => {
  const { firstName, lastName, attendance } = req.body;
  if (!firstName?.trim() || !lastName?.trim() || !['yes', 'no'].includes(attendance)) {
    return res.status(400).json({ error: 'Заполните имя, фамилию и выберите ответ' });
  }
  const invites = await readInvites();
  const index = invites.findIndex(item => item.slug === req.params.slug && item.active !== false);
  if (index < 0) return res.status(404).json({ error: 'Приглашение не найдено' });
  invites[index].response = {
    firstName: firstName.trim(),
    lastName: lastName.trim(),
    attendance,
    submittedAt: new Date().toISOString()
  };
  await writeInvites(invites);
  res.json({ ok: true });
});

app.post('/api/admin/login', (req, res) => {
  const { login, password } = req.body;
  if (login === ADMIN_LOGIN && password === ADMIN_PASSWORD) {
    req.session.admin = true;
    return res.json({ ok: true });
  }
  res.status(401).json({ error: 'Неверный логин или пароль' });
});

app.post('/api/admin/logout', requireAdmin, (req, res) => {
  req.session.destroy(() => res.json({ ok: true }));
});

app.get('/api/admin/session', (req, res) => {
  res.json({ authenticated: Boolean(req.session?.admin) });
});

app.get('/api/admin/invites', requireAdmin, async (req, res) => {
  const invites = await readInvites();
  res.json(invites.map(invite => ({ ...invite, url: `${PUBLIC_URL}/i/${invite.slug}` })));
});

app.post('/api/admin/invites', requireAdmin, async (req, res) => {
  const { guestNames, greeting } = req.body;
  if (!guestNames?.trim()) return res.status(400).json({ error: 'Укажите имя гостя или семьи' });
  const invites = await readInvites();
  const invite = {
    id: nanoid(10),
    slug: nanoid(8),
    guestNames: guestNames.trim(),
    greeting: (greeting || 'Дорогие гости').trim(),
    active: true,
    createdAt: new Date().toISOString(),
    response: null
  };
  invites.unshift(invite);
  await writeInvites(invites);
  res.json({ ...invite, url: `${PUBLIC_URL}/i/${invite.slug}` });
});

app.patch('/api/admin/invites/:id', requireAdmin, async (req, res) => {
  const invites = await readInvites();
  const index = invites.findIndex(item => item.id === req.params.id);
  if (index < 0) return res.status(404).json({ error: 'Приглашение не найдено' });
  const allowed = ['guestNames', 'greeting', 'active'];
  for (const key of allowed) {
    if (key in req.body) invites[index][key] = req.body[key];
  }
  await writeInvites(invites);
  res.json({ ok: true });
});

app.delete('/api/admin/invites/:id/response', requireAdmin, async (req, res) => {
  const invites = await readInvites();
  const index = invites.findIndex(item => item.id === req.params.id);
  if (index < 0) return res.status(404).json({ error: 'Приглашение не найдено' });
  invites[index].response = null;
  await writeInvites(invites);
  res.json({ ok: true });
});

app.get('/api/admin/export.csv', requireAdmin, async (_req, res) => {
  const invites = await readInvites();
  const rows = [['Приглашение', 'Имя', 'Фамилия', 'Ответ', 'Дата ответа', 'Ссылка']];
  for (const invite of invites) {
    rows.push([
      invite.guestNames,
      invite.response?.firstName || '',
      invite.response?.lastName || '',
      invite.response?.attendance === 'yes' ? 'Буду' : invite.response?.attendance === 'no' ? 'Не буду' : 'Нет ответа',
      invite.response?.submittedAt || '',
      `${PUBLIC_URL}/i/${invite.slug}`
    ]);
  }
  const csv = '\uFEFF' + rows.map(row => row.map(v => `"${String(v).replaceAll('"', '""')}"`).join(';')).join('\n');
  res.setHeader('Content-Type', 'text/csv; charset=utf-8');
  res.setHeader('Content-Disposition', 'attachment; filename="rsvp.csv"');
  res.send(csv);
});

app.get('/i/:slug', (_req, res) => res.sendFile(path.join(__dirname, 'public', 'index.html')));
app.get('/admin', (_req, res) => res.sendFile(path.join(__dirname, 'public', 'admin.html')));

app.listen(PORT, () => {
  console.log(`Wedding site is running at http://localhost:${PORT}`);
  if (ADMIN_PASSWORD === 'change-me-now') console.warn('Change ADMIN_PASSWORD before publishing.');
});
