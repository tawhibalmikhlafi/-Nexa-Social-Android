create or replace function public.nexa_owner_set_ads_settings(
  p_enabled boolean,
  p_placement text,
  p_duration_seconds integer,
  p_frequency_per_session integer
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.nexa_is_owner() then
    raise exception 'OWNER_REQUIRED' using errcode = '42501';
  end if;

  if p_enabled is null
     or p_placement not in ('FEED_TOP', 'FEED_INLINE', 'STORIES_BETWEEN', 'PROFILE_FEED')
     or p_duration_seconds not between 5 and 120
     or p_frequency_per_session not between 1 and 10 then
    raise exception 'INVALID_AD_SETTINGS';
  end if;

  insert into public.nexa_app_settings(setting_key, setting_value, updated_at, updated_by)
  values
    ('ads_enabled', to_jsonb(p_enabled), now(), auth.uid()),
    ('ads_placement', to_jsonb(p_placement), now(), auth.uid()),
    ('ads_duration_seconds', to_jsonb(p_duration_seconds), now(), auth.uid()),
    ('ads_frequency_per_session', to_jsonb(p_frequency_per_session), now(), auth.uid())
  on conflict (setting_key) do update
    set setting_value = excluded.setting_value,
        updated_at = now(),
        updated_by = auth.uid();

  return true;
end;
$$;

revoke all on function public.nexa_owner_set_ads_settings(boolean, text, integer, integer) from public, anon;
grant execute on function public.nexa_owner_set_ads_settings(boolean, text, integer, integer) to authenticated;
notify pgrst, 'reload schema';
