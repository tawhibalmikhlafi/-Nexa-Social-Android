create index if not exists pulse_entries_user_created_idx
  on public.pulse_entries (user_id, created_at desc);
create index if not exists pulse_comments_user_created_idx
  on public.pulse_comments (user_id, created_at desc);
