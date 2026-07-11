import crypto from 'node:crypto';
import { createClient } from '@supabase/supabase-js';
import { nanoid } from 'nanoid';

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const ADMIN_LOGIN = process.env.ADMIN_LOGIN || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;
const SESSION_SECRET = process.env.SESSION_SECRET;
const PUBLIC_URL = (process.env.PUBLIC_URL || '').replace(/\/$/, '');

function getSupabase() {
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    throw new Error('Не заданы SUPABASE_URL и SUPABASE_SERVICE_ROLE_KEY');
  }
  return createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false }
  });
}

function send(res, status, body) {
  res.status(status).json(body);
}

function parseCookies(req) {
  const cookie = req.headers.cookie || '';
  return Object.fromEntries(cookie.split(';').map(v => v.trim()).filter(Boolean).map(v => {
    const i = v.indexOf('=');
    return [decodeURIComponent(v.slice(0, i)), decodeURIComponent(v.slice(i + 1))];
  }));
}

function signSession() {
  const payload = Buffer.from(JSON.stringify({ admin: true, exp: Date.now() + 12 * 60 * 60 * 1000 })).toString('base64url');
  const sig = crypto.createHmac('sha256', SESSION_SECRET).update(payload).digest('base64url');
  return `${payload}.${sig}`;
}

function validSession(req) {
  if (!SESSION_SECRET) return false;
  const token = parseCookies(req).wedding_admin;
  if (!token) return false;
  const [payload, sig] = token.split('.');
  if (!payload || !sig) return false;
  const expected = crypto.createHmac('sha256', SESSION_SECRET).update(payload).digest('base64url');
  const a = Buffer.from(sig);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return false;
  try {
    const data = JSON.parse(Buffer.from(payload, 'base64url').toString());
    return data.admin === true && data.exp > Date.now();
  } catch { return false; }
}

function requireAdmin(req, res) {
  if (!validSession(req)) {
    send(res, 401, { error: 'Требуется авторизация' });
    return false;
  }
  return true;
}

function mapInvite(row, req) {
  const base = PUBLIC_URL || `https://${req.headers.host}`;
  return {
    id: row.id,
    slug: row.slug,
    guestNames: row.guest_names,
    greeting: row.greeting,
    active: row.active,
    createdAt: row.created_at,
    response: row.response_first_name ? {
      firstName: row.response_first_name,
      lastName: row.response_last_name,
      attendance: row.attendance,
      submittedAt: row.submitted_at
    } : null,
    url: `${base}/i/${row.slug}`
  };
}

