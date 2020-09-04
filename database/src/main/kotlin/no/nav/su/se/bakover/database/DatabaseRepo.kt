package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.BehandlingPersistenceObserver
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakPersistenceObserver
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.VilkårsvurderingPersistenceObserver
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.FradragDto
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.OppdragPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

import java.util.UUID
import javax.sql.DataSource

internal class DatabaseRepo(
    private val dataSource: DataSource
) : ObjectRepo,
    SakPersistenceObserver,
    BehandlingPersistenceObserver,
    VilkårsvurderingPersistenceObserver,
    OppdragPersistenceObserver,
    UtbetalingPersistenceObserver {

    override fun hentSak(fnr: Fnr): Sak? = using(sessionOf(dataSource)) { hentSakInternal(fnr, it) }

    private fun hentSakInternal(fnr: Fnr, session: Session): Sak? = "select * from sak where fnr=:fnr"
        .hent(mapOf("fnr" to fnr.toString()), session) { it.toSak(session) }

    override fun nySøknad(sakId: UUID, søknad: Søknad): Søknad {
        return opprettSøknad(sakId = sakId, søknad = søknad)
    }

    override fun opprettSøknadsbehandling(
        sakId: UUID,
        behandling: Behandling
    ): Behandling {
        val behandlingDto = behandling.toDto()
        "insert into behandling (id, sakId, søknadId, opprettet, status) values (:id, :sakId, :soknadId, :opprettet, :status)".oppdatering(
            mapOf(
                "id" to behandlingDto.id,
                "sakId" to sakId,
                "soknadId" to behandlingDto.søknad.id,
                "opprettet" to behandlingDto.opprettet,
                "status" to behandling.status().name
            )
        )
        behandling.addObserver(this)
        return behandling
    }

    internal fun hentOppdrag(oppdragId: UUID30) = using(sessionOf(dataSource)) { session ->
        "select * from oppdrag where id=:id".hent(mapOf("id" to oppdragId.toString()), session) {
            it.toOppdrag(session)
        }
    }

    override fun opprettUbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling {
        """
            insert into utbetaling (id, opprettet, oppdragId, behandlingId)
            values (:id, :opprettet, :oppdragId, :behandlingId)
         """.oppdatering(
            mapOf(
                "id" to utbetaling.id.toString(),
                "opprettet" to utbetaling.opprettet,
                "oppdragId" to oppdragId.toString(),
                "behandlingId" to utbetaling.behandlingId
            )
        )
        utbetaling.utbetalingslinjer.forEach { opprettUtbetalingslinje(utbetaling.id, it) }
        utbetaling.addObserver(this)
        return utbetaling
    }

    internal fun opprettUtbetalingslinje(utbetalingId: UUID30, utbetalingslinje: Utbetalingslinje): Utbetalingslinje {
        """
            insert into utbetalingslinje (id, opprettet, fom, tom, utbetalingId, forrigeUtbetalingslinjeId, beløp)
            values (:id, :opprettet, :fom, :tom, :utbetalingId, :forrigeUtbetalingslinjeId, :belop)
        """.oppdatering(
            mapOf(
                "id" to utbetalingslinje.id.toString(),
                "opprettet" to utbetalingslinje.opprettet,
                "fom" to utbetalingslinje.fom,
                "tom" to utbetalingslinje.tom,
                "utbetalingId" to utbetalingId.toString(),
                "forrigeUtbetalingslinjeId" to utbetalingslinje.forrigeUtbetalingslinjeId?.toString(),
                "belop" to utbetalingslinje.beløp,
            )
        )
        return utbetalingslinje
    }

    private fun Row.toSak(session: Session): Sak {
        val sakId = UUID.fromString(string("id"))
        return Sak(
            id = sakId,
            fnr = Fnr(string("fnr")),
            opprettet = instant("opprettet"),
            søknader = hentSøknaderInternal(sakId, session),
            behandlinger = hentBehandlingerForSak(sakId, session),
            oppdrag = hentOppdragForSak(sakId, session)
        ).also { it.addObserver(this@DatabaseRepo) }
    }

    private fun hentOppdragForSak(sakId: UUID, session: Session) =
        "select * from oppdrag where sakId=:sakId".hent(mapOf("sakId" to sakId), session) {
            it.toOppdrag(session)
        }!!

    private fun Row.toOppdrag(session: Session): Oppdrag {
        val oppdragId = uuid30("id")
        return Oppdrag(
            id = oppdragId,
            opprettet = instant("opprettet"),
            sakId = uuid("sakId"),
            utbetalinger = hentUtbetalinger(oppdragId, session)
        ).also { it.addObserver(this@DatabaseRepo) }
    }

    private fun hentUtbetalingerForBehandling(behandlingId: UUID, session: Session) =
        "select * from utbetaling where behandlingId=:behandlingId".hentListe(
            mapOf("behandlingId" to behandlingId),
            session
        ) {
            it.toUtbetaling(session)
        }.toMutableList()

    internal fun hentUtbetalinger(oppdragId: UUID30, session: Session) =
        "select * from utbetaling where oppdragId=:oppdragId".hentListe(
            mapOf("oppdragId" to oppdragId.toString()),
            session
        ) {
            it.toUtbetaling(session)
        }.toMutableList()

    private fun Row.toUtbetaling(session: Session): Utbetaling {
        val utbetalingId = uuid30("id")
        return Utbetaling(
            id = utbetalingId,
            opprettet = instant("opprettet"),
            oppdragId = uuid30("oppdragId"),
            behandlingId = uuid("behandlingId"),
            simulering = stringOrNull("simulering")?.let { objectMapper.readValue(it, Simulering::class.java) },
            utbetalingslinjer = hentUtbetalingslinjer(utbetalingId, session),
        ).also {
            it.addObserver(this@DatabaseRepo)
        }
    }

    internal fun hentUtbetalingslinjer(utbetalingId: UUID30, session: Session): List<Utbetalingslinje> =
        "select * from utbetalingslinje where utbetalingId=:utbetalingId".hentListe(
            mapOf("utbetalingId" to utbetalingId.toString()),
            session
        ) {
            it.toUtbetalingslinje()
        }

    private fun Row.toUtbetalingslinje(): Utbetalingslinje {
        return Utbetalingslinje(
            id = uuid30("id"),
            fom = localDate("fom"),
            tom = localDate("tom"),
            opprettet = instant("opprettet"),
            forrigeUtbetalingslinjeId = stringOrNull("forrigeUtbetalingslinjeId")?.let { uuid30("forrigeUtbetalingslinjeId") },
            beløp = int("beløp")
        )
    }

    private fun hentSøknaderInternal(sakId: UUID, session: Session) = "select * from søknad where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            it.toSøknad()
        }.toMutableList()

    private fun hentBehandlingerForSak(sakId: UUID, session: Session) = "select * from behandling where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            it.toBehandling(session)
        }.toMutableList()

    override fun hentSak(sakId: UUID): Sak? = using(sessionOf(dataSource)) { hentSakInternal(sakId, it) }

    private fun hentSakInternal(sakId: UUID, session: Session): Sak? = "select * from sak where id=:sakId"
        .hent(mapOf("sakId" to sakId), session) { it.toSak(session) }

    internal fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
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
        val opprettet = now()
        val sakId = UUID.randomUUID()
        val sak = Sak(
            id = sakId,
            fnr = fnr,
            opprettet = opprettet,
            oppdrag = Oppdrag(
                sakId = sakId,
                opprettet = opprettet
            )
        )
        """
            with inserted_sak as(insert into sak (id, fnr, opprettet) values (:sakId, :fnr, :opprettet))
            insert into oppdrag (id, opprettet, sakId) values (:oppdragId, :opprettet, :sakId)
        """.oppdatering(
            mapOf(
                "sakId" to sak.id,
                "fnr" to fnr.toString(),
                "opprettet" to sak.opprettet,
                "oppdragId" to sak.oppdrag.id.toString()
            )
        )
        sak.oppdrag.addObserver(this)
        sak.addObserver(this)
        return sak
    }

    override fun hentSøknad(søknadId: UUID): Søknad? = using(sessionOf(dataSource)) { hentSøknadInternal(søknadId, it) }

    private fun hentSøknadInternal(søknadId: UUID, session: Session): Søknad? = "select * from søknad where id=:id"
        .hent(mapOf("id" to søknadId), session) {
            it.toSøknad()
        }

    private fun Row.toSøknad(): Søknad {
        return Søknad(
            id = uuid("id"),
            søknadInnhold = objectMapper.readValue(string("søknadInnhold")),
            opprettet = instant("opprettet")
        )
    }

    override fun hentBehandling(behandlingId: UUID): Behandling? =
        using(sessionOf(dataSource)) { hentBehandling(behandlingId, it) }

    private fun hentBehandling(behandlingId: UUID, session: Session): Behandling? =
        "select * from behandling where id=:id"
            .hent(mapOf("id" to behandlingId), session) { row ->
                row.toBehandling(session)
            }

    private fun Row.uuid(name: String) = UUID.fromString(string(name))
    private fun Row.uuid30(name: String) = UUID30.fromString(string(name))
    private fun Row.toBehandling(session: Session): Behandling {
        val behandlingId = uuid("id")
        return Behandling(
            id = behandlingId,
            vilkårsvurderinger = hentVilkårsvurderingerInternal(behandlingId, session),
            opprettet = instant("opprettet"),
            søknad = hentSøknadInternal(uuid("søknadId"), session)!!,
            beregninger = hentBeregningerInternal(behandlingId, session),
            utbetalinger = hentUtbetalingerForBehandling(behandlingId, session),
            status = Behandling.BehandlingsStatus.valueOf(string("status")),
            attestant = stringOrNull("attestant")?.let { Attestant(it) },
            sakId = uuid("sakId")
        ).also {
            it.addObserver(this@DatabaseRepo)
        }
    }

    override fun opprettVilkårsvurderinger(
        behandlingId: UUID,
        vilkårsvurderinger: List<Vilkårsvurdering>
    ): List<Vilkårsvurdering> {
        return vilkårsvurderinger.map { opprettVilkårsvurdering(behandlingId, it) }
    }

    override fun hentVilkårsvurderinger(behandlingId: UUID): MutableList<Vilkårsvurdering> =
        using(sessionOf(dataSource)) { hentVilkårsvurderingerInternal(behandlingId, it) }

    private fun hentVilkårsvurderingerInternal(behandlingId: UUID, session: Session): MutableList<Vilkårsvurdering> =
        "select * from vilkårsvurdering where behandlingId=:behandlingId".hentListe(
            mapOf("behandlingId" to behandlingId),
            session
        ) {
            it.toVilkårsvurdering()
        }.toMutableList()

    private fun Row.toVilkårsvurdering() = Vilkårsvurdering(
        id = UUID.fromString(string("id")),
        vilkår = Vilkår.valueOf(string("vilkår")),
        begrunnelse = string("begrunnelse"),
        status = Vilkårsvurdering.Status.valueOf(string("status")),
        opprettet = instant("opprettet")
    ).also { it.addObserver(this@DatabaseRepo) }

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

    private fun String.oppdatering(params: Map<String, Any?>) {
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
        månedsberegninger = hentMånedsberegninger(uuid("id"), session),
        fradrag = hentFradrag(uuid("id"), session)
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
        beregning.månedsberegninger.forEach { opprettMånedsberegning(dto.id, it) }
        dto.fradrag.forEach { opprettFradrag(dto.id, it) }
        return beregning
    }

    override fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.BehandlingsStatus
    ): Behandling.BehandlingsStatus {
        "update behandling set status = :status where id = :id".oppdatering(
            mapOf(
                "id" to behandlingId,
                "status" to status.name
            )
        )
        return status
    }

    override fun hentOppdrag(sakId: UUID): Oppdrag {
        return using(sessionOf(dataSource)) {
            hentOppdragForSak(sakId, it)
        }
    }

    override fun hentFnr(sakId: UUID): Fnr {
        return using(sessionOf(dataSource)) {
            "select fnr from sak where id=:sakId".hent(mapOf("sakId" to sakId), it) {
                Fnr(it.string("fnr"))
            }!!
        }
    }

    override fun attester(behandlingId: UUID, attestant: Attestant): Attestant {
        "update behandling set attestant = :attestant where id=:id".oppdatering(
            mapOf(
                "id" to behandlingId,
                "attestant" to attestant.id
            )
        )
        return attestant
    }

    private fun hentMånedsberegninger(beregningId: UUID, session: Session) =
        "select * from månedsberegning where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
            it.toMånedsberegning()
        }.toMutableList()

    private fun Row.toMånedsberegning() = Månedsberegning(
        id = uuid("id"),
        opprettet = instant("opprettet"),
        fom = localDate("fom"),
        tom = localDate("tom"),
        grunnbeløp = int("grunnbeløp"),
        sats = Sats.valueOf(string("sats")),
        beløp = int("beløp"),
        fradrag = int("fradrag")
    )

    private fun opprettMånedsberegning(beregningId: UUID, månedsberegning: Månedsberegning) {
        """
            insert into månedsberegning (id, opprettet, fom, tom, grunnbeløp, beregningId, sats, beløp, fradrag)
            values (:id, :opprettet, :fom, :tom, :grunnbelop, :beregningId, :sats, :belop, :fradrag)
        """.oppdatering(
            mapOf(
                "id" to månedsberegning.id,
                "opprettet" to månedsberegning.opprettet,
                "fom" to månedsberegning.fom,
                "tom" to månedsberegning.tom,
                "grunnbelop" to månedsberegning.grunnbeløp,
                "beregningId" to beregningId,
                "sats" to månedsberegning.sats.name,
                "belop" to månedsberegning.beløp,
                "fradrag" to månedsberegning.fradrag
            )
        )
    }

    private fun hentFradrag(beregningId: UUID, session: Session) =
        "select * from fradrag where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
            it.toFradrag()
        }.toMutableList()

    private fun Row.toFradrag() = Fradrag(
        id = uuid("id"),
        beløp = int("beløp"),
        beskrivelse = stringOrNull("beskrivelse"),
        type = Fradragstype.valueOf(string("fradragstype"))
    )

    private fun opprettFradrag(beregningId: UUID, fradrag: FradragDto) {
        """
            insert into fradrag (id, beregningId, fradragstype, beløp, beskrivelse)
            values (:id, :beregningId, :fradragstype, :belop, :beskrivelse)
        """
            .oppdatering(
                mapOf(
                    "id" to fradrag.id,
                    "beregningId" to beregningId,
                    "fradragstype" to fradrag.type.toString(),
                    "belop" to fradrag.beløp,
                    "beskrivelse" to fradrag.beskrivelse
                )
            )
    }

    override fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Simulering {
        "update utbetaling set simulering = to_json(:simulering::json) where id = :id".oppdatering(
            mapOf(
                "id" to utbetalingId.toString(),
                "simulering" to objectMapper.writeValueAsString(simulering)
            )
        )
        return simulering
    }
}
