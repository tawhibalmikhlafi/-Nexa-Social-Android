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
         where (b.blocker_id = auth.uid() and b.blocked_id = nexa_request_follow.target_id)
            or (b.blocker_id = nexa_request_follow.target_id and b.blocked_id = auth.uid())
    ) then
        raise exception 'follow is not allowed for blocked accounts';
    end if;

    -- Lock the target profile so simultaneous requests for the same account
    -- are serialized before checking/inserting the pending request.
    select coalesce(s.is_private_account, false)
      into is_private
      from public.profiles p
      left join public.user_privacy_settings s on s.user_id = p.id
     where p.id = nexa_request_follow.target_id
     for update of p;

    if not found then
        raise exception 'profile not found';
    end if;

    if not is_private then
        update public.follow_requests fr
           set status = 'cancelled', updated_at = now()
         where fr.requester_id = auth.uid()
           and fr.target_id = nexa_request_follow.target_id
           and fr.status = 'pending';

        insert into public.follows (follower_id, following_id)
        values (auth.uid(), nexa_request_follow.target_id)
        on conflict do nothing;
        return 'following';
    end if;

    if not exists (
        select 1
          from public.follow_requests fr
         where fr.requester_id = auth.uid()
           and fr.target_id = nexa_request_follow.target_id
           and fr.status = 'pending'
    ) then
        insert into public.follow_requests (requester_id, target_id, status)
        values (auth.uid(), nexa_request_follow.target_id, 'pending');
    end if;

    return 'requested';
end;
$$;

revoke all on function public.nexa_request_follow(uuid) from public, anon;
grant execute on function public.nexa_request_follow(uuid) to authenticated;
notify pgrst, 'reload schema';
