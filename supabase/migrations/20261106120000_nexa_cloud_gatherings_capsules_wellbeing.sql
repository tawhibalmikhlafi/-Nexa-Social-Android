-- Cloud-backed foundations for Nexa gatherings, encrypted time capsules and wellbeing plans.
-- Capsule plaintext is never stored here; only AES-GCM ciphertext and IV are accepted.
create table if not exists public.nexa_gatherings (
    id uuid primary key default gen_random_uuid(),
    host_id uuid not null references public.profiles(id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 3 and 120),
    description text not null default '' check (char_length(description) <= 3000),
    location text not null default '',
    is_virtual boolean not null default false,
    starts_at timestamptz not null,
    duration_minutes integer not null default 60 check (duration_minutes between 15 and 1440),
    capacity integer not null default 0 check (capacity >= 0),
    tags text[] not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists nexa_gatherings_starts_at_idx on public.nexa_gatherings(starts_at);
create index if not exists nexa_gatherings_host_id_idx on public.nexa_gatherings(host_id);

create table if not exists public.nexa_gathering_attendees (
    gathering_id uuid not null references public.nexa_gatherings(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (gathering_id, user_id)
);
create index if not exists nexa_gathering_attendees_user_idx on public.nexa_gathering_attendees(user_id);

create table if not exists public.nexa_time_capsules (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 3 and 120),
    ciphertext text not null,
    iv text not null,
    unlock_at timestamptz not null,
    opened_at timestamptz,
    created_at timestamptz not null default now(),
    constraint nexa_capsule_iv_nonempty check (char_length(iv) between 8 and 128)
);
create index if not exists nexa_time_capsules_user_unlock_idx on public.nexa_time_capsules(user_id, unlock_at);

create table if not exists public.nexa_wellbeing_plans (
    user_id uuid primary key references public.profiles(id) on delete cascade,
    daily_intention text not null default '' check (char_length(daily_intention) <= 500),
    focus_mode boolean not null default false,
    quiet_hours_enabled boolean not null default true,
    quiet_start_hour integer not null default 22 check (quiet_start_hour between 0 and 23),
    quiet_end_hour integer not null default 7 check (quiet_end_hour between 0 and 23),
    daily_goal_minutes integer not null default 45 check (daily_goal_minutes between 5 and 1440),
    mindful_reminders boolean not null default true,
    weekly_reflection boolean not null default true,
    updated_at timestamptz not null default now()
);

alter table public.nexa_gatherings enable row level security;
alter table public.nexa_gathering_attendees enable row level security;
alter table public.nexa_time_capsules enable row level security;
alter table public.nexa_wellbeing_plans enable row level security;

drop policy if exists nexa_gatherings_read_authenticated on public.nexa_gatherings;
create policy nexa_gatherings_read_authenticated on public.nexa_gatherings
for select to authenticated using (true);
drop policy if exists nexa_gatherings_insert_owner on public.nexa_gatherings;
create policy nexa_gatherings_insert_owner on public.nexa_gatherings
for insert to authenticated with check (host_id = (select auth.uid()));
drop policy if exists nexa_gatherings_update_owner on public.nexa_gatherings;
create policy nexa_gatherings_update_owner on public.nexa_gatherings
for update to authenticated using (host_id = (select auth.uid())) with check (host_id = (select auth.uid()));
drop policy if exists nexa_gatherings_delete_owner on public.nexa_gatherings;
create policy nexa_gatherings_delete_owner on public.nexa_gatherings
for delete to authenticated using (host_id = (select auth.uid()));

drop policy if exists nexa_gathering_attendees_read_authenticated on public.nexa_gathering_attendees;
create policy nexa_gathering_attendees_read_authenticated on public.nexa_gathering_attendees
for select to authenticated using (true);
drop policy if exists nexa_gathering_attendees_insert_self on public.nexa_gathering_attendees;
create policy nexa_gathering_attendees_insert_self on public.nexa_gathering_attendees
for insert to authenticated with check (user_id = (select auth.uid()));
drop policy if exists nexa_gathering_attendees_delete_self_or_host on public.nexa_gathering_attendees;
create policy nexa_gathering_attendees_delete_self_or_host on public.nexa_gathering_attendees
for delete to authenticated using (
    user_id = (select auth.uid()) or exists (
        select 1 from public.nexa_gatherings g
        where g.id = gathering_id and g.host_id = (select auth.uid())
    )
);

drop policy if exists nexa_time_capsules_owner_all on public.nexa_time_capsules;
create policy nexa_time_capsules_owner_all on public.nexa_time_capsules
for all to authenticated using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists nexa_wellbeing_owner_all on public.nexa_wellbeing_plans;
create policy nexa_wellbeing_owner_all on public.nexa_wellbeing_plans
for all to authenticated using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

grant select, insert, update, delete on public.nexa_gatherings to authenticated;
grant select, insert, delete on public.nexa_gathering_attendees to authenticated;
grant select, insert, update, delete on public.nexa_time_capsules to authenticated;
grant select, insert, update on public.nexa_wellbeing_plans to authenticated;
revoke all on public.nexa_gatherings, public.nexa_gathering_attendees, public.nexa_time_capsules, public.nexa_wellbeing_plans from anon;
