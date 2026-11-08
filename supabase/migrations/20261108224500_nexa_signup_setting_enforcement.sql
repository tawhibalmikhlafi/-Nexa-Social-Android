create or replace function public.nexa_signup_allowed()
returns boolean language sql stable security definer set search_path=public
as $$
 select coalesce((select (setting_value #>> '{}')::boolean from public.nexa_app_settings where setting_key='new_registrations_enabled'),true);
$$;
revoke all on function public.nexa_signup_allowed() from public;
grant execute on function public.nexa_signup_allowed() to anon,authenticated;
notify pgrst,'reload schema';