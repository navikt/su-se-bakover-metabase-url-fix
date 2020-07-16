package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.BehandlingPersistenceObserver
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakPersistenceObserver
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.VilkårsvurderingPersistenceObserver
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.MånedsberegningDto
import no.nav.su.se.bakover.domain.beregning.Sats
import java.util.UUID
import javax.sql.DataSource

internal class DatabaseRepo(
    private val dataSource: DataSource
) : ObjectRepo, SakPersistenceObserver, BehandlingPersistenceObserver,
    VilkårsvurderingPersistenceObserver {

    override fun hentSak(fnr: Fnr): Sak? = using(sessionOf(dataSource)) { hentSakInternal(fnr, it) }

    private fun hentSakInternal(fnr: Fnr, session: Session): Sak? = "select * from sak where fnr=:fnr"
        .hent(mapOf("fnr" to fnr.toString()), session) { row ->
            row.toSak(session).also {
                it.addObserver(this)
            }
        }

    override fun nySøknad(sakId: UUID, søknad: Søknad): Søknad {
        return opprettSøknad(sakId = sakId, søknad = søknad)
    }

    override fun opprettSøknadsbehandling(
        sakId: UUID,
        behandling: Behandling
    ): Behandling {
        val behandlingDto = behandling.toDto()
        "insert into behandling (id, sakId, søknadId, opprettet) values (:id, :sakId, :soknadId, :opprettet)".oppdatering(
            mapOf(
                "id" to behandlingDto.id,
                "sakId" to sakId,
                "soknadId" to behandlingDto.søknad.id,
                "opprettet" to behandlingDto.opprettet
            )
        )
        behandling.addObserver(this)
        return behandling
    }

    private fun Row.toSak(session: Session): Sak {
        val sakId = UUID.fromString(string("id"))
        return Sak(
            id = sakId,
            fnr = Fnr(string("fnr")),
            opprettet = instant("opprettet"),
            søknader = hentSøknader(sakId, session),
            behandlinger = hentBehandlinger(sakId, session)
        )
    }

    fun hentSøknader(sakId: UUID, session: Session) = "select * from søknad where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            Søknad(
                id = UUID.fromString(it.string("id")),
                søknadInnhold = objectMapper.readValue<SøknadInnhold>(it.string("søknadInnhold"))
            )
        }.toMutableList()

    fun hentBehandlinger(sakId: UUID, session: Session) = "select * from behandling where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            it.toBehandling(session)
        }.toMutableList()

    override fun hentSak(sakId: UUID): Sak? = using(sessionOf(dataSource)) { hentSakInternal(sakId, it) }

    private fun hentSakInternal(sakId: UUID, session: Session): Sak? = "select * from sak where id=:sakId"
        .hent(mapOf("sakId" to sakId), session) { row ->
            row.toSak(session).also {
                it.addObserver(this)
            }
        }

    private fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        val søknadDto = søknad.toDto()
        "insert into søknad (id, sakId, søknadInnhold, opprettet) values (:id, :sakId, to_json(:soknad::json), :opprettet)".oppdatering(
            mapOf(
                "id" to søknadDto.id,
                "sakId" to sakId,
                "soknad" to objectMapper.writeValueAsString(søknadDto.søknadInnhold),
                "opprettet" to søknadDto.opprettet
            )
        )
        return søknad
    }

    override fun opprettSak(fnr: Fnr): Sak {
        val sak = Sak(fnr = fnr)
        val dto = sak.toDto()
        "insert into sak (id, fnr, opprettet) values (:id, :fnr, :opprettet)".oppdatering(
            mapOf(
                "id" to dto.id,
                "fnr" to fnr.toString(),
                "opprettet" to dto.opprettet
            )
        )
        sak.addObserver(this)
        return sak
    }

    override fun hentSøknad(søknadId: UUID): Søknad? = using(sessionOf(dataSource)) { hentSøknad(søknadId, it) }

    private fun hentSøknad(søknadId: UUID, session: Session): Søknad? = "select * from søknad where id=:id"
        .hent(mapOf("id" to søknadId), session) {
            Søknad(
                id = UUID.fromString(it.string("id")),
                søknadInnhold = objectMapper.readValue<SøknadInnhold>(it.string("søknadInnhold"))
            )
        }

    override fun hentBehandling(behandlingId: UUID): Behandling? =
        using(sessionOf(dataSource)) { hentBehandling(behandlingId, it) }

    private fun hentBehandling(behandlingId: UUID, session: Session): Behandling? =
        "select * from behandling where id=:id"
            .hent(mapOf("id" to behandlingId), session) { row ->
                row.toBehandling(session).also {
                    it.addObserver(this)
                }
            }

    private fun Row.uuid(name: String) = UUID.fromString(string(name))
    private fun Row.toBehandling(session: Session) = Behandling(
        id = uuid("id"),
        vilkårsvurderinger = hentVilkårsvurderinger(uuid("id"), session),
        opprettet = instant("opprettet"),
        søknad = hentSøknad(uuid("søknadId"), session)!!,
        beregninger = hentBeregningerInternal(uuid("id"), session)
    )

    override fun opprettVilkårsvurderinger(
        behandlingId: UUID,
        vilkårsvurderinger: List<Vilkårsvurdering>
    ): List<Vilkårsvurdering> {
        return vilkårsvurderinger.map { opprettVilkårsvurdering(behandlingId, it) }
    }

    override fun hentVilkårsvurderinger(behandlingId: UUID): MutableList<Vilkårsvurdering> =
        using(sessionOf(dataSource)) { hentVilkårsvurderinger(behandlingId, it) }

    private fun hentVilkårsvurderinger(behandlingId: UUID, session: Session): MutableList<Vilkårsvurdering> =
        "select * from vilkårsvurdering where behandlingId=:behandlingId".hentListe(
            mapOf("behandlingId" to behandlingId),
            session
        ) { row ->
            row.toVilkårsvurdering(session).also {
                it.addObserver(this)
            }
        }.toMutableList()

    private fun Row.toVilkårsvurdering(session: Session) = Vilkårsvurdering(
        id = UUID.fromString(string("id")),
        vilkår = Vilkår.valueOf(string("vilkår")),
        begrunnelse = string("begrunnelse"),
        status = Vilkårsvurdering.Status.valueOf(string("status")),
        opprettet = instant("opprettet")
    )

    private fun opprettVilkårsvurdering(behandlingId: UUID, vilkårsvurdering: Vilkårsvurdering): Vilkårsvurdering {
        val dto = vilkårsvurdering.toDto()
        "insert into vilkårsvurdering (id, behandlingId, vilkår, begrunnelse, status, opprettet) values (:id, :behandlingId, :vilkar, :begrunnelse, :status, :opprettet)"
            .oppdatering(
                mapOf(
                    "id" to dto.id,
                    "behandlingId" to behandlingId,
                    "vilkar" to dto.vilkår.name,
                    "begrunnelse" to dto.begrunnelse,
                    "status" to dto.status.name,
                    "opprettet" to dto.opprettet
                )
            )
        vilkårsvurdering.addObserver(this)
        return vilkårsvurdering
    }

    private fun String.oppdatering(params: Map<String, Any>) {
        using(sessionOf(dataSource, returnGeneratedKey = true)) {
            it.run(
                queryOf(
                    this,
                    params
                ).asUpdate
            )
        }
    }
