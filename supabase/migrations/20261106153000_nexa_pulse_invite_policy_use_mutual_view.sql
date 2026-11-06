drop policy if exists "pulse challenge mutual followers can invite" on public.pulse_friend_challenges;
create policy "pulse challenge mutual followers can invite"
on public.pulse_friend_challenges for insert to authenticated
with check (
  challenger_id = (select auth.uid())
  and status = 'pending'
  and exists (
    select 1 from public.pulse_prompts p
    where p.id = prompt_id and p.is_published = true
      and now() >= p.starts_at and now() < p.ends_at
  )
  and exists (
    select 1 from public.pulse_mutual_friends mf
    where mf.id = challenged_user_id
  )
);
