-- Record a durable in-app security notice for every successful email/password login.
create or replace function public.nexa_record_login_notification()
returns boolean
language plpgsql
security definer
set search_path=public,auth
as $$
begin
  if auth.uid() is null then
    raise exception 'NOT_AUTHENTICATED' using errcode='42501';
  end if;
  insert into public.notifications(user_id,actor_id,type,preview,is_read)
  values (
    auth.uid(),
    null,
    'LOGIN_ALERT',
    'تم تسجيل الدخول إلى حسابك في Nexa بنجاح.',
    false
  );
  return true;
end;
$$;
revoke all on function public.nexa_record_login_notification() from public,anon;
grant execute on function public.nexa_record_login_notification() to authenticated;
notify pgrst,'reload schema';