//    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): T? = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle) }
//    private fun <T> String.hentListe(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): List<T> = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asList) }

    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), session: Session, rowMapping: (Row) -> T): T? =
        session.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)

    private fun <T> String.hentListe(
        params: Map<String, Any> = emptyMap(),
        session: Session,
        rowMapping: (Row) -> T
    ): List<T> = session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)

    override fun oppdaterVilkårsvurdering(
        vilkårsvurdering: Vilkårsvurdering
    ): Vilkårsvurdering {
        val vilkårsvurderingDto = vilkårsvurdering.toDto()
        "update vilkårsvurdering set begrunnelse = :begrunnelse, status = :status where id = :id"
            .oppdatering(
                mapOf(
                    "id" to vilkårsvurderingDto.id,
                    "begrunnelse" to vilkårsvurderingDto.begrunnelse,
                    "status" to vilkårsvurderingDto.status.name
                )
            )
        return vilkårsvurdering
    }

    override fun hentVilkårsvurdering(id: UUID): Vilkårsvurdering? = using(sessionOf(dataSource)) {
        hentVilkårsvurdering(id, it)
    }

    private fun hentVilkårsvurdering(id: UUID, session: Session) =
        "select * from vilkårsvurdering where id = :id".hent(mapOf("id" to id), session) { row ->
            row.toVilkårsvurdering(session).also {
                it.addObserver(this)
            }
        }

    override fun hentBeregninger(behandlingId: UUID): MutableList<Beregning> =
        using(sessionOf(dataSource)) { hentBeregningerInternal(behandlingId, it) }

    private fun hentBeregningerInternal(behandlingId: UUID, session: Session) =
        "select * from beregning where behandlingId = :id".hentListe(mapOf("id" to behandlingId), session) {
            it.toBeregning(session)
        }.toMutableList()

    private fun Row.toBeregning(session: Session) = Beregning(
        id = uuid("id"),
        opprettet = instant("opprettet"),
        fom = localDate("fom"),
        tom = localDate("tom"),
        sats = Sats.valueOf(string("sats")),
        månedsberegninger = hentMånedsberegninger(uuid("id"), session)
    )

    override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
        val dto = beregning.toDto()
        "insert into beregning (id, opprettet, fom, tom, behandlingId, sats) values (:id, :opprettet, :fom, :tom, :behandlingId, :sats)".oppdatering(
            mapOf(
                "id" to dto.id,
                "opprettet" to dto.opprettet,
                "fom" to dto.fom,
                "tom" to dto.tom,
                "behandlingId" to behandlingId,
                "sats" to dto.sats.name
            )
        )
        dto.månedsberegninger.forEach { opprettMånedsberegning(dto.id, it) }
        return beregning
    }

    private fun hentMånedsberegninger(beregningId: UUID, session: Session) =
        "select * from månedsberegning where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
            it.toMånedsberegning(session)
        }.toMutableList()

    private fun Row.toMånedsberegning(session: Session) = Månedsberegning(
        id = uuid("id"),
        opprettet = instant("opprettet"),
        fom = localDate("fom"),
        tom = localDate("tom"),
        grunnbeløp = int("grunnbeløp"),
        sats = Sats.valueOf(string("sats")),
        beløp = int("beløp")
    )

    private fun opprettMånedsberegning(beregningId: UUID, månedsberegning: MånedsberegningDto) {
        "insert into månedsberegning (id, opprettet, fom, tom, grunnbeløp, beregningId, sats, beløp) values (:id, :opprettet, :fom, :tom, :grunnbelop, :beregningId, :sats, :belop)".oppdatering(
            mapOf(
                "id" to månedsberegning.id,
                "opprettet" to månedsberegning.opprettet,
                "fom" to månedsberegning.fom,
                "tom" to månedsberegning.tom,
                "grunnbelop" to månedsberegning.grunnbeløp,
                "beregningId" to beregningId,
                "sats" to månedsberegning.sats.name,
                "belop" to månedsberegning.beløp
            )
        )
    }
}
