create or replace function public.nexa_owner_list_reports(p_type text default 'ALL', p_limit integer default 150)
returns jsonb language plpgsql stable security definer set search_path=public,auth
as $$
declare result jsonb;
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  if upper(coalesce(p_type,'ALL')) not in ('ALL','POST','COMMENT','CLIP','USER','COMMUNITY','GROUP_POST') then
    raise exception 'INVALID_REPORT_TYPE';
  end if;
  select coalesce(jsonb_agg(to_jsonb(x) order by x.created_at desc),'[]'::jsonb) into result
  from (
    select r.id, upper(r.target_type) as target_type, r.target_id, r.reason, r.note,
      coalesce(r.status,'pending') as status, r.created_at, r.reporter_id,
      coalesce(nullif(reporter.display_name,''),nullif(reporter.username,''),split_part(coalesce(ru.email,''),'@',1),'مستخدم') as reporter_name,
      coalesce(reporter.username,'') as reporter_username, coalesce(ru.email,'') as reporter_email,
      case when lower(r.target_type)='user' then r.target_id
           when lower(r.target_type) in ('post','group_post') then (select p.author_id from public.posts p where p.id=r.target_id)
           when lower(r.target_type)='comment' then (select c.author_id from public.comments c where c.id=r.target_id)
           when lower(r.target_type)='clip' then (select cl.author_id from public.clips cl where cl.id=r.target_id)
           else null end as target_author_id,
      coalesce(nullif(target_profile.display_name,''),nullif(target_profile.username,''),'') as target_author_name,
      coalesce(target_profile.username,'') as target_author_username,
      coalesce(r.target_preview,
        case lower(r.target_type)
          when 'post' then (select left(coalesce(p.text,''),500) from public.posts p where p.id=r.target_id)
          when 'group_post' then (select left(coalesce(p.text,''),500) from public.posts p where p.id=r.target_id)
          when 'comment' then (select left(coalesce(c.text,''),500) from public.comments c where c.id=r.target_id)
          when 'clip' then (select left(coalesce(cl.caption,''),500) from public.clips cl where cl.id=r.target_id)
          when 'user' then (select coalesce(nullif(pr.display_name,''),pr.username,'') from public.profiles pr where pr.id=r.target_id)
          else r.target_preview end,'') as target_preview
    from public.content_reports r
    left join auth.users ru on ru.id=r.reporter_id
    left join public.profiles reporter on reporter.id=r.reporter_id
    left join public.profiles target_profile on target_profile.id =
      case when lower(r.target_type)='user' then r.target_id
           when lower(r.target_type) in ('post','group_post') then (select p.author_id from public.posts p where p.id=r.target_id)
           when lower(r.target_type)='comment' then (select c.author_id from public.comments c where c.id=r.target_id)
           when lower(r.target_type)='clip' then (select cl.author_id from public.clips cl where cl.id=r.target_id)
           else null end
    where upper(coalesce(p_type,'ALL'))='ALL' or upper(r.target_type)=upper(p_type)
    order by r.created_at desc limit greatest(1,least(coalesce(p_limit,150),300))
  ) x;
  return result;
end $$;
revoke all on function public.nexa_owner_list_reports(text,integer) from public,anon;
grant execute on function public.nexa_owner_list_reports(text,integer) to authenticated;
notify pgrst,'reload schema';