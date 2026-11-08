-- Owner console backend: user directory, moderation, report alerts and official Nexa identity.
-- No password data is exposed. Supabase Auth password hashes are never queried or returned.

alter table public.profiles
  add column if not exists is_verified boolean not null default false,
  add column if not exists verified_label text;

create table if not exists public.nexa_announcements (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 140),
  body text not null check (char_length(body) between 1 and 5000),
  audience text not null default 'ALL' check (audience in ('ALL')),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now()
);
alter table public.nexa_announcements enable row level security;
revoke all on public.nexa_announcements from anon, authenticated;
grant select on public.nexa_announcements to authenticated;
create policy nexa_announcements_read_authenticated on public.nexa_announcements
  for select to authenticated using (true);

drop policy if exists nexa_reports_owner_read on public.content_reports;
create policy nexa_reports_owner_read on public.content_reports
  for select to authenticated using (public.nexa_is_owner());
drop policy if exists nexa_reports_owner_update on public.content_reports;
create policy nexa_reports_owner_update on public.content_reports
  for update to authenticated using (public.nexa_is_owner())
  with check (public.nexa_is_owner());

create or replace function public.nexa_owner_dashboard_stats()
returns jsonb language plpgsql stable security definer set search_path=public,auth
as $$
declare result jsonb;
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  select jsonb_build_object(
    'users_total',(select count(*) from auth.users),
    'users_today',(select count(*) from auth.users where created_at >= date_trunc('day',now())),
    'posts_total',(select count(*) from public.posts),
    'reports_open',(select count(*) from public.content_reports where status is null or status in ('OPEN','PENDING')),
    'suspended_users',(select count(*) from public.profiles where account_status='SUSPENDED')
  ) into result;
  return result;
end $$;
revoke all on function public.nexa_owner_dashboard_stats() from public,anon;
grant execute on function public.nexa_owner_dashboard_stats() to authenticated;

create or replace function public.nexa_owner_list_users(p_search text default null, p_limit integer default 100, p_offset integer default 0)
returns table(user_id uuid,email text,username text,display_name text,created_at timestamptz,account_status text,is_verified boolean)
language plpgsql stable security definer set search_path=public,auth
as $$
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  return query
  select u.id,u.email,p.username,p.display_name,u.created_at,
         coalesce(p.account_status,'ACTIVE'),coalesce(p.is_verified,false)
  from auth.users u left join public.profiles p on p.id=u.id
  where p_search is null or p_search='' or
    coalesce(p.username,'') ilike '%'||p_search||'%' or
    coalesce(p.display_name,'') ilike '%'||p_search||'%' or
    coalesce(u.email,'') ilike '%'||p_search||'%'
  order by u.created_at desc
  limit greatest(1,least(p_limit,200)) offset greatest(p_offset,0);
end $$;
revoke all on function public.nexa_owner_list_users(text,integer,integer) from public,anon;
grant execute on function public.nexa_owner_list_users(text,integer,integer) to authenticated;

create or replace function public.nexa_owner_set_user_status(p_user_id uuid,p_status text,p_reason text default null)
returns boolean language plpgsql security definer set search_path=public,auth
as $$
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  if p_user_id=auth.uid() then raise exception 'CANNOT_RESTRICT_OWNER'; end if;
  if p_status not in ('ACTIVE','SUSPENDED') then raise exception 'INVALID_STATUS'; end if;
  update public.profiles set account_status=p_status,updated_at=now() where id=p_user_id;
  if not found then raise exception 'PROFILE_NOT_FOUND'; end if;
  return true;
end $$;
revoke all on function public.nexa_owner_set_user_status(uuid,text,text) from public,anon;
grant execute on function public.nexa_owner_set_user_status(uuid,text,text) to authenticated;

create or replace function public.nexa_owner_create_announcement(p_title text,p_body text)
returns uuid language plpgsql security definer set search_path=public,auth
as $$
declare new_id uuid;
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  insert into public.nexa_announcements(title,body,created_by)
  values(trim(p_title),trim(p_body),auth.uid()) returning id into new_id;
  return new_id;
end $$;
revoke all on function public.nexa_owner_create_announcement(text,text) from public,anon;
grant execute on function public.nexa_owner_create_announcement(text,text) to authenticated;

create or replace function public.nexa_notify_owner_new_report()
returns trigger language plpgsql security definer set search_path=public
as $$
begin
  insert into public.notifications(user_id,actor_id,type,preview,is_read)
  select r.user_id,new.reporter_id,'ADMIN_REPORT',
         'بلاغ جديد: '||coalesce(new.target_type,'محتوى')||' - '||coalesce(new.reason,'بدون سبب'),
         false
  from public.nexa_admin_roles r where r.role='OWNER';
  return new;
end $$;
drop trigger if exists nexa_owner_report_alert on public.content_reports;
create trigger nexa_owner_report_alert after insert on public.content_reports
for each row execute function public.nexa_notify_owner_new_report();

notify pgrst,'reload schema';
