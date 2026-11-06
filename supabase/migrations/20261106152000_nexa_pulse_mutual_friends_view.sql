-- Expose only the signed-in user's mutual-follow friends for Pulse invitations.
create or replace view public.pulse_mutual_friends
with (security_barrier = true)
as
select p.id, p.username, p.display_name, p.avatar_url
from public.follows outgoing
join public.follows incoming
  on incoming.follower_id = outgoing.following_id
 and incoming.following_id = outgoing.follower_id
join public.profiles p
  on p.id = outgoing.following_id
where outgoing.follower_id = auth.uid()
  and incoming.following_id = auth.uid();

revoke all on public.pulse_mutual_friends from anon, authenticated;
grant select on public.pulse_mutual_friends to authenticated;
