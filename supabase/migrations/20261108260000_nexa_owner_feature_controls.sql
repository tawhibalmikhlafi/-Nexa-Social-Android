-- Granular owner feature switches enforced by Postgres RLS.
insert into public.nexa_app_settings(setting_key, setting_value) values
  ('posts_enabled', 'true'::jsonb),
  ('comments_enabled', 'true'::jsonb),
  ('stories_enabled', 'true'::jsonb),
  ('reels_enabled', 'true'::jsonb)
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
    'ads_enabled', 'posts_enabled', 'comments_enabled', 'stories_enabled', 'reels_enabled'
  ) then
    if jsonb_typeof(p_value) <> 'boolean' then
      raise exception 'INVALID_SETTING_VALUE';
    end if;
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

-- Owners can always operate the platform. Other users are gated by both account
-- status/maintenance and the per-feature switch.
create or replace function public.nexa_can_use_feature(p_feature text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.nexa_is_owner()
    or (
      public.nexa_can_write()
      and coalesce(
        (select s.setting_value = 'true'::jsonb
           from public.nexa_app_settings s
          where s.setting_key = p_feature),
        true
      )
    );
$$;
revoke all on function public.nexa_can_use_feature(text) from public, anon;
grant execute on function public.nexa_can_use_feature(text) to authenticated;

-- Re-apply the existing ownership rules with the new feature-level gate.
drop policy if exists nexa_posts_insert on public.posts;
create policy nexa_posts_insert on public.posts for insert to authenticated
with check (author_id = auth.uid() and public.nexa_can_use_feature('posts_enabled'));
drop policy if exists nexa_posts_update on public.posts;
create policy nexa_posts_update on public.posts for update to authenticated
using (author_id = auth.uid() and public.nexa_can_use_feature('posts_enabled'))
with check (author_id = auth.uid() and public.nexa_can_use_feature('posts_enabled'));
drop policy if exists nexa_posts_delete on public.posts;
create policy nexa_posts_delete on public.posts for delete to authenticated
using (author_id = auth.uid() and public.nexa_can_use_feature('posts_enabled'));

drop policy if exists nexa_comments_insert on public.comments;
create policy nexa_comments_insert on public.comments for insert to authenticated
with check (
  author_id = auth.uid()
  and public.nexa_can_use_feature('comments_enabled')
  and nexa_can_view_post(post_id)
  and nexa_can_comment_on_post(post_id)
);
drop policy if exists nexa_comments_update on public.comments;
create policy nexa_comments_update on public.comments for update to authenticated
using (author_id = auth.uid() and public.nexa_can_use_feature('comments_enabled'))
with check (author_id = auth.uid() and public.nexa_can_use_feature('comments_enabled'));
drop policy if exists nexa_comments_delete on public.comments;
create policy nexa_comments_delete on public.comments for delete to authenticated
using (
  public.nexa_can_use_feature('comments_enabled')
  and (
    author_id = auth.uid()
    or exists(select 1 from public.posts p where p.id = comments.post_id and p.author_id = auth.uid())
  )
);

drop policy if exists nexa_stories_insert on public.stories;
create policy nexa_stories_insert on public.stories for insert to authenticated
with check (author_id = auth.uid() and public.nexa_can_use_feature('stories_enabled'));
drop policy if exists nexa_stories_update on public.stories;
create policy nexa_stories_update on public.stories for update to authenticated
using (author_id = auth.uid() and public.nexa_can_use_feature('stories_enabled'))
with check (author_id = auth.uid() and public.nexa_can_use_feature('stories_enabled'));
drop policy if exists nexa_stories_delete on public.stories;
create policy nexa_stories_delete on public.stories for delete to authenticated
using (author_id = auth.uid() and public.nexa_can_use_feature('stories_enabled'));

drop policy if exists nexa_clips_insert on public.clips;
create policy nexa_clips_insert on public.clips for insert to authenticated
with check (author_id = auth.uid() and public.nexa_can_use_feature('reels_enabled'));
drop policy if exists nexa_clips_update on public.clips;
create policy nexa_clips_update on public.clips for update to authenticated
using (author_id = auth.uid() and public.nexa_can_use_feature('reels_enabled'))
with check (author_id = auth.uid() and public.nexa_can_use_feature('reels_enabled'));
drop policy if exists nexa_clips_delete on public.clips;
create policy nexa_clips_delete on public.clips for delete to authenticated
using (author_id = auth.uid() and public.nexa_can_use_feature('reels_enabled'));

notify pgrst, 'reload schema';
