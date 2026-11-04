-- Per-account starred messages synchronized across devices.
create table if not exists public.message_stars (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(message_id,user_id)
);
alter table public.message_stars enable row level security;
drop policy if exists nexa_message_stars_select on public.message_stars;
create policy nexa_message_stars_select on public.message_stars for select to authenticated using (user_id=auth.uid());
drop policy if exists nexa_message_stars_insert on public.message_stars;
create policy nexa_message_stars_insert on public.message_stars for insert to authenticated with check (
  user_id=auth.uid() and exists (
    select 1 from public.messages m join public.conversations c on c.id=m.conversation_id
    where m.id=message_id and (c.user_a=auth.uid() or c.user_b=auth.uid())
  )
);
drop policy if exists nexa_message_stars_delete on public.message_stars;
create policy nexa_message_stars_delete on public.message_stars for delete to authenticated using (user_id=auth.uid());
grant select,insert,delete on public.message_stars to authenticated;
notify pgrst,'reload schema';