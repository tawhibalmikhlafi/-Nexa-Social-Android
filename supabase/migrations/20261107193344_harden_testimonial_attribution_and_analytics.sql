-- Prevent clients from forging testimonial author identity or inserting fake analytics rows.
create or replace function public.nexa_guard_profile_verified_flags()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if tg_op='UPDATE' and tg_table_name='nexa_profile_testimonials' and old.is_verified then
    new.author_id := old.author_id;
    new.profile_id := old.profile_id;
    new.is_verified := true;
    return new;
  end if;
  if coalesce(auth.role(),'') <> 'service_role'
     and current_setting('nexa.verified_testimonial',true) is distinct from 'true' then
    if tg_op='INSERT' then new.is_verified:=false;
    else new.is_verified:=old.is_verified;
    end if;
  end if;
  return new;
end $$;

create or replace function public.nexa_record_profile_analytics(
  target_profile uuid,event_kind text,event_label text default null
)
returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if auth.uid() is null or target_profile is null or target_profile=auth.uid() then return false; end if;
  if event_kind not in ('PROFILE_VIEW','SKILL_CLICK','SOCIAL_LINK_CLICK','CV_DOWNLOAD','QR_SCAN','TESTIMONIAL_RECEIVED') then return false; end if;
  if not public.nexa_can_view_profile(target_profile) then return false; end if;
  if exists (
    select 1 from public.nexa_profile_analytics_events
    where profile_id=target_profile and viewer_id=auth.uid() and event_type=event_kind
      and (event_kind='PROFILE_VIEW' or coalesce(event_key,'')=coalesce(event_label,''))
      and created_at>now()-interval '15 minutes'
  ) then return true; end if;
  insert into public.nexa_profile_analytics_events(profile_id,viewer_id,event_type,event_key)
  values(target_profile,auth.uid(),event_kind,event_label);
  return true;
end $$;
revoke all on function public.nexa_record_profile_analytics(uuid,text,text) from public,anon;
grant execute on function public.nexa_record_profile_analytics(uuid,text,text) to authenticated;

revoke insert,update,delete on public.nexa_profile_analytics_events from authenticated;
grant select on public.nexa_profile_analytics_events to authenticated;
notify pgrst,'reload schema';
