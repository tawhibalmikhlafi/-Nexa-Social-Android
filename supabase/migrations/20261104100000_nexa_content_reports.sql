-- Nexa content reports. A signed-in user can submit and view only their own reports.
-- Duplicate reports for the same target by the same reporter are idempotent.

create table if not exists public.content_reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references public.profiles(id) on delete cascade,
    target_type text not null check (target_type in ('post','comment','user','clip','community')),
    target_id uuid not null,
    reason text not null check (reason in ('spam','harassment','hate_speech','misinformation','sexual_content','violence','self_harm','impersonation','other')),
    note text not null default '' check (char_length(note) <= 1000),
    created_at timestamptz not null default now(),
    unique (reporter_id, target_type, target_id)
);

create index if not exists content_reports_created_at_idx
    on public.content_reports (created_at desc);

alter table public.content_reports enable row level security;

drop policy if exists content_reports_insert_own on public.content_reports;
create policy content_reports_insert_own
    on public.content_reports
    for insert to authenticated
    with check (reporter_id = (select auth.uid()));

drop policy if exists content_reports_select_own on public.content_reports;
create policy content_reports_select_own
    on public.content_reports
    for select to authenticated
    using (reporter_id = (select auth.uid()));

revoke all on public.content_reports from anon, public;
grant select, insert on public.content_reports to authenticated;
