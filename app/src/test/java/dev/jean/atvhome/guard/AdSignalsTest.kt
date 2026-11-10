package dev.jean.atvhome.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The real assets/guard/rules.json against the texts streaming players show, in several languages. */
class AdSignalsTest {

    private val signals = AdSignals(rules())

    private fun judge(vararg labels: Label, countdown: Label? = null) =
        signals.judge(Reading(Source.SCREEN, labels = labels.toList()), countdown)

    @Test fun mediaSessionFlagIsAnAd() {
        assertEquals(Verdict.Ad(), signals.judge(Reading(Source.SESSION, adFlag = true)))
    }

    @Test fun badgesInEveryLanguageAreAds() {
        for (text in listOf("Anúncio 1 de 2 · 0:23", "Ad 0:15", "Anuncio 0:10", "Publicité 12", "Werbung 5", "Annuncio 1 di 2", "Anúncio")) {
            assertTrue(text, judge(l(text)) is Verdict.Ad)
        }
    }

    @Test fun aBareBadgeOnABusyScreenIsAMenu() {
        val menu = (1..9).map { l("Item $it", 0.5f, 0.1f * it) }
        assertEquals(Verdict.None, judge(l("Anúncio"), *menu.toTypedArray()))
    }

    @Test fun aSubtitleSayingTheWordIsNotABreak() {
        assertEquals(Verdict.None, judge(l("Comercial!", 0.5f, 0.88f)))
        assertEquals(Verdict.None, judge(l("Anúncio", 0.48f, 0.9f)))
    }

    @Test fun aSponsoredCardInARowOfCardsIsNotABreak() {
        val row = listOf("Trailer oficial", "Canal Exemplo", "1,2 mi de visualizações", "Lançamentos").map { l(it, 0.3f, 0.85f) }
        assertEquals(Verdict.None, judge(l("Ad", 0.1f, 0.8f), *row.toTypedArray()))
        // A real player badge carries its countdown, so it still counts there
        assertTrue(judge(l("Ad · 0:15", 0.1f, 0.8f), *row.toTypedArray()) is Verdict.Ad)
    }

    @Test fun wordsThatOnlyLookLikeAdsAreNot() {
        for (text in listOf("Ad Astra", "Adicionar à lista", "Anunciação")) assertEquals(text, Verdict.None, judge(l(text)))
    }

    @Test fun skipButtons() {
        assertTrue(judge(l("Pular anúncio")) is Verdict.Skippable)
        assertTrue(judge(l("Skip Ad")) is Verdict.Skippable)
        val bare = judge(l("Pular", 0.9f, 0.85f), l("Anúncio 0:05", 0.1f, 0.9f))
        assertEquals("Pular", (bare as Verdict.Skippable).button.text)
    }

    @Test fun skipWithoutAnAdOrForTheIntroIsNeverPressed() {
        for (text in listOf("Pular", "Pular abertura", "Skip Intro", "Pular resumo")) assertEquals(text, Verdict.None, judge(l(text)))
        assertTrue(judge(l("Pular abertura"), l("Anúncio 0:05", 0.1f, 0.9f)) is Verdict.Ad)
    }

    @Test fun viewIdsNameTheButton() {
        val reading = Reading(Source.TREE, labels = listOf(Label("", 0.9f, 0.9f, viewId = "com.x:id/skip_ad_button")))
        assertTrue(signals.judge(reading) is Verdict.Skippable)
    }

    @Test fun subtitlesAndOpenControlsAreNotAds() {
        assertEquals(Verdict.None, judge(l("Ela disse que o anúncio da festa saiu no jornal de ontem à noite")))
        assertEquals(Verdict.None, judge(l("1:23:45"), l("Pausar"), l("Legendas")))
    }

    @Test fun aProvenCountdownAloneIsAnAd() {
        val counter = l("12")
        val verdict = judge(countdown = counter)
        assertTrue(verdict is Verdict.Ad && counter in verdict.keep)
    }

    companion object {
        /** Gradle runs unit tests from app/: the test proves the JSON that ships in the APK. */
        fun rules() = Rules.parse(File("src/main/assets/guard/rules.json").readText())
        fun l(text: String, x: Float = 0.9f, y: Float = 0.9f) = Label(text, x, y)
    }
}
