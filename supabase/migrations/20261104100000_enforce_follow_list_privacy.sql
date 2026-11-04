-- Enforce the existing "Show Followers List" / "Show Following List" settings
-- at the database boundary. This migration is additive and preserves all follow rows.
-- The privacy preferences are already written to public.user_privacy_settings.

create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated;

create or replace function private.nexa_can_view_follow_edge(
    p_follower_id uuid,
    p_following_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select
        -- Each participant can always see their own relationship row.
        (select auth.uid()) = p_follower_id
        or (select auth.uid()) = p_following_id
        or (
            coalesce(
                (select s.show_following_list
                 from public.user_privacy_settings s
                 where s.user_id = p_follower_id
                 limit 1),
                true
            )
            and
            coalesce(
                (select s.show_followers_list
                 from public.user_privacy_settings s
                 where s.user_id = p_following_id
                 limit 1),
                true
            )
        );
$$;

revoke all on function private.nexa_can_view_follow_edge(uuid, uuid) from public;
grant execute on function private.nexa_can_view_follow_edge(uuid, uuid) to authenticated;

alter table public.follows enable row level security;

drop policy if exists nexa_follows_select on public.follows;
create policy nexa_follows_select
on public.follows
for select
to authenticated
using (private.nexa_can_view_follow_edge(follower_id, following_id));
