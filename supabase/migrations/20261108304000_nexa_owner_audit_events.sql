create table if not exists public.nexa_owner_audit_events (
 id uuid primary key default gen_random_uuid(), actor_id uuid not null references auth.users(id) on delete restrict,
 action text not null, entity_type text not null, entity_id uuid, details jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now()
);
alter table public.nexa_owner_audit_events enable row level security;
revoke all on public.nexa_owner_audit_events from public,anon,authenticated;
create or replace function public.nexa_owner_save_ad(
 p_id uuid,p_title text,p_body text,p_media_url text,p_media_type text,p_click_url text,p_placement text,p_audience text,
 p_starts_at timestamptz,p_ends_at timestamptz,p_duration_seconds integer,p_frequency_per_session integer,p_enabled boolean
) returns uuid language plpgsql security definer set search_path=public,auth as $$
declare v_id uuid; v_action text;
begin
 if not public.nexa_is_owner() then raise exception 'owner_required' using errcode='42501'; end if;
 if nullif(btrim(p_title),'') is null or char_length(btrim(p_title))>140 then raise exception 'invalid_title'; end if;
 if nullif(btrim(p_body),'') is null or char_length(btrim(p_body))>3000 then raise exception 'invalid_body'; end if;
 if p_placement not in ('FEED_TOP','FEED_INLINE','STORIES_BETWEEN','PROFILE_FEED') then raise exception 'invalid_placement'; end if;
 if p_audience not in ('ALL','NEW_USERS','VERIFIED','FOLLOWERS') then raise exception 'invalid_audience'; end if;
 if p_media_type not in ('NONE','IMAGE','VIDEO') then raise exception 'invalid_media_type'; end if;
 if p_ends_at<=p_starts_at then raise exception 'invalid_date_range'; end if;
 if p_duration_seconds not between 5 and 120 or p_frequency_per_session not between 1 and 10 then raise exception 'invalid_delivery_settings'; end if;
 if p_media_type<>'NONE' and coalesce(p_media_url,'') !~* '^https?://' then raise exception 'invalid_media_url'; end if;
 if coalesce(p_click_url,'')<>'' and p_click_url !~* '^https?://' then raise exception 'invalid_click_url'; end if;
 if p_id is null then
  insert into public.nexa_owner_ads(title,body,media_url,media_type,click_url,placement,audience,starts_at,ends_at,duration_seconds,frequency_per_session,is_enabled,created_by)
  values(btrim(p_title),btrim(p_body),nullif(btrim(p_media_url),''),p_media_type,nullif(btrim(p_click_url),''),p_placement,p_audience,p_starts_at,p_ends_at,p_duration_seconds,p_frequency_per_session,coalesce(p_enabled,false),auth.uid())
  returning id into v_id; v_action:='CREATED';
 else
  update public.nexa_owner_ads set title=btrim(p_title),body=btrim(p_body),media_url=nullif(btrim(p_media_url),''),media_type=p_media_type,
   click_url=nullif(btrim(p_click_url),''),placement=p_placement,audience=p_audience,starts_at=p_starts_at,ends_at=p_ends_at,
   duration_seconds=p_duration_seconds,frequency_per_session=p_frequency_per_session,is_enabled=coalesce(p_enabled,false),updated_at=now()
  where id=p_id returning id into v_id;
  if v_id is null then raise exception 'ad_not_found'; end if; v_action:='UPDATED';
 end if;
 insert into public.nexa_owner_audit_events(actor_id,action,entity_type,entity_id,details)
 values(auth.uid(),v_action,'AD',v_id,jsonb_build_object('enabled',coalesce(p_enabled,false),'placement',p_placement,'audience',p_audience));
 return v_id;
end $$;
revoke all on function public.nexa_owner_save_ad(uuid,text,text,text,text,text,text,text,timestamptz,timestamptz,integer,integer,boolean) from public,anon;
grant execute on function public.nexa_owner_save_ad(uuid,text,text,text,text,text,text,text,timestamptz,timestamptz,integer,integer,boolean) to authenticated;
create or replace function public.nexa_owner_list_audit(p_limit integer default 200)
returns jsonb language plpgsql security definer set search_path=public,auth as $$
declare v_result jsonb;
begin
 if not public.nexa_is_owner() then raise exception 'owner_required' using errcode='42501'; end if;
 select coalesce(jsonb_agg(x.item order by x.created_at desc),'[]'::jsonb) into v_result from (
  select jsonb_build_object('id',id,'actor_id',actor_id,'action',action,'entity_type',entity_type,'entity_id',entity_id,'details',details,'created_at',created_at) item,created_at from public.nexa_owner_audit_events
  union all
  select jsonb_build_object('id',id,'actor_id',actor_user_id,'action',action,'entity_type','USER_MODERATION','entity_id',target_user_id,'details',jsonb_build_object('reason',reason),'created_at',created_at),created_at from public.nexa_user_moderation_log
  union all
  select jsonb_build_object('id',id,'actor_id',actor_user_id,'action','DELETE_CONTENT','entity_type',content_type,'entity_id',content_id,'details',jsonb_build_object('reason',reason),'created_at',created_at),created_at from public.nexa_owner_content_deletion_log
  union all
  select jsonb_build_object('id',id,'actor_id',actor_id,'action','REPORT_STATUS','entity_type','REPORT','entity_id',report_id,'details',jsonb_build_object('from',previous_status,'to',new_status,'note',note),'created_at',created_at),created_at from public.nexa_owner_report_audit
 ) x;
 return v_result;
end $$;
revoke all on function public.nexa_owner_list_audit(integer) from public,anon;
grant execute on function public.nexa_owner_list_audit(integer) to authenticated;
