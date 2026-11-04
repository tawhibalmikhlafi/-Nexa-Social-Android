-- Automatically publish a prepared Nexa Pulse challenge every day using pg_cron.
create extension if not exists pg_cron;

create table if not exists public.pulse_prompt_bank (
  id bigint generated always as identity primary key,
  title text not null unique,
  description text not null default '',
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

insert into public.pulse_prompt_bank (title, description) values
('ما أجمل شيء حدث لك اليوم؟','شاركنا لحظة بسيطة جعلت يومك أجمل.'),
('ما مهارة تتمنى تعلمها؟','ما المهارة التي تريد إتقانها، ولماذا؟'),
('من شخص ألهمك مؤخرًا؟','شاركنا أثرًا إيجابيًا تركه شخص في حياتك.'),
('ما هدفك الصغير لهذا الأسبوع؟','اختر هدفًا واقعيًا وشاركنا خطوتك الأولى.'),
('ما كتاب أو فيلم تنصح به؟','اقترح شيئًا أعجبك واذكر السبب.'),
('ما عادة حسّنت يومك؟','شارك عادة بسيطة تساعدك في حياتك اليومية.'),
('ما مكان تتمنى زيارته؟','أخبرنا عن المكان الذي تحلم بزيارته.'),
('ما نصيحة تتمنى لو عرفتها مبكرًا؟','شارك نصيحة قد تفيد مجتمع Nexa.'),
('ما الشيء الذي تشعر بالامتنان له؟','اكتب شيئًا واحدًا تقدّره اليوم.'),
('ما مشروع تتمنى إنجازه؟','حدثنا عن فكرة تريد تحويلها إلى واقع.'),
('كيف تتعامل مع يوم صعب؟','شارك طريقة صحية تساعدك على استعادة هدوئك.'),
('ما أفضل ذكرى من طفولتك؟','استرجع ذكرى جميلة تحب مشاركتها.'),
('ما تطبيق تستخدمه كثيرًا؟','اقترح تطبيقًا مفيدًا ووضح فائدته.'),
('ما صفة تقدّرها في الأصدقاء؟','ما الصفة التي تجعل الصداقة مميزة بالنسبة لك؟'),
('ما شيء جديد جربته مؤخرًا؟','احكِ لنا عن تجربة جديدة خضتها.'),
('ما معنى النجاح بالنسبة لك؟','صف النجاح بكلماتك بعيدًا عن توقعات الآخرين.'),
('ما نصيحة تقدمها لطالب؟','شارك فكرة تساعد على الدراسة والتعلم.'),
('ما وجبتك المفضلة؟','ما الوجبة التي لا تملّ منها؟'),
('ما أمر تتطلع إليه هذا الشهر؟','شارك شيئًا تتمنى حدوثه قريبًا.'),
('ما أفضل طريقة لتعلم شيء جديد؟','شارك أسلوبًا يساعدك على الفهم والتذكر.')
on conflict (title) do nothing;

alter table public.pulse_prompt_bank enable row level security;
revoke all on public.pulse_prompt_bank from anon, authenticated;

create or replace function public.publish_next_pulse_prompt()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_start timestamptz;
  v_end timestamptz;
  v_title text;
  v_description text;
begin
  if exists (
    select 1 from public.pulse_prompts
    where is_published = true and starts_at <= now() and ends_at > now()
  ) then
    select max(ends_at) into v_start
    from public.pulse_prompts
    where is_published = true and ends_at > now();
  else
    v_start := date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';
  end if;

  if exists (
    select 1 from public.pulse_prompts
    where is_published = true and starts_at <= v_start and ends_at > v_start
  ) then
    return;
  end if;

  v_end := v_start + interval '1 day';

  select b.title, b.description into v_title, v_description
  from public.pulse_prompt_bank b
  where b.is_active = true
    and not exists (
      select 1 from public.pulse_prompts p
      where p.title = b.title
        and p.starts_at > now() - interval '30 days'
    )
  order by random()
  limit 1;

  if v_title is null then
    select title, description into v_title, v_description
    from public.pulse_prompt_bank
    where is_active = true
    order by random()
    limit 1;
  end if;

  if v_title is not null then
    insert into public.pulse_prompts(title, description, starts_at, ends_at, is_published)
    values (v_title, coalesce(v_description, ''), v_start, v_end, true);
  end if;
end;
$$;

revoke all on function public.publish_next_pulse_prompt() from public, anon, authenticated;

do $$
begin
  if exists (select 1 from cron.job where jobname = 'nexa-pulse-publish-daily') then
    perform cron.unschedule('nexa-pulse-publish-daily');
  end if;
  perform cron.schedule(
    'nexa-pulse-publish-daily',
    '5 0 * * *',
    'select public.publish_next_pulse_prompt();'
  );
end;
$$;

select public.publish_next_pulse_prompt();
