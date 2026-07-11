create table if not exists public.invites (
  id text primary key,
  slug text unique not null,
  guest_names text not null,
  greeting text not null default 'Дорогие гости',
  active boolean not null default true,
  created_at timestamptz not null default now(),
  response_first_name text,
  response_last_name text,
  attendance text check (attendance in ('yes','no')),
  submitted_at timestamptz
);

create index if not exists invites_slug_idx on public.invites (slug);

alter table public.invites enable row level security;
-- Политики не требуются: сайт обращается к таблице только через серверную функцию
-- с SUPABASE_SERVICE_ROLE_KEY, которая обходит RLS.
