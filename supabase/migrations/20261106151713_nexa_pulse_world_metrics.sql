alter table public.pulse_entries
  add column if not exists world_id text,
  add column if not exists world_name text;

alter table public.pulse_entries
  drop constraint if exists pulse_entry_world_reference_pair;
alter table public.pulse_entries
  add constraint pulse_entry_world_reference_pair check (
    (world_id is null and world_name is null)
    or (
      world_id is not null
      and char_length(trim(coalesce(world_name, ''))) between 1 and 100
    )
  );

create index if not exists pulse_entries_world_activity_idx
  on public.pulse_entries (world_id, created_at desc)
  where world_id is not null;
