-- Owner-only content moderation and auditable hard deletion.
create table if not exists public.nexa_owner_content_deletion_log (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid not null references auth.users(id),
  content_type text not null check (content_type in ('POST','COMMENT','CLIP','CLIP_COMMENT')),
  content_id uuid not null,
  reason text not null,
  content_snapshot jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
alter table public.nexa_owner_content_deletion_log enable row level security;
revoke all on public.nexa_owner_content_deletion_log from public, anon, authenticated;

create or replace function public.nexa_owner_list_content(p_type text default 'ALL', p_limit integer default 100)
returns jsonb
language plpgsql stable security definer set search_path=public,auth
as $$
declare result jsonb;
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  if upper(coalesce(p_type,'ALL')) not in ('ALL','POST','COMMENT','CLIP','CLIP_COMMENT') then
    raise exception 'INVALID_CONTENT_TYPE';
  end if;
  with content_rows as (
    select p.id,'POST'::text as content_type,
           left(coalesce(p.text,''),500) as body,
           coalesce(nullif(pr.display_name,''),pr.username,'مستخدم') as author_name,
           p.created_at
      from public.posts p left join public.profiles pr on pr.id=p.author_id
     where upper(coalesce(p_type,'ALL')) in ('ALL','POST')
    union all
    select c.id,'COMMENT',left(c.text,500),
           coalesce(nullif(pr.display_name,''),pr.username,'مستخدم'),c.created_at
      from public.comments c left join public.profiles pr on pr.id=c.author_id
     where upper(coalesce(p_type,'ALL')) in ('ALL','COMMENT')
    union all
    select cl.id,'CLIP',left(cl.caption,500),
           coalesce(nullif(pr.display_name,''),pr.username,'مستخدم'),cl.created_at
      from public.clips cl left join public.profiles pr on pr.id=cl.author_id
     where upper(coalesce(p_type,'ALL')) in ('ALL','CLIP')
    union all
    select cc.id,'CLIP_COMMENT',left(cc.content,500),
           coalesce(nullif(pr.display_name,''),pr.username,'مستخدم'),cc.created_at
      from public.clip_comments cc left join public.profiles pr on pr.id=cc.author_id
     where upper(coalesce(p_type,'ALL')) in ('ALL','CLIP_COMMENT') and not cc.is_deleted
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',id,'content_type',content_type,'body',body,'author_name',author_name,'created_at',created_at
  ) order by created_at desc),'[]'::jsonb)
  into result
  from (select * from content_rows order by created_at desc limit greatest(1,least(coalesce(p_limit,100),300))) q;
  return result;
end;
$$;
revoke all on function public.nexa_owner_list_content(text,integer) from public,anon;
grant execute on function public.nexa_owner_list_content(text,integer) to authenticated;

create or replace function public.nexa_owner_delete_content(
  p_type text,
  p_content_id uuid,
  p_reason text default 'حذف إداري من لوحة مالك Nexa'
)
returns jsonb
language plpgsql security definer set search_path=public,auth
as $$
declare
  normalized_type text := upper(coalesce(p_type,''));
  snapshot jsonb;
  media jsonb := '[]'::jsonb;
  actor uuid := auth.uid();
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  if actor is null or p_content_id is null then raise exception 'INVALID_DELETE_TARGET'; end if;
  if normalized_type not in ('POST','COMMENT','CLIP','CLIP_COMMENT') then raise exception 'INVALID_CONTENT_TYPE'; end if;

  if normalized_type='POST' then
    select to_jsonb(p) into snapshot from public.posts p where p.id=p_content_id for update;
    if snapshot is null then raise exception 'CONTENT_NOT_FOUND'; end if;
    media := media || case when snapshot->>'image_uri' is not null then
      jsonb_build_array(jsonb_build_object('bucket','post-media','path',
        case when snapshot->>'image_uri' like '%/storage/v1/object/%'
          then regexp_replace(snapshot->>'image_uri','^.*?/storage/v1/object/(public|sign)/post-media/','')
          else snapshot->>'image_uri' end))
      else '[]'::jsonb end;
    if jsonb_typeof(snapshot->'image_uris')='array' then
      media := media || (select coalesce(jsonb_agg(jsonb_build_object('bucket','post-media','path',
        case when v like '%/storage/v1/object/%'
          then regexp_replace(v,'^.*?/storage/v1/object/(public|sign)/post-media/','')
          else v end)),'[]'::jsonb)
        from jsonb_array_elements_text(snapshot->'image_uris') as x(v) where v is not null and v <> '');
    end if;
    delete from public.posts where id=p_content_id;
  elsif normalized_type='COMMENT' then
    select to_jsonb(c) into snapshot from public.comments c where c.id=p_content_id for update;
    if snapshot is null then raise exception 'CONTENT_NOT_FOUND'; end if;
    if snapshot->>'image_uri' is not null then
      media := jsonb_build_array(jsonb_build_object('bucket','post-media','path',
        case when snapshot->>'image_uri' like '%/storage/v1/object/%'
          then regexp_replace(snapshot->>'image_uri','^.*?/storage/v1/object/(public|sign)/post-media/','')
          else snapshot->>'image_uri' end));
    end if;
    delete from public.comments where id=p_content_id;
  elsif normalized_type='CLIP' then
    select to_jsonb(c) into snapshot from public.clips c where c.id=p_content_id for update;
    if snapshot is null then raise exception 'CONTENT_NOT_FOUND'; end if;
    media := jsonb_build_array(jsonb_build_object('bucket','clips-media','path',snapshot->>'video_path'));
    if snapshot->>'cover_url' is not null then
      media := media || jsonb_build_array(jsonb_build_object('bucket','clip-covers','path',
        case when snapshot->>'cover_url' like '%/storage/v1/object/%'
          then regexp_replace(snapshot->>'cover_url','^.*?/storage/v1/object/(public|sign)/clip-covers/','')
          else snapshot->>'cover_url' end));
    end if;
    delete from public.clips where id=p_content_id;
  else
    select to_jsonb(c) into snapshot from public.clip_comments c where c.id=p_content_id for update;
    if snapshot is null then raise exception 'CONTENT_NOT_FOUND'; end if;
    delete from public.clip_comments where id=p_content_id;
  end if;

  insert into public.nexa_owner_content_deletion_log(actor_user_id,content_type,content_id,reason,content_snapshot)
  values(actor,normalized_type,p_content_id,left(trim(coalesce(p_reason,'حذف إداري')),1000),snapshot);

  return jsonb_build_object('success',true,'content_type',normalized_type,'content_id',p_content_id,'media_paths',media);
end;
$$;
revoke all on function public.nexa_owner_delete_content(text,uuid,text) from public,anon;
grant execute on function public.nexa_owner_delete_content(text,uuid,text) to authenticated;

drop policy if exists nexa_owner_delete_content_media on storage.objects;
create policy nexa_owner_delete_content_media on storage.objects
for delete to authenticated
using (
  bucket_id in ('post-media','clips-media','clip-covers')
  and public.nexa_is_owner()
);

notify pgrst,'reload schema';
