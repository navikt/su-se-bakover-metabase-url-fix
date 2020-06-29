package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class PersistentDomainObjectTest {
    @Test
    fun `throw exception if multiple persistence observers assigned`() {
        val sak = Sak(Fnr("12345678910"), 1L, mutableListOf())
        assertDoesNotThrow { sak.addObserver(someObserver) }
        assertThrows<PersistenceObserverException> { sak.addObserver(someObserver) }
    }

    @Test
    fun `throw exception if unassigned persistence observer is invoked`() {
        val sak = Sak(Fnr("12345678910"), 1L, mutableListOf())
        assertThrows<UninitializedPropertyAccessException> { sak.nySøknad(SøknadInnholdTestdataBuilder.build()) }
    }

    private val someObserver = object : SakPersistenceObserver {
        override fun nySøknad(sakId: Long, søknadInnhold: SøknadInnhold): Stønadsperiode =
            Stønadsperiode(1L, Søknad(1L, SøknadInnholdTestdataBuilder.build()))
    }
}
