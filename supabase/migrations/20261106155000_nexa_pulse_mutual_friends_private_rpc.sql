drop policy if exists "pulse challenge mutual followers can invite" on public.pulse_friend_challenges;
drop view if exists public.pulse_mutual_friends;

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create or replace function private.pulse_mutual_friends_for_current_user()
returns table (id uuid, username text, display_name text, avatar_url text)
language sql
security definer
set search_path = ''
as $$
  select p.id, p.username, p.display_name, p.avatar_url
  from public.follows outgoing
  join public.follows incoming
    on incoming.follower_id = outgoing.following_id
   and incoming.following_id = outgoing.follower_id
  join public.profiles p on p.id = outgoing.following_id
  where outgoing.follower_id = (select auth.uid())
    and incoming.following_id = (select auth.uid());
$$;
revoke all on function private.pulse_mutual_friends_for_current_user() from public, anon;
grant execute on function private.pulse_mutual_friends_for_current_user() to authenticated;

create or replace function public.pulse_get_mutual_friends()
returns table (id uuid, username text, display_name text, avatar_url text)
language sql
security invoker
set search_path = public
as $$
  select * from private.pulse_mutual_friends_for_current_user();
$$;
revoke all on function public.pulse_get_mutual_friends() from public, anon;
grant execute on function public.pulse_get_mutual_friends() to authenticated;

create policy "pulse challenge mutual followers can invite"
on public.pulse_friend_challenges for insert to authenticated
with check (
  challenger_id = (select auth.uid())
  and status = 'pending'
  and exists (
    select 1 from public.pulse_prompts p
    where p.id = prompt_id and p.is_published = true
      and now() >= p.starts_at and now() < p.ends_at
  )
  and exists (
    select 1 from public.pulse_get_mutual_friends() mf
    where mf.id = challenged_user_id
  )
);
