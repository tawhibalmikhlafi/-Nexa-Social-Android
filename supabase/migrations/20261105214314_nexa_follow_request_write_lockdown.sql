-- Follow requests are created and transitioned only by authenticated RPCs.
-- Direct table writes bypass block checks and the state-transition rules.
revoke all on table public.follow_requests from public, anon, authenticated;
grant select on table public.follow_requests to authenticated;

-- The RPCs are SECURITY DEFINER and enforce auth.uid(), ownership, status and block checks.
grant execute on function public.nexa_request_follow(uuid) to authenticated;
grant execute on function public.nexa_respond_follow_request(uuid, boolean) to authenticated;
grant execute on function public.nexa_cancel_follow_request(uuid) to authenticated;

notify pgrst, 'reload schema';
