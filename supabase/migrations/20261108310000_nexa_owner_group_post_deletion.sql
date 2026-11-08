create or replace function public.nexa_owner_delete_content(p_type text,p_content_id uuid,p_reason text default 'حذف إداري من لوحة مالك Nexa')
returns jsonb language plpgsql security definer set search_path=public,auth as $$
declare normalized_type text:=upper(coalesce(p_type,'')); snapshot jsonb; media jsonb:='[]'::jsonb; actor uuid:=auth.uid();
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 if actor is null or p_content_id is null then raise exception 'INVALID_DELETE_TARGET'; end if;
 if normalized_type not in ('POST','COMMENT','CLIP','CLIP_COMMENT','GROUP_POST') then raise exception 'INVALID_CONTENT_TYPE'; end if;
 if normalized_type='POST' then
  select to_jsonb(p) into snapshot from public.posts p where p.id=p_content_id for update;
  if snapshot is null then raise exception 'CONTENT_NOT_FOUND'; end if;
  if snapshot->>'image_uri' is not null then media:=media||jsonb_build_array(jsonb_build_object('bucket','post-media','path',regexp_replace(snapshot->>'image_uri','^.*?/storage/v1/object/(public|sign)/post-media/',''))); end if;
  if jsonb_typeof(snapshot->'image_uris')='array' then media:=media||(select coalesce(jsonb_agg(jsonb_build_object('bucket','post-media','path',regexp_replace(v,'^.*?/storage/v1/object/(public|sign)/post-media/',''))),'[]'::jsonb) from jsonb_array_elements_text(snapshot->'image_uris') x(v) where v is not null and v<>''); end if;
  delete from public.posts where id=p_content_id;
 elsif normalized_type='GROUP_POST' then
  select to_jsonb(gp) into snapshot from public.group_posts gp where gp.id=p_content_id for update;
  if snapshot is null then raise exception 'CONTENT_NOT_FOUND'; end if;
  if nullif(snapshot->>'media_url','') is not null then media:=jsonb_build_array(jsonb_build_object('bucket','post-media','path',regexp_replace(snapshot->>'media_url','^.*?/storage/v1/object/(public|sign)/post-media/',''))); end if;
  delete from public.group_posts where id=p_content_id;
 elsif normalized_type='COMMENT' then
  select to_jsonb(c) into snapshot from public.comments c where c.id=p_content_id for update;
  if snapshot is null then raise exception 'CONTENT_NOT_FOUND'; end if;
  if snapshot->>'image_uri' is not null then media:=jsonb_build_array(jsonb_build_object('bucket','post-media','path',regexp_replace(snapshot->>'image_uri','^.*?/storage/v1/object/(public|sign)/post-media/',''))); end if;
  delete from public.comments where id=p_content_id;
 elsif normalized_type='CLIP' then
  select to_jsonb(c) into snapshot from public.clips c where c.id=p_content_id for update;
  if snapshot is null then raise exception 'CONTENT_NOT_FOUND'; end if;
  media:=jsonb_build_array(jsonb_build_object('bucket','clips-media','path',snapshot->>'video_path'));
  if snapshot->>'cover_url' is not null then media:=media||jsonb_build_array(jsonb_build_object('bucket','clip-covers','path',regexp_replace(snapshot->>'cover_url','^.*?/storage/v1/object/(public|sign)/clip-covers/',''))); end if;
  delete from public.clips where id=p_content_id;
 else
  select to_jsonb(c) into snapshot from public.clip_comments c where c.id=p_content_id for update;
  if snapshot is null then raise exception 'CONTENT_NOT_FOUND'; end if;
  delete from public.clip_comments where id=p_content_id;
 end if;
 insert into public.nexa_owner_content_deletion_log(actor_user_id,content_type,content_id,reason,content_snapshot)
 values(actor,normalized_type,p_content_id,left(trim(coalesce(p_reason,'حذف إداري')),1000),snapshot);
 insert into public.nexa_owner_audit_events(actor_id,action,entity_type,entity_id,details)
 values(actor,'DELETED',normalized_type,p_content_id,jsonb_build_object('reason',left(trim(coalesce(p_reason,'')),1000)));
 return jsonb_build_object('success',true,'content_type',normalized_type,'content_id',p_content_id,'media_paths',media);
end $$;
revoke all on function public.nexa_owner_delete_content(text,uuid,text) from public,anon;
grant execute on function public.nexa_owner_delete_content(text,uuid,text) to authenticated;