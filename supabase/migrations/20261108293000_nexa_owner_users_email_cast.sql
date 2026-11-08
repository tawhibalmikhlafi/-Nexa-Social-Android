-- auth.users.email is varchar(255), while the RPC contract returns text.
-- Cast explicitly so PostgREST can return the owner user list reliably.
create or replace function public.nexa_owner_list_users(
  p_search text default null,
  p_limit integer default 100,
  p_offset integer default 0
)
returns table(
  user_id uuid,
  email text,
  username text,
  display_name text,
  created_at timestamptz,
  account_status text,
  is_verified boolean
)
language plpgsql
stable
security definer
set search_path = public, auth
as $function$
begin
  if not public.nexa_is_owner() then
    raise exception 'OWNER_REQUIRED' using errcode='42501';
  end if;

  return query
  select
    u.id,
    u.email::text,
    p.username::text,
    p.display_name::text,
    u.created_at,
    coalesce(p.account_status, 'ACTIVE')::text,
    coalesce(p.is_verified, false)
  from auth.users u
  left join public.profiles p on p.id = u.id
  where p_search is null or p_search = ''
     or coalesce(p.username, '') ilike '%' || p_search || '%'
     or coalesce(p.display_name, '') ilike '%' || p_search || '%'
     or coalesce(u.email, '') ilike '%' || p_search || '%'
  order by u.created_at desc
  limit greatest(1, least(p_limit, 200))
  offset greatest(p_offset, 0);
end;
$function$;

revoke all on function public.nexa_owner_list_users(text,integer,integer) from public, anon;
grant execute on function public.nexa_owner_list_users(text,integer,integer) to authenticated;
notify pgrst, 'reload schema';
