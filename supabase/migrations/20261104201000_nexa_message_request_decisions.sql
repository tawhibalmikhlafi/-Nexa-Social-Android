create table if not exists public.message_request_decisions (
  user_id uuid not null references public.profiles(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  status text not null check (status in ('accepted','rejected')),
  updated_at timestamptz not null default now(),
  primary key (user_id, conversation_id)
);
alter table public.message_request_decisions enable row level security;
drop policy if exists message_request_decisions_select_self on public.message_request_decisions;
create policy message_request_decisions_select_self on public.message_request_decisions for select to authenticated using (user_id = auth.uid());
drop policy if exists message_request_decisions_insert_self on public.message_request_decisions;
create policy message_request_decisions_insert_self on public.message_request_decisions for insert to authenticated with check (user_id = auth.uid());
drop policy if exists message_request_decisions_update_self on public.message_request_decisions;
create policy message_request_decisions_update_self on public.message_request_decisions for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
grant select, insert, update on public.message_request_decisions to authenticated;
