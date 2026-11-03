-- Nexa blocks: a per-account block list stored on the server.
-- Mirrors com.nexa.social.domain.model.BlockedUser and SupabaseSafetyDataSource.

create table if not exists public.blocks (
    blocker_id uuid not null references public.profiles (id) on delete cascade,
    blocked_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    constraint blocks_no_self check (blocker_id <> blocked_id)
);

create index if not exists blocks_blocked_idx on public.blocks (blocked_id);
create index if not exists blocks_blocker_idx on public.blocks (blocker_id);

alter table public.blocks enable row level security;

drop policy if exists nexa_blocks_select on public.blocks;
create policy nexa_blocks_select on public.blocks
for select to authenticated using (blocker_id = auth.uid());

drop policy if exists nexa_blocks_insert on public.blocks;
create policy nexa_blocks_insert on public.blocks
for insert to authenticated with check (blocker_id = auth.uid());

drop policy if exists nexa_blocks_delete on public.blocks;
create policy nexa_blocks_delete on public.blocks
for delete to authenticated using (blocker_id = auth.uid());

grant select, insert, delete on public.blocks to authenticated;
