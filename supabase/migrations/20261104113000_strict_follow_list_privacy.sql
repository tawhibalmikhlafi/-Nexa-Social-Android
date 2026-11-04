-- Strict follower/following privacy. Lists are read only through guarded RPCs.
create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated;

create or replace function private.nexa_can_view_follow_list(
    p_target_user_id uuid,
    p_list_type text
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select
        (select auth.uid()) is not null
        and (
            (select auth.uid()) = p_target_user_id
            or case p_list_type
                when 'followers' then coalesce(
                    (select s.show_followers_list
                     from public.user_privacy_settings s
                     where s.user_id = p_target_user_id
                     limit 1), true)
                when 'following' then coalesce(
                    (select s.show_following_list
                     from public.user_privacy_settings s
                     where s.user_id = p_target_user_id
                     limit 1), true)
                else false
            end
        );
$$;

create or replace function public.nexa_get_follow_list(
    p_target_user_id uuid,
    p_list_type text
)
returns table(user_id uuid)
language sql
stable
security definer
set search_path = ''
as $$
    select case when p_list_type = 'followers' then f.follower_id else f.following_id end
    from public.follows f
    where p_list_type in ('followers', 'following')
      and ((p_list_type = 'followers' and f.following_id = p_target_user_id)
        or (p_list_type = 'following' and f.follower_id = p_target_user_id))
      and private.nexa_can_view_follow_list(p_target_user_id, p_list_type);
$$;

create or replace function public.nexa_is_following(
    p_follower_id uuid,
    p_following_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select (select auth.uid()) = p_follower_id
       and exists (
           select 1 from public.follows f
           where f.follower_id = p_follower_id
             and f.following_id = p_following_id
       );
$$;

revoke all on function private.nexa_can_view_follow_list(uuid, text) from public, anon;
grant execute on function private.nexa_can_view_follow_list(uuid, text) to authenticated;
revoke all on function public.nexa_get_follow_list(uuid, text) from public, anon;
grant execute on function public.nexa_get_follow_list(uuid, text) to authenticated;
revoke all on function public.nexa_is_following(uuid, uuid) from public, anon;
grant execute on function public.nexa_is_following(uuid, uuid) to authenticated;

alter table public.follows enable row level security;
drop policy if exists nexa_follows_select on public.follows;
drop policy if exists nexa_follows_select_own_following on public.follows;
create policy nexa_follows_select_own_following
on public.follows for select to authenticated
using ((select auth.uid()) = follower_id);
