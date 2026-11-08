-- Preserve report history when an account that reviewed a report is deleted.
alter table public.content_reports
  drop constraint if exists content_reports_reviewed_by_fkey;

alter table public.content_reports
  add constraint content_reports_reviewed_by_fkey
  foreign key (reviewed_by) references public.profiles(id)
  on delete set null;
