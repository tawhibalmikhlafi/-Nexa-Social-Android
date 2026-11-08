create or replace function public.nexa_owner_list_users_full(p_search text default null,p_limit integer default 100,p_offset integer default 0)
returns jsonb language plpgsql stable security definer set search_path=public,auth as $$
declare result jsonb;
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 select coalesce(jsonb_agg(to_jsonb(x) order by x.created_at desc),'[]'::jsonb) into result
 from (
  select u.id as user_id,u.email::text as email,p.username::text as username,p.display_name::text as display_name,
   p.avatar_url::text as avatar_url,u.created_at,u.last_sign_in_at,
   coalesce(p.account_status,'ACTIVE')::text as account_status,coalesce(p.is_verified,false) as is_verified,
   (select count(*) from public.posts po where po.author_id=u.id) as posts_count,
   (select count(*) from public.follows f where f.following_id=u.id) as followers_count,
   (select count(*) from public.follows f where f.follower_id=u.id) as following_count
  from auth.users u left join public.profiles p on p.id=u.id
  where p_search is null or p_search='' or coalesce(p.username,'') ilike '%'||p_search||'%' or coalesce(p.display_name,'') ilike '%'||p_search||'%' or coalesce(u.email,'') ilike '%'||p_search||'%'
  order by u.created_at desc limit greatest(1,least(p_limit,200)) offset greatest(p_offset,0)
 ) x;
 return result;
end $$;
revoke all on function public.nexa_owner_list_users_full(text,integer,integer) from public,anon;
grant execute on function public.nexa_owner_list_users_full(text,integer,integer) to authenticated;