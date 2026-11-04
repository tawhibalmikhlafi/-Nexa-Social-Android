-- Short-lived Instagram-style notes shown in the messages inbox.
create table if not exists public.user_notes (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  note text not null check (char_length(btrim(note)) between 1 and 60),
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '24 hours')
);
create index if not exists user_notes_expires_at_idx on public.user_notes(expires_at);
alter table public.user_notes enable row level security;
drop policy if exists user_notes_select_self_or_following on public.user_notes;
create policy user_notes_select_self_or_following on public.user_notes
for select to authenticated using (
  user_id = auth.uid() or exists (
    select 1 from public.follows f
    where f.follower_id = auth.uid() and f.following_id = user_notes.user_id
  )
);
drop policy if exists user_notes_insert_own on public.user_notes;
create policy user_notes_insert_own on public.user_notes
for insert to authenticated with check (user_id = auth.uid());
drop policy if exists user_notes_update_own on public.user_notes;
create policy user_notes_update_own on public.user_notes
for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists user_notes_delete_own on public.user_notes;
create policy if exists user_notes_delete_own on public.user_notes
for delete to authenticated using (user_id = auth.uid());
grant select, insert, update, delete on public.user_notes to authenticated;
notify pgrst, 'reload schema';
