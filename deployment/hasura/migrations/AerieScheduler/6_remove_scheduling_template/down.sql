-- Scheduling Template
create table scheduling_template (
  id integer generated always as identity,
  revision integer not null default 0,

  model_id integer not null,
  description text not null,
  simulation_arguments jsonb not null,

  constraint scheduling_template_synthetic_key
    primary key (id)
);

comment on table scheduling_template is e''
  'A template scheduling spec from which scheduling requests may be based.'
  '\n'
  'The associated scheduling goals are stored in the scheduling_template_goals table.';
comment on column scheduling_template.id is e''
  'The synthetic identifier for this scheduling template.';
comment on column scheduling_template.revision is e''
  'A monotonic clock that ticks for every change to this scheduling template.';
comment on column scheduling_template.model_id is e''
  'The mission model used to which this scheduling template is associated.';
comment on column scheduling_template.description is e''
  'A text description of this scheduling template.';
comment on column scheduling_template.simulation_arguments is e''
  'A set of simulation arguments to be used for simulation during scheduling.';

create function increment_revision_on_update_scheduling_template()
  returns trigger
  security definer
language plpgsql as $$begin
  new.revision = old.revision + 1;
return new;
end$$;

create trigger increment_revision_on_update_scheduling_template_trigger
  before update on scheduling_template
  for each row
  when (pg_trigger_depth() < 1)
  execute function increment_revision_on_update_scheduling_template();

-- Scheduling Template Goals
create table scheduling_template_goals (
  template_id integer not null,
  goal_id integer not null,
  priority integer
    not null
    default null -- Nulls are detected and replaced with the next
                 -- available priority by the insert trigger
    constraint non_negative_template_goal_priority check (priority >= 0),

  constraint scheduling_template_goals_primary_key
    primary key (template_id, goal_id),
  constraint scheduling_template_goals_unique_priorities
    unique (template_id, priority) deferrable initially deferred,
  constraint scheduling_template_goals_references_scheduling_template
    foreign key (template_id)
      references scheduling_template
      on update cascade
      on delete cascade,
  constraint scheduling_template_goals_references_scheduling_goals
    foreign key (goal_id)
      references scheduling_goal
      on update cascade
      on delete cascade
);

comment on table scheduling_template_goals is e''
  'A join table associating scheduling templates with scheduling goals.';
comment on column scheduling_template_goals.template_id is e''
  'The ID of the scheduling template a scheduling goal is associated with.';
comment on column scheduling_template_goals.goal_id is e''
  'The ID of the scheduling goal a scheduling template is associated with.';
comment on column scheduling_template_goals.priority is e''
  'The relative priority of a scheduling goal in relation to other '
  'scheduling goals within the same template.';

create or replace function insert_scheduling_template_goal_func()
  returns trigger as $$begin
    if NEW.priority IS NULL then
      NEW.priority = (
        select coalesce(max(priority), -1) from scheduling_template_goals
        where template_id = NEW.template_id
      ) + 1;
    elseif NEW.priority > (
      select coalesce(max(priority), -1) from scheduling_template_goals
      where template_id = NEW.template_id
    ) + 1 then
      raise exception 'Inserted priority % for template_id % is not consecutive', NEW.priority, NEW.template_id;
    end if;
    update scheduling_template_goals
      set priority = priority + 1
      where template_id = NEW.template_id
        and priority >= NEW.priority;
    return NEW;
  end;$$
language plpgsql;

comment on function insert_scheduling_template_goal_func is e''
  'Checks that the inserted priority is consecutive, and reorders (increments) higher or equal priorities to make room.';

create or replace function update_scheduling_template_goal_func()
  returns trigger as $$begin
    if (pg_trigger_depth() = 1) then
      if NEW.priority > OLD.priority then
        if NEW.priority > (
          select coalesce(max(priority), -1) from scheduling_template_goals
          where template_id = new.template_id
        ) + 1 then
          raise exception 'Updated priority % for template_id % is not consecutive', NEW.priority, new.template_id;
        end if;
        update scheduling_template_goals
          set priority = priority - 1
          where template_id = NEW.template_id
            and priority between OLD.priority + 1 and NEW.priority
            and goal_id <> NEW.goal_id;
      else
        update scheduling_template_goals
        set priority = priority + 1
        where template_id = NEW.template_id
          and priority between NEW.priority and OLD.priority - 1
          and goal_id <> NEW.goal_id;
      end if;
    end if;
    return NEW;
  end;$$
language plpgsql;

comment on function update_scheduling_template_goal_func is e''
  'Checks that the updated priority is consecutive, and reorders priorities to make room.';

create function delete_scheduling_template_goal_func()
  returns trigger as $$begin
    update scheduling_template_goals
      set priority = priority - 1
      where template_id = OLD.template_id
        and priority > OLD.priority;
    return null;
  end;$$
language plpgsql;

comment on function delete_scheduling_template_goal_func() is e''
  'Reorders (decrements) priorities to fill the gap from deleted priority.';

create trigger insert_scheduling_template_goal
  before insert
  on scheduling_template_goals
  for each row
execute function insert_scheduling_template_goal_func();

create trigger update_scheduling_template_goal
  before update
  on scheduling_template_goals
  for each row
  when (OLD.priority is distinct from NEW.priority)
execute function update_scheduling_template_goal_func();

create trigger delete_scheduling_template_goal
  after delete
  on scheduling_template_goals
  for each row
execute function delete_scheduling_template_goal_func();


call migrations.mark_migration_rolled_back('6');
