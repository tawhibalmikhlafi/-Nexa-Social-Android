-- Nexa professional profile suite: timeline, languages, credentials, status,
-- testimonials, favorites, achievements and private analytics.
create or replace function public.nexa_can_view_profile(target_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select target_user = auth.uid()
    or exists (
      select 1
      from public.profiles p
      where p.id = target_user
        and coalesce(p.is_private_account, false) = false
    )
    or exists (
      select 1
      from public.follows f
      where f.follower_id = auth.uid()
        and f.following_id = target_user
    );
$$;
revoke all on function public.nexa_can_view_profile(uuid) from public;
grant execute on function public.nexa_can_view_profile(uuid) to authenticated;

create table if not exists public.nexa_profile_timeline (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  title text not null check (char_length(btrim(title)) between 1 and 120),
  event_date date not null,
  event_type text not null check (event_type in ('EDUCATION','WORK','ACHIEVEMENT','TRAVEL','PERSONAL')),
  description text not null default '' check (char_length(description) <= 600),
  icon text not null default '',
  is_visible boolean not null default true,
  is_pinned boolean not null default false,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists nexa_profile_timeline_user_date_idx on public.nexa_profile_timeline(user_id,event_date desc);

create table if not exists public.nexa_profile_languages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  language_code text not null,
  language_name text not null check (char_length(btrim(language_name)) between 1 and 80),
  flag_emoji text not null default '',
  proficiency text not null check (proficiency in ('NATIVE','FLUENT','ADVANCED','INTERMEDIATE','BEGINNER')),
  is_native boolean not null default false,
  is_private boolean not null default false,
  certificate_name text,
  certificate_score text,
  certificate_url text,
  is_verified boolean not null default false,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  unique(user_id,language_code)
);
create index if not exists nexa_profile_languages_user_idx on public.nexa_profile_languages(user_id,sort_order);

create table if not exists public.nexa_profile_credentials (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  record_type text not null check (record_type in ('CERTIFICATE','EXPERIENCE')),
  title text not null check (char_length(btrim(title)) between 1 and 160),
  issuer text not null default '' check (char_length(issuer) <= 160),
  start_date date,
  end_date date,
  is_current boolean not null default false,
  location text,
  description text not null default '' check (char_length(description) <= 1200),
  verification_url text,
  organization_logo_url text,
  document_url text,
  category text not null default 'PROFESSIONAL' check (category in ('ACADEMIC','PROFESSIONAL','VOLUNTEER','PERSONAL')),
  is_verified boolean not null default false,
  is_pinned boolean not null default false,
  is_visible boolean not null default true,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (end_date is null or start_date is null or end_date >= start_date)
);
create index if not exists nexa_profile_credentials_user_idx on public.nexa_profile_credentials(user_id,sort_order);

create table if not exists public.nexa_profile_status (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  status_code text not null,
  custom_text text check (custom_text is null or char_length(custom_text) <= 80),
  emoji text not null default '',
  color_key text not null default 'PURPLE',
  is_visible boolean not null default true,
  notify_on_expiry boolean not null default false,
  expires_at timestamptz,
  updated_at timestamptz not null default now()
);
create table if not exists public.nexa_profile_status_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  status_code text not null,
  custom_text text,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  expires_at timestamptz
);
create index if not exists nexa_profile_status_history_user_idx on public.nexa_profile_status_history(user_id,started_at desc);

create table if not exists public.nexa_profile_testimonials (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  author_id uuid references public.profiles(id) on delete set null,
  author_name text not null check (char_length(btrim(author_name)) between 1 and 100),
  author_avatar_url text,
  author_title text,
  author_company text,
  body text not null default '' check (char_length(body) <= 500),
  rating smallint check (rating between 1 and 5),
  source_type text not null default 'TEXT' check (source_type in ('TEXT','VIDEO','AUDIO')),
  media_url text,
  media_duration_seconds smallint,
  linkedin_url text,
  is_verified boolean not null default false,
  is_approved boolean not null default true,
  is_visible boolean not null default true,
  is_pinned boolean not null default false,
  created_at timestamptz not null default now(),
  check (
    (source_type = 'TEXT' and media_duration_seconds is null)
    or (source_type = 'VIDEO' and media_duration_seconds between 1 and 60)
    or (source_type = 'AUDIO' and media_duration_seconds between 1 and 90)
  )
);
create index if not exists nexa_profile_testimonials_user_idx on public.nexa_profile_testimonials(profile_id,created_at desc);

