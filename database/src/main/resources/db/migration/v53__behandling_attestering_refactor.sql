with a as (select id, attestant, status from behandling),
     b as (select json_build_object('attestering',
                                    json_build_object('attestant', a.attestant, 'underkjennelsesgrunn', null))::text as value, a.id as id
           from a)
update behandling set attestant = b.value from b where behandling.id = b.id and behandling.attestant is not null;

alter table behandling alter column attestant type jsonb using attestant::jsonb