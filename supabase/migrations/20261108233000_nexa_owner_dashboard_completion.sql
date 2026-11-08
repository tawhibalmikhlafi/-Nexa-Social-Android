-- Owner console completion: safe official publishing, user detail lookup and ad delivery policy.
create or replace function public.nexa_owner_create_official_post(p_text text)
returns uuid
language plpgsql
security definer
set search_path = public, auth
as $$
declare new_post_id uuid;
begin
  if not public.nexa_is_owner() then
    raise exception 'OWNER_REQUIRED' using errcode = '42501';
  end if;
  if char_length(trim(coalesce(p_text, ''))) not between 1 and 5000 then
    raise exception 'INVALID_POST_TEXT';
  end if;
  insert into public.posts(author_id, text, audience)
  values (auth.uid(), trim(p_text), 'PUBLIC_LOCAL')
  returning id into new_post_id;
  return new_post_id;
end;
$$;
revoke all on function public.nexa_owner_create_official_post(text) from public, anon;
grant execute on function public.nexa_owner_create_official_post(text) to authenticated;

create or replace function public.nexa_owner_user_details(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, auth
as $$
declare result jsonb;
begin
  if not public.nexa_is_owner() then
    raise exception 'OWNER_REQUIRED' using errcode = '42501';
  end if;
  select jsonb_build_object(
    'user_id', u.id,
    'email', u.email,
    'created_at', u.created_at,
    'last_sign_in_at', u.last_sign_in_at,
    'profile', coalesce(to_jsonb(p), '{}'::jsonb),
    'posts_count', (select count(*) from public.posts x where x.author_id = u.id),
    'followers_count', (select count(*) from public.follows f where f.following_id = u.id),
    'following_count', (select count(*) from public.follows f where f.follower_id = u.id)
  )
  into result
  from auth.users u
  left join public.profiles p on p.id = u.id
  where u.id = p_user_id;
  if result is null then raise exception 'USER_NOT_FOUND'; end if;
  return result;
end;
$$;
revoke all on function public.nexa_owner_user_details(uuid) from public, anon;
grant execute on function public.nexa_owner_user_details(uuid) to authenticated;

-- Values are JSONB so the client can update these settings without exposing table writes.
insert into public.nexa_app_settings(setting_key, setting_value) values
  ('ads_enabled', 'false'::jsonb),
  ('ads_placement', '"FEED_INLINE"'::jsonb),
  ('ads_duration_seconds', '15'::jsonb),
  ('ads_frequency_per_session', '2'::jsonb)
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

  if p_key in ('maintenance_mode', 'new_registrations_enabled', 'official_badge_enabled', 'ads_enabled') then
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
notify pgrst, 'reload schema';


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
  if p_placement not in ('FEED_TOP', 'FEED_INLINE', 'STORIES_BETWEEN', 'PROFILE_FEED') then
    raise exception 'INVALID_AD_PLACEMENT';
  end if;
  if p_duration_seconds not between 5 and 120 then raise exception 'ADS_DURATION_OUT_OF_RANGE'; end if;
  if p_frequency_per_session not between 1 and 10 then raise exception 'ADS_FREQUENCY_OUT_OF_RANGE'; end if;

  insert into public.nexa_app_settings(setting_key, setting_value, updated_at, updated_by)
  values
    ('ads_enabled', to_jsonb(p_enabled), now(), auth.uid()),
    ('ads_placement', to_jsonb(p_placement), now(), auth.uid()),
    ('ads_duration_seconds', to_jsonb(p_duration_seconds), now(), auth.uid()),
    ('ads_frequency_per_session', to_jsonb(p_frequency_per_session), now(), auth.uid())
  on conflict(setting_key) do update
    set setting_value = excluded.setting_value, updated_at = now(), updated_by = auth.uid();
  return true;
end;
$$;
revoke all on function public.nexa_owner_set_ads_settings(boolean, text, integer, integer) from public, anon;
grant execute on function public.nexa_owner_set_ads_settings(boolean, text, integer, integer) to authenticated;
notify pgrst, 'reload schema';