create table if not exists public.nexa_testimonial_requests (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  request_token uuid not null unique default gen_random_uuid(),
  recipient_email text,
  expires_at timestamptz not null default (now() + interval '30 days'),
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.nexa_profile_favorites (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  category text not null check (category in ('BOOK','MOVIE','MUSIC','FOOD','GAME','DESTINATION','INSPIRATION','QUOTE')),
  title text not null check (char_length(btrim(title)) between 1 and 140),
  creator text,
  year text,
  image_url text,
  external_url text,
  quote text check (quote is null or char_length(quote) <= 500),
  description text check (description is null or char_length(description) <= 500),
  card_color text not null default 'PURPLE',
  is_pinned boolean not null default false,
  is_visible boolean not null default true,
  sort_order integer not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists nexa_profile_favorites_user_idx on public.nexa_profile_favorites(user_id,sort_order);

create table if not exists public.nexa_profile_achievements (
  user_id uuid not null references public.profiles(id) on delete cascade,
  achievement_code text not null,
  progress integer not null default 0 check (progress >= 0),
  target integer not null default 1 check (target > 0),
  unlocked_at timestamptz,
  is_visible boolean not null default true,
  is_featured boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key(user_id,achievement_code)
);
create index if not exists nexa_profile_achievements_user_idx on public.nexa_profile_achievements(user_id,unlocked_at desc);

create table if not exists public.nexa_profile_analytics_events (
  id bigint generated always as identity primary key,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  viewer_id uuid references auth.users(id) on delete set null,
  event_type text not null check (event_type in ('PROFILE_VIEW','SKILL_CLICK','SOCIAL_LINK_CLICK','CV_DOWNLOAD','QR_SCAN','TESTIMONIAL_RECEIVED')),
  event_key text,
  country_code text,
  created_at timestamptz not null default now()
);
create index if not exists nexa_profile_analytics_profile_date_idx on public.nexa_profile_analytics_events(profile_id,created_at desc);
create index if not exists nexa_profile_analytics_viewer_idx on public.nexa_profile_analytics_events(viewer_id,created_at desc);

-- RLS: owners manage their content; visible records are readable only when the
-- profile itself is viewable. Private language rows are owner-only.
do $$
declare t text;
begin
  foreach t in array array[
    'nexa_profile_timeline','nexa_profile_languages','nexa_profile_credentials',
    'nexa_profile_status','nexa_profile_status_history','nexa_profile_testimonials',
    'nexa_testimonial_requests','nexa_profile_favorites','nexa_profile_achievements',
    'nexa_profile_analytics_events'
  ] loop
    execute format('alter table public.%I enable row level security',t);
    execute format('grant select, insert, update, delete on public.%I to authenticated',t);
  end loop;
end $$;
grant usage, select on sequence public.nexa_profile_analytics_events_id_seq to authenticated;

drop policy if exists nexa_timeline_select on public.nexa_profile_timeline;
create policy nexa_timeline_select on public.nexa_profile_timeline for select to authenticated
using (is_visible and public.nexa_can_view_profile(user_id) or user_id=auth.uid());
drop policy if exists nexa_timeline_owner_write on public.nexa_profile_timeline;
create policy nexa_timeline_owner_write on public.nexa_profile_timeline for all to authenticated
using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists nexa_languages_select on public.nexa_profile_languages;
create policy nexa_languages_select on public.nexa_profile_languages for select to authenticated
using ((not is_private and public.nexa_can_view_profile(user_id)) or user_id=auth.uid());
drop policy if exists nexa_languages_owner_write on public.nexa_profile_languages;
create policy nexa_languages_owner_write on public.nexa_profile_languages for all to authenticated
using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists nexa_credentials_select on public.nexa_profile_credentials;
create policy nexa_credentials_select on public.nexa_profile_credentials for select to authenticated
using ((is_visible and public.nexa_can_view_profile(user_id)) or user_id=auth.uid());
drop policy if exists nexa_credentials_owner_write on public.nexa_profile_credentials;
create policy nexa_credentials_owner_write on public.nexa_profile_credentials for all to authenticated
using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists nexa_status_select on public.nexa_profile_status;
create policy nexa_status_select on public.nexa_profile_status for select to authenticated
using ((is_visible and (expires_at is null or expires_at > now()) and public.nexa_can_view_profile(user_id)) or user_id=auth.uid());
drop policy if exists nexa_status_owner_write on public.nexa_profile_status;
create policy nexa_status_owner_write on public.nexa_profile_status for all to authenticated
using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists nexa_status_history_owner on public.nexa_profile_status_history;
create policy nexa_status_history_owner on public.nexa_profile_status_history for all to authenticated
using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists nexa_testimonials_select on public.nexa_profile_testimonials;
create policy nexa_testimonials_select on public.nexa_profile_testimonials for select to authenticated
using ((is_approved and is_visible and public.nexa_can_view_profile(profile_id)) or profile_id=auth.uid());
drop policy if exists nexa_testimonials_owner_insert on public.nexa_profile_testimonials;
create policy nexa_testimonials_owner_insert on public.nexa_profile_testimonials for insert to authenticated
with check (profile_id=auth.uid() and author_id is null or author_id=auth.uid());
drop policy if exists nexa_testimonials_owner_update on public.nexa_profile_testimonials;
create policy nexa_testimonials_owner_update on public.nexa_profile_testimonials for update to authenticated
using (profile_id=auth.uid() or author_id=auth.uid())
with check (profile_id=auth.uid() or author_id=auth.uid());
drop policy if exists nexa_testimonials_owner_delete on public.nexa_profile_testimonials;
create policy nexa_testimonials_owner_delete on public.nexa_profile_testimonials for delete to authenticated
using (profile_id=auth.uid() or author_id=auth.uid());

drop policy if exists nexa_testimonial_requests_owner on public.nexa_testimonial_requests;
create policy nexa_testimonial_requests_owner on public.nexa_testimonial_requests for all to authenticated
using (profile_id=auth.uid()) with check (profile_id=auth.uid());

drop policy if exists nexa_favorites_select on public.nexa_profile_favorites;
create policy nexa_favorites_select on public.nexa_profile_favorites for select to authenticated
using ((is_visible and public.nexa_can_view_profile(user_id)) or user_id=auth.uid());
drop policy if exists nexa_favorites_owner_write on public.nexa_profile_favorites;
create policy nexa_favorites_owner_write on public.nexa_profile_favorites for all to authenticated
using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists nexa_achievements_select on public.nexa_profile_achievements;
create policy nexa_achievements_select on public.nexa_profile_achievements for select to authenticated
using ((is_visible and public.nexa_can_view_profile(user_id)) or user_id=auth.uid());
drop policy if exists nexa_achievements_owner_write on public.nexa_profile_achievements;
create policy nexa_achievements_owner_write on public.nexa_profile_achievements for all to authenticated
using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists nexa_analytics_insert on public.nexa_profile_analytics_events;
create policy nexa_analytics_insert on public.nexa_profile_analytics_events for insert to authenticated
with check (viewer_id=auth.uid() and profile_id<>auth.uid());
drop policy if exists nexa_analytics_select on public.nexa_profile_analytics_events;
create policy nexa_analytics_select on public.nexa_profile_analytics_events for select to authenticated
using (profile_id=auth.uid());
-- No client-side updates/deletes for analytics events.
revoke update, delete on public.nexa_profile_analytics_events from authenticated;

notify pgrst, 'reload schema';
