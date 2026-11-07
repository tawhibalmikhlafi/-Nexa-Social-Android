-- Expire profile statuses automatically and notify only when the owner opted in.
create or replace function public.nexa_expire_profile_statuses()
returns integer language plpgsql security definer set search_path=public,pg_temp as $$
declare expired_row record;
declare affected integer := 0;
begin
  for expired_row in
    select user_id,status_code,custom_text,notify_on_expiry
      from public.nexa_profile_status
     where expires_at is not null and expires_at<=now()
     for update skip locked
  loop
    if expired_row.notify_on_expiry then
      insert into public.notifications(user_id,actor_id,type,preview,is_read)
      values(
        expired_row.user_id,null,'PROFILE_STATUS_EXPIRED',
        coalesce(nullif(expired_row.custom_text,''),expired_row.status_code) || ' — انتهت مدة حالتك',
        false
      );
    end if;
    delete from public.nexa_profile_status where user_id=expired_row.user_id;
    affected := affected + 1;
  end loop;
  return affected;
end $$;
revoke all on function public.nexa_expire_profile_statuses() from public,anon,authenticated;

do $$
begin
  if not exists(select 1 from cron.job where jobname='nexa-expire-profile-statuses') then
    perform cron.schedule('nexa-expire-profile-statuses','*/5 * * * *','select public.nexa_expire_profile_statuses();');
  end if;
end $$;

create or replace function public.nexa_limit_pinned_credentials()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if new.is_pinned and (tg_op='INSERT' or not old.is_pinned) then
    if (select count(*) from public.nexa_profile_credentials
        where user_id=new.user_id and is_pinned and id is distinct from new.id) >= 1 then
      raise exception 'You can pin one primary certificate or experience';
    end if;
  end if;
  return new;
end $$;
revoke all on function public.nexa_limit_pinned_credentials() from public,anon,authenticated;
drop trigger if exists nexa_limit_pinned_credentials_trigger on public.nexa_profile_credentials;
create trigger nexa_limit_pinned_credentials_trigger
before insert or update of is_pinned on public.nexa_profile_credentials
for each row execute function public.nexa_limit_pinned_credentials();
notify pgrst,'reload schema';
