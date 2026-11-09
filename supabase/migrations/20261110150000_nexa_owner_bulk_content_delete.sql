create or replace function public.nexa_owner_list_content(p_type text default 'ALL', p_limit integer default 100)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, auth
as $function$
declare result jsonb;
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  if upper(coalesce(p_type,'ALL')) not in ('ALL','POST','COMMENT','CLIP','CLIP_COMMENT','GROUP_POST') then raise exception 'INVALID_CONTENT_TYPE'; end if;
  with content_rows as (
    select p.id,'POST'::text as content_type,left(coalesce(p.text,''),500) as body,coalesce(nullif(pr.display_name,''),pr.username,'مستخدم') as author_name,p.created_at
      from public.posts p left join public.profiles pr on pr.id=p.author_id where upper(coalesce(p_type,'ALL')) in ('ALL','POST')
    union all
    select c.id,'COMMENT',left(c.text,500),coalesce(nullif(pr.display_name,''),pr.username,'مستخدم'),c.created_at
      from public.comments c left join public.profiles pr on pr.id=c.author_id where upper(coalesce(p_type,'ALL')) in ('ALL','COMMENT')
    union all
    select cl.id,'CLIP',left(cl.caption,500),coalesce(nullif(pr.display_name,''),pr.username,'مستخدم'),cl.created_at
      from public.clips cl left join public.profiles pr on pr.id=cl.author_id where upper(coalesce(p_type,'ALL')) in ('ALL','CLIP')
    union all
    select cc.id,'CLIP_COMMENT',left(cc.content,500),coalesce(nullif(pr.display_name,''),pr.username,'مستخدم'),cc.created_at
      from public.clip_comments cc left join public.profiles pr on pr.id=cc.author_id where upper(coalesce(p_type,'ALL')) in ('ALL','CLIP_COMMENT') and not cc.is_deleted
    union all
    select gp.id,'GROUP_POST',left(gp.content,500),coalesce(nullif(pr.display_name,''),pr.username,'مستخدم'),gp.created_at
      from public.group_posts gp left join public.profiles pr on pr.id=gp.author_id where upper(coalesce(p_type,'ALL')) in ('ALL','GROUP_POST') and not gp.is_deleted
  )
  select coalesce(jsonb_agg(jsonb_build_object('id',id,'content_type',content_type,'body',body,'author_name',author_name,'created_at',created_at) order by created_at desc),'[]'::jsonb)
    into result from (select * from content_rows order by created_at desc limit greatest(1,least(coalesce(p_limit,100),300))) q;
  return result;
end;
$function$;

create or replace function public.nexa_owner_delete_content_batch(p_items jsonb,p_reason text default 'حذف جماعي من لوحة مالك Nexa')
returns jsonb language plpgsql security definer set search_path = public, auth
as $function$
declare item jsonb; deleted jsonb := '[]'::jsonb; media jsonb := '[]'::jsonb; result jsonb; item_type text; item_id uuid; total integer := 0;
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  if jsonb_typeof(p_items) <> 'array' then raise exception 'INVALID_ITEMS'; end if;
  total := jsonb_array_length(p_items);
  if total < 1 or total > 200 then raise exception 'BATCH_SIZE_MUST_BE_1_TO_200'; end if;
  for item in select value from jsonb_array_elements(p_items)
  loop
    item_type := upper(coalesce(item->>'type',''));
    item_id := nullif(item->>'id','')::uuid;
    if item_id is null or item_type not in ('POST','COMMENT','CLIP','CLIP_COMMENT','GROUP_POST') then raise exception 'INVALID_DELETE_TARGET'; end if;
    result := public.nexa_owner_delete_content(item_type,item_id,left(trim(coalesce(p_reason,'حذف جماعي من لوحة مالك Nexa')),1000));
    deleted := deleted || jsonb_build_array(jsonb_build_object('id',item_id,'type',item_type));
    media := media || coalesce(result->'media_paths','[]'::jsonb);
  end loop;
  return jsonb_build_object('success',true,'deleted_count',jsonb_array_length(deleted),'deleted',deleted,'media_paths',media);
end;
$function$;
revoke all on function public.nexa_owner_delete_content_batch(jsonb,text) from public;
revoke all on function public.nexa_owner_delete_content_batch(jsonb,text) from anon;
grant execute on function public.nexa_owner_delete_content_batch(jsonb,text) to authenticated;
notify pgrst,'reload schema';
