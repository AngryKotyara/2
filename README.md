# Larissa & Vladislav — Vercel + Supabase

Версия сайта с рабочей админ-панелью, персональными ссылками и постоянным хранением RSVP.

## 1. Создайте базу Supabase

1. Создайте проект в Supabase.
2. Откройте SQL Editor.
3. Выполните содержимое файла `supabase.sql`.
4. В Project Settings → API скопируйте:
   - Project URL;
   - `service_role` key. Не публикуйте этот ключ и не вставляйте его в код.

## 2. Переменные Vercel

В Vercel: Project → Settings → Environment Variables добавьте:

- `SUPABASE_URL` — Project URL;
- `SUPABASE_SERVICE_ROLE_KEY` — service_role key;
- `ADMIN_LOGIN` — например `admin`;
- `ADMIN_PASSWORD` — новый сложный пароль;
- `SESSION_SECRET` — случайная строка длиной не менее 32 символов;
- `PUBLIC_URL` — адрес проекта, например `https://ваш-проект.vercel.app`.

Добавьте значения для Production, Preview и Development либо как минимум Production.
После этого выполните Redeploy.

## 3. Адреса

- Сайт: `/`
- Админ-панель: `/admin`
- Персональная ссылка: `/i/код`

## Безопасность

Пароль, старый SESSION_SECRET и любые ключи, опубликованные в прежнем README, нужно считать раскрытыми и больше не использовать.
