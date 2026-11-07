-- Reuse the existing supported system notification type for status expiry alerts.
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
        expired_row.user_id,null,'system',
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
