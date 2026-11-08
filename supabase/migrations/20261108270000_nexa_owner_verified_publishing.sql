-- Optional trust gate: only verified accounts may publish posts.
insert into public.nexa_app_settings(setting_key, setting_value)
values ('verified_only_posting', 'false'::jsonb)
on conflict (setting_key) do nothing;

create or replace function public.nexa_owner_set_setting(p_key text, p_value jsonb)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare numeric_value integer;
begin
  if not public.nexa_is_owner() then
    raise exception 'OWNER_REQUIRED' using errcode = '42501';
  end if;

  if p_key in (
    'maintenance_mode', 'new_registrations_enabled', 'official_badge_enabled',
    'ads_enabled', 'posts_enabled', 'comments_enabled', 'stories_enabled',
    'reels_enabled', 'verified_only_posting'
  ) then
    if jsonb_typeof(p_value) <> 'boolean' then raise exception 'INVALID_SETTING_VALUE'; end if;
  elsif p_key = 'ads_placement' then
    if jsonb_typeof(p_value) <> 'string'
       or p_value #>> '{}' not in ('FEED_TOP', 'FEED_INLINE', 'STORIES_BETWEEN', 'PROFILE_FEED') then
      raise exception 'INVALID_AD_PLACEMENT';
    end if;
  elsif p_key in ('ads_duration_seconds', 'ads_frequency_per_session') then
    if jsonb_typeof(p_value) <> 'number' then raise exception 'INVALID_SETTING_VALUE'; end if;
    numeric_value := (p_value #>> '{}')::integer;
    if p_key = 'ads_duration_seconds' and numeric_value not between 5 and 120 then
      raise exception 'ADS_DURATION_OUT_OF_RANGE';
    end if;
    if p_key = 'ads_frequency_per_session' and numeric_value not between 1 and 10 then
      raise exception 'ADS_FREQUENCY_OUT_OF_RANGE';
    end if;
  else
    raise exception 'INVALID_SETTING';
  end if;

  insert into public.nexa_app_settings(setting_key, setting_value, updated_at, updated_by)
  values(p_key, p_value, now(), auth.uid())
  on conflict(setting_key) do update
    set setting_value = excluded.setting_value, updated_at = now(), updated_by = auth.uid();
  return true;
end;
$$;
revoke all on function public.nexa_owner_set_setting(text, jsonb) from public, anon;
grant execute on function public.nexa_owner_set_setting(text, jsonb) to authenticated;

create or replace function public.nexa_can_publish_post()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.nexa_is_owner()
    or (
      public.nexa_can_use_feature('posts_enabled')
      and (
        coalesce(
          (select s.setting_value = 'true'::jsonb
             from public.nexa_app_settings s
            where s.setting_key = 'verified_only_posting'),
          false
        ) = false
        or exists (
          select 1 from public.profiles p
          where p.id = auth.uid() and p.is_verified = true
        )
      )
    );
$$;
revoke all on function public.nexa_can_publish_post() from public, anon;
grant execute on function public.nexa_can_publish_post() to authenticated;

drop policy if exists nexa_posts_insert on public.posts;
create policy nexa_posts_insert on public.posts for insert to authenticated
with check (author_id = auth.uid() and public.nexa_can_publish_post());

notify pgrst, 'reload schema';
