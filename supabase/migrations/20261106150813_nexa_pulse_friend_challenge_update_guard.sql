create or replace function public.guard_pulse_friend_challenge_response()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if old.status <> 'pending'
     or old.challenged_user_id <> (select auth.uid())
     or new.id <> old.id
     or new.prompt_id <> old.prompt_id
     or new.challenger_id <> old.challenger_id
     or new.challenged_user_id <> old.challenged_user_id
     or new.message <> old.message
     or new.created_at <> old.created_at
     or new.status not in ('accepted','declined')
     or new.responded_at is null then
    raise exception 'Invalid Pulse challenge response';
  end if;
  return new;
end;
$$;

revoke all on function public.guard_pulse_friend_challenge_response() from public, anon, authenticated;
drop trigger if exists pulse_guard_friend_challenge_response on public.pulse_friend_challenges;
create trigger pulse_guard_friend_challenge_response
before update on public.pulse_friend_challenges
for each row execute function public.guard_pulse_friend_challenge_response();
