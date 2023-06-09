create table resource_type (
  model_id integer not null,
  name text not null,
  schema jsonb not null,

  constraint resource_type_pkey
    primary key (model_id, name),
  constraint resource_type_from_mission_model
    foreign key (model_id)
    references mission_model
    on delete cascade
);

comment on table resource_type is e''
  'A description of a parametric activity type supported by the associated mission model.';

comment on column resource_type.name is e''
  'The name of this resource type, unique within a mission model.';
comment on column resource_type.model_id is e''
  'The model defining this resource type.';
comment on column resource_type.schema is e''
  'The structure of this resource type.';
