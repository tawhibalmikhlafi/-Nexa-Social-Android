-- Extend profiles with optional user-controlled personal details.
-- Join date remains the canonical profiles.created_at timestamp and is never client-editable.
alter table public.profiles
  add column if not exists place_of_origin text,
  add column if not exists current_residence text,
  add column if not exists birth_date date,
  add column if not exists skills text[] not null default '{}'::text[],
  add column if not exists hobbies text[] not null default '{}'::text[];

comment on column public.profiles.place_of_origin is 'User supplied place of origin';
comment on column public.profiles.current_residence is 'User supplied current residence';
comment on column public.profiles.birth_date is 'User birth date; nullable and user controlled';
comment on column public.profiles.skills is 'User profile skills as text array';
comment on column public.profiles.hobbies is 'User profile hobbies as text array';

notify pgrst, 'reload schema';
