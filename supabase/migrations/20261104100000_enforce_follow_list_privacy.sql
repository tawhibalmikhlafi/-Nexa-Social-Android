-- Enforce the existing "Show Followers List" / "Show Following List" settings
-- at the database boundary. This migration is additive and preserves all follow rows.
-- The privacy preferences are already written to public.user_privacy_settings by the app.

create or replace function public.nexa_can_view_follow_edge(
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
        auth.uid() = p_follower_id
        or auth.uid() = p_following_id
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

revoke all on function public.nexa_can_view_follow_edge(uuid, uuid) from public;
grant execute on function public.nexa_can_view_follow_edge(uuid, uuid) to authenticated;

alter table public.follows enable row level security;

drop policy if exists nexa_follows_select on public.follows;
create policy nexa_follows_select
on public.follows
for select
to authenticated
using (public.nexa_can_view_follow_edge(follower_id, following_id));

-- Keep the function's lookup privileges narrowly scoped. It returns only a boolean
-- for the current authenticated request and exposes no preference row or user data.
