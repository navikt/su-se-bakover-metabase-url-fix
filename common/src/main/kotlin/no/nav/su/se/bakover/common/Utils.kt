package no.nav.su.se.bakover.common

import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter

fun Int.januar(year: Int) = LocalDate.of(year, Month.JANUARY, this)
fun Int.februar(year: Int) = LocalDate.of(year, Month.FEBRUARY, this)
fun Int.mars(year: Int) = LocalDate.of(year, Month.MARCH, this)
fun Int.april(year: Int) = LocalDate.of(year, Month.APRIL, this)
fun Int.mai(year: Int) = LocalDate.of(year, Month.MAY, this)
fun Int.juni(year: Int) = LocalDate.of(year, Month.JUNE, this)
fun Int.juli(year: Int) = LocalDate.of(year, Month.JULY, this)
fun Int.august(year: Int) = LocalDate.of(year, Month.AUGUST, this)
fun Int.september(year: Int) = LocalDate.of(year, Month.SEPTEMBER, this)
fun Int.oktober(year: Int) = LocalDate.of(year, Month.OCTOBER, this)
fun Int.november(year: Int) = LocalDate.of(year, Month.NOVEMBER, this)
fun Int.desember(year: Int) = LocalDate.of(year, Month.DECEMBER, this)
fun idag(clock: Clock = Clock.systemUTC()) = LocalDate.now(clock)

fun now(clock: Clock = Clock.systemUTC()): Tidspunkt = Tidspunkt.now(clock)

fun LocalDate.startOfDay() = this.atStartOfDay().toTidspunkt()
fun LocalDate.endOfDay() = this.atStartOfDay().plusDays(1).minusNanos(1).toTidspunkt()
fun LocalDate.between(fraOgMed: LocalDate, tilOgMed: LocalDate) =
    (this == fraOgMed || this == tilOgMed) || this.isAfter(fraOgMed) && this.isBefore(tilOgMed)

fun Tidspunkt.between(fraOgMed: Tidspunkt, tilOgMed: Tidspunkt) =
    (this == fraOgMed || this == tilOgMed) || this.instant.isAfter(fraOgMed.instant) && this.instant.isBefore(tilOgMed.instant)

fun LocalDate.ddMMyyyy() = this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
