create or replace function public.nexa_owner_list_audit(p_limit integer default 200)
returns jsonb language plpgsql security definer set search_path=public,auth as $$
declare v_result jsonb;
begin
 if not public.nexa_is_owner() then raise exception 'owner_required' using errcode='42501'; end if;
 select coalesce(jsonb_agg(q.item order by q.created_at desc),'[]'::jsonb) into v_result
 from (
  select x.item,x.created_at from (
   select jsonb_build_object('id',id,'actor_id',actor_id,'action',action,'entity_type',entity_type,'entity_id',entity_id,'details',details,'created_at',created_at) item,created_at from public.nexa_owner_audit_events
   union all
   select jsonb_build_object('id',id,'actor_id',actor_user_id,'action',action,'entity_type','USER_MODERATION','entity_id',target_user_id,'details',jsonb_build_object('reason',reason),'created_at',created_at),created_at from public.nexa_user_moderation_log
   union all
   select jsonb_build_object('id',id,'actor_id',actor_user_id,'action','DELETE_CONTENT','entity_type',content_type,'entity_id',content_id,'details',jsonb_build_object('reason',reason),'created_at',created_at),created_at from public.nexa_owner_content_deletion_log
   union all
   select jsonb_build_object('id',id,'actor_id',actor_id,'action','REPORT_STATUS','entity_type','REPORT','entity_id',report_id,'details',jsonb_build_object('from',previous_status,'to',new_status,'note',note),'created_at',created_at),created_at from public.nexa_owner_report_audit
  ) x order by x.created_at desc limit greatest(1,least(coalesce(p_limit,200),500))
 ) q;
 return v_result;
end $$;
revoke all on function public.nexa_owner_list_audit(integer) from public,anon;
grant execute on function public.nexa_owner_list_audit(integer) to authenticated;