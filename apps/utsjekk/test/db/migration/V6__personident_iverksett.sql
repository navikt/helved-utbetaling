alter table iverksett
    add column person_id text null;

create index iverksett_person_id_idx on iverksett(person_id);