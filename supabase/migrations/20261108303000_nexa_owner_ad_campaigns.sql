create table if not exists public.nexa_owner_ads (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 140),
  body text not null check (char_length(body) between 1 and 3000),
  media_url text,
  media_type text not null default 'NONE' check (media_type in ('NONE','IMAGE','VIDEO')),
  click_url text,
  placement text not null check (placement in ('FEED_TOP','FEED_INLINE','STORIES_BETWEEN','PROFILE_FEED')),
  audience text not null default 'ALL' check (audience in ('ALL','NEW_USERS','VERIFIED','FOLLOWERS')),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  duration_seconds integer not null default 15 check (duration_seconds between 5 and 120),
  frequency_per_session integer not null default 2 check (frequency_per_session between 1 and 10),
  is_enabled boolean not null default false,
  impressions bigint not null default 0 check (impressions >= 0),
  clicks bigint not null default 0 check (clicks >= 0),
  created_by uuid not null references auth.users(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint nexa_owner_ads_date_check check (ends_at > starts_at),
  constraint nexa_owner_ads_media_check check ((media_type = 'NONE' and (media_url is null or media_url = '')) or (media_type in ('IMAGE','VIDEO') and media_url ~* '^https?://')),
  constraint nexa_owner_ads_click_url_check check (click_url is null or click_url = '' or click_url ~* '^https?://')
);
alter table public.nexa_owner_ads enable row level security;
revoke all on public.nexa_owner_ads from public, anon, authenticated;
create index if not exists nexa_owner_ads_active_idx on public.nexa_owner_ads(is_enabled, starts_at, ends_at);
create or replace function public.nexa_owner_list_ads()
returns jsonb language plpgsql security definer set search_path = public, auth as $$
begin
 if not public.nexa_is_owner() then raise exception 'owner_required' using errcode='42501'; end if;
 return coalesce((select jsonb_agg(to_jsonb(a) order by a.created_at desc) from public.nexa_owner_ads a), '[]'::jsonb);
end $$;
revoke all on function public.nexa_owner_list_ads() from public, anon;
grant execute on function public.nexa_owner_list_ads() to authenticated;
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
create or replace function public.nexa_owner_record_ad_event(p_ad_id uuid,p_event text)
returns boolean language plpgsql security definer set search_path=public,auth as $$
begin
 if auth.uid() is null or p_event not in ('IMPRESSION','CLICK') then return false; end if;
 update public.nexa_owner_ads set impressions=impressions+case when p_event='IMPRESSION' then 1 else 0 end,
  clicks=clicks+case when p_event='CLICK' then 1 else 0 end
 where id=p_ad_id and is_enabled=true and starts_at<=now() and ends_at>now();
 return found;
end $$;
revoke all on function public.nexa_owner_record_ad_event(uuid,text) from public,anon;
grant execute on function public.nexa_owner_record_ad_event(uuid,text) to authenticated;
