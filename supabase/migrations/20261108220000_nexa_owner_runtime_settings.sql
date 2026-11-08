create table if not exists public.nexa_app_settings (
  setting_key text primary key,
  setting_value jsonb not null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id)
);
alter table public.nexa_app_settings enable row level security;
revoke all on public.nexa_app_settings from anon,authenticated;
grant select,insert,update on public.nexa_app_settings to authenticated;
drop policy if exists nexa_app_settings_owner_all on public.nexa_app_settings;
create policy nexa_app_settings_owner_all on public.nexa_app_settings
for all to authenticated using (public.nexa_is_owner()) with check (public.nexa_is_owner());

insert into public.nexa_app_settings(setting_key,setting_value) values
 ('maintenance_mode','false'::jsonb),
 ('new_registrations_enabled','true'::jsonb),
 ('official_badge_enabled','true'::jsonb)
on conflict(setting_key) do nothing;

create or replace function public.nexa_owner_get_settings()
returns jsonb language plpgsql stable security definer set search_path=public
as $$
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 return coalesce((select jsonb_object_agg(setting_key,setting_value) from public.nexa_app_settings),'{}'::jsonb);
end $$;
revoke all on function public.nexa_owner_get_settings() from public,anon;
grant execute on function public.nexa_owner_get_settings() to authenticated;

create or replace function public.nexa_owner_set_setting(p_key text,p_value jsonb)
returns boolean language plpgsql security definer set search_path=public
as $$
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 if p_key not in ('maintenance_mode','new_registrations_enabled','official_badge_enabled') then raise exception 'INVALID_SETTING'; end if;
 insert into public.nexa_app_settings(setting_key,setting_value,updated_at,updated_by)
 values(p_key,p_value,now(),auth.uid())
 on conflict(setting_key) do update set setting_value=excluded.setting_value,updated_at=now(),updated_by=auth.uid();
 return true;
end $$;
revoke all on function public.nexa_owner_set_setting(text,jsonb) from public,anon;
grant execute on function public.nexa_owner_set_setting(text,jsonb) to authenticated;
notify pgrst,'reload schema';