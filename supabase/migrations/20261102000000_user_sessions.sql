-- Nexa active user sessions (server-side source of truth).

create table if not exists public.user_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    device_name text not null,
    ip_address text,
    last_active_at timestamptz not null default now(),
    is_current boolean not null default false,
    created_at timestamptz not null default now(),

    constraint user_sessions_user_device_unique unique (user_id, device_name)
);

drop trigger if exists nexa_user_sessions_touch_updated_at on public.user_sessions;
create trigger nexa_user_sessions_touch_updated_at
before update on public.user_sessions
for each row execute function public.nexa_touch_updated_at();

-- RLS policies for user_sessions
alter table public.user_sessions enable row level security;

drop policy if exists nexa_user_sessions_select on public.user_sessions;
create policy nexa_user_sessions_select on public.user_sessions
for select to authenticated using (user_id = auth.uid());

drop policy if exists nexa_user_sessions_insert on public.user_sessions;
create policy nexa_user_sessions_insert on public.user_sessions
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists nexa_user_sessions_update on public.user_sessions;
create policy nexa_user_sessions_update on public.user_sessions
for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists nexa_user_sessions_delete on public.user_sessions;
create policy nexa_user_sessions_delete on public.user_sessions
for delete to authenticated using (user_id = auth.uid());

grant select, insert, update, delete on public.user_sessions to authenticated;

notify pgrst, 'reload schema';
