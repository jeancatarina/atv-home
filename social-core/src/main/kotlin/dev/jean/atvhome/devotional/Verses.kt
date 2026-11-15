package dev.jean.atvhome.devotional

import java.time.LocalDate

data class Verse(val text: String, val ref: String)

/**
 * "Palavra do dia": one verse per day, the same for the phones and the TV. Almeida Revista e
 * Corrigida (public domain). The list is small on purpose; it cycles by calendar day.
 */
object Verses {
    val all: List<Verse> = listOf(
        Verse("O Senhor é o meu pastor; nada me faltará.", "Salmos 23:1"),
        Verse("Lâmpada para os meus pés é a tua palavra e luz para o meu caminho.", "Salmos 119:105"),
        Verse("Confia no Senhor de todo o teu coração e não te estribes no teu próprio entendimento.", "Provérbios 3:5"),
        Verse("Tudo posso naquele que me fortalece.", "Filipenses 4:13"),
        Verse("Deus é o nosso refúgio e fortaleza, socorro bem presente na angústia.", "Salmos 46:1"),
        Verse("As misericórdias do Senhor não têm fim; renovam-se cada manhã.", "Lamentações 3:22-23"),
        Verse("Entrega o teu caminho ao Senhor, confia nele, e o mais ele fará.", "Salmos 37:5"),
        Verse("Vinde a mim, todos os que estais cansados e oprimidos, e eu vos aliviarei.", "Mateus 11:28"),
        Verse("Este é o dia que fez o Senhor; regozijemo-nos e alegremo-nos nele.", "Salmos 118:24"),
        Verse("Agora, pois, permanecem a fé, a esperança e o amor, estes três, mas o maior destes é o amor.", "1 Coríntios 13:13"),
        Verse("Confia ao Senhor as tuas obras, e os teus desígnios serão estabelecidos.", "Provérbios 16:3"),
        Verse("Buscai, pois, em primeiro lugar, o seu reino e a sua justiça, e todas estas coisas vos serão acrescentadas.", "Mateus 6:33"),
    )

    fun of(date: LocalDate): Verse = all[Math.floorMod(date.toEpochDay(), all.size.toLong()).toInt()]
}
