-- Repair follow graph counters and make follow mutations go through guarded RPCs only.
-- Also prevent follow requests between accounts that have blocked one another.

create or replace function public.nexa_sync_follow_counts()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if tg_op = 'INSERT' then
        update public.profiles
           set followers_count = followers_count + 1
         where id = new.following_id;
        update public.profiles
           set following_count = following_count + 1
         where id = new.follower_id;
        return new;
    elsif tg_op = 'DELETE' then
        update public.profiles
           set followers_count = greatest(followers_count - 1, 0)
         where id = old.following_id;
        update public.profiles
           set following_count = greatest(following_count - 1, 0)
         where id = old.follower_id;
        return old;
    end if;
    return null;
end;
$$;

drop trigger if exists nexa_sync_follow_counts on public.follows;
create trigger nexa_sync_follow_counts
after insert or delete on public.follows
for each row execute function public.nexa_sync_follow_counts();

revoke all on function public.nexa_sync_follow_counts() from public, anon, authenticated;

-- Client writes must use the RPCs below; direct REST inserts could otherwise skip
-- block checks and private-account request handling.
drop policy if exists nexa_follows_insert on public.follows;
revoke insert on public.follows from anon, authenticated;
grant select, delete on public.follows to authenticated;

create or replace function public.nexa_request_follow(target_id uuid)
returns text
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    is_private boolean;
begin
    if auth.uid() is null or target_id is null or auth.uid() = target_id then
        raise exception 'invalid follow request';
    end if;

    if exists (
        select 1
          from public.blocks b
         where (b.blocker_id = auth.uid() and b.blocked_id = target_id)
            or (b.blocker_id = target_id and b.blocked_id = auth.uid())
    ) then
        raise exception 'follow is not allowed for blocked accounts';
    end if;

    select coalesce(s.is_private_account, false)
      into is_private
      from public.profiles p
      left join public.user_privacy_settings s on s.user_id = p.id
     where p.id = target_id;

    if not found then
        raise exception 'profile not found';
    end if;

    if not is_private then
        update public.follow_requests
           set status = 'cancelled', updated_at = now()
         where requester_id = auth.uid()
           and follow_requests.target_id = nexa_request_follow.target_id
           and status = 'pending';

        insert into public.follows (follower_id, following_id)
        values (auth.uid(), target_id)
        on conflict do nothing;
        return 'following';
    end if;

    insert into public.follow_requests (requester_id, target_id, status)
    values (auth.uid(), target_id, 'pending')
    on conflict (requester_id, target_id) where status = 'pending' do nothing;

    return 'requested';
end;
$$;

create or replace function public.nexa_respond_follow_request(request_id uuid, approve boolean)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    req public.follow_requests%rowtype;
begin
    if auth.uid() is null or request_id is null then
        return false;
    end if;

    select *
      into req
      from public.follow_requests
     where id = request_id
       and target_id = auth.uid()
       and status = 'pending'
     for update;

    if not found then
        return false;
    end if;

    if approve and exists (
        select 1
          from public.blocks b
         where (b.blocker_id = req.requester_id and b.blocked_id = req.target_id)
            or (b.blocker_id = req.target_id and b.blocked_id = req.requester_id)
    ) then
        raise exception 'cannot approve a blocked account';
    end if;

    update public.follow_requests
       set status = case when approve then 'accepted' else 'declined' end,
           updated_at = now()
     where id = request_id;

    if approve then
        insert into public.follows (follower_id, following_id)
        values (req.requester_id, req.target_id)
        on conflict do nothing;
    end if;

    return true;
end;
$$;

create or replace function public.nexa_cancel_follow_request(request_id uuid)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if auth.uid() is null or request_id is null then
        return false;
    end if;

    update public.follow_requests
       set status = 'cancelled', updated_at = now()
     where id = request_id
       and requester_id = auth.uid()
       and status = 'pending';

    return found;
end;
$$;

revoke all on function public.nexa_request_follow(uuid) from public, anon;
revoke all on function public.nexa_respond_follow_request(uuid, boolean) from public, anon;
revoke all on function public.nexa_cancel_follow_request(uuid) from public, anon;
grant execute on function public.nexa_request_follow(uuid) to authenticated;
grant execute on function public.nexa_respond_follow_request(uuid, boolean) to authenticated;
grant execute on function public.nexa_cancel_follow_request(uuid) to authenticated;

revoke all on function public.nexa_remove_follower(uuid) from public, anon;
grant execute on function public.nexa_remove_follower(uuid) to authenticated;

-- Reconcile any stale counters, including rows created before the trigger was restored.
update public.profiles p
   set followers_count = (
           select count(*)::integer from public.follows f where f.following_id = p.id
       ),
       following_count = (
           select count(*)::integer from public.follows f where f.follower_id = p.id
       );

notify pgrst, 'reload schema';
