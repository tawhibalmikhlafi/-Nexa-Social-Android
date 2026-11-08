create or replace function public.nexa_owner_create_announcement(p_title text,p_body text)
returns uuid language plpgsql security definer set search_path=public,auth as $$
declare new_id uuid;
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 if char_length(trim(coalesce(p_title,''))) not between 1 and 140 or char_length(trim(coalesce(p_body,''))) not between 1 and 5000 then raise exception 'INVALID_ANNOUNCEMENT'; end if;
 insert into public.nexa_announcements(title,body,created_by) values(trim(p_title),trim(p_body),auth.uid()) returning id into new_id;
 insert into public.nexa_owner_audit_events(actor_id,action,entity_type,entity_id,details) values(auth.uid(),'CREATED','ANNOUNCEMENT',new_id,jsonb_build_object('title',left(trim(p_title),140)));
 return new_id;
end $$;
revoke all on function public.nexa_owner_create_announcement(text,text) from public,anon;
grant execute on function public.nexa_owner_create_announcement(text,text) to authenticated;

create or replace function public.nexa_owner_create_official_post(p_text text)
returns uuid language plpgsql security definer set search_path=public,auth as $$
declare new_post_id uuid;
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 if char_length(trim(coalesce(p_text,''))) not between 1 and 5000 then raise exception 'INVALID_POST_TEXT'; end if;
 insert into public.posts(author_id,text,audience) values(auth.uid(),trim(p_text),'PUBLIC_LOCAL') returning id into new_post_id;
 insert into public.nexa_owner_audit_events(actor_id,action,entity_type,entity_id,details) values(auth.uid(),'PUBLISHED','OFFICIAL_POST',new_post_id,jsonb_build_object('preview',left(trim(p_text),160)));
 return new_post_id;
end $$;
revoke all on function public.nexa_owner_create_official_post(text) from public,anon;
grant execute on function public.nexa_owner_create_official_post(text) to authenticated;

create or replace function public.nexa_owner_reply_support(p_ticket_id uuid,p_reply text,p_status text default 'RESOLVED')
returns boolean language plpgsql security definer set search_path=public,auth as $$
declare ticket_user uuid;
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 if p_status not in ('IN_PROGRESS','RESOLVED','CLOSED') then raise exception 'INVALID_STATUS'; end if;
 update public.nexa_support_tickets set admin_reply=trim(p_reply),status=p_status,updated_at=now(),replied_by=auth.uid()
 where id=p_ticket_id returning user_id into ticket_user;
 if not found then return false; end if;
 insert into public.nexa_owner_audit_events(actor_id,action,entity_type,entity_id,details)
 values(auth.uid(),'REPLIED','SUPPORT_TICKET',p_ticket_id,jsonb_build_object('status',p_status,'user_id',ticket_user));
 return true;
end $$;
revoke all on function public.nexa_owner_reply_support(uuid,text,text) from public,anon;
grant execute on function public.nexa_owner_reply_support(uuid,text,text) to authenticated;

create or replace function public.nexa_owner_set_setting(p_key text,p_value jsonb)
returns boolean language plpgsql security definer set search_path=public,auth as $$
declare numeric_value integer;
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 if p_key in ('maintenance_mode','new_registrations_enabled','official_badge_enabled','ads_enabled','posts_enabled','comments_enabled','stories_enabled','reels_enabled','verified_only_posting') then
  if jsonb_typeof(p_value)<>'boolean' then raise exception 'INVALID_SETTING_VALUE'; end if;
 elsif p_key='ads_placement' then
  if jsonb_typeof(p_value)<>'string' or p_value#>>'{}' not in ('FEED_TOP','FEED_INLINE','STORIES_BETWEEN','PROFILE_FEED') then raise exception 'INVALID_AD_PLACEMENT'; end if;
 elsif p_key in ('ads_duration_seconds','ads_frequency_per_session') then
  if jsonb_typeof(p_value)<>'number' then raise exception 'INVALID_SETTING_VALUE'; end if;
  numeric_value:=(p_value#>>'{}')::integer;
  if p_key='ads_duration_seconds' and numeric_value not between 5 and 120 then raise exception 'ADS_DURATION_OUT_OF_RANGE'; end if;
  if p_key='ads_frequency_per_session' and numeric_value not between 1 and 10 then raise exception 'ADS_FREQUENCY_OUT_OF_RANGE'; end if;
 else raise exception 'INVALID_SETTING'; end if;
 insert into public.nexa_app_settings(setting_key,setting_value,updated_at,updated_by)
 values(p_key,p_value,now(),auth.uid()) on conflict(setting_key) do update
 set setting_value=excluded.setting_value,updated_at=now(),updated_by=auth.uid();
 insert into public.nexa_owner_audit_events(actor_id,action,entity_type,details)
 values(auth.uid(),'UPDATED_SETTING','PLATFORM_SETTING',jsonb_build_object('key',p_key,'value',p_value));
 return true;
end $$;
revoke all on function public.nexa_owner_set_setting(text,jsonb) from public,anon;
grant execute on function public.nexa_owner_set_setting(text,jsonb) to authenticated;