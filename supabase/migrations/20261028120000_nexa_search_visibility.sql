-- Nexa people search. The RPC is the only supported way to search profiles so that accounts
-- which disabled "show in search" (or are private) never leak through the REST endpoint.

create or replace function public.nexa_search_users(q text, lim integer default 20)
returns setof public.profiles
language sql
stable
security definer
set search_path = public
as $$
    select p.*
    from public.profiles p
    left join public.user_privacy_settings s on s.user_id = p.id
    where p.account_status = 'ACTIVE'
      and (
          p.id = auth.uid()
          or (
              coalesce(s.show_in_search, true)
              and (
                  coalesce(s.is_private_account, false) = false
                  or exists (
                      select 1 from public.follows f
                      where f.follower_id = auth.uid()
                        and f.following_id = p.id
                  )
              )
          )
      )
      and (
          p.username ilike '%' || coalesce(q, '') || '%'
          or p.display_name ilike '%' || coalesce(q, '') || '%'
      )
    order by
        case
            when p.username = coalesce(q, '') then 0
            when p.username ilike coalesce(q, '') || '%' then 1
            else 2
        end,
        p.username
    limit greatest(1, least(coalesce(lim, 20), 50));
$$;

revoke all on function public.nexa_search_users(text, integer) from public;
grant execute on function public.nexa_search_users(text, integer) to authenticated;

notify pgrst, 'reload schema';
