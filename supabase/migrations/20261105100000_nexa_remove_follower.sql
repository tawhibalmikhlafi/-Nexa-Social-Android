-- Allow a profile owner to remove an incoming follower safely.
-- The function runs as the caller; RLS remains the authorization boundary.
drop policy if exists nexa_follows_delete_own_followers on public.follows;
create policy nexa_follows_delete_own_followers
on public.follows
for delete
to authenticated
using (following_id = (select auth.uid()));

create or replace function public.nexa_remove_follower(p_follower_id uuid)
returns boolean
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
    removed_count integer;
begin
    if (select auth.uid()) is null
       or p_follower_id is null
       or p_follower_id = (select auth.uid()) then
        return false;
    end if;

    delete from public.follows
    where follower_id = p_follower_id
      and following_id = (select auth.uid());

    get diagnostics removed_count = row_count;
    return removed_count > 0;
end;
$$;

revoke all on function public.nexa_remove_follower(uuid) from public;
grant execute on function public.nexa_remove_follower(uuid) to authenticated;

notify pgrst, 'reload schema';
