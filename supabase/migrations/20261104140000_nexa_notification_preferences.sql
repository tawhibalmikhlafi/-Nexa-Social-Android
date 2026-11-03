-- Cross-device notification preferences. Device notification permission remains OS-controlled.
create table if not exists public.user_notification_preferences (
    user_id uuid primary key references public.profiles(id) on delete cascade,
    notifications_enabled boolean not null default true,
    categories jsonb not null default '{}'::jsonb
        check (jsonb_typeof(categories) = 'object'),
    updated_at timestamptz not null default now()
);

alter table public.user_notification_preferences enable row level security;

drop policy if exists "Users can read own notification preferences" on public.user_notification_preferences;
create policy "Users can read own notification preferences"
    on public.user_notification_preferences for select
    to authenticated using (auth.uid() = user_id);

drop policy if exists "Users can insert own notification preferences" on public.user_notification_preferences;
create policy "Users can insert own notification preferences"
    on public.user_notification_preferences for insert
    to authenticated with check (auth.uid() = user_id);

drop policy if exists "Users can update own notification preferences" on public.user_notification_preferences;
create policy "Users can update own notification preferences"
    on public.user_notification_preferences for update
    to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

grant select, insert, update on public.user_notification_preferences to authenticated;
