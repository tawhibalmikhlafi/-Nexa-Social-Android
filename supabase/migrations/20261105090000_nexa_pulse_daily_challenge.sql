-- Nexa Pulse: daily community challenge MVP.
create table if not exists public.pulse_prompts (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text not null default '',
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  constraint pulse_prompt_dates check (ends_at > starts_at)
);

create table if not exists public.pulse_entries (
  id uuid primary key default gen_random_uuid(),
  prompt_id uuid not null references public.pulse_prompts(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now(),
  constraint pulse_entry_body_length check (char_length(trim(body)) between 1 and 1000),
  constraint pulse_one_entry_per_user unique (prompt_id, user_id)
);

create table if not exists public.pulse_reactions (
  entry_id uuid not null references public.pulse_entries(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (entry_id, user_id)
);

create index if not exists pulse_prompts_active_idx
  on public.pulse_prompts (starts_at, ends_at) where is_published = true;
create index if not exists pulse_entries_feed_idx
  on public.pulse_entries (prompt_id, created_at desc);
create index if not exists pulse_reactions_user_idx
  on public.pulse_reactions (user_id);

alter table public.pulse_prompts enable row level security;
alter table public.pulse_entries enable row level security;
alter table public.pulse_reactions enable row level security;

revoke all on public.pulse_prompts, public.pulse_entries, public.pulse_reactions from anon, authenticated;
grant select on public.pulse_prompts to authenticated;
grant select, insert, delete on public.pulse_entries to authenticated;
grant select, insert, delete on public.pulse_reactions to authenticated;

drop policy if exists "pulse published prompts readable" on public.pulse_prompts;
create policy "pulse published prompts readable" on public.pulse_prompts
for select to authenticated using (is_published = true);

drop policy if exists "pulse entries readable" on public.pulse_entries;
create policy "pulse entries readable" on public.pulse_entries
for select to authenticated using (
  exists (select 1 from public.pulse_prompts p
    where p.id = prompt_id and p.is_published = true)
);

drop policy if exists "pulse users create own active entry" on public.pulse_entries;
create policy "pulse users create own active entry" on public.pulse_entries
for insert to authenticated with check (
  user_id = (select auth.uid())
  and exists (select 1 from public.pulse_prompts p
    where p.id = prompt_id and p.is_published = true
      and now() >= p.starts_at and now() < p.ends_at)
);

drop policy if exists "pulse users delete own entry" on public.pulse_entries;
create policy "pulse users delete own entry" on public.pulse_entries
for delete to authenticated using (user_id = (select auth.uid()));

drop policy if exists "pulse reactions readable" on public.pulse_reactions;
create policy "pulse reactions readable" on public.pulse_reactions
for select to authenticated using (
  exists (select 1 from public.pulse_entries e
    join public.pulse_prompts p on p.id = e.prompt_id
    where e.id = entry_id and p.is_published = true)
);

drop policy if exists "pulse users react as themselves" on public.pulse_reactions;
create policy "pulse users react as themselves" on public.pulse_reactions
for insert to authenticated with check (
  user_id = (select auth.uid())
  and exists (select 1 from public.pulse_entries e
    join public.pulse_prompts p on p.id = e.prompt_id
    where e.id = entry_id and p.is_published = true)
);

drop policy if exists "pulse users remove own reaction" on public.pulse_reactions;
create policy "pulse users remove own reaction" on public.pulse_reactions
for delete to authenticated using (user_id = (select auth.uid()));
