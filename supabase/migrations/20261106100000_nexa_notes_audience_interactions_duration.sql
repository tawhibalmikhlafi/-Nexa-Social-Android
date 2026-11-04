alter table public.user_notes
  add column if not exists visibility text not null default 'FOLLOWING',
  add column if not exists duration_hours integer not null default 24;
alter table public.user_notes
  drop constraint if exists user_notes_visibility_valid;
alter table public.user_notes
  add constraint user_notes_visibility_valid
  check (visibility in ('FOLLOWING','FOLLOWERS','EVERYONE'));
alter table public.user_notes
  drop constraint if exists user_notes_duration_valid;
alter table public.user_notes
  add constraint user_notes_duration_valid
  check (duration_hours in (1,6,12,24,48));

create or replace function public.nexa_can_view_note(note_owner uuid)
returns boolean
language sql stable security definer
set search_path=public,pg_temp
as $$
 select auth.uid() = note_owner
   or exists (
     select 1 from public.user_notes n
     where n.user_id=note_owner and n.expires_at > now()
       and (
         n.visibility='EVERYONE'
         or (n.visibility='FOLLOWING' and exists (
           select 1 from public.follows f
           where f.follower_id=note_owner and f.following_id=auth.uid()
         ))
         or (n.visibility='FOLLOWERS' and exists (
           select 1 from public.follows f
           where f.follower_id=auth.uid() and f.following_id=note_owner
         ))
       )
   );
$$;
revoke all on function public.nexa_can_view_note(uuid) from public;
grant execute on function public.nexa_can_view_note(uuid) to authenticated;

drop policy if exists user_notes_select_self_or_following on public.user_notes;
create policy user_notes_select_audience on public.user_notes
for select to authenticated using (
 user_id=auth.uid() or public.nexa_can_view_note(user_id)
);

create table if not exists public.user_note_interactions (
 id uuid primary key default gen_random_uuid(),
 note_user_id uuid not null references public.user_notes(user_id) on delete cascade,
 actor_id uuid not null references public.profiles(id) on delete cascade,
 kind text not null check(kind in ('REACTION','REPLY')),
 content text not null check(char_length(btrim(content)) between 1 and 200),
 created_at timestamptz not null default now()
);
create index if not exists user_note_interactions_note_idx
 on public.user_note_interactions(note_user_id,created_at desc);
alter table public.user_note_interactions enable row level security;
drop policy if exists user_note_interactions_select on public.user_note_interactions;
create policy user_note_interactions_select on public.user_note_interactions
for select to authenticated using (public.nexa_can_view_note(note_user_id));
drop policy if exists user_note_interactions_insert on public.user_note_interactions;
create policy user_note_interactions_insert on public.user_note_interactions
for insert to authenticated with check (
 actor_id=auth.uid()
 and note_user_id <> auth.uid()
 and public.nexa_can_view_note(note_user_id)
);
grant select,insert on public.user_note_interactions to authenticated;

notify pgrst,'reload schema';
