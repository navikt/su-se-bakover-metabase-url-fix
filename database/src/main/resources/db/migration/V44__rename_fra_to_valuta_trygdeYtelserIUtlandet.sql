UPDATE søknad SET søknadInnhold = jsonb_set(søknadinnhold, '{inntektOgPensjon, trygdeytelseIUtlandet}', '[]') where søknadinnhold #>> '{inntektOgPensjon, trygdeytelseIUtlandet}' != '[]';
UPDATE søknad SET søknadInnhold = jsonb_set(søknadinnhold, '{ektefelle, inntektOgPensjon, trygdeytelseIUtlandet}', '[]') where søknadinnhold #>> '{ektefelle, inntektOgPensjon, trygdeytelseIUtlandet}' != '[]';