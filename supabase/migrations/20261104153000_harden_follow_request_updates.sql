-- Follow-request state transitions must go through SECURITY DEFINER RPCs.
drop policy if exists nexa_follow_requests_update on public.follow_requests;
revoke update on public.follow_requests from authenticated;

create or replace function public.nexa_cancel_follow_request(request_id uuid)
returns boolean
language plpgsql
security definer
set search_path=public
as $$
begin
 update public.follow_requests
 set status='cancelled', updated_at=now()
 where id=request_id and requester_id=auth.uid() and status='pending';
 return found;
end;
$$;

grant execute on function public.nexa_cancel_follow_request(uuid) to authenticated;
grant execute on function public.nexa_respond_follow_request(uuid,boolean) to authenticated;
grant execute on function public.nexa_request_follow(uuid) to authenticated;
notify pgrst,'reload schema';
