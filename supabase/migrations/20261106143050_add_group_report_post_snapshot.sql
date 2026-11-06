alter table public.content_reports
  add column if not exists target_preview text not null default '';
