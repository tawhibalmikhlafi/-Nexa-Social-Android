create table if not exists public.nexa_support_tickets(
 id uuid primary key default gen_random_uuid(),
 user_id uuid not null references auth.users(id) on delete cascade,
 subject text not null check(char_length(subject) between 1 and 160),
 message text not null check(char_length(message) between 1 and 5000),
 status text not null default 'OPEN' check(status in ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
 admin_reply text,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 replied_by uuid references auth.users(id)
);
alter table public.nexa_support_tickets enable row level security;
revoke all on public.nexa_support_tickets from anon;
grant select,insert,update on public.nexa_support_tickets to authenticated;
drop policy if exists nexa_support_own_read on public.nexa_support_tickets;
create policy nexa_support_own_read on public.nexa_support_tickets for select to authenticated
using(user_id=auth.uid() or public.nexa_is_owner());
drop policy if exists nexa_support_own_insert on public.nexa_support_tickets;
create policy nexa_support_own_insert on public.nexa_support_tickets for insert to authenticated
with check(user_id=auth.uid());
drop policy if exists nexa_support_owner_update on public.nexa_support_tickets;
create policy nexa_support_owner_update on public.nexa_support_tickets for update to authenticated
using(public.nexa_is_owner()) with check(public.nexa_is_owner());

create or replace function public.nexa_owner_list_support()
returns table(id uuid,user_id uuid,subject text,message text,status text,admin_reply text,created_at timestamptz)
language plpgsql stable security definer set search_path=public
as $$
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 return query select t.id,t.user_id,t.subject,t.message,t.status,t.admin_reply,t.created_at
 from public.nexa_support_tickets t order by t.created_at desc limit 200;
end $$;
revoke all on function public.nexa_owner_list_support() from public,anon;
grant execute on function public.nexa_owner_list_support() to authenticated;

create or replace function public.nexa_owner_reply_support(p_ticket_id uuid,p_reply text,p_status text default 'RESOLVED')
returns boolean language plpgsql security definer set search_path=public
as $$
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 if p_status not in ('IN_PROGRESS','RESOLVED','CLOSED') then raise exception 'INVALID_STATUS'; end if;
 update public.nexa_support_tickets set admin_reply=trim(p_reply),status=p_status,updated_at=now(),replied_by=auth.uid()
 where id=p_ticket_id;
 return found;
end $$;
revoke all on function public.nexa_owner_reply_support(uuid,text,text) from public,anon;
grant execute on function public.nexa_owner_reply_support(uuid,text,text) to authenticated;

create or replace function public.nexa_broadcast_announcement()
returns trigger language plpgsql security definer set search_path=public
as $$
begin
 insert into public.notifications(user_id,actor_id,type,preview,is_read)
 select p.id,new.created_by,'ANNOUNCEMENT',left(new.title||': '||new.body,500),false
 from public.profiles p where p.account_status='ACTIVE' and p.id<>new.created_by;
 return new;
end $$;
drop trigger if exists nexa_announcement_broadcast on public.nexa_announcements;
create trigger nexa_announcement_broadcast after insert on public.nexa_announcements
for each row execute function public.nexa_broadcast_announcement();

create or replace function public.nexa_notify_owner_support()
returns trigger language plpgsql security definer set search_path=public
as $$
begin
 insert into public.notifications(user_id,actor_id,type,preview,is_read)
 select r.user_id,new.user_id,'SUPPORT_TICKET','طلب دعم جديد: '||new.subject,false
 from public.nexa_admin_roles r where r.role='OWNER';
 return new;
end $$;
drop trigger if exists nexa_owner_support_alert on public.nexa_support_tickets;
create trigger nexa_owner_support_alert after insert on public.nexa_support_tickets
for each row execute function public.nexa_notify_owner_support();
notify pgrst,'reload schema';