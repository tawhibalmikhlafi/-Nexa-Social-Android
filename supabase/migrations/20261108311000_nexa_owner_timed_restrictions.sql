alter table public.profiles add column if not exists restriction_expires_at timestamptz;
alter table public.nexa_user_moderation_log add column if not exists expires_at timestamptz;
create or replace function public.nexa_owner_moderate_user_timed(
 p_user_id uuid,p_action text,p_reason text default 'إجراء إداري من لوحة مالك Nexa',p_duration_hours integer default null
) returns boolean language plpgsql security definer set search_path=public,auth as $$
declare next_status text; expiry timestamptz; normalized text:=upper(coalesce(p_action,''));
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 if p_user_id is null or p_user_id=auth.uid() then raise exception 'CANNOT_MODERATE_SELF'; end if;
 if exists(select 1 from public.nexa_admin_roles where user_id=p_user_id and role='OWNER') then raise exception 'CANNOT_MODERATE_OWNER'; end if;
 next_status:=case normalized when 'BAN' then 'BANNED' when 'RESTRICT' then 'SUSPENDED' when 'RESTORE' then 'ACTIVE' else null end;
 if next_status is null then raise exception 'INVALID_MODERATION_ACTION'; end if;
 if normalized='RESTRICT' and p_duration_hours is not null and p_duration_hours not between 1 and 8760 then raise exception 'INVALID_RESTRICTION_DURATION'; end if;
 expiry:=case when normalized='RESTRICT' and p_duration_hours is not null then now()+make_interval(hours=>p_duration_hours) else null end;
 update public.profiles set account_status=next_status,restriction_expires_at=expiry,updated_at=now() where id=p_user_id;
 if not found then raise exception 'PROFILE_NOT_FOUND'; end if;
 insert into public.nexa_user_moderation_log(target_user_id,actor_user_id,action,reason,expires_at)
 values(p_user_id,auth.uid(),normalized,left(trim(coalesce(p_reason,'')),1000),expiry);
 insert into public.nexa_owner_audit_events(actor_id,action,entity_type,entity_id,details)
 values(auth.uid(),normalized,'USER',p_user_id,jsonb_build_object('reason',left(trim(coalesce(p_reason,'')),1000),'expires_at',expiry));
 return true;
end $$;
revoke all on function public.nexa_owner_moderate_user_timed(uuid,text,text,integer) from public,anon;
grant execute on function public.nexa_owner_moderate_user_timed(uuid,text,text,integer) to authenticated;
do $$ declare existing_job bigint; begin
 select jobid into existing_job from cron.job where jobname='nexa-expire-owner-restrictions' limit 1;
 if existing_job is not null then perform cron.unschedule(existing_job); end if;
 perform cron.schedule('nexa-expire-owner-restrictions','*/5 * * * *',
  $job$update public.profiles set account_status='ACTIVE',restriction_expires_at=null,updated_at=now()
       where account_status='SUSPENDED' and restriction_expires_at is not null and restriction_expires_at<=now()$job$);
end $$;