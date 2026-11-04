-- Image-only Pulse entries are allowed by the Android composer; keep the DB constraint aligned.
alter table public.pulse_entries
  drop constraint if exists pulse_entry_body_length;
alter table public.pulse_entries
  add constraint pulse_entry_body_length check (
    (char_length(trim(body)) between 1 and 1000)
    or (char_length(trim(body)) = 0 and image_path is not null)
  );
