alter table public.content_reports
  drop constraint if exists content_reports_target_type_check;

alter table public.content_reports
  add constraint content_reports_target_type_check
  check (target_type = any (array[
    'post', 'comment', 'user', 'clip', 'community', 'group_post'
  ]::text[]));