export default async function handler(req, res) {
  try {
    const parts = Array.isArray(req.query.path) ? req.query.path : [req.query.path].filter(Boolean);
    const route = parts.join('/');

    if (route === 'admin/login' && req.method === 'POST') {
      if (!ADMIN_PASSWORD || !SESSION_SECRET) return send(res, 500, { error: 'Не настроены ADMIN_PASSWORD или SESSION_SECRET' });
      const { login, password } = req.body || {};
      if (login !== ADMIN_LOGIN || password !== ADMIN_PASSWORD) return send(res, 401, { error: 'Неверный логин или пароль' });
      const token = signSession();
      res.setHeader('Set-Cookie', `wedding_admin=${encodeURIComponent(token)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=43200`);
      return send(res, 200, { ok: true });
    }

    if (route === 'admin/logout' && req.method === 'POST') {
      res.setHeader('Set-Cookie', 'wedding_admin=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0');
      return send(res, 200, { ok: true });
    }

    if (route === 'admin/session' && req.method === 'GET') {
      return send(res, 200, { authenticated: validSession(req) });
    }

    const supabase = getSupabase();

    if (parts[0] === 'invite' && parts.length === 2 && req.method === 'GET') {
      const { data, error } = await supabase.from('invites').select('*').eq('slug', parts[1]).eq('active', true).maybeSingle();
      if (error) throw error;
      if (!data) return send(res, 404, { error: 'Приглашение не найдено' });
      const invite = mapInvite(data, req);
      return send(res, 200, { id: invite.id, slug: invite.slug, greeting: invite.greeting, guestNames: invite.guestNames, response: invite.response });
    }

    if (parts[0] === 'invite' && parts[2] === 'rsvp' && req.method === 'POST') {
      const { firstName, lastName, attendance } = req.body || {};
      if (!firstName?.trim() || !lastName?.trim() || !['yes', 'no'].includes(attendance)) {
        return send(res, 400, { error: 'Заполните имя, фамилию и выберите ответ' });
      }
      const { data: existing, error: findError } = await supabase.from('invites').select('id').eq('slug', parts[1]).eq('active', true).maybeSingle();
      if (findError) throw findError;
      if (!existing) return send(res, 404, { error: 'Приглашение не найдено' });
      const { error } = await supabase.from('invites').update({
        response_first_name: firstName.trim(), response_last_name: lastName.trim(), attendance, submitted_at: new Date().toISOString()
      }).eq('id', existing.id);
      if (error) throw error;
      return send(res, 200, { ok: true });
    }

    if (route === 'admin/invites' && req.method === 'GET') {
      if (!requireAdmin(req, res)) return;
      const { data, error } = await supabase.from('invites').select('*').order('created_at', { ascending: false });
      if (error) throw error;
      return send(res, 200, data.map(row => mapInvite(row, req)));
    }

    if (route === 'admin/invites' && req.method === 'POST') {
      if (!requireAdmin(req, res)) return;
      const { guestNames, greeting } = req.body || {};
      if (!guestNames?.trim()) return send(res, 400, { error: 'Укажите имя гостя или семьи' });
      const row = {
        id: nanoid(10), slug: nanoid(8), guest_names: guestNames.trim(),
        greeting: (greeting || 'Дорогие гости').trim(), active: true
      };
      const { data, error } = await supabase.from('invites').insert(row).select('*').single();
      if (error) throw error;
      return send(res, 200, mapInvite(data, req));
    }

    if (parts[0] === 'admin' && parts[1] === 'invites' && parts.length === 3 && req.method === 'PATCH') {
      if (!requireAdmin(req, res)) return;
      const allowed = {};
      if ('guestNames' in req.body) allowed.guest_names = String(req.body.guestNames).trim();
      if ('greeting' in req.body) allowed.greeting = String(req.body.greeting).trim();
      if ('active' in req.body) allowed.active = Boolean(req.body.active);
      const { error } = await supabase.from('invites').update(allowed).eq('id', parts[2]);
      if (error) throw error;
      return send(res, 200, { ok: true });
    }

    if (parts[0] === 'admin' && parts[1] === 'invites' && parts[3] === 'response' && req.method === 'DELETE') {
      if (!requireAdmin(req, res)) return;
      const { error } = await supabase.from('invites').update({
        response_first_name: null, response_last_name: null, attendance: null, submitted_at: null
      }).eq('id', parts[2]);
      if (error) throw error;
      return send(res, 200, { ok: true });
    }

    if (route === 'admin/export.csv' && req.method === 'GET') {
      if (!requireAdmin(req, res)) return;
      const { data, error } = await supabase.from('invites').select('*').order('created_at', { ascending: false });
      if (error) throw error;
      const base = PUBLIC_URL || `https://${req.headers.host}`;
      const rows = [['Приглашение', 'Имя', 'Фамилия', 'Ответ', 'Дата ответа', 'Ссылка']];
      for (const row of data) rows.push([
        row.guest_names, row.response_first_name || '', row.response_last_name || '',
        row.attendance === 'yes' ? 'Буду' : row.attendance === 'no' ? 'Не буду' : 'Нет ответа',
        row.submitted_at || '', `${base}/i/${row.slug}`
      ]);
      const csv = '\uFEFF' + rows.map(r => r.map(v => `"${String(v).replaceAll('"', '""')}"`).join(';')).join('\n');
      res.setHeader('Content-Type', 'text/csv; charset=utf-8');
      res.setHeader('Content-Disposition', 'attachment; filename="rsvp.csv"');
      return res.status(200).send(csv);
    }

    return send(res, 404, { error: 'Маршрут не найден' });
  } catch (error) {
    console.error(error);
    return send(res, 500, { error: 'Ошибка сервера. Проверьте настройки Supabase и Vercel.' });
  }
}
