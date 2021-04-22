package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuidOrNull
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.util.UUID
import javax.sql.DataSource

internal class VilkårsvurderingPostgresRepo(
    private val dataSource: DataSource,
    private val grunnlagPostgresRepo: GrunnlagPostgresRepo,
) : VilkårsvurderingRepo {

    override fun lagre(behandlingId: UUID, vilkår: Vilkår.Vurdert<Grunnlag.Uføregrunnlag>) {
        dataSource.withTransaction { tx ->
            slettForBehandlingId(behandlingId, tx)
            vilkår.vurdering.forEach {
                lagre(behandlingId, it, tx)
            }
        }
    }

    private fun lagre(behandlingId: UUID, vurderingsperiode: Vurderingsperiode<Grunnlag.Uføregrunnlag>, session: Session) {
        """
                insert into vilkårsvurdering_uføre
                (
                    id,
                    opprettet,
                    behandlingId,
                    uføre_grunnlag_id,
                    vurdering,
                    resultat,
                    begrunnelse,
                    fraOgMed,
                    tilOgMed
                ) values 
                (
                    :id,
                    :opprettet,
                    :behandlingId,
                    :ufore_grunnlag_id,
                    :vurdering,
                    :resultat,
                    :begrunnelse,
                    :fraOgMed,
                    :tilOgMed
                )
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to vurderingsperiode.id,
                    "opprettet" to vurderingsperiode.opprettet,
                    "behandlingId" to behandlingId,
                    "ufore_grunnlag_id" to vurderingsperiode.grunnlag?.id,
                    "vurdering" to when (vurderingsperiode) {
                        is Vurderingsperiode.Manuell -> "MANUELL"
                    },
                    "resultat" to when (vurderingsperiode.resultat) {
                        Resultat.Avslag -> "AVSLAG"
                        Resultat.Innvilget -> "INNVILGET"
                    },
                    "begrunnelse" to vurderingsperiode.begrunnelse,
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                session,
            )
    }

    private fun slettForBehandlingId(behandlingId: UUID, session: Session) {
        """
                delete from vilkårsvurdering_uføre vu 
                where vu.behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            )
    }

    fun hent(behandlingId: UUID): Vilkår<Grunnlag.Uføregrunnlag> {
        val vurderingsperioder = dataSource.withSession { session ->
            """
                select * from vilkårsvurdering_uføre vu
                join behandling b on vu.behandlingid = b.id
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId,
                    ),
                    session,
                ) {
                    it.toVurderingsperioder()
                }
        }
        return Vilkår.Vurdert.Uførhet(
            vurdering = vurderingsperioder,
        )
    }

    private fun Row.toVurderingsperioder(): Vurderingsperiode<Grunnlag.Uføregrunnlag> {
        return Vurderingsperiode.Manuell(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            resultat = when (val resultat = string("resultat")) {
                "AVSLAG" -> Resultat.Avslag
                "INNVILGET" -> Resultat.Innvilget
                else -> throw IllegalStateException("Kan ikke mappe databaseverdi til domenemodell. Ukjent uføregrunnlagsresultat: $resultat")
            },
            grunnlag = uuidOrNull("uføre_grunnlag_id")?.let {
                grunnlagPostgresRepo.hentForUføregrunnlagId(it)
            },
            begrunnelse = string("begrunnelse"),
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }
}
