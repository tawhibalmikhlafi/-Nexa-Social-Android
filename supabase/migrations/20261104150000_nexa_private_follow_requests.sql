-- Enforce private-account visibility and provide a secure follow-request workflow.
create table if not exists public.follow_requests (
    id uuid primary key default gen_random_uuid(),
    requester_id uuid not null references public.profiles(id) on delete cascade,
    target_id uuid not null references public.profiles(id) on delete cascade,
    status text not null default 'pending' check (status in ('pending','accepted','declined','cancelled')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint follow_requests_not_self check (requester_id <> target_id)
);
create unique index if not exists follow_requests_one_pending
on public.follow_requests(requester_id,target_id) where status='pending';
create index if not exists follow_requests_target_pending
on public.follow_requests(target_id,created_at desc) where status='pending';
alter table public.follow_requests enable row level security;
drop policy if exists nexa_follow_requests_select on public.follow_requests;
create policy nexa_follow_requests_select on public.follow_requests
for select to authenticated using (requester_id=auth.uid() or target_id=auth.uid());
drop policy if exists nexa_follow_requests_insert on public.follow_requests;
create policy nexa_follow_requests_insert on public.follow_requests
for insert to authenticated with check (
 requester_id=auth.uid()
 and exists(select 1 from public.profiles p where p.id=target_id)
 and coalesce((select s.is_private_account from public.user_privacy_settings s where s.user_id=target_id),false)
);
drop policy if exists nexa_follow_requests_update on public.follow_requests;
create policy nexa_follow_requests_update on public.follow_requests
for update to authenticated using (target_id=auth.uid() or requester_id=auth.uid())
with check (
 (target_id=auth.uid() and status in ('accepted','declined'))
 or (requester_id=auth.uid() and status='cancelled')
);
grant select,insert,update on public.follow_requests to authenticated;

create or replace function public.nexa_can_view_post(target_post_id uuid)
returns boolean language sql stable security definer set search_path=public as $$
 select exists (
  select 1 from public.posts p
  where p.id=target_post_id and (
   p.author_id=auth.uid()
   or (
    (p.audience='PUBLIC_LOCAL' or p.audience='FOLLOWERS')
    and (
     not coalesce((select s.is_private_account from public.user_privacy_settings s where s.user_id=p.author_id),false)
     or exists(select 1 from public.follows f where f.follower_id=auth.uid() and f.following_id=p.author_id)
    )
    and (
     p.audience='PUBLIC_LOCAL'
     or exists(select 1 from public.follows f where f.follower_id=auth.uid() and f.following_id=p.author_id)
    )
   )
  )
 );
$$;
grant execute on function public.nexa_can_view_post(uuid) to authenticated;
drop policy if exists nexa_posts_select on public.posts;
create policy nexa_posts_select on public.posts for select to authenticated
using (public.nexa_can_view_post(id));

drop policy if exists nexa_follows_insert on public.follows;
create policy nexa_follows_insert on public.follows for insert to authenticated
with check (
 follower_id=auth.uid()
 and (
  not coalesce((select s.is_private_account from public.user_privacy_settings s where s.user_id=following_id),false)
  or exists(select 1 from public.follow_requests r
    where r.requester_id=follower_id and r.target_id=following_id and r.status='accepted')
 )
);

create or replace function public.nexa_request_follow(target_id uuid)
returns text language plpgsql security definer set search_path=public as $$
declare is_private boolean;
begin
 if auth.uid() is null or auth.uid()=target_id then raise exception 'invalid follow request'; end if;
 select coalesce(s.is_private_account,false) into is_private
 from public.profiles p left join public.user_privacy_settings s on s.user_id=p.id
 where p.id=target_id;
 if not found then raise exception 'profile not found'; end if;
 if not is_private then
  insert into public.follows(follower_id,following_id) values(auth.uid(),target_id)
  on conflict do nothing;
  return 'following';
 end if;
 insert into public.follow_requests(requester_id,target_id,status)
 values(auth.uid(),target_id,'pending')
 on conflict (requester_id,target_id) where status='pending' do nothing;
 return 'requested';
end;
$$;

create or replace function public.nexa_respond_follow_request(request_id uuid, approve boolean)
returns boolean language plpgsql security definer set search_path=public as $$
declare req public.follow_requests%rowtype;
begin
 select * into req from public.follow_requests where id=request_id and target_id=auth.uid() and status='pending' for update;
 if not found then return false; end if;
 update public.follow_requests set status=case when approve then 'accepted' else 'declined' end, updated_at=now()
 where id=request_id;
 if approve then
  insert into public.follows(follower_id,following_id) values(req.requester_id,req.target_id)
  on conflict do nothing;
 end if;
 return true;
end;
$$;
grant execute on function public.nexa_request_follow(uuid) to authenticated;
grant execute on function public.nexa_respond_follow_request(uuid,boolean) to authenticated;
notify pgrst,'reload schema';
