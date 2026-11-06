-- Group-specific reports are visible to the group's owner and active admins only.
alter table public.content_reports
  add column if not exists group_id uuid references public.groups(id) on delete cascade,
  add column if not exists status text not null default 'pending',
  add column if not exists reviewed_by uuid references public.profiles(id),
  add column if not exists reviewed_at timestamptz;

alter table public.content_reports
  drop constraint if exists content_reports_status_check;
alter table public.content_reports
  add constraint content_reports_status_check
  check (status in ('pending','reviewed','dismissed'));

create index if not exists content_reports_group_status_created_idx
  on public.content_reports(group_id, status, created_at desc)
  where group_id is not null;

drop policy if exists content_reports_insert_own on public.content_reports;
create policy content_reports_insert_own
on public.content_reports for insert to authenticated
with check (
  reporter_id = (select auth.uid())
  and (
    group_id is null
    or (
      target_type = 'group_post'
      and exists (
        select 1
        from public.group_posts p
        join public.group_members m on m.group_id = p.group_id
        where p.id = content_reports.target_id
          and p.group_id = content_reports.group_id
          and p.is_deleted = false
          and m.user_id = (select auth.uid())
          and m.status = 'active'
      )
    )
  )
);

drop policy if exists content_reports_select_group_admins on public.content_reports;
create policy content_reports_select_group_admins
on public.content_reports for select to authenticated
using (
  group_id is not null
  and public.nexa_is_group_admin(group_id, (select auth.uid()))
);

drop policy if exists content_reports_update_group_admins on public.content_reports;
create policy content_reports_update_group_admins
on public.content_reports for update to authenticated
using (
  group_id is not null
  and public.nexa_is_group_admin(group_id, (select auth.uid()))
)
with check (
  group_id is not null
  and public.nexa_is_group_admin(group_id, (select auth.uid()))
  and reviewed_by = (select auth.uid())
);

create or replace function public.notify_group_admins_of_post_report()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_group_name text;
  v_preview text;
begin
  if new.group_id is null or new.target_type <> 'group_post' then
    return new;
  end if;

  select name into v_group_name
  from public.groups
  where id = new.group_id;

  v_preview := 'بلاغ جديد على منشور في مجموعة ' || coalesce(v_group_name, '');

  insert into public.notifications(user_id, actor_id, type, preview, group_id)
  select distinct recipients.user_id, new.reporter_id, 'group_post_report',
         left(v_preview, 180), new.group_id
  from (
    select g.owner_id as user_id
    from public.groups g
    where g.id = new.group_id
    union
    select gm.user_id
    from public.group_members gm
    where gm.group_id = new.group_id
      and gm.role = 'admin'
      and gm.status = 'active'
  ) recipients
  where recipients.user_id <> new.reporter_id;

  return new;
end;
$$;

revoke all on function public.notify_group_admins_of_post_report() from public, anon, authenticated;

drop trigger if exists trg_notify_group_admins_of_post_report on public.content_reports;
create trigger trg_notify_group_admins_of_post_report
after insert on public.content_reports
for each row execute function public.notify_group_admins_of_post_report();

grant select, insert, update on public.content_reports to authenticated;
